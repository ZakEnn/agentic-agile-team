package com.agile.team.infrastructure.adapter.ai;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.port.SkillPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The governance-only injection policy.
 * <p>
 * This encodes the counter-evidence in SDLC_AGENT_PLAN.md §2.5(a): bulk context
 * injection measurably hurts agent performance while raising cost, so only
 * governance skills — which are included for auditability rather than accuracy —
 * go into the system prompt.
 */
class SkillContextResolverTest {

    @Test
    void shouldInjectGovernanceSkills() {
        FakeSkillPort skills = new FakeSkillPort()
                .withSkill("review-criteria", "Severity taxonomy", true,
                        "CRITICAL blocks approval.", AgentRole.REVIEWER);

        String context = new SkillContextResolver(skills).resolveGovernanceContext(AgentRole.REVIEWER);

        assertTrue(context.contains("review-criteria"));
        assertTrue(context.contains("CRITICAL blocks approval."));
        assertTrue(context.contains("compliance mandatory"));
    }

    @Test
    void shouldNotInjectNonGovernanceSkills() {
        // git-conventions is useful knowledge but injecting it wholesale into every
        // prompt is the documented anti-pattern. It is retrieved per task instead.
        FakeSkillPort skills = new FakeSkillPort()
                .withSkill("git-conventions", "Branch naming", false,
                        "Use feature/ prefix.", AgentRole.DEV);

        String context = new SkillContextResolver(skills).resolveGovernanceContext(AgentRole.DEV);

        assertEquals("", context);
        assertFalse(skills.contentWasLoadedFor("git-conventions"),
                "non-governance skill content should not even be read");
    }

    @Test
    void shouldInjectOnlyTheGovernanceSubsetWhenBothApply() {
        FakeSkillPort skills = new FakeSkillPort()
                .withSkill("review-criteria", "Severity taxonomy", true, "GOVERNED", AgentRole.REVIEWER)
                .withSkill("git-conventions", "Branch naming", false, "NOT-GOVERNED", AgentRole.REVIEWER);

        String context = new SkillContextResolver(skills).resolveGovernanceContext(AgentRole.REVIEWER);

        assertTrue(context.contains("GOVERNED"));
        assertFalse(context.contains("NOT-GOVERNED"));
    }

    @Test
    void shouldReturnEmptyForRoleWithNoSkills() {
        assertEquals("", new SkillContextResolver(new FakeSkillPort())
                .resolveGovernanceContext(AgentRole.PO));
    }

    @Test
    void shouldFallBackToDescriptionWhenGovernanceContentCannotBeLoaded() {
        // Silently dropping the rules an auditable decision is based on would be
        // worse than degrading loudly.
        FakeSkillPort skills = new FakeSkillPort()
                .withSkill("review-criteria", "Severity taxonomy", true, "", AgentRole.REVIEWER);

        String context = new SkillContextResolver(skills).resolveGovernanceContext(AgentRole.REVIEWER);

        assertTrue(context.contains("Severity taxonomy"));
    }

    @Test
    void shouldReportGovernanceSkillNamesForTheAuditTrail() {
        FakeSkillPort skills = new FakeSkillPort()
                .withSkill("review-criteria", "Severity taxonomy", true, "x", AgentRole.REVIEWER)
                .withSkill("git-conventions", "Branch naming", false, "y", AgentRole.REVIEWER);

        List<String> names = new SkillContextResolver(skills).governanceSkillNames(AgentRole.REVIEWER);

        assertEquals(List.of("review-criteria"), names);
    }

    private static class FakeSkillPort implements SkillPort {
        private final Map<String, SkillDescriptor> descriptors = new LinkedHashMap<>();
        private final Map<String, String> contents = new LinkedHashMap<>();
        private final Map<String, List<AgentRole>> roles = new LinkedHashMap<>();
        private final List<String> loaded = new ArrayList<>();

        FakeSkillPort withSkill(String name, String description, boolean governance,
                                String content, AgentRole... applicableRoles) {
            descriptors.put(name, new SkillDescriptor(name, description, governance));
            contents.put(name, content);
            roles.put(name, List.of(applicableRoles));
            return this;
        }

        @Override
        public List<SkillDescriptor> resolveSkills(AgentRole role) {
            return descriptors.values().stream()
                    .filter(d -> roles.get(d.name()).contains(role))
                    .toList();
        }

        @Override
        public String loadSkillContent(String skillName) {
            loaded.add(skillName);
            return contents.getOrDefault(skillName, "");
        }

        boolean contentWasLoadedFor(String skillName) {
            return loaded.contains(skillName);
        }
    }
}
