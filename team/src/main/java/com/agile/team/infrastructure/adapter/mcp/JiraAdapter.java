package com.agile.team.infrastructure.adapter.mcp;

import com.agile.team.domain.port.JiraPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JiraAdapter implements JiraPort {

    private final ChatClient chatClient;

    public JiraAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String createIssue(String projectKey, String summary, String description, String issueType) {
        String response = chatClient.prompt()
                .user(String.format("Create a Jira issue in project '%s' with summary '%s', description '%s', type '%s'",
                        projectKey, summary, description, issueType))
                .call()
                .content();
        return response != null ? response : "";
    }

    @Override
    public void updateIssueStatus(String issueKey, String status) {
        chatClient.prompt()
                .user(String.format("Update Jira issue '%s' status to '%s'", issueKey, status))
                .call()
                .content();
    }

    @Override
    public Optional<String> getIssueStatus(String issueKey) {
        String response = chatClient.prompt()
                .user("Get the current status of Jira issue: " + issueKey)
                .call()
                .content();
        return Optional.ofNullable(response);
    }

    @Override
    public List<String> getSprintIssues(String sprintId) {
        String response = chatClient.prompt()
                .user("List all issues in Jira sprint: " + sprintId)
                .call()
                .content();
        return response != null ? List.of(response.split("\n")) : List.of();
    }

    @Override
    public void addComment(String issueKey, String comment) {
        chatClient.prompt()
                .user(String.format("Add comment to Jira issue '%s': %s", issueKey, comment))
                .call()
                .content();
    }
}
