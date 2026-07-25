package com.agile.team.infrastructure.persistence;

import com.agile.team.domain.agent.*;
import com.agile.team.infrastructure.persistence.entity.AgentJpaEntity;
import com.agile.team.infrastructure.persistence.repository.AgentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AgentRepositoryAdapter implements AgentRepository {

    private final AgentJpaRepository jpaRepository;

    public AgentRepositoryAdapter(AgentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Agent save(Agent agent) {
        AgentJpaEntity entity = new AgentJpaEntity(
                agent.getId().value(),
                agent.getName(),
                agent.getRole().name()
        );
        jpaRepository.save(entity);
        return agent;
    }

    @Override
    public Optional<Agent> findById(AgentId id) {
        return jpaRepository.findById(id.value())
                .map(this::toDomain);
    }

    @Override
    public List<Agent> findByRole(AgentRole role) {
        return jpaRepository.findByRole(role.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Agent> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private Agent toDomain(AgentJpaEntity entity) {
        return new Agent(
                AgentId.of(entity.getId()),
                entity.getName(),
                AgentRole.valueOf(entity.getRole())
        );
    }
}
