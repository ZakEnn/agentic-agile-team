package com.agile.team.domain.stage;

import com.agile.team.domain.wave.WaveId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One durable execution of one SDLC stage for one wave.
 * <p>
 * This is the unit the orchestrator claims, runs, retries and records. Because it is
 * a database row rather than a thread, an instance dying mid-stage leaves a
 * {@code RUNNING} row that can be reclaimed, and a restart resumes exactly where the
 * wave had reached.
 */
public class StageRun {

    /** Base for exponential backoff between attempts. */
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final UUID id;
    private final WaveId waveId;
    private final SdlcStage stage;
    private final String idempotencyKey;
    private final int maxAttempts;
    private final Instant createdAt;

    private StageStatus status;
    private int attempt;
    private String inputArtifact;
    private String outputArtifact;
    private String errorMessage;
    private long tokensUsed;
    private Instant availableAt;
    private String claimedBy;
    private Instant claimedAt;
    private Instant updatedAt;

    public StageRun(UUID id, WaveId waveId, SdlcStage stage, String idempotencyKey,
                    int maxAttempts, StageStatus status, int attempt,
                    String inputArtifact, String outputArtifact, String errorMessage,
                    long tokensUsed, Instant availableAt, String claimedBy,
                    Instant claimedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.waveId = waveId;
        this.stage = stage;
        this.idempotencyKey = idempotencyKey;
        this.maxAttempts = maxAttempts;
        this.status = status;
        this.attempt = attempt;
        this.inputArtifact = inputArtifact;
        this.outputArtifact = outputArtifact;
        this.errorMessage = errorMessage;
        this.tokensUsed = tokensUsed;
        this.availableAt = availableAt;
        this.claimedBy = claimedBy;
        this.claimedAt = claimedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Enqueue a stage. The idempotency key is {@code waveId:stage}, so enqueuing the
     * same stage twice for the same wave violates a unique constraint rather than
     * silently duplicating the work — which is the behaviour you want when two
     * instances both react to the same completion.
     */
    public static StageRun enqueue(WaveId waveId, SdlcStage stage, String inputArtifact, int maxAttempts) {
        Instant now = Instant.now();
        return new StageRun(
                UUID.randomUUID(), waveId, stage,
                idempotencyKeyFor(waveId, stage),
                maxAttempts, StageStatus.PENDING, 0,
                inputArtifact, null, null, 0L,
                now, null, null, now, now);
    }

    public static String idempotencyKeyFor(WaveId waveId, SdlcStage stage) {
        return waveId.value() + ":" + stage.name();
    }

    /** Mark as claimed by an instance. */
    public void claim(String instanceId) {
        if (status != StageStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot claim a stage in status " + status + " (stage=" + stage + ")");
        }
        this.status = StageStatus.RUNNING;
        this.attempt = attempt + 1;
        this.claimedBy = instanceId;
        this.claimedAt = Instant.now();
        this.updatedAt = this.claimedAt;
    }

    public void succeed(String outputArtifact, long tokens) {
        this.status = StageStatus.SUCCEEDED;
        this.outputArtifact = outputArtifact;
        this.tokensUsed = tokensUsed + tokens;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Record a failure. Returns to {@link StageStatus#RETRYING} with exponential
     * backoff while attempts remain, and lands in {@link StageStatus#DEAD_LETTER}
     * once they are exhausted — a bounded loop, not an open-ended one.
     */
    public void fail(String message, long tokens) {
        this.tokensUsed = tokensUsed + tokens;
        this.errorMessage = message;
        this.updatedAt = Instant.now();
        this.claimedBy = null;

        if (attempt >= maxAttempts) {
            this.status = StageStatus.DEAD_LETTER;
        } else {
            this.status = StageStatus.RETRYING;
            this.availableAt = Instant.now().plus(backoffFor(attempt));
        }
    }

    /** Make a retrying stage claimable again once its backoff has elapsed. */
    public void makeAvailable() {
        if (status == StageStatus.RETRYING || status == StageStatus.AWAITING_APPROVAL) {
            this.status = StageStatus.PENDING;
            this.availableAt = Instant.now();
            this.updatedAt = this.availableAt;
        }
    }

    public void awaitApproval() {
        this.status = StageStatus.AWAITING_APPROVAL;
        this.claimedBy = null;
        this.updatedAt = Instant.now();
    }

    public void exceedBudget(String message) {
        this.status = StageStatus.BUDGET_EXCEEDED;
        this.errorMessage = message;
        this.claimedBy = null;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        this.status = StageStatus.CANCELLED;
        this.errorMessage = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * Reclaim a stage abandoned by a dead instance.
     * <p>
     * A {@code RUNNING} row whose owner has gone away would otherwise block its wave
     * forever. Treated as a failed attempt so it still respects the attempt ceiling.
     */
    public void reclaimAsFailed(String reason) {
        if (status != StageStatus.RUNNING) {
            return;
        }
        fail(reason, 0);
    }

    static Duration backoffFor(int attemptNumber) {
        long seconds = BASE_BACKOFF.toSeconds() * (1L << Math.min(attemptNumber, 10));
        return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.toSeconds()));
    }

    public UUID getId() { return id; }
    public WaveId getWaveId() { return waveId; }
    public SdlcStage getStage() { return stage; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public StageStatus getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getInputArtifact() { return inputArtifact; }
    public String getOutputArtifact() { return outputArtifact; }
    public String getErrorMessage() { return errorMessage; }
    public long getTokensUsed() { return tokensUsed; }
    public Instant getAvailableAt() { return availableAt; }
    public String getClaimedBy() { return claimedBy; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean hasAttemptsRemaining() { return attempt < maxAttempts; }
}
