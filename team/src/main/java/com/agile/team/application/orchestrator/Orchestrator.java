package com.agile.team.application.orchestrator;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.SpecAgent;
import com.agile.team.application.gate.GatePolicy;
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
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveContext;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Drives a wave through the SDLC stages.
 * <p>
 * <strong>M1 scope:</strong> the SPEC stage and the SPEC_APPROVAL gate. Execution is
 * synchronous — the caller waits for the spec to be drafted. That is a deliberate
 * intermediate state (DECISIONS.md D-010): M3 replaces it with the durable,
 * DB-backed stage machine from SDLC_AGENT_PLAN.md §3.3, at which point stages
 * become resumable and multi-instance safe. Doing it synchronously first keeps M1
 * deterministic and testable rather than layering agents on top of the in-JVM
 * {@code @Async} event bus the plan identified as the actual defect.
 */
@Service
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final WaveRepository waveRepository;
    private final AgentRepository agentRepository;
    private final ConversationRepository conversationRepository;
    private final SpecAgent specAgent;
    private final GatePolicy gatePolicy;

    public Orchestrator(WaveRepository waveRepository,
                        AgentRepository agentRepository,
                        ConversationRepository conversationRepository,
                        SpecAgent specAgent,
                        GatePolicy gatePolicy) {
        this.waveRepository = waveRepository;
        this.agentRepository = agentRepository;
        this.conversationRepository = conversationRepository;
        this.specAgent = specAgent;
        this.gatePolicy = gatePolicy;
    }

    /**
     * Start a wave: create it, run the SPEC stage, and either park at the approval
     * gate or auto-approve according to policy.
     */
    @Transactional
    public WaveId startWave(StartWaveCommand command) {
        Wave wave = new Wave(WaveId.generate(), command.waveName(), command.context());
        waveRepository.save(wave);

        MDC.put("waveId", wave.getId().value().toString());
        try {
            ConversationHistory history = ConversationHistory.startForWave(wave.getId());
            Agent poAgent = requireAgent(AgentRole.PO);

            record(history, poAgent, MessageType.STAGE_STARTED,
                    "[SPEC] Drafting specification for: " + command.taskDescription());

            AgentOutcome<SpecDraft> outcome;
            try {
                outcome = specAgent.run(new SpecAgent.SpecAgentRequest(
                        wave.getId().value().toString(),
                        command.taskDescription(),
                        command.keyword(),
                        command.context()));
            } catch (RuntimeException e) {
                log.error("[SPEC] stage failed for wave={}: {}", wave.getId().value(), e.getMessage());
                record(history, poAgent, MessageType.STAGE_FAILED,
                        "[SPEC] Failed: " + e.getMessage());
                conversationRepository.save(history);
                wave.fail("SPEC_AGENT");
                waveRepository.save(wave);
                throw e;
            }

            outcome.notes().forEach(note ->
                    record(history, poAgent, MessageType.AGENT_NOTE, "[SPEC] " + note));

            Specification specification = Specification.fromDraft(
                    SpecificationId.generate(),
                    outcome.artifact(),
                    describeSource(command));
            wave.addSpecification(specification);

            record(history, poAgent, MessageType.STAGE_COMPLETED,
                    "[SPEC] Draft ready (%d acceptance criteria, %d tokens)".formatted(
                            outcome.artifact().acceptanceCriteria().size(),
                            outcome.usage().total()));

            applySpecGate(wave, specification, history, poAgent);

            waveRepository.save(wave);
            conversationRepository.save(history);
            return wave.getId();
        } finally {
            MDC.remove("waveId");
        }
    }

    /**
     * Record a human decision at the SPEC_APPROVAL gate.
     * <p>
     * This is the endpoint the original system was missing entirely: it logged
     * "awaiting manual validation" with no mechanism to supply that validation, so
     * the only way to advance was to uncomment code and redeploy.
     */
    @Transactional
    public Wave decideSpecification(WaveId waveId, SpecificationId specificationId, GateDecision decision) {
        Wave wave = waveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalArgumentException("Wave not found: " + waveId.value()));
        Specification specification = wave.findSpecification(specificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Specification " + specificationId.value() + " not found on wave " + waveId.value()));

        ConversationHistory history = conversationRepository.findByWaveId(waveId)
                .orElse(ConversationHistory.startForWave(waveId));
        Agent poAgent = requireAgent(AgentRole.PO);

        specification.applyDecision(decision);

        record(history, poAgent, MessageType.GATE_DECISION,
                "[SPEC_APPROVAL] %s by %s: %s".formatted(
                        decision.approved() ? "APPROVED" : "REJECTED",
                        decision.decidedBy(),
                        decision.reason()));

        if (decision.approved()) {
            wave.startExecution(decision.decidedBy());
            record(history, poAgent, MessageType.WAVE_STATUS_UPDATE,
                    "Wave moved to IN_PROGRESS, authorized by " + decision.decidedBy());
        } else {
            wave.fail(decision.decidedBy());
            record(history, poAgent, MessageType.WAVE_STATUS_UPDATE,
                    "Wave FAILED at SPEC_APPROVAL: " + decision.reason());
        }

        waveRepository.save(wave);
        conversationRepository.save(history);
        return wave;
    }

    /** Apply a human edit to a draft specification, before any decision is made. */
    @Transactional
    public Wave editSpecification(WaveId waveId, SpecificationId specificationId,
                                  SpecDraft edited, String editedBy) {
        Wave wave = waveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalArgumentException("Wave not found: " + waveId.value()));
        Specification specification = wave.findSpecification(specificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Specification " + specificationId.value() + " not found on wave " + waveId.value()));

        specification.applyEdit(edited, editedBy);

        ConversationHistory history = conversationRepository.findByWaveId(waveId)
                .orElse(ConversationHistory.startForWave(waveId));
        record(history, requireAgent(AgentRole.PO), MessageType.AGENT_NOTE,
                "[SPEC] Draft edited by " + editedBy);

        waveRepository.save(wave);
        conversationRepository.save(history);
        return wave;
    }

    private void applySpecGate(Wave wave, Specification specification,
                               ConversationHistory history, Agent poAgent) {
        Optional<GateDecision> auto = gatePolicy.tryAutoResolve(GateName.SPEC_APPROVAL);
        if (auto.isEmpty()) {
            record(history, poAgent, MessageType.AWAITING_APPROVAL,
                    "[SPEC_APPROVAL] Awaiting human decision. "
                            + "POST /api/waves/" + wave.getId().value()
                            + "/specifications/" + specification.getId().value() + "/approve");
            return;
        }

        GateDecision decision = auto.get();
        specification.applyDecision(decision);
        record(history, poAgent, MessageType.GATE_DECISION,
                "[SPEC_APPROVAL] %s by %s".formatted(
                        decision.approved() ? "APPROVED" : "REJECTED", decision.decidedBy()));
        if (decision.approved()) {
            wave.startExecution(decision.decidedBy());
        }
    }

    private String describeSource(StartWaveCommand command) {
        if (command.context().hasConfluenceSpace() && command.keyword() != null) {
            return command.context().confluenceSpaceKey() + ":" + command.keyword();
        }
        return "intent-only";
    }

    private Agent requireAgent(AgentRole role) {
        return agentRepository.findByRole(role).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + role + " agent registered"));
    }

    private void record(ConversationHistory history, Agent agent, MessageType type, String payload) {
        history.record(AgentMessage.create(agent.getId(), agent.getId(), type, payload));
    }

    /**
     * Everything needed to start a wave.
     *
     * @param waveName        human label
     * @param taskDescription the intent
     * @param keyword         Confluence search term; optional
     * @param context         per-wave targeting, replacing the old hardcoded constants
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
