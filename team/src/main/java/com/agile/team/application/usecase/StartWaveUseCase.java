package com.agile.team.application.usecase;

import com.agile.team.domain.wave.WaveId;
import com.agile.team.application.orchestrator.Orchestrator;
import org.springframework.stereotype.Service;

@Service
public class StartWaveUseCase {

    private final Orchestrator orchestrator;

    public StartWaveUseCase(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public WaveId execute(String waveName, String taskDescription, String keyword, String jiraTextLanguage) {
        return orchestrator.startWave(waveName, taskDescription, keyword, jiraTextLanguage);
    }
}
