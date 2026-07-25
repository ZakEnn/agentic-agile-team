package com.agile.team.domain.conversation;

import com.agile.team.domain.wave.WaveId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ConversationHistory {

    private final UUID id;
    private final WaveId waveId;
    private final List<AgentMessage> messages;

    public ConversationHistory(UUID id, WaveId waveId) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (waveId == null) throw new IllegalArgumentException("waveId must not be null");
        this.id = id;
        this.waveId = waveId;
        this.messages = new ArrayList<>();
    }

    public static ConversationHistory startForWave(WaveId waveId) {
        return new ConversationHistory(UUID.randomUUID(), waveId);
    }

    public void record(AgentMessage message) {
        if (message == null) throw new IllegalArgumentException("message must not be null");
        messages.add(message);
    }

    public UUID getId() { return id; }
    public WaveId getWaveId() { return waveId; }
    public List<AgentMessage> getMessages() { return Collections.unmodifiableList(messages); }
    public int messageCount() { return messages.size(); }
}
