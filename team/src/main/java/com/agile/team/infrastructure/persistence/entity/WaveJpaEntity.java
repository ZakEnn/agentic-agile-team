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

    // --- Per-wave targeting context (V1.3.0). Previously compile-time constants. ---

    @Column(name = "confluence_space_key", length = 100)
    private String confluenceSpaceKey;

    @Column(name = "gitlab_project", length = 500)
    private String gitLabProject;

    @Column(name = "jira_project_key", length = 50)
    private String jiraProjectKey;

    @Column(name = "language", length = 50)
    private String language;

    /** Cumulative model tokens consumed by this wave, for budget enforcement. */
    @Column(name = "tokens_used", nullable = false)
    private long tokensUsed;

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
    public String getConfluenceSpaceKey() { return confluenceSpaceKey; }
    public void setConfluenceSpaceKey(String confluenceSpaceKey) { this.confluenceSpaceKey = confluenceSpaceKey; }
    public String getGitLabProject() { return gitLabProject; }
    public void setGitLabProject(String gitLabProject) { this.gitLabProject = gitLabProject; }
    public String getJiraProjectKey() { return jiraProjectKey; }
    public void setJiraProjectKey(String jiraProjectKey) { this.jiraProjectKey = jiraProjectKey; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public long getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(long tokensUsed) { this.tokensUsed = tokensUsed; }
}
