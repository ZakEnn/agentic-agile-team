package com.agile.team.application.orchestrator;

import com.agile.team.application.orchestrator.handler.SpecStageHandler;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.conversation.AgentMessage;
import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.conversation.MessageType;
import com.agile.team.domain.gate.GateDecision;
import com.agile.team.domain.gate.GateName;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.stage.StageRunRepository;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveContext;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.infrastructure.config.SdlcProperties;
import com.agile.team.infrastructure.persistence.ArtifactCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The wave lifecycle API: start a wave, decide a gate, edit a draft.
 * <p>
 * As of M3 this no longer executes anything. It creates the wave and <em>enqueues</em>
 * the first stage; {@link StageWorker} claims and runs it. That separation is the
 * point of the durable machine — the caller no longer blocks on a model call, and a
 * restart mid-wave resumes from the queue rather than losing the work.
 */
@Service
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final WaveRepository waveRepository;
    private final AgentRepository agentRepository;
    private final ConversationRepository conversationRepository;
    private final StageRunRepository stageRuns;
    private final ArtifactCodec codec;
    private final SdlcProperties properties;

    public Orchestrator(WaveRepository waveRepository,
                        AgentRepository agentRepository,
                        ConversationRepository conversationRepository,
                        StageRunRepository stageRuns,
                        ArtifactCodec codec,
                        SdlcProperties properties) {
        this.waveRepository = waveRepository;
        this.agentRepository = agentRepository;
        this.conversationRepository = conversationRepository;
        this.stageRuns = stageRuns;
        this.codec = codec;
        this.properties = properties;
    }

    /** Create a wave and enqueue its SPEC stage. Returns immediately. */
    @Transactional
    public WaveId startWave(StartWaveCommand command) {
        Wave wave = new Wave(WaveId.generate(), command.waveName(), command.context());
        waveRepository.save(wave);

        ConversationHistory history = ConversationHistory.startForWave(wave.getId());
        record(history, AgentRole.PO, MessageType.SPECIFICATION_REQUEST,
                "Wave started: " + command.taskDescription());
        conversationRepository.save(history);

        stageRuns.save(StageRun.enqueue(
                wave.getId(),
                SdlcStage.SPEC,
                codec.write(new SpecStageHandler.SpecStageInput(
                        command.taskDescription(), command.keyword())),
                properties.getBudget().getMaxStageAttempts()));

        log.info("Wave {} created and SPEC stage enqueued", wave.getId().value());
        return wave.getId();
    }

    /**
     * Record a human decision at the SPEC_APPROVAL gate and, on approval, release the
     * pipeline into the next stage.
     */
    @Transactional
    public Wave decideSpecification(WaveId waveId, SpecificationId specificationId, GateDecision decision) {
        Wave wave = requireWave(waveId);
        Specification specification = wave.findSpecification(specificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Specification " + specificationId.value() + " not found on wave " + waveId.value()));

        ConversationHistory history = conversationRepository.findByWaveId(waveId)
                .orElseGet(() -> ConversationHistory.startForWave(waveId));

        specification.applyDecision(decision);

        record(history, AgentRole.PO, MessageType.GATE_DECISION,
                "[SPEC_APPROVAL] %s by %s: %s".formatted(
                        decision.approved() ? "APPROVED" : "REJECTED",
                        decision.decidedBy(), decision.reason()));

        if (decision.approved()) {
            wave.startExecution(decision.decidedBy());
            record(history, AgentRole.PO, MessageType.WAVE_STATUS_UPDATE,
                    "Wave moved to IN_PROGRESS, authorized by " + decision.decidedBy());
            enqueueNextAfterSpec(wave, specification);
        } else {
            wave.fail(decision.decidedBy());
            record(history, AgentRole.PO, MessageType.WAVE_STATUS_UPDATE,
                    "Wave FAILED at SPEC_APPROVAL: " + decision.reason());
        }

        waveRepository.save(wave);
        conversationRepository.save(history);
        return wave;
    }

    /** Apply a human edit to a draft, before any decision has been recorded. */
    @Transactional
    public Wave editSpecification(WaveId waveId, SpecificationId specificationId,
                                  SpecDraft edited, String editedBy) {
        Wave wave = requireWave(waveId);
        Specification specification = wave.findSpecification(specificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Specification " + specificationId.value() + " not found on wave " + waveId.value()));

        specification.applyEdit(edited, editedBy);

        ConversationHistory history = conversationRepository.findByWaveId(waveId)
                .orElseGet(() -> ConversationHistory.startForWave(waveId));
        record(history, AgentRole.PO, MessageType.AGENT_NOTE, "[SPEC] Draft edited by " + editedBy);

        waveRepository.save(wave);
        conversationRepository.save(history);
        return wave;
    }

    private void enqueueNextAfterSpec(Wave wave, Specification specification) {
        SdlcStage next = SdlcStage.SPEC.next();
        if (next == null) {
            return;
        }
        if (stageRuns.findByWaveAndStage(wave.getId(), next).isPresent()) {
            log.debug("Stage {} already enqueued for wave {}", next, wave.getId().value());
            return;
        }
        stageRuns.save(StageRun.enqueue(wave.getId(), next,
                codec.write(specification.toDraft()),
                properties.getBudget().getMaxStageAttempts()));
        log.info("Wave {} approved at SPEC_APPROVAL; {} enqueued", wave.getId().value(), next);
    }

    private Wave requireWave(WaveId waveId) {
        return waveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalArgumentException("Wave not found: " + waveId.value()));
    }

    private void record(ConversationHistory history, AgentRole role, MessageType type, String payload) {
        Agent agent = agentRepository.findByRole(role).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + role + " agent registered"));
        history.record(AgentMessage.create(agent.getId(), agent.getId(), type, payload));
    }

    /**
     * @param waveName        human label
     * @param taskDescription the intent
     * @param keyword         Confluence search term; optional
     * @param context         per-wave targeting
     */
    public record StartWaveCommand(
            String waveName,
            String taskDescription,
            String keyword,
            WaveContext context
    ) {
        public StartWaveCommand {
            if (waveName == null || waveName.isBlank()) {
                throw new IllegalArgumentException("waveName must not be blank");
            }
            if (taskDescription == null || taskDescription.isBlank()) {
                throw new IllegalArgumentException("taskDescription must not be blank");
            }
            if (context == null) {
                context = WaveContext.forSpecOnly(null, null);
            }
        }
    }
}
