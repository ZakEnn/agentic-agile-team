package com.agile.team.application.usecase;

import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.ReviewDisposition;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import org.springframework.stereotype.Service;

@Service
public class CompleteReviewUseCase {

    private final WaveRepository waveRepository;

    public CompleteReviewUseCase(WaveRepository waveRepository) {
        this.waveRepository = waveRepository;
    }

    /**
     * Completes a wave after review. Enforces the ReviewGate governance check internally
     * via the Wave aggregate — will throw if gate is not passed.
     */
    public void execute(WaveId waveId, String authorizedBy,
                        ReviewDisposition reviewDisposition, CodeQualityScore qualityScore) {
        Wave wave = waveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalStateException("Wave not found: " + waveId));

        wave.complete(authorizedBy, reviewDisposition, qualityScore);
        waveRepository.save(wave);
    }
}
