package com.agile.team.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class ConversationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "wave_id", nullable = false)
    private UUID waveId;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("timestamp ASC")
    private List<AgentMessageJpaEntity> messages = new ArrayList<>();

    public ConversationJpaEntity() {}

    public ConversationJpaEntity(UUID id, UUID waveId) {
        this.id = id;
        this.waveId = waveId;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWaveId() { return waveId; }
    public void setWaveId(UUID waveId) { this.waveId = waveId; }
    public List<AgentMessageJpaEntity> getMessages() { return messages; }
    public void setMessages(List<AgentMessageJpaEntity> messages) { this.messages = messages; }
}
