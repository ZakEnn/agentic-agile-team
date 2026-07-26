package com.agile.team.application.review;

import com.agile.team.domain.artifact.ReviewVerdict;
import com.agile.team.domain.review.Finding;
import com.agile.team.domain.review.Severity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Renders a {@link ReviewVerdict} as the markdown comment posted to the merge request.
 * <p>
 * Ported from {@code reviewer-agent}, with the ordering changed: blocking findings
 * come first and the computed decision is stated at the top. A reviewer's output is
 * read by a person in a hurry, and burying the one thing that stops the merge under
 * a list of suggestions is how useful findings get missed.
 */
@Component
public class ReviewCommentFormatter {

    private static final Map<Severity, String> ICONS = Map.of(
            Severity.CRITICAL, "🔴",
            Severity.MAJOR, "🟠",
            Severity.MINOR, "🟡",
            Severity.SUGGESTION, "💡");

    public String format(ReviewVerdict verdict) {
        StringBuilder md = new StringBuilder();

        md.append("## AI Code Review\n\n");
        md.append(decisionBanner(verdict)).append("\n\n");
        md.append(verdict.summary()).append("\n\n");

        List<Finding> blocking = verdict.blockingFindings();
        if (!blocking.isEmpty()) {
            md.append("### Blocking findings\n\n");
            blocking.forEach(f -> appendFinding(md, f));
        }

        List<Finding> nonBlocking = verdict.findings().stream()
                .filter(f -> !f.blocksApproval())
                .toList();
        if (!nonBlocking.isEmpty()) {
            md.append("### Non-blocking\n\n");
            nonBlocking.forEach(f -> appendFinding(md, f));
        }

        if (verdict.findings().isEmpty()) {
            md.append("No defects found in this diff.\n\n");
        }

        md.append("### Automated quality gate\n\n");
        md.append(qualityLine(verdict)).append("\n\n");

        md.append("---\n");
        if (!verdict.governanceSkillVersions().isEmpty()) {
            md.append("Severities classified using: ")
              .append(String.join(", ", verdict.governanceSkillVersions()))
              .append(". ");
        }
        md.append("Approval is computed from finding severities, not asserted by the model.\n");

        return md.toString();
    }

    private String decisionBanner(ReviewVerdict verdict) {
        return switch (verdict.approvalStatus()) {
            case APPROVED -> "**APPROVED** — no blocking findings.";
            case CHANGES_REQUESTED -> "**CHANGES REQUESTED** — "
                    + verdict.blockingFindings().size() + " blocking finding(s).";
            default -> "**" + verdict.approvalStatus() + "**";
        };
    }

    private String qualityLine(ReviewVerdict verdict) {
        return switch (verdict.qualityScore().status()) {
            case PASSED -> "SonarQube: PASSED (%.0f%% of conditions met)."
                    .formatted(verdict.qualityScore().score());
            case FAILED -> "SonarQube: FAILED (%.0f%% of conditions met). This blocks completion."
                    .formatted(verdict.qualityScore().score());
            case UNKNOWN -> "SonarQube: **UNKNOWN** — the gate could not be evaluated. "
                    + "This blocks completion: missing evidence is not treated as a pass.";
        };
    }

    private void appendFinding(StringBuilder md, Finding finding) {
        md.append(ICONS.getOrDefault(finding.severity(), "•"))
          .append(" **").append(finding.severity()).append("** · `")
          .append(finding.category()).append("`\n");
        md.append("- `").append(finding.filePath()).append('`');
        if (finding.line() != null) {
            md.append(" line ").append(finding.line());
        }
        md.append('\n');
        md.append("- ").append(finding.description()).append("\n\n");
    }
}
