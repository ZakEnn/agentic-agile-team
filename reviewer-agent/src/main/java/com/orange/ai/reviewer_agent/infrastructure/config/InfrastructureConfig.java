package com.orange.ai.reviewer_agent.infrastructure.config;

import com.orange.ai.reviewer_agent.infrastructure.gitlab.GitLabProperties;
import com.orange.ai.reviewer_agent.infrastructure.jira.JiraProperties;
import com.orange.ai.reviewer_agent.infrastructure.sonarqube.SonarQubeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        GitLabProperties.class,
        JiraProperties.class,
        SonarQubeProperties.class
})
public class InfrastructureConfig {
}
