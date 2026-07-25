package com.agile.team.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "confluence")
public record ConfluenceProperties(
        String baseUrl,
        String accessToken
) {
}
