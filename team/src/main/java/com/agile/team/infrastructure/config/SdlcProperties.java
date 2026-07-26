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
    private Deployment deployment = new Deployment();
    private Design design = new Design();

    public Map<String, GateConfig> getGates() { return gates; }
    public void setGates(Map<String, GateConfig> gates) { this.gates = gates; }
    public Budget getBudget() { return budget; }
    public void setBudget(Budget budget) { this.budget = budget; }
    public Deployment getDeployment() { return deployment; }
    public void setDeployment(Deployment deployment) { this.deployment = deployment; }
    public Design getDesign() { return design; }
    public void setDesign(Design design) { this.design = design; }

    public static class GateConfig {
        /** Defaults to REQUIRED: a gate that is not configured is still supervised. */
        private GateMode mode = GateMode.REQUIRED;

        public GateMode getMode() { return mode; }
        public void setMode(GateMode mode) { this.mode = mode; }
    }

    /**
     * Policy-as-code guardrails for the Release stage. Everything here fails closed:
     * an unlisted environment is refused, not permitted.
     */
    public static class Deployment {
        private java.util.List<String> allowedEnvironments = java.util.List.of("dev", "staging");
        private java.util.List<String> productionEnvironments = java.util.List.of("production", "prod");
        private java.util.List<String> frozenEnvironments = java.util.List.of();
        private boolean blockOutsideWindow = true;
        private String windowStart = "09:00";
        private String windowEnd = "16:00";
        private String timeZone = "Europe/Paris";

        public java.util.List<String> getAllowedEnvironments() { return allowedEnvironments; }
        public void setAllowedEnvironments(java.util.List<String> v) { this.allowedEnvironments = v; }
        public java.util.List<String> getProductionEnvironments() { return productionEnvironments; }
        public void setProductionEnvironments(java.util.List<String> v) { this.productionEnvironments = v; }
        public java.util.List<String> getFrozenEnvironments() { return frozenEnvironments; }
        public void setFrozenEnvironments(java.util.List<String> v) { this.frozenEnvironments = v; }
        public boolean isBlockOutsideWindow() { return blockOutsideWindow; }
        public void setBlockOutsideWindow(boolean v) { this.blockOutsideWindow = v; }
        public String getWindowStart() { return windowStart; }
        public void setWindowStart(String v) { this.windowStart = v; }
        public String getWindowEnd() { return windowEnd; }
        public void setWindowEnd(String v) { this.windowEnd = v; }
        public String getTimeZone() { return timeZone; }
        public void setTimeZone(String v) { this.timeZone = v; }
    }

    public static class Design {
        /**
         * Whether a design's impacted modules must be verified against the real
         * repository tree. Default true: accepting module names on faith is how a
         * hallucinated module becomes a hallucinated file and then a build failure
         * three stages later with no obvious cause.
         */
        private boolean requireModuleVerification = true;

        public boolean isRequireModuleVerification() { return requireModuleVerification; }
        public void setRequireModuleVerification(boolean v) { this.requireModuleVerification = v; }
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
