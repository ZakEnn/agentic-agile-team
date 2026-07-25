package com.agile.team.domain.conversation;

import com.agile.team.domain.agent.AgentId;
import com.agile.team.domain.wave.WaveId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationHistoryTest {

    @Test
    void shouldStartForWave() {
        WaveId waveId = WaveId.generate();
        ConversationHistory history = ConversationHistory.startForWave(waveId);

        assertNotNull(history.getId());
        assertEquals(waveId, history.getWaveId());
        assertEquals(0, history.messageCount());
    }

    @Test
    void shouldRecordMessages() {
        ConversationHistory history = ConversationHistory.startForWave(WaveId.generate());

        AgentMessage msg = AgentMessage.create(
                AgentId.generate(), AgentId.generate(),
                MessageType.TASK_ASSIGNMENT, "Implement feature X"
        );
        history.record(msg);

        assertEquals(1, history.messageCount());
        assertEquals(msg, history.getMessages().get(0));
    }

    @Test
    void shouldRejectNullMessage() {
        ConversationHistory history = ConversationHistory.startForWave(WaveId.generate());
        assertThrows(IllegalArgumentException.class, () -> history.record(null));
    }

    @Test
    void shouldReturnUnmodifiableMessageList() {
        ConversationHistory history = ConversationHistory.startForWave(WaveId.generate());
        assertThrows(UnsupportedOperationException.class,
                () -> history.getMessages().add(null));
    }
}
