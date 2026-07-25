package com.agile.team.domain.agent;

/**
 * One role per SDLC domain (SDLC_AGENT_PLAN.md §3.2).
 * <p>
 * ARCHITECT and RELEASE were added in M1 to complete the roster: the original four
 * covered spec, code, review and QA, leaving architecture/design and
 * deployment/ops with no owner at all — the two domains the plan identified as
 * entirely absent.
 */
public enum AgentRole {

    /** Product Owner — turns intent and documentation into a testable specification. */
    PO,

    /** Architect — decides where and how, before code exists. */
    ARCHITECT,

    /** Developer — produces a working branch with a passing build. */
    DEV,

    /** QA — proves the acceptance criteria hold, from a real test runner. */
    QA,

    /** Reviewer — judges quality against the governance criteria. */
    REVIEWER,

    /** Release/Ops — decides and executes deployment. */
    RELEASE
}
