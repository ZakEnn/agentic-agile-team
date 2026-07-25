package com.agile.team.domain.port;

import com.agile.team.domain.review.CodeQualityScore;

import java.util.Optional;

public interface SonarQubePort {

    Optional<CodeQualityScore> getQualityGateStatus(String projectKey);

    Optional<CodeQualityScore> analyzeProject(String projectKey);
}
