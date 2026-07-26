package com.orange.ai.reviewer_agent.application.tools;

import com.orange.ai.reviewer_agent.domain.jira.IssuesSummary;
import com.orange.ai.reviewer_agent.domain.jira.QualityGateStatus;
import com.orange.ai.reviewer_agent.domain.jira.SonarQubeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SonarQubeTools {
    private final SonarQubeService sonarQubeService;

    public SonarQubeTools(SonarQubeService sonarQubeService) {
        this.sonarQubeService = sonarQubeService;
    }

    @Tool(description = "Get the quality gate status of a SonarQube project")
    public QualityGateStatus getQualityGateStatus(String sonarProjectKey) {
        return sonarQubeService.getQualityGateStatus(sonarProjectKey);
    }

    @Tool(description = "Get the issues summary for a specific branch of a SonarQube project")
    public IssuesSummary getIssuesForBranch(String sonarProjectKey, String s) {
        return sonarQubeService.getIssuesForMergeRequest(sonarProjectKey, s);
    }

}