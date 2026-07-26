package com.agile.team.application.agent;

import com.agile.team.domain.artifact.Implementation;
import com.agile.team.domain.artifact.QaVerdict;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.infrastructure.adapter.ai.PromptTemplates;
import com.agile.team.support.ScriptedCodeExecutor;
import com.agile.team.support.ScriptedLlmGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QaAgentTest {

    private ScriptedLlmGateway llm;
    private ScriptedCodeExecutor executor;
    private QaAgent agent;

    private static final SpecDraft SPEC = new SpecDraft(
            "Add retry to the SFTP poller",
            "Retry transient failures.",
            List.of("Given a timeout, when polling, then it retries",
                    "Given retries are exhausted, then an alert is raised"));

    private static final Implementation IMPL = new Implementation(
            "feature/SCA-1", null, List.of("SftpPoller.java"), true, true,
            "Added retry loop", "Tests run: 4");

    @BeforeEach
    void setUp() {
        llm = new ScriptedLlmGateway();
        executor = new ScriptedCodeExecutor();
        agent = new QaAgent(llm, executor, new PromptTemplates(), "mvn,-q,test", 60);
    }

    @Test
    void shouldPassWhenTheSuiteIsGreenAndEveryCriterionIsVerified() {
        executor.succeedsWith("Tests run: 4, Failures: 0");
        llm.respondWith(assessment(
                verified("Given a timeout, when polling, then it retries", "shouldRetryOnTimeout"),
                verified("Given retries are exhausted, then an alert is raised", "shouldAlertOnExhaustion")));

        AgentOutcome<QaVerdict> outcome = agent.run(request());

        assertTrue(outcome.artifact().passed());
        assertTrue(outcome.artifact().unverified().isEmpty());
    }

    @Test
    void shouldFailWhenTheSuiteIsGreenButACriterionIsUnverified() {
        // The behaviour that matters most in this agent. A green suite that does not
        // exercise a criterion is not evidence for that criterion, and letting it pass
        // is exactly what this stage exists to prevent.
        executor.succeedsWith("Tests run: 4, Failures: 0");
        llm.respondWith(assessment(
                verified("Given a timeout, when polling, then it retries", "shouldRetryOnTimeout"),
                unverified("Given retries are exhausted, then an alert is raised",
                        "no test covers the alert path")));

        AgentOutcome<QaVerdict> outcome = agent.run(request());

        assertFalse(outcome.artifact().passed(), "an unverified criterion must fail the stage");
        assertEquals(1, outcome.artifact().unverified().size());
        assertTrue(outcome.notes().stream()
                .anyMatch(n -> n.contains("green but the verdict is FAILED")));
    }

    @Test
    void shouldFailWhenTheSuiteIsRedEvenIfEveryCriterionLooksVerified() {
        executor.failsWith(1, "Tests run: 4, Failures: 1");
        llm.respondWith(assessment(
                verified("Given a timeout, when polling, then it retries", "shouldRetryOnTimeout"),
                verified("Given retries are exhausted, then an alert is raised", "shouldAlert")));

        assertFalse(agent.run(request()).artifact().passed());
    }

    @Test
    void shouldFailWhenNoCriteriaWereAssessedAtAll() {
        // An empty assessment is not a pass. It is a QA agent that did nothing.
        executor.succeedsWith("Tests run: 0");
        llm.respondWith(new QaAgent.QaAssessment(List.of(), List.of(), "", "nothing to check"));

        assertFalse(agent.run(request()).artifact().passed());
    }

    @Test
    void shouldDeriveTheVerdictFromTheRunnerNotFromTheModel() {
        // QaAssessment deliberately has no `passed` field, so the model has no way to
        // assert an outcome. This pins that design.
        assertFalse(java.util.Arrays.stream(QaAgent.QaAssessment.class.getRecordComponents())
                .anyMatch(c -> c.getName().equals("passed")));
    }

    @Test
    void shouldPutTheTestOutputAndCriteriaInThePrompt() {
        executor.succeedsWith("Tests run: 4, Failures: 0 -- shouldRetryOnTimeout PASSED");
        llm.respondWith(assessment(
                verified("Given a timeout, when polling, then it retries", "shouldRetryOnTimeout"),
                verified("Given retries are exhausted, then an alert is raised", "shouldAlert")));

        agent.run(request());

        String prompt = llm.lastRequest().userPrompt();
        assertTrue(prompt.contains("shouldRetryOnTimeout PASSED"));
        assertTrue(prompt.contains("Given a timeout, when polling, then it retries"));
        assertTrue(prompt.contains("Exit code: 0"));
    }

    @Test
    void shouldRecordTheTestExitCodeInItsNotes() {
        executor.failsWith(2, "boom");
        llm.respondWith(assessment(
                unverified("Given a timeout, when polling, then it retries", "suite failed")));

        AgentOutcome<QaVerdict> outcome = agent.run(request());

        assertTrue(outcome.notes().stream().anyMatch(n -> n.contains("exited 2")));
    }

    @Test
    void shouldReportFailingTestNames() {
        executor.failsWith(1, "failures");
        llm.respondWith(new QaAgent.QaAssessment(
                List.of(unverified("Given a timeout, when polling, then it retries", "it failed")),
                List.of("shouldRetryOnTimeout"), "coverage dropped", "one test failed"));

        QaVerdict verdict = agent.run(request()).artifact();

        assertEquals(List.of("shouldRetryOnTimeout"), verdict.failingTests());
        assertEquals("coverage dropped", verdict.coverageNote());
    }

    @Test
    void shouldRejectACriterionResultWithNoCriterion() {
        assertThrows(com.agile.team.domain.artifact.ArtifactValidationException.class,
                () -> new QaVerdict.CriterionResult("  ", true, "evidence"));
    }

    private QaAgent.QaRequest request() {
        return new QaAgent.QaRequest("wave-1", "ws-1", SPEC, IMPL);
    }

    private QaAgent.QaAssessment assessment(QaVerdict.CriterionResult... results) {
        return new QaAgent.QaAssessment(List.of(results), List.of(), "", "assessed");
    }

    private QaVerdict.CriterionResult verified(String criterion, String test) {
        return new QaVerdict.CriterionResult(criterion, true, "verified by " + test);
    }

    private QaVerdict.CriterionResult unverified(String criterion, String why) {
        return new QaVerdict.CriterionResult(criterion, false, why);
    }
}
