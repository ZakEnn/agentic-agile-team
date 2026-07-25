package com.agile.team.domain.conversation;

import com.agile.team.domain.agent.AgentId;

import java.time.Instant;
import java.util.UUID;

public record AgentMessage(
        UUID id,
        AgentId fromAgent,
        AgentId toAgent,
        MessageType type,
        String payload,
        Instant timestamp
) {
    public AgentMessage {
        if (id == null) id = UUID.randomUUID();
        if (fromAgent == null) throw new IllegalArgumentException("fromAgent must not be null");
        if (toAgent == null) throw new IllegalArgumentException("toAgent must not be null");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload must not be blank");
        if (timestamp == null) timestamp = Instant.now();
    }

    public static AgentMessage create(AgentId from, AgentId to, MessageType type, String payload) {
        return new AgentMessage(UUID.randomUUID(), from, to, type, payload, Instant.now());
    }
}
