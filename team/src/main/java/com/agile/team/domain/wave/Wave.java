package com.agile.team.domain.wave;

import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.ReviewDisposition;
import com.agile.team.domain.review.ReviewGate;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.task.Task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Wave {

    private final WaveId id;
    private final String name;
    private WaveStatus status;
    private final List<Specification> specifications;
    private final List<Task> tasks;
    private final List<StateTransition> transitions;
    private final Instant createdAt;
    /** Which Confluence space / GitLab project / Jira project this wave targets. */
    private WaveContext context;

    public Wave(WaveId id, String name) {
        this(id, name, WaveContext.forSpecOnly(null, null));
    }

    public Wave(WaveId id, String name, WaveContext context) {
        if (id == null) throw new IllegalArgumentException("Wave id must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Wave name must not be blank");
        this.id = id;
        this.name = name;
        this.status = WaveStatus.PLANNING;
        this.specifications = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.transitions = new ArrayList<>();
        this.createdAt = Instant.now();
        this.context = context != null ? context : WaveContext.forSpecOnly(null, null);
    }

    /** Reconstruction constructor for loading from persistence */
    public Wave(WaveId id, String name, WaveStatus status, List<Specification> specifications,
                List<Task> tasks, List<StateTransition> transitions, Instant createdAt) {
        this(id, name, status, specifications, tasks, transitions, createdAt,
                WaveContext.forSpecOnly(null, null));
    }

    /** Reconstruction constructor including targeting context. */
    public Wave(WaveId id, String name, WaveStatus status, List<Specification> specifications,
                List<Task> tasks, List<StateTransition> transitions, Instant createdAt,
                WaveContext context) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.specifications = new ArrayList<>(specifications);
        this.tasks = new ArrayList<>(tasks);
        this.transitions = new ArrayList<>(transitions);
        this.createdAt = createdAt;
        this.context = context != null ? context : WaveContext.forSpecOnly(null, null);
    }

    /** Locate a specification owned by this wave. */
    public java.util.Optional<Specification> findSpecification(
            com.agile.team.domain.specification.SpecificationId specificationId) {
        return specifications.stream()
                .filter(s -> s.getId().equals(specificationId))
                .findFirst();
    }

    /**
     * True when at least one specification has passed the SPEC_APPROVAL gate.
     * {@link #startExecution(String)} requires this: an unapproved spec must not
     * reach the Developer agent.
     */
    public boolean hasApprovedSpecification() {
        return specifications.stream().anyMatch(Specification::isApproved);
    }

    public void addSpecification(Specification specification) {
        if (status != WaveStatus.PLANNING) {
            throw new IllegalStateException("Can only add specifications during PLANNING phase");
        }
        specifications.add(specification);
    }

    public void addTask(Task task) {
        tasks.add(task);
    }

    /**
     * Begin execution. Requires at least one specification that has passed the
     * SPEC_APPROVAL gate.
     * <p>
     * The approval requirement is enforced here, in the aggregate, rather than only
     * in the orchestrator. The original system's governance failure was precisely
     * that a correct invariant ({@code ReviewGate}) sat in the domain while the
     * handler fed it fabricated inputs — an invariant a caller can route around is
     * not an invariant. Making unapproved execution unrepresentable means no future
     * handler can regress it by accident.
     */
    public void startExecution(String authorizedBy) {
        if (status != WaveStatus.PLANNING) {
            throw new IllegalStateException("Can only start execution from PLANNING state");
        }
        if (specifications.isEmpty()) {
            throw new IllegalStateException("Cannot start wave without specifications");
        }
        if (!hasApprovedSpecification()) {
            throw new IllegalStateException(
                    "Cannot start wave: no specification has passed the SPEC_APPROVAL gate");
        }
        transition(WaveStatus.IN_PROGRESS, authorizedBy);
    }

    public void moveToReview(String authorizedBy) {
        if (status != WaveStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only move to review from IN_PROGRESS state");
        }
        transition(WaveStatus.IN_REVIEW, authorizedBy);
    }

    public void complete(String authorizedBy, ReviewDisposition reviewDisposition, CodeQualityScore qualityScore) {
        if (status != WaveStatus.IN_REVIEW) {
            throw new IllegalStateException("Can only complete from IN_REVIEW state");
        }
        if (!ReviewGate.canComplete(reviewDisposition, qualityScore)) {
            throw new IllegalStateException("Cannot complete wave: ReviewGate check failed");
        }
        transition(WaveStatus.COMPLETED, authorizedBy);
    }

    public void fail(String authorizedBy) {
        transition(WaveStatus.FAILED, authorizedBy);
    }

    private void transition(WaveStatus newStatus, String authorizedBy) {
        transitions.add(StateTransition.create(this.status, newStatus, authorizedBy));
        this.status = newStatus;
    }

    public WaveId getId() { return id; }
    public String getName() { return name; }
    public WaveStatus getStatus() { return status; }
    public List<Specification> getSpecifications() { return Collections.unmodifiableList(specifications); }
    public List<Task> getTasks() { return Collections.unmodifiableList(tasks); }
    public List<StateTransition> getTransitions() { return Collections.unmodifiableList(transitions); }
    public Instant getCreatedAt() { return createdAt; }
    public WaveContext getContext() { return context; }
}
