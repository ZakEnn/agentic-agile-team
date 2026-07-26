
package com.orange.ai.reviewer_agent.domain.review;

import java.util.List;

/**
 * Represents a structured code review comment
 */
public record ReviewComment(
        String summary,
        List<Issue> issues,
        List<Suggestion> suggestions,
        String overallAssessment
) {
    public record Issue(
            String severity,
            String filePath,
            Integer lineNumber,
            String description,
            String category
    ) {}

    public record Suggestion(
            String filePath,
            Integer lineNumber,
            String description,
            String suggestedCode
    ) {}
}