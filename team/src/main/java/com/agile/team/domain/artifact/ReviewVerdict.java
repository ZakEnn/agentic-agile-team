package com.agile.team.domain.artifact;

import com.agile.team.domain.review.ApprovalStatus;
import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.Finding;
import com.agile.team.domain.review.ReviewDisposition;
import com.agile.team.domain.review.Severity;

import java.util.List;

/**
 * The Reviewer agent's output artifact.
 * <p>
 * The model supplies {@code summary} and {@code findings}. It does <strong>not</strong>
 * supply the disposition — {@link #disposition()} is computed from the findings using
 * the governance taxonomy. That inversion is the whole point: a model that says
 * "approved" while listing a CRITICAL finding cannot approve anything, and a model
 * whose prose happens to contain the word "approved" cannot either.
 *
 * @param summary                 overview of the change
 * @param findings                classified findings; may be empty
 * @param qualityScore            the real SonarQube result, or {@code unknown()} — never fabricated
 * @param governanceSkillVersions which governance skills produced this judgment,
 *                                recorded so a decision can be reproduced later
 */
public record ReviewVerdict(
        String summary,
        List<Finding> findings,
        CodeQualityScore qualityScore,
        List<String> governanceSkillVersions
) {

    public ReviewVerdict {
        if (summary == null || summary.isBlank()) {
            throw new ArtifactValidationException("ReviewVerdict.summary must not be blank");
        }
        findings = findings == null ? List.of() : List.copyOf(findings);
        // A missing quality score fails closed rather than defaulting to a pass.
        if (qualityScore == null) {
            qualityScore = CodeQualityScore.unknown();
        }
        governanceSkillVersions = governanceSkillVersions == null
                ? List.of() : List.copyOf(governanceSkillVersions);
    }

    /** Convenience for model binding, which supplies only summary and findings. */
    public ReviewVerdict(String summary, List<Finding> findings) {
        this(summary, findings, CodeQualityScore.unknown(), List.of());
    }

    public ReviewVerdict withQualityScore(CodeQualityScore score) {
        return new ReviewVerdict(summary, findings, score, governanceSkillVersions);
    }

    public ReviewVerdict withGovernanceSkills(List<String> skills) {
        return new ReviewVerdict(summary, findings, qualityScore, skills);
    }

    /**
     * Computed, never asserted by the model: approved exactly when no finding blocks.
     */
    public ApprovalStatus approvalStatus() {
        return findings.stream().anyMatch(Finding::blocksApproval)
                ? ApprovalStatus.CHANGES_REQUESTED
                : ApprovalStatus.APPROVED;
    }

    public ReviewDisposition disposition() {
        return new ReviewDisposition(approvalStatus(), buildComment(), java.time.Instant.now());
    }

    public List<Finding> blockingFindings() {
        return findings.stream().filter(Finding::blocksApproval).toList();
    }

    public long countBySeverity(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    private String buildComment() {
        List<Finding> blocking = blockingFindings();
        if (blocking.isEmpty()) {
            return summary + " (no blocking findings)";
        }
        return summary + " (" + blocking.size() + " blocking finding(s): "
                + blocking.stream()
                        .map(f -> f.severity() + " in " + f.filePath())
                        .distinct()
                        .reduce((a, b) -> a + ", " + b).orElse("")
                + ")";
    }
}
