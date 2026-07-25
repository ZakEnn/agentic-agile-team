package com.agile.team.application.event;

import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.wave.WaveId;

public record SpecificationReadyEvent(
        WaveId waveId,
        SpecificationId specificationId
) {
}
