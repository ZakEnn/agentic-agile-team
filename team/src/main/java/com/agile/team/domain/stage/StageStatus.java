package com.agile.team.domain.stage;

/**
 * Lifecycle of a single stage execution.
 * <p>
 * Persisted, so a restart resumes rather than loses the wave. The previous design
 * held this state implicitly in a thread on an {@code @Async} executor, which meant
 * a mid-wave restart lost the work with no record of where it had reached.
 */
public enum StageStatus {

    /** Enqueued and waiting to be claimed. Visible to the claim query once due. */
    PENDING,

    /** Claimed by an instance and executing. */
    RUNNING,

    /** Produced a valid artifact. */
    SUCCEEDED,

    /** Failed but retryable — will return to PENDING after its backoff elapses. */
    RETRYING,

    /** Parked at a human-in-the-loop gate. Not claimable; waits for a decision. */
    AWAITING_APPROVAL,

    /** Failed permanently after exhausting its attempts. Needs human attention. */
    DEAD_LETTER,

    /** Stopped because the wave exceeded its token budget. */
    BUDGET_EXCEEDED,

    /** Not run because an earlier stage failed. */
    CANCELLED;

    /** Whether the claim query should pick this up. */
    public boolean isClaimable() {
        return this == PENDING;
    }

    /** Whether the stage has reached a state it will not leave on its own. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == DEAD_LETTER
                || this == BUDGET_EXCEEDED || this == CANCELLED;
    }

    public boolean isFailure() {
        return this == DEAD_LETTER || this == BUDGET_EXCEEDED;
    }
}
