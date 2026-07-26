package com.agile.team.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A record that a governance skill was applied to a specific decision.
 * <p>
 * Persisted so a review verdict can be traced back to the exact criteria that
 * produced it — the question "why was this rejected, and under which rules?" needs
 * an answer that survives a restart.
 */
@Entity
@Table(name = "skill_governance_audit")
public class SkillGovernanceAuditJpaEntity {

    @Id
    private UUID id;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "wave_id")
    private UUID waveId;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    public SkillGovernanceAuditJpaEntity() {
    }

    public SkillGovernanceAuditJpaEntity(UUID id, String skillName, UUID waveId,
                                         String agentId, Instant usedAt) {
        this.id = id;
        this.skillName = skillName;
        this.waveId = waveId;
        this.agentId = agentId;
        this.usedAt = usedAt;
    }

    public UUID getId() { return id; }
    public String getSkillName() { return skillName; }
    public UUID getWaveId() { return waveId; }
    public String getAgentId() { return agentId; }
    public Instant getUsedAt() { return usedAt; }
}
