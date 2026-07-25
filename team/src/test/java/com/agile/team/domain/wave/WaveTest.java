package com.agile.team.domain.wave;

import com.agile.team.domain.gate.GateDecision;
import com.agile.team.domain.gate.GateName;
import com.agile.team.domain.review.ApprovalStatus;
import com.agile.team.domain.review.CodeQualityScore;
import com.agile.team.domain.review.ReviewDisposition;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaveTest {

    private Wave wave;

    @BeforeEach
    void setUp() {
        wave = new Wave(WaveId.generate(), "Sprint 1");
    }

    @Test
    void shouldCreateWaveInPlanningStatus() {
        assertEquals(WaveStatus.PLANNING, wave.getStatus());
        assertTrue(wave.getTransitions().isEmpty());
    }

    @Test
    void shouldAddSpecificationDuringPlanning() {
        Specification spec = new Specification(
                SpecificationId.generate(), "Feature A", "Content", "page-1", List.of("AC1")
        );
        wave.addSpecification(spec);
        assertEquals(1, wave.getSpecifications().size());
    }

    @Test
    void shouldNotAddSpecificationAfterPlanning() {
        addSpecAndStartExecution();
        Specification spec = new Specification(
                SpecificationId.generate(), "Feature B", "Content", "page-2", List.of()
        );
        assertThrows(IllegalStateException.class, () -> wave.addSpecification(spec));
    }

    @Test
    void shouldTransitionToInProgress() {
        addSpecAndStartExecution();

        assertEquals(WaveStatus.IN_PROGRESS, wave.getStatus());
        assertEquals(1, wave.getTransitions().size());
        assertEquals("HUMAN", wave.getTransitions().get(0).authorizedBy());
    }

    @Test
    void shouldNotStartWithoutSpecifications() {
        assertThrows(IllegalStateException.class, () -> wave.startExecution("HUMAN"));
    }

    @Test
    void shouldNotStartWhenSpecificationHasNotPassedTheApprovalGate() {
        // M1: an unapproved spec must not reach the Developer agent. The invariant
        // lives in the aggregate so no handler can route around it.
        wave.addSpecification(new Specification(
                SpecificationId.generate(), "Feature A", "Content", "page-1", List.of("AC1")
        ));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> wave.startExecution("HUMAN"));
        assertTrue(thrown.getMessage().contains("SPEC_APPROVAL"));
    }

    @Test
    void shouldNotApproveSpecificationWithoutAcceptanceCriteria() {
        Specification spec = new Specification(
                SpecificationId.generate(), "Feature A", "Content", "page-1", List.of()
        );

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> spec.applyDecision(GateDecision.approvedBy(GateName.SPEC_APPROVAL, "alice", "ok")));
        assertTrue(thrown.getMessage().contains("acceptance criteria"));
    }

    @Test
    void shouldRecordWhoApprovedTheSpecification() {
        Specification spec = approvedSpec("alice");
        assertTrue(spec.isApproved());
        assertEquals("alice", spec.getDecidedBy());
        assertNotNull(spec.getDecidedAt());
    }

    @Test
    void shouldDistinguishAutomatedApprovalFromHumanApproval() {
        // Auto-approvals must stay identifiable, otherwise trust metrics lie.
        GateDecision auto = GateDecision.autoApproved(GateName.SPEC_APPROVAL);
        GateDecision human = GateDecision.approvedBy(GateName.SPEC_APPROVAL, "alice", "looks good");

        assertTrue(auto.isAutomated());
        assertFalse(human.isAutomated());
    }

    @Test
    void shouldTransitionToReview() {
        addSpecAndStartExecution();
        wave.moveToReview("DEV_AGENT");

        assertEquals(WaveStatus.IN_REVIEW, wave.getStatus());
        assertEquals(2, wave.getTransitions().size());
    }

    @Test
    void shouldCompleteWhenReviewGatePasses() {
        addSpecAndStartExecution();
        wave.moveToReview("DEV_AGENT");

        ReviewDisposition approved = ReviewDisposition.approved("LGTM");
        CodeQualityScore passing = CodeQualityScore.passing(85.0);

        wave.complete("REVIEWER_AGENT", approved, passing);
        assertEquals(WaveStatus.COMPLETED, wave.getStatus());
    }

    @Test
    void shouldNotCompleteWhenReviewGateFails() {
        addSpecAndStartExecution();
        wave.moveToReview("DEV_AGENT");

        ReviewDisposition rejected = ReviewDisposition.rejected("Issues found");
        CodeQualityScore passing = CodeQualityScore.passing(85.0);

        assertThrows(IllegalStateException.class,
                () -> wave.complete("REVIEWER_AGENT", rejected, passing));
    }

    @Test
    void shouldNotCompleteWhenQualityGateFails() {
        addSpecAndStartExecution();
        wave.moveToReview("DEV_AGENT");

        ReviewDisposition approved = ReviewDisposition.approved("LGTM");
        CodeQualityScore failing = CodeQualityScore.failing(30.0);

        assertThrows(IllegalStateException.class,
                () -> wave.complete("REVIEWER_AGENT", approved, failing));
    }

    @Test
    void shouldRecordTransitionAuthorization() {
        addSpecAndStartExecution();
        StateTransition transition = wave.getTransitions().get(0);

        assertEquals(WaveStatus.PLANNING, transition.fromStatus());
        assertEquals(WaveStatus.IN_PROGRESS, transition.toStatus());
        assertEquals("HUMAN", transition.authorizedBy());
        assertNotNull(transition.timestamp());
    }

    private void addSpecAndStartExecution() {
        wave.addSpecification(approvedSpec("HUMAN"));
        wave.startExecution("HUMAN");
    }

    private static Specification approvedSpec(String approver) {
        Specification spec = new Specification(
                SpecificationId.generate(), "Feature A", "Content", "page-1", List.of("AC1")
        );
        spec.applyDecision(GateDecision.approvedBy(GateName.SPEC_APPROVAL, approver, "approved for test"));
        return spec;
    }
}
