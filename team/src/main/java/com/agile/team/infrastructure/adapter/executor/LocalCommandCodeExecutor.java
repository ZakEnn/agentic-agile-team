package com.agile.team.infrastructure.adapter.executor;

import com.agile.team.domain.port.CodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs commands in a workspace directory on the local filesystem.
 * <p>
 * <strong>This is not a security sandbox.</strong> It provides path confinement, a
 * command allowlist and a hard timeout — enough to stop an agent's mistake, not
 * enough to stop an agent's compromise. SDLC_AGENT_PLAN.md §2.6 is explicit that
 * prompt injection has no convincing mitigation, and an agent that reads Confluence
 * pages written by other people while holding a GitLab write token is a live
 * injection-to-exfiltration path. Production must run this inside a container with
 * no credentials and allow-listed egress; see IMPLEMENTATION_LOG.md (M4, BLOCKED).
 * <p>
 * What it does provide is the thing the gate depends on: a <em>real</em> exit code.
 */
@Component
@EnableConfigurationProperties(LocalCommandCodeExecutor.ExecutorProperties.class)
public class LocalCommandCodeExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(LocalCommandCodeExecutor.class);
    private static final int MAX_CAPTURED_OUTPUT = 200_000;

    private final ExecutorProperties properties;
    private final Path root;

    public LocalCommandCodeExecutor(ExecutorProperties properties) {
        this.properties = properties;
        this.root = Path.of(properties.workspaceRoot()).toAbsolutePath().normalize();
    }

    @Override
    public String prepareWorkspace(String waveId) {
        String workspaceId = (waveId != null ? sanitise(waveId) : "wave") + "-" + UUID.randomUUID();
        Path workspace = root.resolve(workspaceId);
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new CodeExecutionException("Failed to create workspace " + workspaceId, e);
        }
        log.info("Prepared workspace {}", workspace);
        return workspaceId;
    }

    @Override
    public void discardWorkspace(String workspaceId) {
        Path workspace = resolveWorkspace(workspaceId);
        if (!Files.exists(workspace)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not discard workspace {}: {}", workspaceId, e.getMessage());
        }
    }

    @Override
    public void applyChanges(String workspaceId, List<FileChange> changes) {
        Path workspace = resolveWorkspace(workspaceId);
        for (FileChange change : changes) {
            Path target = resolveInside(workspace, change.path());
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, change.content(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.debug("Wrote {} ({} chars)", change.path(), change.content().length());
            } catch (IOException e) {
                throw new CodeExecutionException("Failed to write " + change.path(), e);
            }
        }
    }

    @Override
    public ExecutionResult run(String workspaceId, ExecutionRequest request) {
        Path workspace = resolveWorkspace(workspaceId);
        String executable = request.command().get(0);
        if (!properties.isAllowed(executable)) {
            // Refuse rather than run: the allowlist is the difference between "the
            // agent builds the project" and "the agent runs whatever it decided to".
            throw new CodeExecutionException(
                    "Command '" + executable + "' is not on the allowlist "
                            + properties.allowedCommands()
                            + ". Add it explicitly if the pipeline genuinely needs it.");
        }

        long started = System.currentTimeMillis();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(request.command())
                    .directory(workspace.toFile())
                    .redirectErrorStream(true);
            builder.environment().putAll(request.env());
            process = builder.start();

            String output = readOutput(process.getInputStream());
            boolean finished = process.waitFor(request.timeoutSeconds(), TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - started;

            if (!finished) {
                process.destroyForcibly();
                log.warn("Command {} timed out after {}s", request.command(), request.timeoutSeconds());
                return new ExecutionResult(-1, output, true, duration);
            }

            int exitCode = process.exitValue();
            log.info("Command {} exited {} in {}ms", request.command(), exitCode, duration);
            return new ExecutionResult(exitCode, output, false, duration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new CodeExecutionException("Interrupted while running " + request.command(), e);
        } catch (IOException e) {
            throw new CodeExecutionException("Failed to run " + request.command(), e);
        }
    }

    private String readOutput(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_CAPTURED_OUTPUT);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Path resolveWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new CodeExecutionException("workspaceId must not be blank");
        }
        Path workspace = root.resolve(sanitise(workspaceId)).normalize();
        if (!workspace.startsWith(root)) {
            throw new CodeExecutionException("Workspace escapes the configured root: " + workspaceId);
        }
        return workspace;
    }

    /**
     * Resolve a workspace-relative path, refusing anything that escapes.
     * <p>
     * The check is on the <em>normalised</em> path, so {@code ../../etc/passwd} and
     * absolute paths are both rejected. A model producing a traversal path is not a
     * hypothetical: it is a plausible output of a confused or manipulated agent.
     */
    private Path resolveInside(Path workspace, String relativePath) {
        Path candidate = workspace.resolve(relativePath).normalize();
        if (!candidate.startsWith(workspace)) {
            throw new CodeExecutionException(
                    "Path escapes the workspace and was refused: " + relativePath);
        }
        return candidate;
    }

    private String sanitise(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    @ConfigurationProperties(prefix = "sdlc.executor")
    public record ExecutorProperties(
            String workspaceRoot,
            List<String> allowedCommands,
            long defaultTimeoutSeconds
    ) {
        public ExecutorProperties {
            if (workspaceRoot == null || workspaceRoot.isBlank()) {
                workspaceRoot = System.getProperty("java.io.tmpdir") + "/sdlc-workspaces";
            }
            if (allowedCommands == null || allowedCommands.isEmpty()) {
                // Build tooling only. Deliberately excludes shells: allowing sh or cmd
                // would make the allowlist decorative, since anything can be run through them.
                allowedCommands = List.of("mvn", "mvnw", "mvnw.cmd", "gradle", "gradlew",
                        "npm", "node", "git", "java");
            }
            if (defaultTimeoutSeconds <= 0) defaultTimeoutSeconds = 600;
        }

        public boolean isAllowed(String executable) {
            if (executable == null) return false;
            String name = Path.of(executable).getFileName().toString().toLowerCase();
            return allowedCommands.stream().anyMatch(allowed -> name.equals(allowed.toLowerCase()));
        }
    }
}
