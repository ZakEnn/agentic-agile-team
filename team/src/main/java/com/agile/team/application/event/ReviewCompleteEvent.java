package com.agile.team.application.event;

import com.agile.team.domain.agent.AgentId;
import com.agile.team.domain.review.ApprovalStatus;
import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.ReviewDisposition;
import com.agile.team.domain.task.TaskId;
import com.agile.team.domain.wave.WaveId;

public record ReviewCompleteEvent(
        WaveId waveId,
        AgentId reviewerAgentId,
        TaskId taskId,
        ReviewDisposition reviewDisposition,
        CodeQualityScore qualityScore
) {
}
