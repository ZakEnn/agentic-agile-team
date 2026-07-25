package com.agile.team.infrastructure.persistence.repository;

import com.agile.team.infrastructure.persistence.entity.AgentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentJpaRepository extends JpaRepository<AgentJpaEntity, UUID> {
    List<AgentJpaEntity> findByRole(String role);
}
