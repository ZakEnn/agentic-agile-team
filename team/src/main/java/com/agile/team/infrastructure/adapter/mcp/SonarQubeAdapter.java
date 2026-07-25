package com.agile.team.infrastructure.adapter.mcp;

import com.agile.team.domain.port.SonarQubePort;
import com.agile.team.domain.review.CodeQualityScore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SonarQubeAdapter implements SonarQubePort {

    private final ChatClient chatClient;

    public SonarQubeAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Optional<CodeQualityScore> getQualityGateStatus(String projectKey) {
        String response = chatClient.prompt()
                .user("Get the SonarQube quality gate status for project: " + projectKey +
                      ". Return the quality score as a number and whether it passed (true/false).")
                .call()
                .content();

        if (response == null) {
            return Optional.empty();
        }

        // Parse AI response to extract score and pass/fail
        boolean passed = response.toLowerCase().contains("passed") || response.toLowerCase().contains("true");
        double score = extractScore(response);

        return Optional.of(new CodeQualityScore(score, passed));
    }

    @Override
    public Optional<CodeQualityScore> analyzeProject(String projectKey) {
        String response = chatClient.prompt()
                .user("Trigger a SonarQube analysis for project: " + projectKey +
                      " and return the quality score and gate status.")
                .call()
                .content();

        if (response == null) {
            return Optional.empty();
        }

        boolean passed = response.toLowerCase().contains("passed") || response.toLowerCase().contains("true");
        double score = extractScore(response);

        return Optional.of(new CodeQualityScore(score, passed));
    }

    private double extractScore(String response) {
        try {
            // Try to extract a numeric score from the response
            String[] parts = response.split("[^0-9.]");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    double val = Double.parseDouble(part);
                    if (val >= 0 && val <= 100) {
                        return val;
                    }
                }
            }
        } catch (NumberFormatException e) {
            // Default to 0 if parsing fails
        }
        return 0.0;
    }
}
