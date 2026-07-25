package com.agile.team.application.event;

import com.agile.team.domain.task.TaskId;
import com.agile.team.domain.wave.WaveId;

public record QaCompleteEvent(
        WaveId waveId,
        TaskId taskId,
        boolean passed,
        String report
) {
}
