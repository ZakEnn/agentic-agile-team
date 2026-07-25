package com.agile.team.domain.wave;

import java.time.Instant;

public record StateTransition(
        WaveStatus fromStatus,
        WaveStatus toStatus,
        String authorizedBy,
        Instant timestamp
) {
    public StateTransition {
        if (fromStatus == null) throw new IllegalArgumentException("fromStatus must not be null");
        if (toStatus == null) throw new IllegalArgumentException("toStatus must not be null");
        if (authorizedBy == null || authorizedBy.isBlank()) throw new IllegalArgumentException("authorizedBy must not be blank");
        if (timestamp == null) timestamp = Instant.now();
    }

    public static StateTransition create(WaveStatus from, WaveStatus to, String authorizedBy) {
        return new StateTransition(from, to, authorizedBy, Instant.now());
    }
}
