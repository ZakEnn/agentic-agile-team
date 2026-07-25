package com.agile.team.interfaces.rest.dto;

import com.agile.team.domain.specification.Specification;

import java.time.Instant;
import java.util.List;

/**
 * A specification as presented to a human at the approval gate.
 * <p>
 * Exposing the acceptance criteria as a list — rather than as a blob of model
 * output — is the point: this is what the reviewer is actually approving, and what
 * the QA agent will later verify.
 */
public record SpecificationResponse(
        String specificationId,
        String waveId,
        String title,
        String content,
        List<String> acceptanceCriteria,
        List<String> outOfScope,
        String sourcePageId,
        String status,
        String decidedBy,
        String decisionReason,
        Instant decidedAt,
        Instant createdAt
) {
    public static SpecificationResponse from(String waveId, Specification spec) {
        return new SpecificationResponse(
                spec.getId().value().toString(),
                waveId,
                spec.getTitle(),
                spec.getContent(),
                spec.getAcceptanceCriteria(),
                spec.getOutOfScope(),
                spec.getSourcePageId(),
                spec.getStatus().name(),
                spec.getDecidedBy(),
                spec.getDecisionReason(),
                spec.getDecidedAt(),
                spec.getCreatedAt()
        );
    }
}
