package com.agile.team.application.agent;

import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.artifact.ArtifactValidationException;
import com.agile.team.domain.artifact.ChangePlan;
import com.agile.team.domain.artifact.Implementation;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.infrastructure.adapter.ai.PromptTemplates;
import com.agile.team.infrastructure.adapter.ai.SkillContextResolver;
import com.agile.team.support.ScriptedCodeExecutor;
import com.agile.team.support.ScriptedLlmGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeveloperAgentTest {

    private ScriptedLlmGateway llm;
    private ScriptedCodeExecutor executor;
    private DeveloperAgent agent;

    private static final SpecDraft SPEC = new SpecDraft(
            "Add retry to the SFTP poller",
            "Retry transient failures with exponential backoff.",
            List.of("Given a timeout, when polling, then it retries"),
            List.of("Changing the schedule"));

    private static final ChangePlan PLAN = new ChangePlan(
            "Added a bounded retry loop around session.connect().",
            List.of(new ChangePlan.FileEdit("src/main/java/SftpPoller.java", "class SftpPoller {}"),
                    new ChangePlan.FileEdit("src/test/java/SftpPollerTest.java", "class SftpPollerTest {}")));

    @BeforeEach
    void setUp() {
        llm = new ScriptedLlmGateway();
        executor = new ScriptedCodeExecutor();
        agent = new DeveloperAgent(llm, executor, new SkillContextResolver(new NoSkills()),
                new PromptTemplates(), "mvn,-q,compile", "mvn,-q,test", 60);
    }

    @Test
    void shouldProduceACompleteImplementationWhenBuildAndTestsPass() {
        llm.respondWith(PLAN);
        executor.succeedsWith("BUILD SUCCESS").succeedsWith("Tests run: 4, Failures: 0");

        AgentOutcome<Implementation> outcome = agent.run(request(null));

        Implementation implementation = outcome.artifact();
        assertTrue(implementation.isComplete());
        assertTrue(implementation.buildPassed());
        assertTrue(implementation.testsPassed());
        assertEquals(2, implementation.filesChanged().size());
    }

    @Test
    void shouldWriteTheProposedFilesIntoTheWorkspace() {
        llm.respondWith(PLAN);
        executor.succeedsWith("ok").succeedsWith("ok");

        agent.run(request(null));

        assertEquals(2, executor.appliedChanges().size());
        assertEquals("src/main/java/SftpPoller.java", executor.appliedChanges().get(0).path());
    }

    @Test
    void shouldFailTheStageWhenTheBuildFails() {
        // The central guarantee: a model cannot report that its own change compiles.
        llm.respondWith(PLAN);
        executor.failsWith(1, "SftpPoller.java:[12,5] cannot find symbol: backoff");

        DeveloperAgent.BuildFailedException thrown = assertThrows(
                DeveloperAgent.BuildFailedException.class, () -> agent.run(request(null)));

        assertTrue(thrown.getMessage().contains("Build failed"));
        assertTrue(thrown.output().contains("cannot find symbol"),
                "the compiler's own words must reach the retry");
    }

    @Test
    void shouldNotRunTestsWhenTheBuildFailed() {
        llm.respondWith(PLAN);
        executor.failsWith(1, "compilation failure");

        assertThrows(DeveloperAgent.BuildFailedException.class, () -> agent.run(request(null)));

        assertEquals(1, executor.commandsRun().size(),
                "there is nothing to test when the code does not compile");
    }

    @Test
    void shouldFailTheStageWhenTestsFail() {
        llm.respondWith(PLAN);
        executor.succeedsWith("BUILD SUCCESS")
                .failsWith(1, "Tests run: 4, Failures: 1 -- shouldRetryOnTimeout");

        DeveloperAgent.BuildFailedException thrown = assertThrows(
                DeveloperAgent.BuildFailedException.class, () -> agent.run(request(null)));

        assertTrue(thrown.getMessage().contains("Tests failed"));
        assertTrue(thrown.output().contains("shouldRetryOnTimeout"));
    }

    @Test
    void shouldTreatATimeoutAsAFailure() {
        llm.respondWith(PLAN);
        executor.timesOut();

        DeveloperAgent.BuildFailedException thrown = assertThrows(
                DeveloperAgent.BuildFailedException.class, () -> agent.run(request(null)));

        assertTrue(thrown.getMessage().contains("timed out"));
    }

    @Test
    void shouldFeedThePreviousBuildFailureBackIntoTheRetryPrompt() {
        // The only mechanism by which the agent can actually improve on a retry.
        llm.respondWith(PLAN);
        executor.succeedsWith("ok").succeedsWith("ok");

        agent.run(request("SftpPoller.java:[12,5] cannot find symbol: backoff"));

        String prompt = llm.lastRequest().userPrompt();
        assertTrue(prompt.contains("cannot find symbol: backoff"));
        assertFalse(prompt.contains("This is the first attempt"));
    }

    @Test
    void shouldSayItIsTheFirstAttemptWhenThereIsNoPriorFailure() {
        llm.respondWith(PLAN);
        executor.succeedsWith("ok").succeedsWith("ok");

        agent.run(request(null));

        assertTrue(llm.lastRequest().userPrompt().contains("This is the first attempt"));
    }

    @Test
    void shouldIncludeAcceptanceCriteriaAndNonGoalsInThePrompt() {
        llm.respondWith(PLAN);
        executor.succeedsWith("ok").succeedsWith("ok");

        agent.run(request(null));

        String prompt = llm.lastRequest().userPrompt();
        assertTrue(prompt.contains("Given a timeout, when polling, then it retries"));
        assertTrue(prompt.contains("Changing the schedule"));
    }

    @Test
    void shouldRejectAChangePlanWithNoChanges() {
        assertThrows(ArtifactValidationException.class,
                () -> new ChangePlan("I decided nothing needed changing", List.of()));
    }

    @Test
    void shouldRejectFileEditsThatEscapeTheWorkspace() {
        // Defence in depth: refused at the artifact boundary as well as in the executor,
        // so the attempt is visible in the audit trail as a validation failure.
        assertThrows(ArtifactValidationException.class,
                () -> new ChangePlan.FileEdit("../../../etc/passwd", "x"));
        assertThrows(ArtifactValidationException.class,
                () -> new ChangePlan.FileEdit("/etc/passwd", "x"));
        assertThrows(ArtifactValidationException.class,
                () -> new ChangePlan.FileEdit("C:\\Windows\\System32\\evil.dll", "x"));
    }

    @Test
    void shouldNotConsiderAnImplementationCompleteWithoutBothSignals() {
        assertFalse(new Implementation("b", null, List.of("f"), false, true, "s", "o").isComplete());
        assertFalse(new Implementation("b", null, List.of("f"), true, false, "s", "o").isComplete());
        assertFalse(new Implementation("b", null, List.of(), true, true, "s", "o").isComplete());
        assertTrue(new Implementation("b", null, List.of("f"), true, true, "s", "o").isComplete());
    }

    @Test
    void shouldRequireAWorkspace() {
        // The caller owns the workspace lifetime, because BUILD keeps it so VERIFY can
        // test the build that was actually produced. The agent will not invent one.
        assertThrows(IllegalArgumentException.class, () -> new DeveloperAgent.DeveloperRequest(
                "wave-1", SPEC, null, "feature/x", null, "  "));
    }

    @Test
    void shouldWorkInTheWorkspaceItWasGiven() {
        llm.respondWith(PLAN);
        executor.succeedsWith("ok").succeedsWith("ok");

        agent.run(new DeveloperAgent.DeveloperRequest(
                "wave-1", SPEC, null, "feature/x", null, "given-workspace"));

        // The agent must not create its own workspace.
        assertTrue(executor.discardedWorkspaces().isEmpty());
    }

    private DeveloperAgent.DeveloperRequest request(String previousFailure) {
        return new DeveloperAgent.DeveloperRequest(
                "wave-1", SPEC, "Change SftpPoller only.", "feature/SCA-1", previousFailure, "ws-1");
    }

    private static class NoSkills implements SkillPort {
        @Override public List<SkillDescriptor> resolveSkills(AgentRole role) { return List.of(); }
        @Override public String loadSkillContent(String name) { return ""; }
    }
}
