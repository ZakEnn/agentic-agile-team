package com.agile.team.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage_run")
public class StageRunJpaEntity {

    @Id
    private UUID id;

    @Column(name = "wave_id", nullable = false)
    private UUID waveId;

    @Column(name = "stage", nullable = false, length = 20)
    private String stage;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "input_artifact", columnDefinition = "TEXT")
    private String inputArtifact;

    @Column(name = "output_artifact", columnDefinition = "TEXT")
    private String outputArtifact;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "tokens_used", nullable = false)
    private long tokensUsed;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public StageRunJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWaveId() { return waveId; }
    public void setWaveId(UUID waveId) { this.waveId = waveId; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getInputArtifact() { return inputArtifact; }
    public void setInputArtifact(String inputArtifact) { this.inputArtifact = inputArtifact; }
    public String getOutputArtifact() { return outputArtifact; }
    public void setOutputArtifact(String outputArtifact) { this.outputArtifact = outputArtifact; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(long tokensUsed) { this.tokensUsed = tokensUsed; }
    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
