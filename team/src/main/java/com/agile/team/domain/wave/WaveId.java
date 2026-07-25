package com.agile.team.domain.wave;

import java.util.UUID;

public record WaveId(UUID value) {
    public WaveId {
        if (value == null) throw new IllegalArgumentException("WaveId value must not be null");
    }

    public static WaveId generate() {
        return new WaveId(UUID.randomUUID());
    }

    public static WaveId of(UUID value) {
        return new WaveId(value);
    }
}
