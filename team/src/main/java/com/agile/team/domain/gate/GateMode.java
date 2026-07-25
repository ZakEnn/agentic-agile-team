package com.agile.team.domain.gate;

/**
 * How a human checkpoint behaves. Configuration, never an interactive prompt
 * (DECISIONS.md D-007).
 * <p>
 * This is the maturity ladder from SDLC_AGENT_PLAN.md §2.6 expressed as a setting:
 * read-only, advised, approval-based, then autonomous within guardrails. Moving a
 * gate from {@link #REQUIRED} to {@link #AUTO_APPROVE} is a deliberate, auditable
 * configuration change — not a code change and not a silent default.
 */
public enum GateMode {

    /**
     * The wave parks and waits for an explicit human decision via the REST API.
     * The production default for every gate.
     */
    REQUIRED,

    /**
     * The gate passes automatically, recording a synthetic approval attributed to
     * {@code AUTO:<gate>} rather than to a person.
     * <p>
     * The attribution matters: an auto-approval that claimed a human name would
     * corrupt the audit trail and make trust metrics meaningless. Used in tests to
     * exercise the full pipeline, and in production only for gates a team has
     * deliberately decided to stop supervising.
     */
    AUTO_APPROVE,

    /**
     * The gate does not exist for this deployment — the stage proceeds with no
     * approval record at all.
     * <p>
     * Distinct from {@link #AUTO_APPROVE}, which asserts "approved by policy".
     * DISABLED asserts "this checkpoint is not part of our process".
     */
    DISABLED
}
