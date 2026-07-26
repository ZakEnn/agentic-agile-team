package com.agile.team.application.orchestrator;

import com.agile.team.domain.port.LlmGateway.TokenUsage;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.wave.Wave;

import java.util.List;

/**
 * Executes one SDLC stage. One implementation per {@link SdlcStage}.
 * <p>
 * The uniform shape is what lets the orchestrator treat all six stages identically —
 * claim, run, record tokens, append notes, advance — without knowing which agent it
 * is driving. Adding a stage means adding a bean, not editing the orchestrator.
 */
public interface StageHandler {

    SdlcStage stage();

    /**
     * Run the stage.
     *
     * @param run  the durable stage record, including any input artifact
     * @param wave the wave being advanced
     * @return what happened, including tokens consumed so the budget can be enforced
     * @throws RuntimeException on failure; the orchestrator handles retry and backoff
     */
    StageOutcome handle(StageRun run, Wave wave);

    /**
     * @param outputArtifact  JSON of the stage's artifact, persisted for the next stage
     * @param usage           tokens consumed
     * @param notes           observations for the audit trail
     * @param awaitApproval   true when the stage completed but the wave must park at
     *                        a gate rather than advance
     */
    record StageOutcome(
            String outputArtifact,
            TokenUsage usage,
            List<String> notes,
            boolean awaitApproval
    ) {
        public StageOutcome {
            if (usage == null) usage = TokenUsage.unknown();
            notes = notes == null ? List.of() : List.copyOf(notes);
        }

        public static StageOutcome completed(String artifact, TokenUsage usage, List<String> notes) {
            return new StageOutcome(artifact, usage, notes, false);
        }

        public static StageOutcome awaitingApproval(String artifact, TokenUsage usage, List<String> notes) {
            return new StageOutcome(artifact, usage, notes, true);
        }
    }
}
