package com.agile.team.infrastructure.persistence;

import com.agile.team.domain.port.SkillAuditPort;
import com.agile.team.infrastructure.persistence.entity.SkillGovernanceAuditJpaEntity;
import com.agile.team.infrastructure.persistence.repository.SkillGovernanceAuditJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SkillAuditAdapter implements SkillAuditPort {

    private static final Logger log = LoggerFactory.getLogger(SkillAuditAdapter.class);

    private final SkillGovernanceAuditJpaRepository repository;

    public SkillAuditAdapter(SkillGovernanceAuditJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(SkillUsage usage) {
        repository.save(new SkillGovernanceAuditJpaEntity(
                UUID.randomUUID(),
                usage.skillName(),
                parseUuid(usage.waveId()),
                usage.agentId(),
                usage.usedAt()));
    }

    @Override
    public List<SkillUsage> findByWave(String waveId) {
        UUID id = parseUuid(waveId);
        if (id == null) {
            return List.of();
        }
        return repository.findByWaveIdOrderByUsedAtAsc(id).stream().map(this::toDomain).toList();
    }

    @Override
    public List<SkillUsage> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private SkillUsage toDomain(SkillGovernanceAuditJpaEntity entity) {
        return new SkillUsage(
                entity.getSkillName(),
                entity.getWaveId() != null ? entity.getWaveId().toString() : null,
                entity.getAgentId(),
                entity.getUsedAt());
    }

    /**
     * Wave ids are UUIDs, but ad-hoc reviews (webhook-triggered, no wave) pass null
     * or a non-UUID correlation id. Storing null is correct there; refusing to store
     * the audit row at all would be worse.
     */
    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.debug("Skill audit wave id '{}' is not a UUID; storing null", value);
            return null;
        }
    }
}
