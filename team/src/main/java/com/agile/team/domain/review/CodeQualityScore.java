package com.agile.team.domain.review;

/**
 * The automated quality signal feeding {@link ReviewGate}.
 * <p>
 * The {@link QualityGateStatus#UNKNOWN} state is the point of this class
 * (DECISIONS.md D-009). The original reviewer handler hardcoded
 * {@code CodeQualityScore.passing(80)} with the comment "since we don't want to
 * block on unavailable SonarQube" — which meant an unreachable quality gate was
 * silently reported as a passing one, and the much-advertised non-negotiable gate
 * became theatre.
 * <p>
 * Missing evidence is not positive evidence. {@code UNKNOWN} does not pass.
 */
public record CodeQualityScore(double score, QualityGateStatus status) {

    public CodeQualityScore {
        if (score < 0 || score > 100) throw new IllegalArgumentException("Score must be between 0 and 100");
        if (status == null) throw new IllegalArgumentException("status must not be null");
    }

    public static CodeQualityScore passing(double score) {
        return new CodeQualityScore(score, QualityGateStatus.PASSED);
    }

    public static CodeQualityScore failing(double score) {
        return new CodeQualityScore(score, QualityGateStatus.FAILED);
    }

    /**
     * The quality gate could not be evaluated — SonarQube unreachable, no analysis
     * for the branch, or a malformed response. Fails closed.
     */
    public static CodeQualityScore unknown() {
        return new CodeQualityScore(0, QualityGateStatus.UNKNOWN);
    }

    /** Only a genuine PASSED counts. UNKNOWN is not a pass. */
    public boolean passed() {
        return status == QualityGateStatus.PASSED;
    }

    public boolean isUnknown() {
        return status == QualityGateStatus.UNKNOWN;
    }

    public enum QualityGateStatus {
        PASSED,
        FAILED,
        /** Not evaluated. Treated as a failure by the gate, never as a pass. */
        UNKNOWN
    }
}
