package com.agile.team.application.agent;

import com.agile.team.application.release.DeploymentPolicy;
import com.agile.team.domain.artifact.ArtifactValidationException;
import com.agile.team.domain.artifact.DeployVerdict;
import com.agile.team.domain.artifact.DesignNote;
import com.agile.team.domain.artifact.SpecDraft;
import com.agile.team.domain.port.DeploymentPort;
import com.agile.team.infrastructure.adapter.ai.PromptTemplates;
import com.agile.team.infrastructure.config.SdlcProperties;
import com.agile.team.support.ScriptedLlmGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The two agents that close the SDLC: Architect at the front, Release at the end. */
class ArchitectAndReleaseAgentTest {

    private static final SpecDraft SPEC = new SpecDraft(
            "Add retry to the SFTP poller",
            "Retry transient failures.",
            List.of("Given a timeout, when polling, then it retries"));

    private static final List<String> REPO_PATHS = List.of(
            "src/main/java/com/orange/epe/SftpPoller.java",
            "src/main/java/com/orange/epe/config/SpringIntegrationSftpConfiguration.java",
            "src/test/java/com/orange/epe/SftpPollerTest.java",
            "pom.xml");

    // --- Architect ---

    @Test
    void shouldAcceptADesignWhoseModulesExistInTheRepository() {
        ScriptedLlmGateway llm = new ScriptedLlmGateway().respondWith(new DesignNote(
                "Wrap connect() in a bounded retry.",
                List.of("src/main/java/com/orange/epe/SftpPoller.java"),
                List.of("Retry could mask a permanent failure"),
                "Unit test the retry boundary.", false));

        AgentOutcome<DesignNote> outcome = architect(llm).run(request(REPO_PATHS, true));

        assertEquals(1, outcome.artifact().impactedModules().size());
        assertTrue(outcome.notes().stream().anyMatch(n -> n.contains("verified against the repository")));
    }

    @Test
    void shouldRejectADesignThatNamesAModuleWhichDoesNotExist() {
        // The specific failure this stage exists to prevent: a hallucinated module
        // becomes a hallucinated file and then a build failure two stages later.
        ScriptedLlmGateway llm = new ScriptedLlmGateway().respondWith(new DesignNote(
                "Add a new retry service.",
                List.of("src/main/java/com/orange/epe/RetryOrchestrator.java"),
                List.of(), "Tests.", false));

        ArtifactValidationException thrown = assertThrows(ArtifactValidationException.class,
                () -> architect(llm).run(request(REPO_PATHS, true)));

        assertTrue(thrown.getMessage().contains("do not exist in the repository"));
        assertTrue(thrown.getMessage().contains("RetryOrchestrator"));
    }

    @Test
    void shouldAcceptADirectoryAsAnImpactedModule() {
        ScriptedLlmGateway llm = new ScriptedLlmGateway().respondWith(new DesignNote(
                "Change the config package.",
                List.of("src/main/java/com/orange/epe/config"),
                List.of(), "Tests.", false));

        assertDoesNotThrow(() -> architect(llm).run(request(REPO_PATHS, true)));
    }

    @Test
    void shouldFailWhenVerificationIsRequiredButTheRepositoryIsUnavailable() {
        ScriptedLlmGateway llm = new ScriptedLlmGateway().respondWith(new DesignNote(
                "Something.", List.of("anything"), List.of(), "Tests.", false));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> architect(llm).run(request(List.of(), true)));

        assertTrue(thrown.getMessage().contains("repository listing is unavailable"));
    }

    @Test
    void shouldWarnRatherThanFailWhenVerificationIsExplicitlyNotRequired() {
        ScriptedLlmGateway llm = new ScriptedLlmGateway().respondWith(new DesignNote(
                "Something.", List.of("anything"), List.of(), "Tests.", false));

        AgentOutcome<DesignNote> outcome = architect(llm).run(request(List.of(), false));

        assertTrue(outcome.notes().stream().anyMatch(n -> n.contains("UNVERIFIED")));
    }

    @Test
    void shouldPutTheRepositoryListingInThePrompt() {
        ScriptedLlmGateway llm = new ScriptedLlmGateway().respondWith(new DesignNote(
                "x", List.of("pom.xml"), List.of(), "y", false));

        architect(llm).run(request(REPO_PATHS, true));

        assertTrue(llm.lastRequest().userPrompt().contains("SftpPoller.java"));
    }

    @Test
    void shouldRejectADesignThatTouchesNothing() {
        assertThrows(ArtifactValidationException.class,
                () -> new DesignNote("approach", List.of(), List.of(), "tests", false));
    }

    // --- Release ---

    @Test
    void shouldCaptureTheRollbackReferenceBeforeDeploying() {
        // Ordering is the point: capturing it afterwards would record the version you
        // are trying to escape from.
        RecordingDeploymentPort port = new RecordingDeploymentPort("v41");

        DeployVerdict verdict = release(port, policy("staging")).run(
                new ReleaseAgent.ReleaseRequest("w1", "app", "staging", "main")).artifact();

        assertTrue(verdict.deployed());
        assertEquals("v41", verdict.rollbackRef());
        assertEquals(List.of("currentVersion", "deploy"), port.calls);
    }

    @Test
    void shouldRefuseToDeployToAnEnvironmentThatIsNotAllowed() {
        RecordingDeploymentPort port = new RecordingDeploymentPort("v1");

        DeployVerdict verdict = release(port, policy("staging")).run(
                new ReleaseAgent.ReleaseRequest("w1", "app", "production", "main")).artifact();

        assertFalse(verdict.deployed());
        assertTrue(verdict.summary().contains("not in the allowed list"));
        assertTrue(port.calls.isEmpty(), "a refused deployment must not touch the platform");
    }

    @Test
    void shouldRefuseToDeployToAFrozenEnvironment() {
        SdlcProperties properties = new SdlcProperties();
        properties.getDeployment().setAllowedEnvironments(List.of("staging"));
        properties.getDeployment().setFrozenEnvironments(List.of("staging"));
        RecordingDeploymentPort port = new RecordingDeploymentPort("v1");

        DeployVerdict verdict = release(port, new DeploymentPolicy(properties)).run(
                new ReleaseAgent.ReleaseRequest("w1", "app", "staging", "main")).artifact();

        assertFalse(verdict.deployed());
        assertTrue(verdict.summary().contains("frozen"));
    }

    @Test
    void shouldReportAFailedDeploymentRatherThanThrowing() {
        RecordingDeploymentPort port = new RecordingDeploymentPort("v1");
        port.failDeploy = true;

        DeployVerdict verdict = release(port, policy("staging")).run(
                new ReleaseAgent.ReleaseRequest("w1", "app", "staging", "main")).artifact();

        assertFalse(verdict.deployed());
        assertTrue(verdict.summary().contains("Deployment failed"));
    }

    @Test
    void shouldMarkAFirstDeploymentAsHavingNoRollbackTarget() {
        RecordingDeploymentPort port = new RecordingDeploymentPort(null);

        AgentOutcome<DeployVerdict> outcome = release(port, policy("staging")).run(
                new ReleaseAgent.ReleaseRequest("w1", "app", "staging", "main"));

        assertTrue(outcome.artifact().deployed());
        assertEquals("NONE-FIRST-DEPLOYMENT", outcome.artifact().rollbackRef());
        assertTrue(outcome.notes().stream().anyMatch(n -> n.contains("no rollback target exists")));
    }

    @Test
    void shouldRefuseASuccessfulDeployVerdictWithNoRollbackReference() {
        assertThrows(ArtifactValidationException.class,
                () -> new DeployVerdict("staging", true, "http://x", null, "deployed"));
    }

    @Test
    void shouldBlockProductionDeploysAtTheWeekend() {
        SdlcProperties properties = new SdlcProperties();
        properties.getDeployment().setAllowedEnvironments(List.of("production"));
        properties.getDeployment().setProductionEnvironments(List.of("production"));
        DeploymentPolicy p = new DeploymentPolicy(properties);

        ZonedDateTime saturday = ZonedDateTime.parse("2026-07-25T10:00:00+02:00[Europe/Paris]")
                .plusDays(1);

        Optional<String> refusal = p.refuse("production", saturday);

        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains("weekend"));
    }

    @Test
    void shouldBlockProductionDeploysOutsideTheWindow() {
        SdlcProperties properties = new SdlcProperties();
        properties.getDeployment().setAllowedEnvironments(List.of("production"));
        properties.getDeployment().setProductionEnvironments(List.of("production"));
        DeploymentPolicy p = new DeploymentPolicy(properties);

        // Friday 20:00 local, outside the 09:00-16:00 window.
        ZonedDateTime lateFriday = ZonedDateTime.parse("2026-07-24T20:00:00+02:00[Europe/Paris]");

        assertTrue(p.refuse("production", lateFriday).isPresent());
    }

    @Test
    void shouldAllowNonProductionDeploysOutsideTheWindow() {
        // The window protects production. Blocking staging at 20:00 would be ceremony.
        DeploymentPolicy p = policy("staging");
        ZonedDateTime lateFriday = ZonedDateTime.parse("2026-07-24T20:00:00+02:00[Europe/Paris]");

        assertTrue(p.refuse("staging", lateFriday).isEmpty());
    }

    // --- helpers ---

    private ArchitectAgent architect(ScriptedLlmGateway llm) {
        return new ArchitectAgent(llm, new PromptTemplates());
    }

    private ArchitectAgent.ArchitectRequest request(List<String> paths, boolean requireVerification) {
        return new ArchitectAgent.ArchitectRequest("w1", SPEC, paths, requireVerification);
    }

    private ReleaseAgent release(DeploymentPort port, DeploymentPolicy policy) {
        return new ReleaseAgent(port, policy);
    }

    private DeploymentPolicy policy(String... allowed) {
        SdlcProperties properties = new SdlcProperties();
        properties.getDeployment().setAllowedEnvironments(List.of(allowed));
        return new DeploymentPolicy(properties);
    }

    private static class RecordingDeploymentPort implements DeploymentPort {
        final List<String> calls = new ArrayList<>();
        private final String currentVersion;
        boolean failDeploy;

        RecordingDeploymentPort(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        @Override public Optional<String> currentVersion(String app, String env) {
            calls.add("currentVersion");
            return Optional.ofNullable(currentVersion);
        }

        @Override public DeploymentResult deploy(String app, String env, String ref) {
            calls.add("deploy");
            if (failDeploy) throw new DeploymentException("platform said no");
            return new DeploymentResult(ref, "https://" + app + "." + env);
        }

        @Override public void rollback(String app, String env, String ref) {
            calls.add("rollback");
        }
    }
}
