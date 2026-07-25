package com.agile.team.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "specifications")
public class SpecificationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "wave_id", nullable = false, insertable = false, updatable = false)
    private UUID waveId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_page_id")
    private String sourcePageId;

    /** JSON array. Stored as JSON rather than newline-joined so a criterion
     *  containing a newline cannot silently split into two. */
    @Column(name = "acceptance_criteria", columnDefinition = "TEXT")
    private String acceptanceCriteria;

    /** JSON array of explicit non-goals. */
    @Column(name = "out_of_scope", columnDefinition = "TEXT")
    private String outOfScope;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // --- SPEC_APPROVAL gate state (V1.3.0) ---

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wave_id")
    private WaveJpaEntity wave;

    public SpecificationJpaEntity() {}

    public SpecificationJpaEntity(UUID id, String title, String content, String sourcePageId,
                                   String acceptanceCriteria, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.sourcePageId = sourcePageId;
        this.acceptanceCriteria = acceptanceCriteria;
        this.createdAt = createdAt;
        this.status = "DRAFT";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWaveId() { return waveId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSourcePageId() { return sourcePageId; }
    public void setSourcePageId(String sourcePageId) { this.sourcePageId = sourcePageId; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
    public String getOutOfScope() { return outOfScope; }
    public void setOutOfScope(String outOfScope) { this.outOfScope = outOfScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public WaveJpaEntity getWave() { return wave; }
    public void setWave(WaveJpaEntity wave) { this.wave = wave; }
}
