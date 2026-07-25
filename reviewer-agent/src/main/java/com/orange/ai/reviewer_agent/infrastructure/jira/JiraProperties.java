package com.orange.ai.reviewer_agent.infrastructure.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
public record JiraProperties(
        String baseUrl,
        String username,
        String apiToken,
        String projectKey,
        String email
) {
}