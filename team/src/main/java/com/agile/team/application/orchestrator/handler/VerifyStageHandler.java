package com.agile.team.application.orchestrator.handler;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.QaAgent;
import com.agile.team.application.orchestrator.StageHandler;
import com.agile.team.domain.artifact.QaVerdict;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.wave.Wave;
import com.agile.team.infrastructure.persistence.ArtifactCodec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the QA agent as a durable stage.
 * <p>
 * Fails the stage when the verdict is negative, so the pipeline retries the build
 * with the QA failure as feedback rather than carrying an unverified change into
 * review. An unverified acceptance criterion is a failure here even when the test
 * suite is green — that gap is the specific thing this stage exists to catch.
 */
@Component
public class VerifyStageHandler implements StageHandler {

    private final QaAgent qaAgent;
    private final ArtifactCodec codec;

    public VerifyStageHandler(QaAgent qaAgent, ArtifactCodec codec) {
        this.qaAgent = qaAgent;
        this.codec = codec;
    }

    @Override
    public SdlcStage stage() {
        return SdlcStage.VERIFY;
    }

    @Override
    public StageOutcome handle(StageRun run, Wave wave) {
        BuildStageHandler.BuildStageOutput build =
                codec.read(run.getInputArtifact(), BuildStageHandler.BuildStageOutput.class);
        if (build == null || build.implementation() == null) {
            throw new IllegalStateException("VERIFY stage has no implementation to verify");
        }
        if (build.workspaceId() == null || build.workspaceId().isBlank()) {
            throw new IllegalStateException(
                    "VERIFY stage has no workspace: the tests must run against the build "
                            + "that was actually produced, not a re-creation of it");
        }

        SpecDraft spec = wave.getSpecifications().stream()
                .filter(Specification::isApproved)
                .findFirst()
                .map(Specification::toDraft)
                .orElseThrow(() -> new IllegalStateException(
                        "VERIFY stage has no approved specification to verify against"));

        AgentOutcome<QaVerdict> outcome = qaAgent.run(new QaAgent.QaRequest(
                wave.getId().value().toString(),
                build.workspaceId(),
                spec,
                build.implementation()));

        List<String> notes = new ArrayList<>(outcome.notes());
        QaVerdict verdict = outcome.artifact();

        if (!verdict.passed()) {
            throw new QaFailedException(describeFailure(verdict));
        }

        notes.add("All %d acceptance criteria verified".formatted(verdict.perCriterion().size()));
        // The implementation travels on so REVIEW knows which merge request to review.
        return StageOutcome.completed(
                codec.write(new VerifyStageOutput(verdict, build.implementation())),
                outcome.usage(), notes);
    }

    /** What VERIFY hands to REVIEW. */
    public record VerifyStageOutput(
            QaVerdict verdict,
            com.agile.team.domain.artifact.Implementation implementation) {
    }

    private String describeFailure(QaVerdict verdict) {
        StringBuilder sb = new StringBuilder("QA failed. ");
        if (!verdict.failingTests().isEmpty()) {
            sb.append("Failing tests: ").append(verdict.failingTests()).append(". ");
        }
        List<QaVerdict.CriterionResult> unverified = verdict.unverified();
        if (!unverified.isEmpty()) {
            sb.append("Unverified criteria: ");
            unverified.forEach(r -> sb.append("\n  - ").append(r.criterion())
                    .append(" (").append(r.evidence()).append(")"));
        }
        return sb.toString();
    }

    /** Carries the QA gap back into the build retry as feedback. */
    public static class QaFailedException extends RuntimeException {
        public QaFailedException(String message) {
            super(message);
        }
    }
}
