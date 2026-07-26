package com.agile.team.interfaces.rest;

import com.agile.team.application.usecase.PerformReviewUseCase;
import com.agile.team.domain.artifact.ReviewVerdict;
import com.agile.team.infrastructure.adapter.gitlab.GitLabDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Review entry points: manual trigger and GitLab webhook.
 * <p>
 * The webhook is the path that never worked in the original {@code reviewer-agent}:
 * its payload DTO declared {@code objectKind}/{@code objectAttributes} against
 * GitLab's {@code object_kind}/{@code object_attributes}, with no naming strategy
 * configured, so both bound to null and the handler dereferenced null on every real
 * delivery. Fixed by explicit {@code @JsonProperty} in {@link GitLabDtos} and pinned
 * by a contract test against a recorded payload.
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final PerformReviewUseCase performReview;

    public ReviewController(PerformReviewUseCase performReview) {
        this.performReview = performReview;
    }

    /** Manual trigger, e.g. from CI or a developer. */
    @PostMapping("/perform")
    public ResponseEntity<ReviewResponse> perform(@RequestParam String projectId,
                                                  @RequestParam String mergeRequestIid,
                                                  @RequestParam(required = false) String sonarProjectKey,
                                                  @RequestParam(defaultValue = "true") boolean postComment) {
        ReviewVerdict verdict = performReview.execute(
                null, projectId, mergeRequestIid, sonarProjectKey, postComment);
        return ResponseEntity.ok(ReviewResponse.from(verdict));
    }

    /**
     * GitLab merge-request webhook.
     * <p>
     * Returns 202 and ignores events that are not reviewable rather than throwing:
     * GitLab retries on error responses, and a 4xx for "this is a pipeline event"
     * would produce a retry storm for a payload that will never be reviewable.
     */
    @PostMapping("/webhook/gitlab")
    public ResponseEntity<ReviewResponse> webhook(@RequestBody GitLabDtos.WebhookPayload payload,
                                                  @RequestParam(required = false) String sonarProjectKey) {
        if (payload == null || !payload.isReviewableMergeRequestEvent()) {
            log.debug("Ignoring non-reviewable webhook event: {}",
                    payload != null ? payload.objectKind() : "null payload");
            return ResponseEntity.accepted().build();
        }

        String projectPath = payload.project() != null ? payload.project().pathWithNamespace() : null;
        Integer iid = payload.objectAttributes().iid();
        if (projectPath == null || iid == null) {
            log.warn("Webhook missing project path or MR iid; ignoring");
            return ResponseEntity.accepted().build();
        }

        String sonarKey = sonarProjectKey != null ? sonarProjectKey : projectPath.replace('/', '_');
        ReviewVerdict verdict = performReview.execute(
                null, projectPath, String.valueOf(iid), sonarKey, true);
        return ResponseEntity.ok(ReviewResponse.from(verdict));
    }

    public record ReviewResponse(
            String disposition,
            String summary,
            int findings,
            int blockingFindings,
            String qualityGate,
            List<FindingView> details,
            List<String> governanceSkills
    ) {
        static ReviewResponse from(ReviewVerdict verdict) {
            return new ReviewResponse(
                    verdict.approvalStatus().name(),
                    verdict.summary(),
                    verdict.findings().size(),
                    verdict.blockingFindings().size(),
                    verdict.qualityScore().status().name(),
                    verdict.findings().stream().map(FindingView::from).toList(),
                    verdict.governanceSkillVersions());
        }
    }

    public record FindingView(String severity, String filePath, Integer line,
                              String description, String category, boolean blocking) {
        static FindingView from(com.agile.team.domain.review.Finding f) {
            return new FindingView(f.severity().name(), f.filePath(), f.line(),
                    f.description(), f.category(), f.blocksApproval());
        }
    }
}
