package com.agile.team.infrastructure.persistence;

import com.agile.team.domain.stage.SdlcStage;
import com.agile.team.domain.stage.StageRun;
import com.agile.team.domain.stage.StageRunRepository;
import com.agile.team.domain.stage.StageStatus;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.infrastructure.persistence.entity.StageRunJpaEntity;
import com.agile.team.infrastructure.persistence.repository.StageRunJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class StageRunRepositoryAdapter implements StageRunRepository {

    private static final Logger log = LoggerFactory.getLogger(StageRunRepositoryAdapter.class);

    private final StageRunJpaRepository repository;

    /**
     * Set once if the database rejects {@code FOR UPDATE SKIP LOCKED}, after which we
     * use the plain select. The conditional UPDATE still guarantees correctness — we
     * lose only the contention optimisation, so degrading is safe rather than
     * dangerous. H2, used by the test tier, does not support the hint in all modes.
     */
    private final AtomicBoolean skipLockedUnsupported = new AtomicBoolean(false);

    public StageRunRepositoryAdapter(StageRunJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public StageRun save(StageRun stageRun) {
        repository.save(toEntity(stageRun));
        return stageRun;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StageRun> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StageRun> findByWaveAndStage(WaveId waveId, SdlcStage stage) {
        return repository.findByIdempotencyKey(StageRun.idempotencyKeyFor(waveId, stage))
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StageRun> findByWave(WaveId waveId) {
        return repository.findByWaveIdOrderByCreatedAtAsc(waveId.value()).stream()
                .map(this::toDomain).toList();
    }

    /**
     * Claim due stages for this instance.
     * <p>
     * Two steps, both required. The locking select narrows contention; the
     * conditional UPDATE is what actually establishes ownership — it succeeds for
     * exactly one caller per row because the {@code status = 'PENDING'} predicate
     * fails for whoever arrives second. A row that loses the race is simply skipped.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<StageRun> claimDueStages(String instanceId, int limit) {
        Instant now = Instant.now();
        List<StageRunJpaEntity> candidates = selectCandidates(now, limit);

        List<StageRun> claimed = new ArrayList<>(candidates.size());
        for (StageRunJpaEntity candidate : candidates) {
            int updated = repository.claim(candidate.getId(), instanceId, now);
            if (updated == 1) {
                repository.findById(candidate.getId()).map(this::toDomain).ifPresent(claimed::add);
            } else {
                log.debug("Stage {} was claimed by another instance; skipping", candidate.getId());
            }
        }
        return claimed;
    }

    private List<StageRunJpaEntity> selectCandidates(Instant now, int limit) {
        if (skipLockedUnsupported.get()) {
            return repository.selectClaimableWithoutLock(now, limit);
        }
        try {
            return repository.selectClaimable(now, limit);
        } catch (Exception e) {
            // Degrade once, loudly. Correctness is preserved by the conditional
            // UPDATE; only the contention optimisation is lost.
            if (skipLockedUnsupported.compareAndSet(false, true)) {
                log.warn("Database rejected FOR UPDATE SKIP LOCKED ({}). Falling back to a plain "
                        + "select; claims remain correct via the conditional update, but "
                        + "concurrent claimers will contend more.", e.getMessage());
            }
            return repository.selectClaimableWithoutLock(now, limit);
        }
    }

    @Override
    @Transactional
    public int releaseDueRetries() {
        return repository.releaseDueRetries(Instant.now());
    }

    @Override
    @Transactional
    public int reclaimStaleRunning(long olderThanSeconds) {
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(olderThanSeconds);
        int deadLettered = repository.deadLetterStaleExhausted(threshold, now);
        int reclaimed = repository.reclaimStaleRunning(threshold, now);
        if (deadLettered > 0 || reclaimed > 0) {
            log.warn("Recovered abandoned stages: {} returned to PENDING, {} dead-lettered",
                    reclaimed, deadLettered);
        }
        return reclaimed + deadLettered;
    }

    private StageRunJpaEntity toEntity(StageRun run) {
        StageRunJpaEntity entity = repository.findById(run.getId()).orElseGet(StageRunJpaEntity::new);
        entity.setId(run.getId());
        entity.setWaveId(run.getWaveId().value());
        entity.setStage(run.getStage().name());
        entity.setStatus(run.getStatus().name());
        entity.setAttempt(run.getAttempt());
        entity.setMaxAttempts(run.getMaxAttempts());
        entity.setIdempotencyKey(run.getIdempotencyKey());
        entity.setInputArtifact(run.getInputArtifact());
        entity.setOutputArtifact(run.getOutputArtifact());
        entity.setErrorMessage(run.getErrorMessage());
        entity.setTokensUsed(run.getTokensUsed());
        entity.setAvailableAt(run.getAvailableAt());
        entity.setClaimedBy(run.getClaimedBy());
        entity.setClaimedAt(run.getClaimedAt());
        entity.setCreatedAt(run.getCreatedAt());
        entity.setUpdatedAt(run.getUpdatedAt());
        return entity;
    }

    private StageRun toDomain(StageRunJpaEntity entity) {
        return new StageRun(
                entity.getId(),
                WaveId.of(entity.getWaveId()),
                SdlcStage.valueOf(entity.getStage()),
                entity.getIdempotencyKey(),
                entity.getMaxAttempts(),
                StageStatus.valueOf(entity.getStatus()),
                entity.getAttempt(),
                entity.getInputArtifact(),
                entity.getOutputArtifact(),
                entity.getErrorMessage(),
                entity.getTokensUsed(),
                entity.getAvailableAt(),
                entity.getClaimedBy(),
                entity.getClaimedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
