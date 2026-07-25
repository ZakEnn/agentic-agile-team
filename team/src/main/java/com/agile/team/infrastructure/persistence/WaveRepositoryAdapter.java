package com.agile.team.infrastructure.persistence;

import com.agile.team.domain.wave.*;
import com.agile.team.infrastructure.persistence.entity.WaveJpaEntity;
import com.agile.team.infrastructure.persistence.mapper.WaveMapper;
import com.agile.team.infrastructure.persistence.repository.WaveJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class WaveRepositoryAdapter implements WaveRepository {

    private final WaveJpaRepository jpaRepository;
    private final WaveMapper mapper;

    public WaveRepositoryAdapter(WaveJpaRepository jpaRepository, WaveMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Wave save(Wave wave) {
        WaveJpaEntity entity = mapper.toEntity(wave);
        jpaRepository.save(entity);
        return wave;
    }

    @Override
    public Optional<Wave> findById(WaveId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Wave> findByStatus(WaveStatus status) {
        return jpaRepository.findByStatus(status.name()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Wave> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
