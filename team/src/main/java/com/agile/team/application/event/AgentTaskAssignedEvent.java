package com.agile.team.application.event;

import com.agile.team.domain.agent.AgentId;
import com.agile.team.domain.task.TaskId;
import com.agile.team.domain.wave.WaveId;

public record AgentTaskAssignedEvent(
        WaveId waveId,
        AgentId agentId,
        TaskId taskId,
        String taskDescription,
        String keyword,
        String jiraTextLanguage
) {
}
