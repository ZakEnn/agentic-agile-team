package com.agile.team.eval;

import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.support.ScriptedLlmGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the eval machinery itself — that the rubric catches the failure modes it
 * claims to. A scoring harness nobody has tested is a harness that will quietly
 * pass everything.
 */
class SpecEvalHarnessTest {

    @Test
    void shouldLoadTheCaseSet() {
        List<SpecEvalCase> cases = SpecEvalHarness.loadCases();
        assertFalse(cases.isEmpty());
        assertTrue(cases.stream().anyMatch(c -> c.name().equals("sftp-retry-grounded")));
    }

    @Test
    void shouldPassAGroundedSpec() {
        SpecEvalCase evalCase = caseNamed("sftp-retry-grounded");
        SpecDraft good = new SpecDraft(
                "Retry transient SFTP failures",
                "The poller should retry with exponential backoff and alert after the final attempt.",
                List.of("Given a socket timeout, when polling, then it retries with backoff",
                        "Given the final retry fails, then an alert must be raised"),
                List.of("Changing the polling schedule"));

        SpecEvalScore score = new SpecEvalScorer().score(evalCase, good);

        assertTrue(score.passed(), score.describe());
        assertEquals(2, score.criteria());
        assertEquals(2, score.testable());
    }

    @Test
    void shouldFailASpecWithTooFewCriteria() {
        SpecEvalScore score = new SpecEvalScorer().score(
                caseNamed("sftp-retry-grounded"),
                new SpecDraft("Retry", "Retry with backoff",
                        List.of("Given a timeout, then it retries with backoff")));

        assertFalse(score.passed());
        assertTrue(score.failures().stream().anyMatch(f -> f.contains("at least 2")));
    }

    @Test
    void shouldFailASpecThatIgnoresTheSuppliedDocumentation() {
        // The agent was handed documentation about retries and backoff. A spec that
        // mentions neither did not read its context.
        SpecEvalScore score = new SpecEvalScorer().score(
                caseNamed("sftp-retry-grounded"),
                new SpecDraft("Improve the poller", "Make it better somehow",
                        List.of("Given a failure, then it should be handled",
                                "Given success, then it must log")));

        assertFalse(score.passed());
        assertTrue(score.failures().stream().anyMatch(f -> f.contains("does not mention 'retry'")));
    }

    @Test
    void shouldFailASpecThatFabricatesTechnology() {
        // The hallucination check: nothing in the intent or the docs mentions Kafka.
        SpecEvalScore score = new SpecEvalScorer().score(
                caseNamed("sftp-retry-grounded"),
                new SpecDraft("Retry via Kafka",
                        "Publish failures to Kafka and retry with backoff.",
                        List.of("Given a timeout, when polling, then it retries with backoff",
                                "Given a retry exhausts, then it must publish to Kafka")));

        assertFalse(score.passed());
        assertTrue(score.failures().stream().anyMatch(f -> f.contains("kafka")));
    }

    @Test
    void shouldWarnButNotFailWhenNonGoalsAreMissing() {
        SpecEvalScore score = new SpecEvalScorer().score(
                caseNamed("no-documentation-available"),
                new SpecDraft("Health endpoint", "Report downstream connectivity",
                        List.of("Given a downstream outage, when called, then it returns DOWN")));

        assertTrue(score.passed());
        assertTrue(score.warnings().stream().anyMatch(w -> w.contains("no non-goals")));
    }

    @Test
    void shouldWarnAboutCriteriaWithNoPassFailCondition() {
        SpecEvalScore score = new SpecEvalScorer().score(
                caseNamed("no-documentation-available"),
                new SpecDraft("Health endpoint", "Report downstream connectivity",
                        List.of("The endpoint is nice and fast"), List.of("Auth")));

        assertTrue(score.warnings().stream()
                .anyMatch(w -> w.contains("no obvious pass/fail condition")));
    }

    @Test
    void shouldRunTheAgentEndToEndThroughTheHarness() {
        // Full path: harness feeds documentation to the agent via the stub port, the
        // scripted gateway returns a draft, the rubric grades it.
        SpecEvalCase evalCase = caseNamed("sftp-retry-grounded");
        ScriptedLlmGateway gateway = new ScriptedLlmGateway().respondWith(new SpecDraft(
                "Retry transient SFTP failures",
                "Retry with exponential backoff after a socket timeout.",
                List.of("Given a socket timeout, when polling, then it retries with backoff",
                        "Given retries are exhausted, then an alert must be raised"),
                List.of("Polling schedule changes")));

        SpecEvalScore score = new SpecEvalHarness(gateway).run(evalCase);

        assertTrue(score.passed(), score.describe());
        assertTrue(gateway.lastRequest().userPrompt().contains("exponential backoff"),
                "harness must actually feed the case documentation to the agent");
    }

    private SpecEvalCase caseNamed(String name) {
        return SpecEvalHarness.loadCases().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No eval case named " + name));
    }
}
