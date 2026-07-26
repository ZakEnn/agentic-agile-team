package com.agile.team.infrastructure.persistence.repository;

import com.agile.team.infrastructure.persistence.entity.SkillGovernanceAuditJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillGovernanceAuditJpaRepository
        extends JpaRepository<SkillGovernanceAuditJpaEntity, UUID> {

    List<SkillGovernanceAuditJpaEntity> findByWaveIdOrderByUsedAtAsc(UUID waveId);
}
