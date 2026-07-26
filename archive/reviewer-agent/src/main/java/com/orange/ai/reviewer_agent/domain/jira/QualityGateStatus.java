package com.orange.ai.reviewer_agent.domain.jira;

import java.util.List;

/**
 * Represents the Quality Gate status from SonarQube
 */
public record QualityGateStatus(
        String status,
        List<Condition> conditions
) {
    public record Condition(
            String metric,
            String operator,
            String value,
            String actualValue,
            String status
    ) {}
}