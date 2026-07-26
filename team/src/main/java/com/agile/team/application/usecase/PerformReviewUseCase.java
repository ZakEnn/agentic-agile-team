package com.agile.team.application.usecase;

import com.agile.team.application.agent.AgentOutcome;
import com.agile.team.application.agent.ReviewerAgent;
import com.agile.team.application.review.ReviewCommentFormatter;
import com.agile.team.domain.artifact.ReviewVerdict;
import com.agile.team.domain.port.GitLabPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Run a review and publish the result back to the merge request.
 * <p>
 * Standalone by design: this is usable as a review bot on its own, independently of
 * the wave pipeline. SDLC_AGENT_PLAN.md §5 sequences M2 before the durable
 * orchestration precisely so this delivers value even if the rest of the roadmap
 * stalls.
 */
@Service
public class PerformReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(PerformReviewUseCase.class);

    private final ReviewerAgent reviewerAgent;
    private final ReviewCommentFormatter formatter;
    private final GitLabPort gitLabPort;

    public PerformReviewUseCase(ReviewerAgent reviewerAgent,
                                ReviewCommentFormatter formatter,
                                GitLabPort gitLabPort) {
        this.reviewerAgent = reviewerAgent;
        this.formatter = formatter;
        this.gitLabPort = gitLabPort;
    }

    public ReviewVerdict execute(String waveId, String projectId, String mergeRequestIid,
                                 String sonarProjectKey, boolean postComment) {
        AgentOutcome<ReviewVerdict> outcome = reviewerAgent.run(
                new ReviewerAgent.ReviewRequest(waveId, projectId, mergeRequestIid, sonarProjectKey));

        ReviewVerdict verdict = outcome.artifact();
        outcome.notes().forEach(note -> log.info("[REVIEW] {}", note));

        if (postComment) {
            try {
                gitLabPort.addMergeRequestComment(projectId, mergeRequestIid, formatter.format(verdict));
            } catch (Exception e) {
                // The verdict is still valid and still gates the merge; failing to
                // publish it is a delivery problem, not a review problem.
                log.error("[REVIEW] Failed to post comment to {}!{}: {}",
                        projectId, mergeRequestIid, e.getMessage());
            }
        }
        return verdict;
    }
}
