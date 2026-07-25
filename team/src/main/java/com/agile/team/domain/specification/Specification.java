package com.agile.team.domain.specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class Specification {

    private final SpecificationId id;
    private final String title;
    private final String content;
    private final String sourcePageId;
    private final List<String> acceptanceCriteria;
    private final Instant createdAt;

    public Specification(SpecificationId id, String title, String content, String sourcePageId, List<String> acceptanceCriteria) {
        if (id == null) throw new IllegalArgumentException("Specification id must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title must not be blank");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Content must not be blank");
        this.id = id;
        this.title = title;
        this.content = content;
        this.sourcePageId = sourcePageId;
        this.acceptanceCriteria = acceptanceCriteria != null ? new ArrayList<>(acceptanceCriteria) : new ArrayList<>();
        this.createdAt = Instant.now();
    }

    /** Reconstruction constructor for loading from persistence */
    public Specification(SpecificationId id, String title, String content, String sourcePageId,
                         List<String> acceptanceCriteria, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.sourcePageId = sourcePageId;
        this.acceptanceCriteria = acceptanceCriteria != null ? new ArrayList<>(acceptanceCriteria) : new ArrayList<>();
        this.createdAt = createdAt;
    }

    public SpecificationId getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSourcePageId() { return sourcePageId; }
    public List<String> getAcceptanceCriteria() { return Collections.unmodifiableList(acceptanceCriteria); }
    public Instant getCreatedAt() { return createdAt; }
}
