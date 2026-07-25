package com.agile.team.infrastructure.adapter.mcp;

import com.agile.team.domain.port.GitLabPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GitLabAdapter implements GitLabPort {

    private final ChatClient chatClient;

    public GitLabAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String createBranch(String projectId, String branchName, String sourceBranch) {
        String response = chatClient.prompt()
                .user(String.format("Create a GitLab branch '%s' from '%s' in project '%s'",
                        branchName, sourceBranch, projectId))
                .call()
                .content();
        return response != null ? response : branchName;
    }

    @Override
    public String createMergeRequest(String projectId, String sourceBranch, String targetBranch, String title, String description) {
        String response = chatClient.prompt()
                .user(String.format("Create a GitLab merge request in project '%s' from '%s' to '%s' with title '%s' and description: %s",
                        projectId, sourceBranch, targetBranch, title, description))
                .call()
                .content();
        return response != null ? response : "";
    }

    @Override
    public Optional<String> getMergeRequestStatus(String projectId, String mergeRequestId) {
        String response = chatClient.prompt()
                .user(String.format("Get the status of GitLab merge request '%s' in project '%s'",
                        mergeRequestId, projectId))
                .call()
                .content();
        return Optional.ofNullable(response);
    }

    @Override
    public void mergeMergeRequest(String projectId, String mergeRequestId) {
        chatClient.prompt()
                .user(String.format("Merge the GitLab merge request '%s' in project '%s'",
                        mergeRequestId, projectId))
                .call()
                .content();
    }

    @Override
    public void addMergeRequestComment(String projectId, String mergeRequestId, String comment) {
        chatClient.prompt()
                .user(String.format("Add a comment to GitLab merge request '%s' in project '%s': %s",
                        mergeRequestId, projectId, comment))
                .call()
                .content();
    }
}
