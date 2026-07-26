package com.agile.team.domain.review;

import com.agile.team.domain.artifact.ArtifactValidationException;

/**
 * One classified review finding.
 * <p>
 * Structured rather than prose so the disposition can be computed from the findings
 * instead of parsed out of a paragraph, and so precision can actually be measured:
 * SDLC_AGENT_PLAN.md §2.5(c) records that review noise, not review capability, is
 * what drives teams to abandon these tools — 30–50% of high-recall findings needing
 * manual triage. You cannot tune what you cannot count.
 *
 * @param severity    classification from the governance taxonomy
 * @param filePath    file the finding applies to
 * @param line        line number, or null when the finding is file-level
 * @param description what is wrong and why it matters
 * @param category    Security / Bug / CodeSmell / Performance / Test
 */
public record Finding(
        Severity severity,
        String filePath,
        Integer line,
        String description,
        String category
) {
    public Finding {
        if (severity == null) {
            throw new ArtifactValidationException(
                    "Finding.severity must be one of CRITICAL, MAJOR, MINOR, SUGGESTION");
        }
        if (description == null || description.isBlank()) {
            throw new ArtifactValidationException("Finding.description must not be blank");
        }
        if (filePath == null || filePath.isBlank()) {
            filePath = "(unspecified)";
        }
        if (category == null || category.isBlank()) {
            category = "General";
        }
        if (line != null && line < 0) {
            line = null;
        }
    }

    public boolean blocksApproval() {
        return severity.blocksApproval();
    }
}
