package com.orange.ai.reviewer_agent.infrastructure.sonarqube;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sonarqube")
public record SonarQubeProperties(
        String baseUrl,
        String token
) {
}