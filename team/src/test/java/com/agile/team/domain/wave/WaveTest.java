package com.agile.team.domain.wave;

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
        Specification spec = new Specification(
                SpecificationId.generate(), "Feature A", "Content", "page-1", List.of()
        );
        wave.addSpecification(spec);
        wave.startExecution("HUMAN");

        assertEquals(WaveStatus.IN_PROGRESS, wave.getStatus());
        assertEquals(1, wave.getTransitions().size());
        assertEquals("HUMAN", wave.getTransitions().get(0).authorizedBy());
    }

    @Test
    void shouldNotStartWithoutSpecifications() {
        assertThrows(IllegalStateException.class, () -> wave.startExecution("HUMAN"));
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
        Specification spec = new Specification(
                SpecificationId.generate(), "Feature A", "Content", "page-1", List.of()
        );
        wave.addSpecification(spec);
        wave.startExecution("HUMAN");
    }
}
