package com.agile.team.domain.specification;

/**
 * Lifecycle of a specification through the SPEC_APPROVAL gate.
 * <p>
 * The original system logged "Pipeline stopped here — awaiting manual validation"
 * but had no mechanism to record or act on that validation: the only way forward
 * was to uncomment code and redeploy. This enum, plus the approval endpoints, turn
 * that comment into an actual operating mode.
 */
public enum SpecificationStatus {

    /** Produced by the Spec Agent, not yet reviewed. The pipeline does not advance. */
    DRAFT,

    /** Approved at the gate. The wave may start execution. */
    APPROVED,

    /** Rejected at the gate. Carries a reason; the wave does not advance. */
    REJECTED;

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean isTerminal() {
        return this != DRAFT;
    }
}
