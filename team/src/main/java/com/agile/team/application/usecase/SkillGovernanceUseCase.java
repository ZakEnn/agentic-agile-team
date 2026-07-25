package com.agile.team.application.usecase;

import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.port.SkillPort.SkillDescriptor;
import com.agile.team.domain.agent.AgentRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Governance use case for Agent Skills.
 * <p>
 * Skills marked as {@code governance: true} (e.g., review-criteria) affect
 * the ReviewGate decision. Changes to these Skills must be auditable and
 * authorized — this service ensures that Skill modifications are tracked
 * with the same rigor as Wave state transitions.
 * <p>
 * In the git-versioned strategy, governance is enforced at merge-time via
 * CODEOWNERS rules. This service provides runtime validation and audit logging
 * to complement the git-level control.
 */
@Service
public class SkillGovernanceUseCase {

    private static final Logger log = LoggerFactory.getLogger(SkillGovernanceUseCase.class);

    private final SkillPort skillPort;
    private final List<SkillAuditEntry> auditLog = Collections.synchronizedList(new ArrayList<>());

    public SkillGovernanceUseCase(SkillPort skillPort) {
        this.skillPort = skillPort;
    }

    /**
     * Validates that governance-sensitive skills are present and loadable
     * before a Wave can proceed through the review phase.
     * Called during Wave state transitions that depend on review criteria.
     *
     * @throws IllegalStateException if a required governance skill is missing or corrupted
     */
    public void validateGovernanceSkillsIntegrity() {
        List<SkillDescriptor> reviewerSkills = skillPort.resolveSkills(AgentRole.REVIEWER);

        List<SkillDescriptor> governanceSkills = reviewerSkills.stream()
                .filter(SkillDescriptor::governance)
                .toList();

        if (governanceSkills.isEmpty()) {
            throw new IllegalStateException(
                    "No governance skills found for REVIEWER role. " +
                    "At least 'review-criteria' must be present for ReviewGate to function correctly."
            );
        }

        for (SkillDescriptor skill : governanceSkills) {
            String content = skillPort.loadSkillContent(skill.name());
            if (content.isBlank()) {
                throw new IllegalStateException(
                        "Governance skill '" + skill.name() + "' is empty or unloadable. " +
                        "Cannot proceed with review phase without valid review criteria."
                );
            }
            log.debug("Governance skill '{}' validated successfully ({} chars)", skill.name(), content.length());
        }
    }

    /**
     * Records an audit entry when a governance skill is loaded for a review decision.
     * This creates traceability between the specific skill version used and the review outcome.
     */
    public void recordSkillUsage(String skillName, String waveId, String agentId) {
        SkillAuditEntry entry = new SkillAuditEntry(
                skillName, waveId, agentId, Instant.now()
        );
        auditLog.add(entry);
        log.info("Governance skill usage recorded: skill={}, wave={}, agent={}", skillName, waveId, agentId);
    }

    /**
     * Returns the audit log of governance skill usage (for observability/compliance).
     */
    public List<SkillAuditEntry> getAuditLog() {
        return Collections.unmodifiableList(auditLog);
    }

    public record SkillAuditEntry(
            String skillName,
            String waveId,
            String agentId,
            Instant timestamp
    ) {}
}
