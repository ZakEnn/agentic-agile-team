package com.agile.team.interfaces.rest.dto;

import com.agile.team.domain.wave.WaveContext;

/**
 * Request to start a wave.
 * <p>
 * The targeting fields (Confluence space, GitLab project, Jira project, language)
 * are supplied per wave. They were previously compile-time constants — {@code "EPE"},
 * {@code "epe-rating-ftth-passive"}, {@code "SCA"}, {@code "French"} — which meant
 * the platform could only ever work on one project.
 */
public record StartWaveRequest(
        String waveName,
        String taskDescription,
        String keyword,
        String confluenceSpaceKey,
        String gitLabProject,
        String jiraProjectKey,
        String language
) {
    public StartWaveRequest {
        if (waveName == null || waveName.isBlank()) {
            throw new IllegalArgumentException("waveName must not be blank");
        }
        if (taskDescription == null || taskDescription.isBlank()) {
            throw new IllegalArgumentException("taskDescription must not be blank");
        }
    }

    public WaveContext toContext() {
        return new WaveContext(confluenceSpaceKey, gitLabProject, jiraProjectKey, language);
    }
}
