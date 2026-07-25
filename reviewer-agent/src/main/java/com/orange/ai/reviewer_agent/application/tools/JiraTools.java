package com.orange.ai.reviewer_agent.application.tools;

import com.orange.ai.reviewer_agent.domain.jira.IssueDetails;
import com.orange.ai.reviewer_agent.domain.jira.JiraService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI Tool for fetching Jira issue information
 */
@Component
public class JiraTools {

    private final JiraService jiraService;

    public JiraTools(JiraService jiraService) {
        this.jiraService = jiraService;
    }

    @Tool(description = "Fetches detailed information about a Jira issue including summary, description, " +
            "acceptance criteria, status, priority, and assignee. Use the Jira issue key (e.g., PROJ-123).")
    public IssueDetails getIssueDetails(String issueKey) {
        return jiraService.getIssueDetails(issueKey);
    }
}
