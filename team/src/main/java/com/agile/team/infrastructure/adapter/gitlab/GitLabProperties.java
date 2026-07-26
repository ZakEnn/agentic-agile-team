package com.agile.team.infrastructure.adapter.gitlab;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitLab connection settings.
 * <p>
 * A plain properties record — unlike the ported original, which nested the REST
 * client as a static class <em>inside</em> the properties record. That worked via
 * component scanning but put an API client inside a configuration type and made the
 * documented file layout wrong.
 */
@ConfigurationProperties(prefix = "gitlab")
public record GitLabProperties(String baseUrl, String token) {

    public GitLabProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://gitlab.com";
        }
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }
}
