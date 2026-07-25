package com.agile.team.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "waves")
public class WaveJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "wave", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AgentMessageJpaEntity> messages = new ArrayList<>();

    @OneToMany(mappedBy = "wave", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SpecificationJpaEntity> specifications = new ArrayList<>();

    @OneToMany(mappedBy = "wave", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TaskJpaEntity> tasks = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wave_transitions", joinColumns = @JoinColumn(name = "wave_id"))
    private List<StateTransitionEmbeddable> transitions = new ArrayList<>();

    public WaveJpaEntity() {}

    public WaveJpaEntity(UUID id, String name, String status, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<AgentMessageJpaEntity> getMessages() { return messages; }
    public void setMessages(List<AgentMessageJpaEntity> messages) { this.messages = messages; }
    public List<SpecificationJpaEntity> getSpecifications() { return specifications; }
    public void setSpecifications(List<SpecificationJpaEntity> specifications) { this.specifications = specifications; }
    public List<TaskJpaEntity> getTasks() { return tasks; }
    public void setTasks(List<TaskJpaEntity> tasks) { this.tasks = tasks; }
    public List<StateTransitionEmbeddable> getTransitions() { return transitions; }
    public void setTransitions(List<StateTransitionEmbeddable> transitions) { this.transitions = transitions; }
}
