package com.agile.team.domain.stage;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.gate.GateName;

/**
 * The six SDLC domains from SDLC_AGENT_PLAN.md §3.2, in pipeline order, each owned
 * by exactly one agent.
 * <p>
 * Each stage declares the gate that must pass <em>after</em> it, if any. Encoding
 * the gate here rather than in orchestration code means the pipeline's autonomy
 * posture is described in one readable place.
 */
public enum SdlcStage {

    /** Intent + Confluence context to a validated, testable specification. */
    SPEC(AgentRole.PO, GateName.SPEC_APPROVAL),

    /** Where and how, before any code: impacted modules, risks, test strategy. */
    DESIGN(AgentRole.ARCHITECT, null),

    /** A working branch with a passing build. */
    BUILD(AgentRole.DEV, null),

    /** Proof the acceptance criteria hold, from a real test runner. */
    VERIFY(AgentRole.QA, null),

    /** Quality judgment against the governance criteria; feeds the ReviewGate. */
    REVIEW(AgentRole.REVIEWER, GateName.MERGE_APPROVAL),

    /** Deployment, with a mandatory rollback reference. */
    RELEASE(AgentRole.RELEASE, GateName.PRODUCTION_DEPLOY);

    private final AgentRole owner;
    private final GateName gateAfter;

    SdlcStage(AgentRole owner, GateName gateAfter) {
        this.owner = owner;
        this.gateAfter = gateAfter;
    }

    public AgentRole owner() {
        return owner;
    }

    /** The human checkpoint after this stage, or {@code null} if it runs unattended. */
    public GateName gateAfter() {
        return gateAfter;
    }

    public boolean hasGate() {
        return gateAfter != null;
    }

    /** The next stage in the pipeline, or {@code null} if this is the last. */
    public SdlcStage next() {
        SdlcStage[] all = values();
        int i = ordinal();
        return i + 1 < all.length ? all[i + 1] : null;
    }
}
