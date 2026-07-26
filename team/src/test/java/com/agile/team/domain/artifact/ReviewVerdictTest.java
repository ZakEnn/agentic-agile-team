package com.agile.team.domain.artifact;

import com.agile.team.domain.review.ApprovalStatus;
import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.Finding;
import com.agile.team.domain.review.ReviewGate;
import com.agile.team.domain.review.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The structural replacement for {@code aiResponse.contains("approved")}.
 * <p>
 * Approval is computed from finding severities. The model classifies; the system
 * decides. These tests pin that inversion.
 */
class ReviewVerdictTest {

    @Test
    void shouldApproveWhenThereAreNoFindings() {
        ReviewVerdict verdict = new ReviewVerdict("Clean change.", List.of());
        assertEquals(ApprovalStatus.APPROVED, verdict.approvalStatus());
        assertTrue(verdict.disposition().isApproved());
    }

    @Test
    void shouldApproveWhenOnlyNonBlockingFindingsExist() {
        ReviewVerdict verdict = new ReviewVerdict("Mostly fine.", List.of(
                finding(Severity.MINOR, "Naming could be clearer"),
                finding(Severity.SUGGESTION, "Consider extracting a method")));

        assertEquals(ApprovalStatus.APPROVED, verdict.approvalStatus());
        assertEquals(0, verdict.blockingFindings().size());
    }

    @Test
    void shouldRequestChangesOnACriticalFinding() {
        ReviewVerdict verdict = new ReviewVerdict("Has a security hole.", List.of(
                finding(Severity.CRITICAL, "SQL built by string concatenation")));

        assertEquals(ApprovalStatus.CHANGES_REQUESTED, verdict.approvalStatus());
        assertEquals(1, verdict.blockingFindings().size());
    }

    @Test
    void shouldRequestChangesOnAMajorFinding() {
        ReviewVerdict verdict = new ReviewVerdict("Logic error.", List.of(
                finding(Severity.MAJOR, "Retry loop never terminates on permanent failure")));

        assertEquals(ApprovalStatus.CHANGES_REQUESTED, verdict.approvalStatus());
    }

    @Test
    void shouldIgnoreProseThatSaysApprovedWhenAFindingBlocks() {
        // The exact bug this design removes: the old code matched on the word
        // "approved" appearing anywhere in the model's prose.
        ReviewVerdict verdict = new ReviewVerdict(
                "This is approved and looks great, ready to merge.",
                List.of(finding(Severity.CRITICAL, "Hardcoded credential")));

        assertEquals(ApprovalStatus.CHANGES_REQUESTED, verdict.approvalStatus(),
                "prose must not be able to override a blocking finding");
    }

    @Test
    void shouldDefaultQualityScoreToUnknownRatherThanPassing() {
        ReviewVerdict verdict = new ReviewVerdict("Summary", List.of());
        assertTrue(verdict.qualityScore().isUnknown());
        assertFalse(verdict.qualityScore().passed());
    }

    @Test
    void shouldNotPassTheReviewGateWithAnUnknownQualityScore() {
        // Even a spotless review cannot complete a wave when the automated gate is
        // unavailable. Both signals are required; neither is assumed.
        ReviewVerdict verdict = new ReviewVerdict("No defects found.", List.of());

        assertTrue(verdict.disposition().isApproved());
        assertFalse(ReviewGate.canComplete(verdict.disposition(), verdict.qualityScore()));
    }

    @Test
    void shouldPassTheReviewGateOnlyWithBothSignals() {
        ReviewVerdict verdict = new ReviewVerdict("No defects found.", List.of())
                .withQualityScore(CodeQualityScore.passing(95));

        assertTrue(ReviewGate.canComplete(verdict.disposition(), verdict.qualityScore()));
    }

    @Test
    void shouldCarryGovernanceProvenance() {
        ReviewVerdict verdict = new ReviewVerdict("Summary", List.of())
                .withGovernanceSkills(List.of("review-criteria"));

        assertEquals(List.of("review-criteria"), verdict.governanceSkillVersions());
    }

    @Test
    void shouldCountBySeverity() {
        ReviewVerdict verdict = new ReviewVerdict("Summary", List.of(
                finding(Severity.MINOR, "a"), finding(Severity.MINOR, "b"),
                finding(Severity.CRITICAL, "c")));

        assertEquals(2, verdict.countBySeverity(Severity.MINOR));
        assertEquals(1, verdict.countBySeverity(Severity.CRITICAL));
        assertEquals(0, verdict.countBySeverity(Severity.MAJOR));
    }

    @Test
    void shouldRejectABlankSummary() {
        assertThrows(ArtifactValidationException.class, () -> new ReviewVerdict("  ", List.of()));
    }

    @Test
    void shouldRejectAFindingWithNoSeverityOrDescription() {
        assertThrows(ArtifactValidationException.class,
                () -> new Finding(null, "F.java", 1, "desc", "Bug"));
        assertThrows(ArtifactValidationException.class,
                () -> new Finding(Severity.MINOR, "F.java", 1, "  ", "Bug"));
    }

    @Test
    void shouldEncodeTheGovernanceSkillsSeverityRules() {
        // These map 1:1 to skills/review-criteria/SKILL.md. If the skill changes,
        // this test should change with it — deliberately, and visibly in review.
        assertTrue(Severity.CRITICAL.blocksApproval());
        assertTrue(Severity.MAJOR.blocksApproval());
        assertFalse(Severity.MINOR.blocksApproval());
        assertFalse(Severity.SUGGESTION.blocksApproval());
    }

    private Finding finding(Severity severity, String description) {
        return new Finding(severity, "src/main/java/Example.java", 10, description, "Bug");
    }
}
