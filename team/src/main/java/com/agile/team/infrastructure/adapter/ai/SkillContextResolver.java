package com.agile.team.infrastructure.adapter.ai;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.port.SkillPort.SkillDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the skill section of an agent's system prompt.
 * <p>
 * <strong>Injects governance skills only.</strong> This is a deliberate reversal of
 * the original {@code SpringAiAgentBridge.resolveSkillContext()}, which loaded the
 * full text of <em>every</em> applicable skill on every call — its own comment read
 * "we load all applicable skills since the count is small".
 * <p>
 * SDLC_AGENT_PLAN.md §2.5(a) records the evidence against that pattern: the
 * AGENTS.md study (arXiv:2602.11988) measured LLM-generated repository context files
 * <em>reducing</em> agent performance by 0.5–2% while raising inference cost 20–23%
 * and adding 2.45–3.92 steps per task. Bulk context injection is the documented
 * anti-pattern, not the best practice.
 * <p>
 * Governance skills are the exception, and they are included for a different reason
 * than accuracy: {@code review-criteria} defines the severity taxonomy the ReviewGate
 * acts on, so the exact text used for a decision must be reproducible and auditable.
 * We accept its token cost because the value is compliance, not uplift. Everything
 * else the agent needs is retrieved narrowly, per task, by the agent itself.
 */
@Component
public class SkillContextResolver {

    private static final Logger log = LoggerFactory.getLogger(SkillContextResolver.class);

    private final SkillPort skillPort;

    public SkillContextResolver(SkillPort skillPort) {
        this.skillPort = skillPort;
    }

    /**
     * @return the governance skill section for this role, or an empty string when the
     *         role has no governance skills — most roles do not.
     */
    public String resolveGovernanceContext(AgentRole role) {
        List<SkillDescriptor> governanceSkills = skillPort.resolveSkills(role).stream()
                .filter(SkillDescriptor::governance)
                .toList();

        if (governanceSkills.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n## Governance (compliance mandatory)\n");
        context.append("The following criteria are binding. Deviation is not permitted, ")
               .append("and your output is audited against them.\n\n");

        for (SkillDescriptor descriptor : governanceSkills) {
            String content = skillPort.loadSkillContent(descriptor.name());
            context.append("### ").append(descriptor.name()).append("\n");
            if (content.isEmpty()) {
                // Fail loudly in the prompt rather than silently dropping the rules
                // an auditable decision is supposed to be based on.
                log.error("Governance skill '{}' has no loadable content", descriptor.name());
                context.append(descriptor.description()).append("\n\n");
            } else {
                context.append(content).append("\n\n");
            }
        }
        return context.toString();
    }

    /**
     * Names and versions of the governance skills applied to a role, for recording
     * alongside a decision so it can be reproduced later.
     */
    public List<String> governanceSkillNames(AgentRole role) {
        return skillPort.resolveSkills(role).stream()
                .filter(SkillDescriptor::governance)
                .map(SkillDescriptor::name)
                .toList();
    }
}
