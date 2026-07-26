package com.agile.team.infrastructure.persistence.repository;

import com.agile.team.infrastructure.persistence.entity.StageRunJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StageRunJpaRepository extends JpaRepository<StageRunJpaEntity, UUID> {

    Optional<StageRunJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<StageRunJpaEntity> findByWaveIdOrderByCreatedAtAsc(UUID waveId);

    /**
     * Select claimable stages, locking them and skipping rows another instance
     * already holds.
     * <p>
     * {@code FOR UPDATE SKIP LOCKED} is what makes this safe across Cloud Foundry
     * instances sharing one datasource: concurrent claimers each get a disjoint set
     * instead of blocking on each other or double-claiming.
     * <p>
     * The subsequent conditional UPDATE in
     * {@code StageRunRepositoryAdapter#claimDueStages} is the correctness guarantee;
     * SKIP LOCKED is the contention optimisation on top of it. Keeping both means the
     * claim stays correct even on a database that ignores the locking hint.
     */
    @Query(value = """
            SELECT * FROM stage_run
            WHERE status = 'PENDING' AND available_at <= :now
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<StageRunJpaEntity> selectClaimable(@Param("now") Instant now, @Param("limit") int limit);

    /** Fallback without the locking hint, for databases that do not support it. */
    @Query(value = """
            SELECT * FROM stage_run
            WHERE status = 'PENDING' AND available_at <= :now
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<StageRunJpaEntity> selectClaimableWithoutLock(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * The atomic claim. Succeeds for exactly one caller per row: the
     * {@code status = 'PENDING'} predicate fails for everyone who arrives second.
     */
    /*
     * flushAutomatically + clearAutomatically on every @Modifying query below.
     *
     * A native UPDATE bypasses the persistence context: without clearing, a
     * subsequent findById returns the *cached* entity, which still shows the row as
     * PENDING with a null claimed_by even though the database row was updated. That
     * produced a claim that appeared to succeed and then handed back stale state.
     * Flushing first ensures pending writes reach the database before the bulk
     * statement runs.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE stage_run
            SET status = 'RUNNING', attempt = attempt + 1,
                claimed_by = :instanceId, claimed_at = :now, updated_at = :now
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("instanceId") String instanceId, @Param("now") Instant now);

    /** Make retrying stages claimable once their backoff has elapsed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE stage_run
            SET status = 'PENDING', updated_at = :now
            WHERE status = 'RETRYING' AND available_at <= :now
            """, nativeQuery = true)
    int releaseDueRetries(@Param("now") Instant now);

    /**
     * Reclaim stages whose owning instance died mid-execution.
     * <p>
     * Returned to PENDING rather than failed outright: the attempt counter was
     * already incremented at claim time, so the attempt ceiling still applies and a
     * genuinely stuck stage still dead-letters eventually.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE stage_run
            SET status = 'PENDING', claimed_by = NULL, updated_at = :now
            WHERE status = 'RUNNING' AND claimed_at < :threshold AND attempt < max_attempts
            """, nativeQuery = true)
    int reclaimStaleRunning(@Param("threshold") Instant threshold, @Param("now") Instant now);

    /** Stale runs that have also exhausted their attempts go straight to dead letter. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE stage_run
            SET status = 'DEAD_LETTER', claimed_by = NULL, updated_at = :now,
                error_message = 'Abandoned by a dead instance after exhausting attempts'
            WHERE status = 'RUNNING' AND claimed_at < :threshold AND attempt >= max_attempts
            """, nativeQuery = true)
    int deadLetterStaleExhausted(@Param("threshold") Instant threshold, @Param("now") Instant now);
}
