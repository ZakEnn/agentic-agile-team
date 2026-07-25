package com.agile.team.application.usecase;

import com.agile.team.application.orchestrator.Orchestrator;
import com.agile.team.domain.wave.WaveContext;
import com.agile.team.domain.wave.WaveId;
import org.springframework.stereotype.Service;

@Service
public class StartWaveUseCase {

    private final Orchestrator orchestrator;

    public StartWaveUseCase(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public WaveId execute(String waveName, String taskDescription, String keyword, WaveContext context) {
        return orchestrator.startWave(
                new Orchestrator.StartWaveCommand(waveName, taskDescription, keyword, context));
    }
}
