package com.agile.team.infrastructure.adapter.skill;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.port.SkillPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filesystem-based Skill registry that reads SKILL.md files from the configured skills directory.
 * Supports progressive disclosure: descriptors (frontmatter) are cached at startup;
 * full content is loaded on demand via {@link #loadSkillContent(String)}.
 */
@Component
public class SkillRegistryAdapter implements SkillPort {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistryAdapter.class);
    private static final String SKILL_FILE = "SKILL.md";
    private static final String FRONTMATTER_DELIMITER = "---";

    private final Path skillsDirectory;
    private final Map<String, ParsedSkill> skillCache = new ConcurrentHashMap<>();

    public SkillRegistryAdapter(@Value("${team.skills.directory:skills}") String skillsDir) {
        this.skillsDirectory = Path.of(skillsDir).toAbsolutePath();
    }

    @PostConstruct
    public void loadSkillIndex() {
        skillCache.clear();

        if (!Files.isDirectory(skillsDirectory)) {
            log.warn("Skills directory not found: {}. No skills will be loaded.", skillsDirectory);
            return;
        }

        try (Stream<Path> dirs = Files.list(skillsDirectory)) {
            dirs.filter(Files::isDirectory)
                .forEach(this::indexSkill);
        } catch (IOException e) {
            log.error("Failed to scan skills directory: {}", skillsDirectory, e);
        }

        log.info("Loaded {} skill descriptor(s) from {}", skillCache.size(), skillsDirectory);
    }

    @Override
    public List<SkillDescriptor> resolveSkills(AgentRole role) {
        if (role == null) return List.of();

        String roleStr = role.name();
        return skillCache.values().stream()
                .filter(skill -> skill.applicableRoles().contains(roleStr))
                .map(skill -> new SkillDescriptor(skill.name(), skill.description(), skill.governance()))
                .collect(Collectors.toList());
    }

    @Override
    public String loadSkillContent(String skillName) {
        if (skillName == null || skillName.isBlank()) return "";

        ParsedSkill skill = skillCache.get(skillName);
        if (skill == null) {
            log.warn("Skill not found: {}", skillName);
            return "";
        }

        // Load full content on demand (progressive disclosure)
        Path skillFile = skill.filePath();
        try {
            String fullContent = Files.readString(skillFile);
            return extractBody(fullContent);
        } catch (IOException e) {
            log.error("Failed to load skill content for: {}", skillName, e);
            return "";
        }
    }

    /**
     * Reloads the skill index from disk. Can be triggered via actuator or event.
     */
    public void reload() {
        log.info("Reloading skills index...");
        loadSkillIndex();
    }

    private void indexSkill(Path skillDir) {
        Path skillFile = skillDir.resolve(SKILL_FILE);
        if (!Files.isRegularFile(skillFile)) {
            log.debug("No SKILL.md in directory: {}", skillDir);
            return;
        }

        try {
            String content = Files.readString(skillFile);
            ParsedSkill parsed = parseFrontmatter(content, skillFile);
            if (parsed != null) {
                skillCache.put(parsed.name(), parsed);
                log.debug("Indexed skill: {} (roles: {})", parsed.name(), parsed.applicableRoles());
            }
        } catch (IOException e) {
            log.error("Failed to read skill file: {}", skillFile, e);
        }
    }

    private ParsedSkill parseFrontmatter(String content, Path filePath) {
        if (!content.startsWith(FRONTMATTER_DELIMITER)) {
            log.warn("Skill file missing frontmatter: {}", filePath);
            return null;
        }

        int secondDelimiter = content.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (secondDelimiter < 0) {
            log.warn("Skill file has unclosed frontmatter: {}", filePath);
            return null;
        }

        String frontmatter = content.substring(FRONTMATTER_DELIMITER.length(), secondDelimiter).trim();

        // Simple YAML-like parsing (no heavy YAML lib needed for this structure)
        String name = extractField(frontmatter, "name");
        String description = extractMultilineField(frontmatter, "description");
        boolean governance = "true".equalsIgnoreCase(extractField(frontmatter, "governance"));
        List<String> roles = extractListField(frontmatter, "applicable_roles");

        if (name == null || name.isBlank()) {
            log.warn("Skill missing 'name' in frontmatter: {}", filePath);
            return null;
        }
        if (description == null || description.isBlank()) {
            description = "No description provided";
        }

        return new ParsedSkill(name, description.trim(), governance, roles, filePath);
    }

    private String extractField(String frontmatter, String key) {
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + ":")) {
                return trimmed.substring(key.length() + 1).trim();
            }
        }
        return null;
    }

    private String extractMultilineField(String frontmatter, String key) {
        String[] lines = frontmatter.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inField = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + ":")) {
                // Check if value is on same line (not multiline with >)
                String remainder = trimmed.substring(key.length() + 1).trim();
                if (remainder.equals(">") || remainder.equals("|")) {
                    inField = true;
                    continue;
                }
                return remainder;
            }
            if (inField) {
                // Multiline continues until next non-indented key
                if (!line.startsWith(" ") && !line.startsWith("\t") && trimmed.contains(":")) {
                    break;
                }
                if (!trimmed.isEmpty()) {
                    if (!result.isEmpty()) result.append(" ");
                    result.append(trimmed);
                }
            }
        }
        return result.toString();
    }

    private List<String> extractListField(String frontmatter, String key) {
        String[] lines = frontmatter.split("\n");
        List<String> values = new ArrayList<>();
        boolean inList = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + ":")) {
                inList = true;
                continue;
            }
            if (inList) {
                if (trimmed.startsWith("- ")) {
                    values.add(trimmed.substring(2).trim());
                } else if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    break;
                }
            }
        }
        return values;
    }

    private String extractBody(String content) {
        if (!content.startsWith(FRONTMATTER_DELIMITER)) {
            return content;
        }
        int secondDelimiter = content.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (secondDelimiter < 0) {
            return content;
        }
        return content.substring(secondDelimiter + FRONTMATTER_DELIMITER.length()).trim();
    }

    private record ParsedSkill(
            String name,
            String description,
            boolean governance,
            List<String> applicableRoles,
            Path filePath
    ) {}
}
