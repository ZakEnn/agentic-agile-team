package com.agile.team.infrastructure.persistence.repository;

import com.agile.team.infrastructure.persistence.entity.WaveJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WaveJpaRepository extends JpaRepository<WaveJpaEntity, UUID> {
    List<WaveJpaEntity> findByStatus(String status);
}
