package com.agile.team.domain.gate;

/**
 * The human-in-the-loop checkpoints named in SDLC_AGENT_PLAN.md §4.
 * <p>
 * Each sits where the plan argues a human is genuinely worth the latency:
 * <ul>
 *   <li>{@link #SPEC_APPROVAL} — where failures concentrate. MAST attributes the
 *       largest share of multi-agent failures to specification and system-design
 *       issues, so a bad spec caught here prevents the entire downstream cascade.</li>
 *   <li>{@link #MERGE_APPROVAL} — largest reversible blast radius. DORA 2025 found
 *       only 24% of developers substantially trust AI-generated code, so the merge
 *       button is not where to spend trust first.</li>
 *   <li>{@link #PRODUCTION_DEPLOY} — largest irreversible blast radius.</li>
 * </ul>
 * Everything between design and review runs unattended; that is where the time
 * savings live, and nothing there can reach production without passing a later gate.
 */
public enum GateName {

    SPEC_APPROVAL("spec-approval"),
    MERGE_APPROVAL("merge-approval"),
    PRODUCTION_DEPLOY("production-deploy");

    private final String configKey;

    GateName(String configKey) {
        this.configKey = configKey;
    }

    /** Key used under {@code sdlc.gates.*} in configuration. */
    public String configKey() {
        return configKey;
    }
}
