package com.agile.team.interfaces.rest.dto;

public record StartWaveRequest(
        String waveName,
        String taskDescription,
        String keyword,
        String jiraTextLanguage
) {
    public StartWaveRequest {
        if (waveName == null || waveName.isBlank()) throw new IllegalArgumentException("waveName must not be blank");
        if (taskDescription == null || taskDescription.isBlank()) throw new IllegalArgumentException("taskDescription must not be blank");
        // keyword is optional — Confluence search will be skipped if null/blank
        // jiraTextLanguage defaults to "French" if not provided
    }

    public String resolvedJiraTextLanguage() {
        return (jiraTextLanguage == null || jiraTextLanguage.isBlank()) ? "French" : jiraTextLanguage;
    }
}
