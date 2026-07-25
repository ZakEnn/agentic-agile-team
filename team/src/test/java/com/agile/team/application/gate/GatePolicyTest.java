package com.agile.team.application.gate;

import com.agile.team.domain.gate.GateDecision;
import com.agile.team.domain.gate.GateMode;
import com.agile.team.domain.gate.GateName;
import com.agile.team.infrastructure.config.SdlcProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GatePolicyTest {

    @Test
    void shouldRequireHumanWhenGateIsUnconfigured() {
        // The safe default matters more than convenience: an unconfigured gate is
        // supervised, never open.
        GatePolicy policy = new GatePolicy(new SdlcProperties());

        assertEquals(GateMode.REQUIRED, policy.modeOf(GateName.SPEC_APPROVAL));
        assertTrue(policy.requiresHuman(GateName.SPEC_APPROVAL));
        assertTrue(policy.tryAutoResolve(GateName.SPEC_APPROVAL).isEmpty());
    }

    @Test
    void shouldAutoApproveWhenConfiguredTo() {
        GatePolicy policy = policyWith(GateName.SPEC_APPROVAL, GateMode.AUTO_APPROVE);

        Optional<GateDecision> decision = policy.tryAutoResolve(GateName.SPEC_APPROVAL);

        assertTrue(decision.isPresent());
        assertTrue(decision.get().approved());
        assertFalse(policy.requiresHuman(GateName.SPEC_APPROVAL));
    }

    @Test
    void shouldAttributeAutoApprovalToPolicyNotToAPerson() {
        // If an automated approval were indistinguishable from a human one, the
        // audit trail would be worthless and trust metrics would be a lie.
        GatePolicy policy = policyWith(GateName.SPEC_APPROVAL, GateMode.AUTO_APPROVE);

        GateDecision decision = policy.tryAutoResolve(GateName.SPEC_APPROVAL).orElseThrow();

        assertTrue(decision.isAutomated());
        assertEquals("AUTO:spec-approval", decision.decidedBy());
    }

    @Test
    void shouldDistinguishDisabledFromAutoApprove() {
        GatePolicy policy = policyWith(GateName.PRODUCTION_DEPLOY, GateMode.DISABLED);

        GateDecision decision = policy.tryAutoResolve(GateName.PRODUCTION_DEPLOY).orElseThrow();

        assertTrue(decision.approved());
        assertTrue(decision.isAutomated());
        assertTrue(decision.reason().contains("disabled"));
    }

    @Test
    void shouldConfigureGatesIndependently() {
        SdlcProperties properties = new SdlcProperties();
        properties.getGates().put(GateName.SPEC_APPROVAL.configKey(), config(GateMode.AUTO_APPROVE));
        properties.getGates().put(GateName.PRODUCTION_DEPLOY.configKey(), config(GateMode.REQUIRED));
        GatePolicy policy = new GatePolicy(properties);

        assertFalse(policy.requiresHuman(GateName.SPEC_APPROVAL));
        assertTrue(policy.requiresHuman(GateName.PRODUCTION_DEPLOY));
        // Unconfigured gate still defaults closed.
        assertTrue(policy.requiresHuman(GateName.MERGE_APPROVAL));
    }

    @Test
    void shouldRequireAReasonWhenRejecting() {
        assertThrows(IllegalArgumentException.class,
                () -> GateDecision.rejectedBy(GateName.SPEC_APPROVAL, "alice", ""));
    }

    @Test
    void shouldRequireAnAttributedDecider() {
        assertThrows(IllegalArgumentException.class,
                () -> GateDecision.approvedBy(GateName.SPEC_APPROVAL, "  ", "fine"));
    }

    private GatePolicy policyWith(GateName gate, GateMode mode) {
        SdlcProperties properties = new SdlcProperties();
        properties.getGates().put(gate.configKey(), config(mode));
        return new GatePolicy(properties);
    }

    private SdlcProperties.GateConfig config(GateMode mode) {
        SdlcProperties.GateConfig c = new SdlcProperties.GateConfig();
        c.setMode(mode);
        return c;
    }
}
