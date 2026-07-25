package com.agile.team.application.usecase;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.port.SkillPort.SkillDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillGovernanceUseCaseTest {

    private SkillGovernanceUseCase useCase;
    private StubSkillPort stubSkillPort;

    @BeforeEach
    void setUp() {
        stubSkillPort = new StubSkillPort();
        useCase = new SkillGovernanceUseCase(stubSkillPort);
    }

    @Test
    void should_validateSuccessfully_when_governanceSkillPresent() {
        stubSkillPort.addSkill(AgentRole.REVIEWER,
                new SkillDescriptor("review-criteria", "Review criteria", true),
                "# Review Criteria\nDetailed content.");

        assertDoesNotThrow(() -> useCase.validateGovernanceSkillsIntegrity());
    }

    @Test
    void should_throwException_when_noGovernanceSkillFound() {
        // No skills registered
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> useCase.validateGovernanceSkillsIntegrity()
        );
        assertTrue(ex.getMessage().contains("No governance skills found"));
    }

    @Test
    void should_throwException_when_governanceSkillContentEmpty() {
        stubSkillPort.addSkill(AgentRole.REVIEWER,
                new SkillDescriptor("review-criteria", "Review criteria", true),
                ""); // Empty content

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> useCase.validateGovernanceSkillsIntegrity()
        );
        assertTrue(ex.getMessage().contains("empty or unloadable"));
    }

    @Test
    void should_recordSkillUsage_inAuditLog() {
        stubSkillPort.addSkill(AgentRole.REVIEWER,
                new SkillDescriptor("review-criteria", "Review criteria", true),
                "# Content");

        useCase.recordSkillUsage("review-criteria", "wave-123", "agent-456");

        assertEquals(1, useCase.getAuditLog().size());
        var entry = useCase.getAuditLog().get(0);
        assertEquals("review-criteria", entry.skillName());
        assertEquals("wave-123", entry.waveId());
        assertEquals("agent-456", entry.agentId());
        assertNotNull(entry.timestamp());
    }

    /**
     * Simple stub implementation of SkillPort for testing.
     */
    private static class StubSkillPort implements SkillPort {
        private final java.util.Map<AgentRole, List<SkillDescriptor>> descriptors = new java.util.HashMap<>();
        private final java.util.Map<String, String> contents = new java.util.HashMap<>();

        void addSkill(AgentRole role, SkillDescriptor descriptor, String content) {
            descriptors.computeIfAbsent(role, k -> new java.util.ArrayList<>()).add(descriptor);
            contents.put(descriptor.name(), content);
        }

        @Override
        public List<SkillDescriptor> resolveSkills(AgentRole role) {
            return descriptors.getOrDefault(role, List.of());
        }

        @Override
        public String loadSkillContent(String skillName) {
            return contents.getOrDefault(skillName, "");
        }
    }
}
