package com.agile.team.application.review;

import com.agile.team.domain.artifact.ReviewVerdict;
import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.Finding;
import com.agile.team.domain.review.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The comment a human actually reads. Ordering is the point: a reviewer's output is
 * read in a hurry, and burying the thing that blocks the merge under a list of
 * suggestions is how useful findings get ignored.
 */
class ReviewCommentFormatterTest {

    private final ReviewCommentFormatter formatter = new ReviewCommentFormatter();

    @Test
    void shouldLeadWithTheComputedDecision() {
        String md = formatter.format(new ReviewVerdict(
                "Has a problem.",
                List.of(new Finding(Severity.CRITICAL, "A.java", 5, "Hardcoded secret", "Security")),
                CodeQualityScore.passing(90), List.of("review-criteria")));

        assertTrue(md.indexOf("CHANGES REQUESTED") < md.indexOf("Has a problem."),
                "the decision must appear before the prose");
        assertTrue(md.contains("### Blocking findings"));
    }

    @Test
    void shouldSeparateBlockingFromNonBlockingFindings() {
        String md = formatter.format(new ReviewVerdict(
                "Mixed.",
                List.of(new Finding(Severity.MINOR, "B.java", 2, "Naming", "CodeSmell"),
                        new Finding(Severity.MAJOR, "A.java", 1, "Logic error", "Bug")),
                CodeQualityScore.passing(90), List.of()));

        assertTrue(md.indexOf("### Blocking findings") < md.indexOf("### Non-blocking"));
        // Blocking content is promoted above non-blocking regardless of input order.
        assertTrue(md.indexOf("Logic error") < md.indexOf("Naming"));
    }

    @Test
    void shouldStateExplicitlyThatAnUnknownGateBlocks() {
        String md = formatter.format(new ReviewVerdict("Clean.", List.of()));

        assertTrue(md.contains("UNKNOWN"));
        assertTrue(md.contains("blocks completion"));
        assertTrue(md.contains("missing evidence is not treated as a pass"));
    }

    @Test
    void shouldSayWhenNoDefectsWereFound() {
        String md = formatter.format(new ReviewVerdict(
                "Clean change.", List.of(), CodeQualityScore.passing(100), List.of()));

        assertTrue(md.contains("APPROVED"));
        assertTrue(md.contains("No defects found"));
    }

    @Test
    void shouldRecordGovernanceProvenanceInTheComment() {
        // So a reader can tell which version of the criteria produced this verdict.
        String md = formatter.format(new ReviewVerdict(
                "Clean.", List.of(), CodeQualityScore.passing(100), List.of("review-criteria")));

        assertTrue(md.contains("review-criteria"));
        assertTrue(md.contains("computed from finding severities"));
    }

    @Test
    void shouldRenderAFindingWithNoLineNumber() {
        String md = formatter.format(new ReviewVerdict(
                "File-level issue.",
                List.of(new Finding(Severity.MAJOR, "A.java", null, "Missing tests", "Test")),
                CodeQualityScore.passing(90), List.of()));

        assertTrue(md.contains("`A.java`"));
        assertFalse(md.contains("line null"));
    }
}
