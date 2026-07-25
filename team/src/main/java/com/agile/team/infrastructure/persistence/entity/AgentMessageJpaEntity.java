package com.agile.team.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_messages")
public class AgentMessageJpaEntity {

    @Id
    private UUID id;

    @Column(name = "from_agent_id", nullable = false)
    private UUID fromAgentId;

    @Column(name = "to_agent_id", nullable = false)
    private UUID toAgentId;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private ConversationJpaEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wave_id")
    private WaveJpaEntity wave;

    public AgentMessageJpaEntity() {}

    public AgentMessageJpaEntity(UUID id, UUID fromAgentId, UUID toAgentId, String messageType, String payload, Instant timestamp) {
        this.id = id;
        this.fromAgentId = fromAgentId;
        this.toAgentId = toAgentId;
        this.messageType = messageType;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getFromAgentId() { return fromAgentId; }
    public void setFromAgentId(UUID fromAgentId) { this.fromAgentId = fromAgentId; }
    public UUID getToAgentId() { return toAgentId; }
    public void setToAgentId(UUID toAgentId) { this.toAgentId = toAgentId; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public ConversationJpaEntity getConversation() { return conversation; }
    public void setConversation(ConversationJpaEntity conversation) { this.conversation = conversation; }
    public WaveJpaEntity getWave() { return wave; }
    public void setWave(WaveJpaEntity wave) { this.wave = wave; }
}
