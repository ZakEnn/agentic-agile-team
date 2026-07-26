package com.agile.team.application.orchestrator;

import com.agile.team.application.gate.GatePolicy;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.conversation.AgentMessage;
import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.conversation.MessageType;
import com.agile.team.domain.gate.GateDecision;
import com.agile.team.domain.gate.GateName;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.infrastructure.config.SdlcProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs one claimed stage and decides what happens next.
 * <p>
 * The one place that knows the pipeline's control flow: budget check, dispatch,
 * record, gate, advance or fail. Everything it needs is either on the claimed
 * {@link StageRun} or in the database, so it is safe to run on any instance and
 * safe to resume after a restart.
 */
@Component
public class StageExecutor {

    private static final Logger log = LoggerFactory.getLogger(StageExecutor.class);

    private final Map<SdlcStage, StageHandler> handlers = new EnumMap<>(SdlcStage.class);
    private final com.agile.team.domain.stage.StageRunRepository stageRuns;
    private final WaveRepository waves;
    private final ConversationRepository conversations;
    private final AgentRepository agents;
    private final GatePolicy gatePolicy;
    private final SdlcProperties properties;
    private final MeterRegistry meterRegistry;

    public StageExecutor(List<StageHandler> stageHandlers,
                         com.agile.team.domain.stage.StageRunRepository stageRuns,
                         WaveRepository waves,
                         ConversationRepository conversations,
                         AgentRepository agents,
                         GatePolicy gatePolicy,
                         SdlcProperties properties,
                         MeterRegistry meterRegistry) {
        stageHandlers.forEach(h -> this.handlers.put(h.stage(), h));
        this.stageRuns = stageRuns;
        this.waves = waves;
        this.conversations = conversations;
        this.agents = agents;
        this.gatePolicy = gatePolicy;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        log.info("Stage handlers registered: {}", this.handlers.keySet());
    }

    /**
     * Execute a claimed stage. Never throws: every failure path is recorded on the
     * {@link StageRun} so the wave's position is always inspectable.
     */
    @Transactional
    public void execute(StageRun run) {
        MDC.put("waveId", run.getWaveId().value().toString());
        MDC.put("stage", run.getStage().name());
        try {
            Wave wave = waves.findById(run.getWaveId()).orElse(null);
            if (wave == null) {
                run.cancel("Wave no longer exists");
                stageRuns.save(run);
                return;
            }

            if (exceedsBudget(wave, run)) {
                return;
            }

            StageHandler handler = handlers.get(run.getStage());
            if (handler == null) {
                // An honest stop rather than a silent skip: the pipeline cannot
                // advance past a stage nobody implements, and pretending otherwise
                // would let a wave "complete" without ever being built or tested.
                String message = "No handler registered for stage " + run.getStage()
                        + " — the pipeline stops here until one is implemented";
                log.warn("[{}] {}", run.getStage(), message);
                run.exceedBudget(message);
                stageRuns.save(run);
                recordMessage(wave, MessageType.STAGE_FAILED, "[" + run.getStage() + "] " + message);
                return;
            }

            runHandler(run, wave, handler);
        } finally {
            MDC.remove("waveId");
            MDC.remove("stage");
        }
    }

    private void runHandler(StageRun run, Wave wave, StageHandler handler) {
        recordMessage(wave, run.getStage(), MessageType.STAGE_STARTED,
                "[%s] attempt %d/%d".formatted(run.getStage(), run.getAttempt(), run.getMaxAttempts()));

        try {
            StageHandler.StageOutcome outcome = handler.handle(run, wave);

            run.succeed(outcome.outputArtifact(), outcome.usage().total());
            stageRuns.save(run);
            addTokensToWave(wave, outcome.usage().total());

            outcome.notes().forEach(note ->
                    recordMessage(wave, run.getStage(), MessageType.AGENT_NOTE,
                            "[" + run.getStage() + "] " + note));
            recordMessage(wave, run.getStage(), MessageType.STAGE_COMPLETED,
                    "[%s] complete (%d tokens)".formatted(run.getStage(), outcome.usage().total()));

            meterRegistry.counter("sdlc.stage.completed", "stage", run.getStage().name()).increment();

            advance(run, wave, outcome.awaitApproval());

        } catch (RuntimeException e) {
            log.error("[{}] stage failed on attempt {}: {}",
                    run.getStage(), run.getAttempt(), e.getMessage());
            run.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), 0);
            stageRuns.save(run);

            meterRegistry.counter("sdlc.stage.failed",
                    "stage", run.getStage().name(),
                    "terminal", String.valueOf(run.getStatus().isFailure())).increment();

            recordMessage(wave, run.getStage(), MessageType.STAGE_FAILED,
                    "[%s] attempt %d failed: %s".formatted(
                            run.getStage(), run.getAttempt(), e.getMessage()));

            if (run.getStatus().isFailure()) {
                failWave(wave, run.getStage() + " exhausted its attempts");
            }
        }
    }

    /** Decide what happens after a successful stage: park at a gate, or enqueue the next. */
    private void advance(StageRun run, Wave wave, boolean handlerRequestedApproval) {
        SdlcStage stage = run.getStage();
        GateName gate = stage.gateAfter();

        if (gate != null) {
            Optional<GateDecision> auto = gatePolicy.tryAutoResolve(gate);
            if (auto.isEmpty() || handlerRequestedApproval) {
                recordMessage(wave, MessageType.AWAITING_APPROVAL,
                        "[%s] awaiting decision at gate %s".formatted(stage, gate.configKey()));
                meterRegistry.counter("sdlc.gate.awaiting", "gate", gate.configKey()).increment();
                return;
            }
            recordMessage(wave, MessageType.GATE_DECISION,
                    "[%s] %s by %s".formatted(gate.configKey(),
                            auto.get().approved() ? "APPROVED" : "REJECTED", auto.get().decidedBy()));
            meterRegistry.counter("sdlc.gate.decided",
                    "gate", gate.configKey(),
                    "automated", String.valueOf(auto.get().isAutomated())).increment();
            if (!auto.get().approved()) {
                failWave(wave, "gate " + gate.configKey() + " rejected");
                return;
            }
        }

        SdlcStage next = stage.next();
        if (next == null) {
            recordMessage(wave, MessageType.WAVE_STATUS_UPDATE, "Pipeline complete");
            return;
        }
        enqueueNext(wave, next, run.getOutputArtifact());
    }

    /**
     * Enqueue the next stage, tolerating a duplicate.
     * <p>
     * The idempotency key makes a second enqueue for the same (wave, stage) a
     * constraint violation rather than duplicated work — which is exactly what should
     * happen if two instances both observe the same completion.
     */
    private void enqueueNext(Wave wave, SdlcStage next, String inputArtifact) {
        if (stageRuns.findByWaveAndStage(wave.getId(), next).isPresent()) {
            log.debug("Stage {} already enqueued for wave {}", next, wave.getId().value());
            return;
        }
        stageRuns.save(StageRun.enqueue(wave.getId(), next, inputArtifact,
                properties.getBudget().getMaxStageAttempts()));
        recordMessage(wave, MessageType.TASK_ASSIGNMENT, "[%s] enqueued".formatted(next));
    }

    private boolean exceedsBudget(Wave wave, StageRun run) {
        long cap = properties.getBudget().getMaxTokensPerWave();
        if (wave.getTokensUsed() < cap) {
            return false;
        }
        String message = "Wave has consumed %d tokens, exceeding its budget of %d"
                .formatted(wave.getTokensUsed(), cap);
        log.warn("[{}] {}", run.getStage(), message);
        run.exceedBudget(message);
        stageRuns.save(run);
        recordMessage(wave, MessageType.BUDGET_EXCEEDED, message);
        meterRegistry.counter("sdlc.wave.budget_exceeded").increment();
        failWave(wave, "token budget exceeded");
        return true;
    }

    private void addTokensToWave(Wave wave, long tokens) {
        wave.addTokensUsed(tokens);
        waves.save(wave);
    }

    private void failWave(Wave wave, String reason) {
        if (wave.getStatus() != com.agile.team.domain.wave.WaveStatus.FAILED) {
            wave.fail("ORCHESTRATOR");
            waves.save(wave);
        }
        recordMessage(wave, MessageType.WAVE_STATUS_UPDATE, "Wave FAILED: " + reason);
    }

    /**
     * Append to the wave's audit trail, attributed to the agent that owns the stage
     * where possible. Attribution matters: "who said this" is half the value of a
     * trail that spans six agents.
     */
    private void recordMessage(Wave wave, MessageType type, String payload) {
        recordMessage(wave, null, type, payload);
    }

    private void recordMessage(Wave wave, SdlcStage stage, MessageType type, String payload) {
        Agent agent = resolveAgent(stage);
        if (agent == null) {
            log.warn("No agents registered; cannot record: {}", payload);
            return;
        }
        ConversationHistory history = conversations.findByWaveId(wave.getId())
                .orElseGet(() -> ConversationHistory.startForWave(wave.getId()));
        history.record(AgentMessage.create(agent.getId(), agent.getId(), type, payload));
        conversations.save(history);
    }

    private Agent resolveAgent(SdlcStage stage) {
        if (stage != null) {
            Optional<Agent> owner = agents.findByRole(stage.owner()).stream().findFirst();
            if (owner.isPresent()) {
                return owner.get();
            }
        }
        return agents.findAll().stream().findFirst().orElse(null);
    }
}
