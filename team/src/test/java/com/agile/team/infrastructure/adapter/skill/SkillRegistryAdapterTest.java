package com.agile.team.infrastructure.adapter.skill;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.port.SkillPort.SkillDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryAdapterTest {

    @TempDir
    Path tempDir;

    private SkillRegistryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SkillRegistryAdapter(tempDir.toString());
    }

    @Test
    void should_resolveSkills_when_skillFilesExistForRole() throws IOException {
        // Given
        createSkillFile("review-criteria",
                "---\n" +
                "name: review-criteria\n" +
                "description: >\n" +
                "  Code review severity taxonomy and quality checklist.\n" +
                "applicable_roles:\n" +
                "  - REVIEWER\n" +
                "  - DEV\n" +
                "governance: true\n" +
                "---\n\n" +
                "# Code Review Criteria\n\nDetailed content here."
        );

        adapter.loadSkillIndex();

        // When
        List<SkillDescriptor> skills = adapter.resolveSkills(AgentRole.REVIEWER);

        // Then
        assertEquals(1, skills.size());
        assertEquals("review-criteria", skills.get(0).name());
        assertTrue(skills.get(0).governance());
    }

    @Test
    void should_returnEmptyList_when_noSkillsForRole() throws IOException {
        // Given
        createSkillFile("review-criteria",
                "---\n" +
                "name: review-criteria\n" +
                "description: Review stuff\n" +
                "applicable_roles:\n" +
                "  - REVIEWER\n" +
                "governance: false\n" +
                "---\n\n# Content"
        );

        adapter.loadSkillIndex();

        // When
        List<SkillDescriptor> skills = adapter.resolveSkills(AgentRole.QA);

        // Then
        assertTrue(skills.isEmpty());
    }

    @Test
    void should_loadSkillContent_when_skillExists() throws IOException {
        // Given
        createSkillFile("git-conventions",
                "---\n" +
                "name: git-conventions\n" +
                "description: Git branch and commit conventions.\n" +
                "applicable_roles:\n" +
                "  - DEV\n" +
                "governance: false\n" +
                "---\n\n" +
                "# Git Conventions\n\nUse feature/ prefix for branches."
        );

        adapter.loadSkillIndex();

        // When
        String content = adapter.loadSkillContent("git-conventions");

        // Then
        assertFalse(content.isEmpty());
        assertTrue(content.contains("# Git Conventions"));
        assertTrue(content.contains("feature/ prefix"));
        // Should NOT contain frontmatter
        assertFalse(content.contains("applicable_roles"));
    }

    @Test
    void should_returnEmptyString_when_skillNotFound() {
        adapter.loadSkillIndex();

        String content = adapter.loadSkillContent("nonexistent-skill");

        assertEquals("", content);
    }

    @Test
    void should_resolveMultipleSkills_when_roleHasMany() throws IOException {
        // Given
        createSkillFile("review-criteria",
                "---\n" +
                "name: review-criteria\n" +
                "description: Review criteria.\n" +
                "applicable_roles:\n" +
                "  - REVIEWER\n" +
                "  - DEV\n" +
                "governance: true\n" +
                "---\n\n# Review"
        );
        createSkillFile("git-conventions",
                "---\n" +
                "name: git-conventions\n" +
                "description: Git conventions.\n" +
                "applicable_roles:\n" +
                "  - DEV\n" +
                "  - REVIEWER\n" +
                "governance: false\n" +
                "---\n\n# Git"
        );

        adapter.loadSkillIndex();

        // When
        List<SkillDescriptor> devSkills = adapter.resolveSkills(AgentRole.DEV);

        // Then
        assertEquals(2, devSkills.size());
    }

    @Test
    void should_handleReload_when_skillsChange() throws IOException {
        // Given — initially empty
        adapter.loadSkillIndex();
        assertTrue(adapter.resolveSkills(AgentRole.DEV).isEmpty());

        // When — add a skill and reload
        createSkillFile("new-skill",
                "---\n" +
                "name: new-skill\n" +
                "description: Newly added.\n" +
                "applicable_roles:\n" +
                "  - DEV\n" +
                "governance: false\n" +
                "---\n\n# New Skill"
        );
        adapter.reload();

        // Then
        assertEquals(1, adapter.resolveSkills(AgentRole.DEV).size());
    }

    @Test
    void should_handleNullRole_gracefully() {
        adapter.loadSkillIndex();
        List<SkillDescriptor> skills = adapter.resolveSkills(null);
        assertTrue(skills.isEmpty());
    }

    private void createSkillFile(String skillName, String content) throws IOException {
        Path skillDir = tempDir.resolve(skillName);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), content);
    }
}
