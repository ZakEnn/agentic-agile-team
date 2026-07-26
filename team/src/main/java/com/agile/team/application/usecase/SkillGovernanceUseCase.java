package com.agile.team.application.usecase;

import com.agile.team.domain.port.SkillAuditPort;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.port.SkillPort.SkillDescriptor;
import com.agile.team.domain.agent.AgentRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final SkillAuditPort auditPort;

    public SkillGovernanceUseCase(SkillPort skillPort, SkillAuditPort auditPort) {
        this.skillPort = skillPort;
        this.auditPort = auditPort;
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
        auditPort.record(new SkillAuditPort.SkillUsage(skillName, waveId, agentId, Instant.now()));
        log.info("Governance skill usage recorded: skill={}, wave={}, agent={}", skillName, waveId, agentId);
    }

    /**
     * The audit log of governance skill usage.
     * <p>
     * Now backed by {@link SkillAuditPort} rather than an in-memory list. The
     * previous implementation kept entries in a
     * {@code Collections.synchronizedList(new ArrayList<>())}, so the traceability
     * this class documents was lost on every restart and invisible to any other
     * instance — an audit trail that does not survive a restart is not one.
     */
    public List<SkillAuditPort.SkillUsage> getAuditLog() {
        return auditPort.findAll();
    }

    /** Governance skills applied during a specific wave. */
    public List<SkillAuditPort.SkillUsage> getAuditLogForWave(String waveId) {
        return auditPort.findByWave(waveId);
    }
}
