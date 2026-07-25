package com.agile.team.domain.port;

import com.agile.team.domain.agent.AgentRole;

import java.util.List;

/**
 * Port for resolving and loading Agent Skills.
 * Skills are filesystem-based units of procedural/prompt knowledge — they are NOT business rules.
 * This port lives in domain because the application layer needs to express the intent
 * "give me the relevant skills for this agent role" without depending on infrastructure.
 */
public interface SkillPort {

    /**
     * Resolve applicable skill descriptors (name + description only) for a given agent role.
     * This supports progressive disclosure: only lightweight metadata is loaded initially.
     */
    List<SkillDescriptor> resolveSkills(AgentRole role);

    /**
     * Load the full content of a skill by name. Called only when the agent determines
     * a specific skill is relevant to the current task.
     */
    String loadSkillContent(String skillName);

    /**
     * Lightweight descriptor for progressive disclosure.
     * Only name and description are loaded into context by default.
     */
    record SkillDescriptor(
            String name,
            String description,
            boolean governance
    ) {
        public SkillDescriptor {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Skill name must not be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("Skill description must not be blank");
        }
    }
}
