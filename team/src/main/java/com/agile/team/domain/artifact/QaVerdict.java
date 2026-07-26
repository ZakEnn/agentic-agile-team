package com.agile.team.domain.artifact;

import java.util.List;

/**
 * The QA stage's result: did each acceptance criterion hold.
 * <p>
 * {@code passed} is derived from the test runner's exit code and the per-criterion
 * results — not asserted by a model. The handler it replaces decided QA outcomes
 * with {@code aiResponse.toLowerCase().contains("passed")}.
 *
 * @param passed        every criterion verified and the suite green
 * @param perCriterion  one result per acceptance criterion
 * @param failingTests  names of failing tests, when the runner reported any
 * @param coverageNote  free-text coverage observation, when available
 * @param summary       what was verified and how
 */
public record QaVerdict(
        boolean passed,
        List<CriterionResult> perCriterion,
        List<String> failingTests,
        String coverageNote,
        String summary
) {
    public QaVerdict {
        perCriterion = perCriterion == null ? List.of() : List.copyOf(perCriterion);
        failingTests = failingTests == null ? List.of() : List.copyOf(failingTests);
        if (summary == null) summary = "";
    }

    /**
     * Build a verdict from evidence rather than from a claim.
     * <p>
     * Passing requires the suite to be green <em>and</em> every criterion to be
     * verified. A green suite that does not cover a criterion is not a pass — that
     * gap is precisely what an acceptance test is for.
     */
    public static QaVerdict from(boolean suiteGreen, List<CriterionResult> results,
                                 List<String> failingTests, String coverageNote, String summary) {
        boolean allVerified = results != null && !results.isEmpty()
                && results.stream().allMatch(CriterionResult::verified);
        return new QaVerdict(suiteGreen && allVerified, results, failingTests, coverageNote, summary);
    }

    public List<CriterionResult> unverified() {
        return perCriterion.stream().filter(r -> !r.verified()).toList();
    }

    /**
     * @param criterion the acceptance criterion, verbatim from the specification
     * @param verified  whether a test demonstrates it
     * @param evidence  which test, or why it could not be verified
     */
    public record CriterionResult(String criterion, boolean verified, String evidence) {
        public CriterionResult {
            if (criterion == null || criterion.isBlank()) {
                throw new ArtifactValidationException("CriterionResult.criterion must not be blank");
            }
            if (evidence == null) evidence = "";
        }
    }
}
