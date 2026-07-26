package com.agile.team.domain.review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReviewGateTest {

    @Test
    void shouldPassWhenBothReviewerApprovedAndQualityPassed() {
        ReviewDisposition approved = ReviewDisposition.approved("Looks good");
        CodeQualityScore passing = CodeQualityScore.passing(90.0);

        assertTrue(ReviewGate.canComplete(approved, passing));
    }

    @Test
    void shouldFailWhenReviewerRejected() {
        ReviewDisposition rejected = ReviewDisposition.rejected("Issues found");
        CodeQualityScore passing = CodeQualityScore.passing(90.0);

        assertFalse(ReviewGate.canComplete(rejected, passing));
    }

    @Test
    void shouldFailWhenQualityGateFailed() {
        ReviewDisposition approved = ReviewDisposition.approved("LGTM");
        CodeQualityScore failing = CodeQualityScore.failing(30.0);

        assertFalse(ReviewGate.canComplete(approved, failing));
    }

    @Test
    void shouldFailWhenBothFailed() {
        ReviewDisposition rejected = ReviewDisposition.rejected("Bad code");
        CodeQualityScore failing = CodeQualityScore.failing(20.0);

        assertFalse(ReviewGate.canComplete(rejected, failing));
    }

    @Test
    void shouldFailWhenReviewDispositionIsNull() {
        CodeQualityScore passing = CodeQualityScore.passing(90.0);
        assertFalse(ReviewGate.canComplete(null, passing));
    }

    @Test
    void shouldFailWhenQualityScoreIsNull() {
        ReviewDisposition approved = ReviewDisposition.approved("LGTM");
        assertFalse(ReviewGate.canComplete(approved, null));
    }

    @Test
    void shouldRejectScoreOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new CodeQualityScore(101, CodeQualityScore.QualityGateStatus.PASSED));
        assertThrows(IllegalArgumentException.class,
                () -> new CodeQualityScore(-1, CodeQualityScore.QualityGateStatus.FAILED));
    }

    @Test
    void shouldNotCompleteWhenTheQualityGateCouldNotBeEvaluated() {
        // The single most important behaviour in this class. The original reviewer
        // hardcoded passing(80) "since we don't want to block on unavailable
        // SonarQube", which turned an outage into an approval. Missing evidence is
        // not positive evidence.
        ReviewDisposition approved = ReviewDisposition.approved("LGTM");

        assertFalse(ReviewGate.canComplete(approved, CodeQualityScore.unknown()));
    }

    @Test
    void shouldReportUnknownDistinctlyFromFailed() {
        assertTrue(CodeQualityScore.unknown().isUnknown());
        assertFalse(CodeQualityScore.unknown().passed());
        assertFalse(CodeQualityScore.failing(10).isUnknown());
        assertFalse(CodeQualityScore.failing(10).passed());
        assertTrue(CodeQualityScore.passing(90).passed());
    }
}
