package com.agile.team.application.orchestrator.handler;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.ReviewerAgent;
import com.agile.team.application.orchestrator.StageHandler;
import com.agile.team.application.review.ReviewCommentFormatter;
import com.agile.team.application.usecase.SkillGovernanceUseCase;
import com.agile.team.domain.artifact.ReviewVerdict;
import com.agile.team.domain.port.GitLabPort;
import com.agile.team.domain.review.ReviewGate;
import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.wave.Wave;
import com.agile.team.infrastructure.persistence.ArtifactCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the Reviewer agent as a durable stage, and applies the {@link ReviewGate}.
 * <p>
 * This is where the governance gate finally has real inputs on both sides: a
 * disposition computed from classified findings, and a quality score fetched from
 * SonarQube (or {@code UNKNOWN}, which does not pass). The original system had the
 * gate but fed it a hardcoded {@code passing(80)} and a substring match.
 */
@Component
public class ReviewStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewStageHandler.class);

    private final ReviewerAgent reviewerAgent;
    private final ReviewCommentFormatter formatter;
    private final GitLabPort gitLabPort;
    private final SkillGovernanceUseCase skillGovernance;
    private final ArtifactCodec codec;

    public ReviewStageHandler(ReviewerAgent reviewerAgent,
                              ReviewCommentFormatter formatter,
                              GitLabPort gitLabPort,
                              SkillGovernanceUseCase skillGovernance,
                              ArtifactCodec codec) {
        this.reviewerAgent = reviewerAgent;
        this.formatter = formatter;
        this.gitLabPort = gitLabPort;
        this.skillGovernance = skillGovernance;
        this.codec = codec;
    }

    @Override
    public SdlcStage stage() {
        return SdlcStage.REVIEW;
    }

    @Override
    public StageOutcome handle(StageRun run, Wave wave) {
        // Validate the governance skill before judging anything with it. A missing or
        // empty review-criteria means the severities have no agreed meaning, and a
        // gate driven by an unstated taxonomy is not governance.
        skillGovernance.validateGovernanceSkillsIntegrity();

        String mergeRequestIid = resolveMergeRequestIid(run);
        String project = wave.getContext().gitLabProject();
        if (project == null || project.isBlank()) {
            throw new IllegalStateException(
                    "REVIEW stage has no GitLab project: the wave was started without one");
        }

        AgentOutcome<ReviewVerdict> outcome = reviewerAgent.run(new ReviewerAgent.ReviewRequest(
                wave.getId().value().toString(), project, mergeRequestIid, project));

        List<String> notes = new ArrayList<>(outcome.notes());
        ReviewVerdict verdict = outcome.artifact();

        verdict.governanceSkillVersions().forEach(skill ->
                skillGovernance.recordSkillUsage(skill,
                        wave.getId().value().toString(), "REVIEWER"));

        try {
            gitLabPort.addMergeRequestComment(project, mergeRequestIid, formatter.format(verdict));
            notes.add("Review comment posted to " + project + "!" + mergeRequestIid);
        } catch (Exception e) {
            // Publishing is delivery, not judgment. The verdict still gates the merge.
            log.error("Could not post review comment: {}", e.getMessage());
            notes.add("Review comment could not be posted: " + e.getMessage());
        }

        if (!ReviewGate.canComplete(verdict.disposition(), verdict.qualityScore())) {
            throw new ReviewGateFailedException(
                    "ReviewGate refused: disposition=%s qualityGate=%s. %s".formatted(
                            verdict.approvalStatus(),
                            verdict.qualityScore().status(),
                            describeBlockers(verdict)));
        }

        notes.add("ReviewGate passed: %d finding(s), quality gate %s".formatted(
                verdict.findings().size(), verdict.qualityScore().status()));
        return StageOutcome.completed(codec.write(verdict), outcome.usage(), notes);
    }

    private String describeBlockers(ReviewVerdict verdict) {
        if (verdict.blockingFindings().isEmpty() && verdict.qualityScore().isUnknown()) {
            return "No blocking findings, but the quality gate could not be evaluated — "
                    + "missing evidence is not treated as a pass.";
        }
        StringBuilder sb = new StringBuilder();
        verdict.blockingFindings().forEach(f -> sb.append("\n  - ").append(f.severity())
                .append(' ').append(f.filePath()).append(": ").append(f.description()));
        return sb.toString();
    }

    /**
     * The merge request to review comes from the Implementation the Developer stage
     * produced, carried through VERIFY.
     * <p>
     * When it is absent the stage fails loudly rather than reviewing something else:
     * a review of the wrong thing is worse than no review. This is currently the
     * common case, because the Developer stage does not yet open merge requests
     * against a real repository — see IMPLEMENTATION_LOG.md (M4, BLOCKED).
     */
    private String resolveMergeRequestIid(StageRun run) {
        VerifyStageHandler.VerifyStageOutput verified =
                codec.read(run.getInputArtifact(), VerifyStageHandler.VerifyStageOutput.class);

        String iid = verified != null && verified.implementation() != null
                ? verified.implementation().mergeRequestIid()
                : null;

        if (iid == null || iid.isBlank()) {
            throw new IllegalStateException(
                    "REVIEW stage has no merge request to review: the implementation carries no "
                            + "merge request id. The Developer stage opens one only when pointed at "
                            + "a real repository (IMPLEMENTATION_LOG.md, M4 BLOCKED).");
        }
        return iid;
    }

    /** Carries the gate's reason back as retry feedback for the build. */
    public static class ReviewGateFailedException extends RuntimeException {
        public ReviewGateFailedException(String message) {
            super(message);
        }
    }
}
