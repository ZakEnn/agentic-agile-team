package com.agile.team.application.gate;

import com.agile.team.domain.gate.GateDecision;
import com.agile.team.domain.gate.GateMode;
import com.agile.team.domain.gate.GateName;
import com.agile.team.infrastructure.config.SdlcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves how each human-in-the-loop checkpoint behaves, from configuration.
 * <p>
 * This is the single place that decides whether a stage parks for a human or
 * proceeds. Keeping it in one component means the autonomy posture of the whole
 * platform is inspectable at a glance, and changing it never requires touching an
 * agent.
 */
@Component
public class GatePolicy {

    private static final Logger log = LoggerFactory.getLogger(GatePolicy.class);

    private final SdlcProperties properties;

    public GatePolicy(SdlcProperties properties) {
        this.properties = properties;
    }

    public GateMode modeOf(GateName gate) {
        SdlcProperties.GateConfig config = properties.getGates().get(gate.configKey());
        // Unconfigured means supervised, never open.
        return config != null ? config.getMode() : GateMode.REQUIRED;
    }

    /**
     * Attempt to pass the gate without human interaction.
     *
     * @return a decision when policy allows the stage to proceed immediately
     *         ({@code AUTO_APPROVE} or {@code DISABLED}); empty when the wave must
     *         park and wait for a human ({@code REQUIRED}).
     */
    public Optional<GateDecision> tryAutoResolve(GateName gate) {
        GateMode mode = modeOf(gate);
        return switch (mode) {
            case REQUIRED -> {
                log.info("Gate {} is REQUIRED — parking for human decision", gate.configKey());
                yield Optional.empty();
            }
            case AUTO_APPROVE -> {
                log.info("Gate {} auto-approved by policy", gate.configKey());
                yield Optional.of(GateDecision.autoApproved(gate));
            }
            case DISABLED -> {
                log.info("Gate {} is DISABLED — proceeding with no approval record", gate.configKey());
                yield Optional.of(new GateDecision(gate, true,
                        GateDecision.AUTO_PREFIX + "disabled", "gate disabled in configuration",
                        java.time.Instant.now()));
            }
        };
    }

    /** True when a human decision is required before the wave can advance. */
    public boolean requiresHuman(GateName gate) {
        return modeOf(gate) == GateMode.REQUIRED;
    }
}
