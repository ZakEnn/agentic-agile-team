package com.agile.team.domain.wave;

import java.util.List;
import java.util.Optional;

public interface WaveRepository {

    Wave save(Wave wave);

    Optional<Wave> findById(WaveId id);

    List<Wave> findByStatus(WaveStatus status);

    List<Wave> findAll();
}
