package com.agile.team.domain.review;

/**
 * The severity taxonomy from {@code skills/review-criteria/SKILL.md}, encoded as
 * code so the approval rule is executable rather than advisory.
 * <p>
 * The skill states: "APPROVED — zero CRITICAL or MAJOR findings" and
 * "CHANGES_REQUESTED — one or more CRITICAL or MAJOR findings present". Putting
 * {@link #blocksApproval()} here means the reviewer agent classifies findings and
 * the <em>system</em> computes the disposition — the model never states a verdict
 * that the pipeline then trusts.
 * <p>
 * That is the structural fix for the original defect, where approval was decided by
 * {@code aiResponse.toLowerCase().contains("approved")} — a test that the sentence
 * "changes are needed before this can be approved" passes.
 */
public enum Severity {

    /** Security vulnerability, data loss risk, or production-breaking bug. */
    CRITICAL(true),

    /** Significant logic error, missing error handling, or performance regression. */
    MAJOR(true),

    /** Style violation, naming inconsistency, or missing documentation. */
    MINOR(false),

    /** Improvement idea or alternative approach. */
    SUGGESTION(false);

    private final boolean blocksApproval;

    Severity(boolean blocksApproval) {
        this.blocksApproval = blocksApproval;
    }

    /** Whether a finding at this severity prevents approval. */
    public boolean blocksApproval() {
        return blocksApproval;
    }
}
