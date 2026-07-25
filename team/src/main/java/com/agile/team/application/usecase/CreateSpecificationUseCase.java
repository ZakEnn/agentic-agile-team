package com.agile.team.application.usecase;

import com.agile.team.domain.port.ConfluencePort;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CreateSpecificationUseCase {

    private final WaveRepository waveRepository;
    private final ConfluencePort confluencePort;

    public CreateSpecificationUseCase(WaveRepository waveRepository, ConfluencePort confluencePort) {
        this.waveRepository = waveRepository;
        this.confluencePort = confluencePort;
    }

    public SpecificationId execute(WaveId waveId, String pageId, String title) {
        Wave wave = waveRepository.findById(waveId)
                .orElseThrow(() -> new IllegalStateException("Wave not found: " + waveId));

        String content = confluencePort.fetchPageContent(pageId)
                .orElseThrow(() -> new IllegalStateException("Confluence page not found: " + pageId));

        Specification specification = new Specification(
                SpecificationId.generate(),
                title,
                content,
                pageId,
                List.of()
        );

        wave.addSpecification(specification);
        waveRepository.save(wave);

        return specification.getId();
    }
}
