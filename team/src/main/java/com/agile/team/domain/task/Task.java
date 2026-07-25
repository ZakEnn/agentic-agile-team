package com.agile.team.domain.task;

import java.time.Instant;

public class Task {

    private final TaskId id;
    private final String title;
    private final String description;
    private TaskStatus status;
    private String jiraKey;
    private final Instant createdAt;
    private Instant updatedAt;

    public Task(TaskId id, String title, String description) {
        if (id == null) throw new IllegalArgumentException("Task id must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Task title must not be blank");
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Reconstruction constructor for loading from persistence */
    public Task(TaskId id, String title, String description, TaskStatus status, String jiraKey, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.jiraKey = jiraKey;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void startProgress() {
        if (this.status != TaskStatus.PENDING && this.status != TaskStatus.BLOCKED) {
            throw new IllegalStateException("Can only start task from PENDING or BLOCKED state");
        }
        this.status = TaskStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
    }

    public void submitForReview() {
        if (this.status != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only submit for review from IN_PROGRESS state");
        }
        this.status = TaskStatus.IN_REVIEW;
        this.updatedAt = Instant.now();
    }

    public void markDone() {
        if (this.status != TaskStatus.IN_REVIEW) {
            throw new IllegalStateException("Can only mark done from IN_REVIEW state");
        }
        this.status = TaskStatus.DONE;
        this.updatedAt = Instant.now();
    }

    public void block() {
        this.status = TaskStatus.BLOCKED;
        this.updatedAt = Instant.now();
    }

    public TaskId getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public String getJiraKey() { return jiraKey; }
    public void setJiraKey(String jiraKey) { this.jiraKey = jiraKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
