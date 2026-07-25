package com.agile.team.domain.agent;

import java.util.UUID;

public record AgentId(UUID value) {
    public AgentId {
        if (value == null) throw new IllegalArgumentException("AgentId value must not be null");
    }

    public static AgentId generate() {
        return new AgentId(UUID.randomUUID());
    }

    public static AgentId of(UUID value) {
        return new AgentId(value);
    }
}
