package com.agile.team.domain.review;

import java.time.Instant;

public record ReviewDisposition(
        ApprovalStatus status,
        String reviewerComment,
        Instant reviewedAt
) {
    public ReviewDisposition {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (reviewedAt == null) reviewedAt = Instant.now();
    }

    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }

    public static ReviewDisposition approved(String comment) {
        return new ReviewDisposition(ApprovalStatus.APPROVED, comment, Instant.now());
    }

    public static ReviewDisposition rejected(String comment) {
        return new ReviewDisposition(ApprovalStatus.REJECTED, comment, Instant.now());
    }
}
