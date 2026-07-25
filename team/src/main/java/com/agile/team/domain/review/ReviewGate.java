package com.agile.team.domain.review;

public final class ReviewGate {

    private ReviewGate() {
        // utility class
    }

    /**
     * Governance gate: blocks completion unless BOTH reviewer approved AND SonarQube quality gate passed.
     */
    public static boolean canComplete(ReviewDisposition reviewDisposition, CodeQualityScore qualityScore) {
        if (reviewDisposition == null || qualityScore == null) {
            return false;
        }
        return reviewDisposition.isApproved() && qualityScore.passed();
    }
}
