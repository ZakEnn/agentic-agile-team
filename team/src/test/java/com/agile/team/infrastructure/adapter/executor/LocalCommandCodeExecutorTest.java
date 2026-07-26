package com.agile.team.infrastructure.adapter.executor;

import com.agile.team.domain.port.CodeExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The executor that decides whether a build actually passed.
 * <p>
 * These tests run real processes. That is the point: the value of this component is
 * that {@code exitCode} comes from the operating system, so a test that mocked the
 * process would verify nothing worth verifying.
 */
class LocalCommandCodeExecutorTest {

    @TempDir Path tempDir;

    private LocalCommandCodeExecutor executor;
    private String workspace;

    @BeforeEach
    void setUp() {
        executor = new LocalCommandCodeExecutor(new LocalCommandCodeExecutor.ExecutorProperties(
                tempDir.toString(), List.of("java", "mvn", "git"), 60));
        workspace = executor.prepareWorkspace("wave-1");
    }

    @Test
    void shouldReportARealSuccessExitCode() {
        // java -version exits 0. A real process, a real exit code.
        CodeExecutor.ExecutionResult result = executor.run(workspace,
                CodeExecutor.ExecutionRequest.of(List.of("java", "-version")));

        assertEquals(0, result.exitCode());
        assertTrue(result.succeeded());
        assertFalse(result.timedOut());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void shouldReportARealFailureExitCode() {
        // An invalid flag makes java exit non-zero. The executor must report that
        // faithfully rather than smoothing it over.
        CodeExecutor.ExecutionResult result = executor.run(workspace,
                CodeExecutor.ExecutionRequest.of(List.of("java", "--not-a-real-flag")));

        assertNotEquals(0, result.exitCode());
        assertFalse(result.succeeded());
        assertFalse(result.output().isBlank(), "the failure output must be captured for feedback");
    }

    @Test
    void shouldWriteFilesIntoTheWorkspace() throws Exception {
        executor.applyChanges(workspace, List.of(
                new CodeExecutor.FileChange("src/main/java/Example.java", "class Example {}"),
                new CodeExecutor.FileChange("README.md", "# Hello")));

        Path written = tempDir.resolve(workspace).resolve("src/main/java/Example.java");
        assertTrue(Files.exists(written));
        assertEquals("class Example {}", Files.readString(written));
    }

    @Test
    void shouldRefusePathsThatEscapeTheWorkspace() {
        // A model producing a traversal path is a plausible output of a confused or
        // manipulated agent, not a hypothetical.
        CodeExecutor.CodeExecutionException thrown = assertThrows(
                CodeExecutor.CodeExecutionException.class,
                () -> executor.applyChanges(workspace, List.of(
                        new CodeExecutor.FileChange("../../escaped.txt", "malicious"))));

        assertTrue(thrown.getMessage().contains("escapes the workspace"));
        assertFalse(Files.exists(tempDir.getParent().resolve("escaped.txt")));
    }

    @Test
    void shouldRefuseDeeplyNestedTraversal() {
        assertThrows(CodeExecutor.CodeExecutionException.class,
                () -> executor.applyChanges(workspace, List.of(
                        new CodeExecutor.FileChange("a/b/../../../../etc/passwd", "x"))));
    }

    @Test
    void shouldRefuseCommandsThatAreNotOnTheAllowlist() {
        // Without this, the allowlist is decorative and the agent can run anything.
        CodeExecutor.CodeExecutionException thrown = assertThrows(
                CodeExecutor.CodeExecutionException.class,
                () -> executor.run(workspace, CodeExecutor.ExecutionRequest.of(
                        List.of("curl", "https://example.com"))));

        assertTrue(thrown.getMessage().contains("not on the allowlist"));
    }

    @Test
    void shouldNotAllowShellsByDefault() {
        // Allowing sh or cmd would let anything through the allowlist.
        LocalCommandCodeExecutor.ExecutorProperties defaults =
                new LocalCommandCodeExecutor.ExecutorProperties(tempDir.toString(), null, 0);

        assertFalse(defaults.isAllowed("sh"));
        assertFalse(defaults.isAllowed("bash"));
        assertFalse(defaults.isAllowed("cmd"));
        assertFalse(defaults.isAllowed("powershell"));
        assertTrue(defaults.isAllowed("mvn"));
    }

    @Test
    void shouldMatchAllowlistOnTheExecutableNameNotThePath() {
        LocalCommandCodeExecutor.ExecutorProperties props =
                new LocalCommandCodeExecutor.ExecutorProperties(tempDir.toString(), List.of("mvn"), 60);

        assertTrue(props.isAllowed("/usr/local/bin/mvn"));
        assertFalse(props.isAllowed("/tmp/evil/curl"));
    }

    @Test
    void shouldIsolateWorkspacesFromEachOther() {
        String other = executor.prepareWorkspace("wave-2");
        assertNotEquals(workspace, other);

        executor.applyChanges(workspace, List.of(new CodeExecutor.FileChange("a.txt", "one")));
        executor.applyChanges(other, List.of(new CodeExecutor.FileChange("a.txt", "two")));

        assertTrue(Files.exists(tempDir.resolve(workspace).resolve("a.txt")));
        assertTrue(Files.exists(tempDir.resolve(other).resolve("a.txt")));
    }

    @Test
    void shouldDiscardAWorkspaceAndItsContents() {
        executor.applyChanges(workspace, List.of(
                new CodeExecutor.FileChange("nested/dir/file.txt", "content")));
        assertTrue(Files.exists(tempDir.resolve(workspace)));

        executor.discardWorkspace(workspace);

        assertFalse(Files.exists(tempDir.resolve(workspace)));
    }

    @Test
    void shouldTruncateOutputTailForFeedback() {
        CodeExecutor.ExecutionResult big = new CodeExecutor.ExecutionResult(
                1, "x".repeat(10_000), false, 5);

        String tail = big.tail(100);

        assertTrue(tail.length() < 200);
        assertTrue(tail.startsWith("... output truncated ..."));
    }

    @Test
    void shouldRejectAnEmptyCommand() {
        assertThrows(IllegalArgumentException.class,
                () -> CodeExecutor.ExecutionRequest.of(List.of()));
    }
}
