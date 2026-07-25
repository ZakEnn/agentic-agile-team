package com.agile.team.application.event;

import com.agile.team.domain.agent.AgentId;
import com.agile.team.domain.task.TaskId;
import com.agile.team.domain.wave.WaveId;

public record ImplementationCompleteEvent(
        WaveId waveId,
        AgentId devAgentId,
        TaskId taskId,
        String mergeRequestId
) {
}
