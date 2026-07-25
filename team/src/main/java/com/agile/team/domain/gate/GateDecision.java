package com.agile.team.domain.gate;

import java.time.Instant;

/**
 * The outcome of a human-in-the-loop checkpoint, recorded for audit.
 * <p>
 * {@code decidedBy} always records who or what decided. A policy auto-approval is
 * attributed to {@code AUTO:<gate>}; a human decision carries their identifier.
 * Keeping those distinguishable is what lets a team measure whether a gate is
 * actually earning its latency before switching it off.
 *
 * @param gate       which checkpoint
 * @param approved   whether the wave may proceed
 * @param decidedBy  human identifier, or {@code AUTO:<gate>} for a policy decision
 * @param reason     free-text rationale; required on rejection
 * @param decidedAt  when
 */
public record GateDecision(
        GateName gate,
        boolean approved,
        String decidedBy,
        String reason,
        Instant decidedAt
) {

    public static final String AUTO_PREFIX = "AUTO:";

    public GateDecision {
        if (gate == null) throw new IllegalArgumentException("gate must not be null");
        if (decidedBy == null || decidedBy.isBlank()) {
            throw new IllegalArgumentException("decidedBy must not be blank — an unattributed approval is not an audit trail");
        }
        if (!approved && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("a rejection must carry a reason");
        }
        if (decidedAt == null) decidedAt = Instant.now();
    }

    /** A decision made by policy rather than a person. */
    public static GateDecision autoApproved(GateName gate) {
        return new GateDecision(gate, true, AUTO_PREFIX + gate.configKey(),
                "auto-approved by gate policy", Instant.now());
    }

    public static GateDecision approvedBy(GateName gate, String who, String reason) {
        return new GateDecision(gate, true, who, reason, Instant.now());
    }

    public static GateDecision rejectedBy(GateName gate, String who, String reason) {
        return new GateDecision(gate, false, who, reason, Instant.now());
    }

    /** True when no human was involved — used to keep trust metrics honest. */
    public boolean isAutomated() {
        return decidedBy.startsWith(AUTO_PREFIX);
    }
}
