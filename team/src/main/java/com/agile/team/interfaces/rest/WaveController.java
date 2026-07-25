package com.agile.team.interfaces.rest;

import com.agile.team.application.usecase.StartWaveUseCase;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.domain.wave.StateTransition;
import com.agile.team.interfaces.rest.dto.StartWaveRequest;
import com.agile.team.interfaces.rest.dto.WaveStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/waves")
public class WaveController {

    private final StartWaveUseCase startWaveUseCase;
    private final WaveRepository waveRepository;

    public WaveController(StartWaveUseCase startWaveUseCase, WaveRepository waveRepository) {
        this.startWaveUseCase = startWaveUseCase;
        this.waveRepository = waveRepository;
    }

    @PostMapping
    public ResponseEntity<String> startWave(@RequestBody StartWaveRequest request) {
        WaveId waveId = startWaveUseCase.execute(
                request.waveName(),
                request.taskDescription(),
                request.keyword(),
                request.resolvedJiraTextLanguage()
        );
        return ResponseEntity.ok(waveId.value().toString());
    }

    @GetMapping("/{waveId}")
    public ResponseEntity<WaveStatusResponse> getWaveStatus(@PathVariable UUID waveId) {
        Wave wave = waveRepository.findById(WaveId.of(waveId))
                .orElse(null);

        if (wave == null) {
            return ResponseEntity.notFound().build();
        }

        List<String> transitions = wave.getTransitions().stream()
                .map(t -> t.fromStatus() + " -> " + t.toStatus() + " by " + t.authorizedBy())
                .toList();

        WaveStatusResponse response = new WaveStatusResponse(
                wave.getId().value().toString(),
                wave.getName(),
                wave.getStatus().name(),
                wave.getTasks().size(),
                wave.getSpecifications().size(),
                transitions,
                wave.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<WaveStatusResponse>> listWaves() {
        List<WaveStatusResponse> waves = waveRepository.findAll().stream()
                .map(wave -> new WaveStatusResponse(
                        wave.getId().value().toString(),
                        wave.getName(),
                        wave.getStatus().name(),
                        wave.getTasks().size(),
                        wave.getSpecifications().size(),
                        List.of(),
                        wave.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(waves);
    }
}
