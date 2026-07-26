package com.orange.ai.reviewer_agent.infrastructure.sonarqube;

import com.orange.ai.reviewer_agent.domain.jira.IssuesSummary;
import com.orange.ai.reviewer_agent.domain.jira.QualityGateStatus;
import com.orange.ai.reviewer_agent.domain.jira.SonarQubeService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

/**
 * Infrastructure implementation of SonarQube API client
 */
@Component
public class SonarQubeClient implements SonarQubeService {

    private final RestClient restClient;

    public SonarQubeClient(SonarQubeProperties properties) {
        String auth = Base64.getEncoder().encodeToString((properties.token() + ":").getBytes());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + auth)
                .build();
    }

    @Override
    public QualityGateStatus getQualityGateStatus(String projectKey) {
        var response = restClient.get()
                .uri("/api/qualitygates/project_status?projectKey={projectKey}", projectKey)
                .retrieve()
                .body(QualityGateResponse.class);

        if (response == null || response.projectStatus() == null) {
            return new QualityGateStatus("NONE", List.of());
        }

        List<QualityGateStatus.Condition> conditions = response.projectStatus().conditions().stream()
                .map(c -> new QualityGateStatus.Condition(
                        c.metricKey(),
                        c.comparator(),
                        c.errorThreshold(),
                        c.actualValue(),
                        c.status()
                ))
                .toList();

        return new QualityGateStatus(response.projectStatus().status(), conditions);
    }

    @Override
    public IssuesSummary getIssuesForMergeRequest(String projectKey, String branchName) {
        var response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/issues/search")
                        .queryParam("componentKeys", projectKey)
                        .queryParam("branch", branchName)
                        .queryParam("resolved", "false")
                        .queryParam("ps", "100") // page size
                        .build())
                .retrieve()
                .body(IssuesResponse.class);

        if (response == null || response.issues() == null) {
            return new IssuesSummary(0, List.of());
        }

        List<IssuesSummary.Issue> issues = response.issues().stream()
                .map(i -> new IssuesSummary.Issue(
                        i.key(),
                        i.severity(),
                        i.type(),
                        i.component(),
                        i.line(),
                        i.message(),
                        i.rule()
                ))
                .toList();

        return new IssuesSummary(response.total(), issues);
    }

    // DTOs for SonarQube API responses
    record QualityGateResponse(ProjectStatus projectStatus) {
        record ProjectStatus(
                String status,
                List<ConditionDto> conditions
        ) {}

        record ConditionDto(
                String status,
                String metricKey,
                String comparator,
                String errorThreshold,
                String actualValue
        ) {}
    }

    record IssuesResponse(
            int total,
            List<IssueDto> issues
    ) {
        record IssueDto(
                String key,
                String rule,
                String severity,
                String component,
                Integer line,
                String message,
                String type
        ) {}
    }
}

