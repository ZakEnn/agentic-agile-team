package com.agile.team.domain.conversation;

/**
 * Kinds of entry in a wave's audit trail.
 * <p>
 * Extended in M1 with stage lifecycle and gate types, so the conversation records
 * not just what agents produced but what was decided about it and by whom. The
 * original set could describe agent outputs but had no way to record a human
 * approval, a stage failure, or a budget stop.
 */
public enum MessageType {
    SPECIFICATION_REQUEST,
    SPECIFICATION_READY,
    TASK_ASSIGNMENT,
    IMPLEMENTATION_COMPLETE,
    REVIEW_REQUEST,
    REVIEW_COMPLETE,
    QA_REQUEST,
    QA_COMPLETE,
    GOVERNANCE_CHECK,
    WAVE_STATUS_UPDATE,

    /** A stage began. */
    STAGE_STARTED,
    /** A stage produced a valid artifact. */
    STAGE_COMPLETED,
    /** A stage failed; payload carries the reason and attempt number. */
    STAGE_FAILED,
    /** A human-in-the-loop gate was decided. Payload records who and why. */
    GATE_DECISION,
    /** The wave is parked waiting for a human decision. */
    AWAITING_APPROVAL,
    /** A wave hit its token budget ceiling and was stopped. */
    BUDGET_EXCEEDED,
    /** An agent's note about retrieval gaps or degraded context. */
    AGENT_NOTE
}
