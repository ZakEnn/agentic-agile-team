package com.agile.team.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;

public record WaveStatusResponse(
        String waveId,
        String name,
        String status,
        int taskCount,
        int specificationCount,
        List<String> transitions,
        Instant createdAt
) {
}
