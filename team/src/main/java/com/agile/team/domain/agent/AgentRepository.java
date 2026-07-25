package com.agile.team.domain.agent;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {

    Agent save(Agent agent);

    Optional<Agent> findById(AgentId id);

    List<Agent> findByRole(AgentRole role);

    List<Agent> findAll();
}
