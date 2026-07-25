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

    @Column(name = "acceptance_criteria", columnDefinition = "TEXT")
    private String acceptanceCriteria;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public WaveJpaEntity getWave() { return wave; }
    public void setWave(WaveJpaEntity wave) { this.wave = wave; }
}
