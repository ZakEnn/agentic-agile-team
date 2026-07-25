package com.agile.team.domain.conversation;

import com.agile.team.domain.agent.AgentId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentMessageTest {

    @Test
    void shouldCreateValidMessage() {
        AgentId from = AgentId.generate();
        AgentId to = AgentId.generate();

        AgentMessage msg = AgentMessage.create(from, to, MessageType.TASK_ASSIGNMENT, "Do the thing");

        assertNotNull(msg.id());
        assertEquals(from, msg.fromAgent());
        assertEquals(to, msg.toAgent());
        assertEquals(MessageType.TASK_ASSIGNMENT, msg.type());
        assertEquals("Do the thing", msg.payload());
        assertNotNull(msg.timestamp());
    }

    @Test
    void shouldRejectNullFromAgent() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentMessage.create(null, AgentId.generate(), MessageType.TASK_ASSIGNMENT, "payload"));
    }

    @Test
    void shouldRejectNullToAgent() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentMessage.create(AgentId.generate(), null, MessageType.TASK_ASSIGNMENT, "payload"));
    }

    @Test
    void shouldRejectNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentMessage.create(AgentId.generate(), AgentId.generate(), null, "payload"));
    }

    @Test
    void shouldRejectBlankPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentMessage.create(AgentId.generate(), AgentId.generate(), MessageType.TASK_ASSIGNMENT, ""));
    }
}
