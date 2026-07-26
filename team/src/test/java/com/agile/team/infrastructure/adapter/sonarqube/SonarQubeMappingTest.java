package com.agile.team.infrastructure.adapter.sonarqube;

import com.agile.team.domain.review.CodeQualityScore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How SonarQube's gate result becomes a {@link CodeQualityScore}.
 * <p>
 * The pass/fail decision comes from SonarQube's own {@code status} field. The
 * numeric score is derived separately, purely for trend reporting. The ported
 * original did the reverse — it scraped "the first 0-100 number it finds" out of a
 * response and inferred pass/fail from a substring match.
 */
class SonarQubeMappingTest {

    @Test
    void shouldMapOkToPassing() {
        assertTrue(SonarQubeRestClient.toScore(status("OK", "OK", "OK")).passed());
    }

    @Test
    void shouldMapErrorToFailing() {
        CodeQualityScore score = SonarQubeRestClient.toScore(status("ERROR", "OK", "ERROR"));
        assertFalse(score.passed());
        assertFalse(score.isUnknown());
    }

    @Test
    void shouldMapWarnToFailingRatherThanPassing() {
        assertFalse(SonarQubeRestClient.toScore(status("WARN", "OK")).passed());
    }

    @Test
    void shouldTreatNoneAsUnknownNotAsPassing() {
        // "NONE" means no analysis exists for the project. Reporting that as a pass
        // is exactly the failure this system was built to remove.
        CodeQualityScore score = SonarQubeRestClient.toScore(status("NONE"));
        assertTrue(score.isUnknown());
        assertFalse(score.passed());
    }

    @Test
    void shouldTreatAnUnrecognisedStatusAsUnknown() {
        assertTrue(SonarQubeRestClient.toScore(status("SOMETHING_NEW")).isUnknown());
    }

    @Test
    void shouldDeriveScoreFromProportionOfConditionsMet() {
        CodeQualityScore score = SonarQubeRestClient.toScore(
                status("ERROR", "OK", "OK", "ERROR", "ERROR"));
        assertEquals(50.0, score.score(), 0.01);
    }

    @Test
    void shouldNotDivideByZeroWhenThereAreNoConditions() {
        CodeQualityScore score = SonarQubeRestClient.toScore(status("OK"));
        assertEquals(100.0, score.score(), 0.01);
        assertTrue(score.passed());
    }

    @Test
    void shouldReportUnknownWhenNotConfigured() {
        // An unconfigured client must not silently look healthy.
        SonarQubeRestClient client = new SonarQubeRestClient(
                new SonarQubeRestClient.SonarQubeProperties("https://sonarqube.invalid", ""));

        CodeQualityScore score = client.getQualityGateStatus("any").orElseThrow();

        assertTrue(score.isUnknown());
        assertFalse(score.passed());
    }

    private SonarQubeRestClient.ProjectStatus status(String overall, String... conditions) {
        List<SonarQubeRestClient.Condition> list = Arrays.stream(conditions)
                .map(s -> new SonarQubeRestClient.Condition(s, "coverage", "LT", "80", "85"))
                .toList();
        return new SonarQubeRestClient.ProjectStatus(overall, list);
    }
}
