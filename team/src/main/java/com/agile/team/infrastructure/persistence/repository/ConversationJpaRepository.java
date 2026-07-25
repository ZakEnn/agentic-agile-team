package com.agile.team.infrastructure.persistence.repository;

import com.agile.team.infrastructure.persistence.entity.ConversationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, UUID> {
    Optional<ConversationJpaEntity> findByWaveId(UUID waveId);
}
