package com.agile.team.infrastructure.config;

import com.agile.team.domain.gate.GateMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Platform configuration: human-in-the-loop gate policy and per-wave budget caps.
 * <p>
 * Gates default to {@link GateMode#REQUIRED} — supervised autonomy is the shipped
 * behaviour, and switching a gate off is a deliberate, reviewable config change
 * (DECISIONS.md D-007).
 * <p>
 * Budget caps are not optional decoration. SDLC_AGENT_PLAN.md §2.4 cites roughly
 * 15x token usage for multi-agent systems, and the mechanism by which that becomes
 * a large bill is an unbounded retry loop. Capping attempts and tokens per wave is
 * the control.
 */
@ConfigurationProperties(prefix = "sdlc")
public class SdlcProperties {

    private Map<String, GateConfig> gates = new LinkedHashMap<>();
    private Budget budget = new Budget();

    public Map<String, GateConfig> getGates() { return gates; }
    public void setGates(Map<String, GateConfig> gates) { this.gates = gates; }
    public Budget getBudget() { return budget; }
    public void setBudget(Budget budget) { this.budget = budget; }

    public static class GateConfig {
        /** Defaults to REQUIRED: a gate that is not configured is still supervised. */
        private GateMode mode = GateMode.REQUIRED;

        public GateMode getMode() { return mode; }
        public void setMode(GateMode mode) { this.mode = mode; }
    }

    public static class Budget {
        /** Hard ceiling on tokens a single wave may consume across all agents. */
        private long maxTokensPerWave = 1_000_000L;
        /** Maximum attempts for a single stage before it is failed permanently. */
        private int maxStageAttempts = 3;

        public long getMaxTokensPerWave() { return maxTokensPerWave; }
        public void setMaxTokensPerWave(long maxTokensPerWave) { this.maxTokensPerWave = maxTokensPerWave; }
        public int getMaxStageAttempts() { return maxStageAttempts; }
        public void setMaxStageAttempts(int maxStageAttempts) { this.maxStageAttempts = maxStageAttempts; }
    }
}
