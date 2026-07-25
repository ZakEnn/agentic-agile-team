package com.agile.team.interfaces.rest;

import com.agile.team.application.usecase.DecideSpecificationUseCase;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.interfaces.rest.dto.SpecDecisionRequest;
import com.agile.team.interfaces.rest.dto.SpecificationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The SPEC_APPROVAL gate, as an API.
 * <p>
 * This is the mechanism the original system lacked: it logged "awaiting manual
 * validation" but offered no way to supply it, so the only route forward was to
 * uncomment code and redeploy. A gate with no endpoint is not a gate.
 */
@RestController
@RequestMapping("/api/waves/{waveId}/specifications")
public class SpecificationController {

    private final DecideSpecificationUseCase decideSpecification;
    private final WaveRepository waveRepository;

    public SpecificationController(DecideSpecificationUseCase decideSpecification,
                                   WaveRepository waveRepository) {
        this.decideSpecification = decideSpecification;
        this.waveRepository = waveRepository;
    }

    @GetMapping
    public ResponseEntity<List<SpecificationResponse>> list(@PathVariable UUID waveId) {
        return waveRepository.findById(WaveId.of(waveId))
                .map(wave -> ResponseEntity.ok(
                        wave.getSpecifications().stream()
                                .map(spec -> SpecificationResponse.from(waveId.toString(), spec))
                                .toList()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{specificationId}")
    public ResponseEntity<SpecificationResponse> get(@PathVariable UUID waveId,
                                                     @PathVariable UUID specificationId) {
        return waveRepository.findById(WaveId.of(waveId))
                .flatMap(wave -> wave.findSpecification(SpecificationId.of(specificationId)))
                .map(spec -> ResponseEntity.ok(SpecificationResponse.from(waveId.toString(), spec)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{specificationId}/approve")
    public ResponseEntity<SpecificationResponse> approve(@PathVariable UUID waveId,
                                                         @PathVariable UUID specificationId,
                                                         @RequestBody SpecDecisionRequest request) {
        Wave wave = decideSpecification.approve(
                WaveId.of(waveId), SpecificationId.of(specificationId),
                request.decidedBy(), request.reason());
        return respond(waveId, specificationId, wave);
    }

    @PostMapping("/{specificationId}/reject")
    public ResponseEntity<SpecificationResponse> reject(@PathVariable UUID waveId,
                                                        @PathVariable UUID specificationId,
                                                        @RequestBody SpecDecisionRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("A rejection must carry a reason");
        }
        Wave wave = decideSpecification.reject(
                WaveId.of(waveId), SpecificationId.of(specificationId),
                request.decidedBy(), request.reason());
        return respond(waveId, specificationId, wave);
    }

    /** Edit a draft before deciding on it. Rejected once a decision exists. */
    @PostMapping("/{specificationId}/edit")
    public ResponseEntity<SpecificationResponse> edit(@PathVariable UUID waveId,
                                                      @PathVariable UUID specificationId,
                                                      @RequestBody SpecDecisionRequest.Edit request) {
        SpecDraft edited = new SpecDraft(
                request.summary(), request.description(),
                request.acceptanceCriteria(), request.outOfScope());
        Wave wave = decideSpecification.edit(
                WaveId.of(waveId), SpecificationId.of(specificationId), edited, request.editedBy());
        return respond(waveId, specificationId, wave);
    }

    private ResponseEntity<SpecificationResponse> respond(UUID waveId, UUID specificationId, Wave wave) {
        return wave.findSpecification(SpecificationId.of(specificationId))
                .map(spec -> ResponseEntity.ok(SpecificationResponse.from(waveId.toString(), spec)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
