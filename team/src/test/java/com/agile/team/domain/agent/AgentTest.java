package com.agile.team.domain.agent;

import com.agile.team.domain.task.Task;
import com.agile.team.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    @Test
    void shouldCreateAgent() {
        Agent agent = new Agent(AgentId.generate(), "PO Bot", AgentRole.PO);

        assertEquals("PO Bot", agent.getName());
        assertEquals(AgentRole.PO, agent.getRole());
        assertTrue(agent.getAssignedTasks().isEmpty());
    }

    @Test
    void shouldAssignTask() {
        Agent agent = new Agent(AgentId.generate(), "Dev Bot", AgentRole.DEV);
        Task task = new Task(TaskId.generate(), "Implement feature", "Description");

        agent.assignTask(task);

        assertEquals(1, agent.getAssignedTasks().size());
    }

    @Test
    void shouldRejectNullName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Agent(AgentId.generate(), null, AgentRole.PO));
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Agent(AgentId.generate(), "  ", AgentRole.PO));
    }

    @Test
    void shouldRejectNullRole() {
        assertThrows(IllegalArgumentException.class,
                () -> new Agent(AgentId.generate(), "Bot", null));
    }
}
