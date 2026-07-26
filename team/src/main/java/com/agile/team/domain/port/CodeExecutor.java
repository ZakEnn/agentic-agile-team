package com.agile.team.domain.port;

import java.util.List;
import java.util.Map;

/**
 * Runs commands and applies file changes in a workspace, and reports the truth
 * about what happened.
 * <p>
 * This is the port that makes the Developer and QA agents honest. SDLC_AGENT_PLAN.md
 * §5 M4 is explicit that the capability worth building here is "run a build and
 * report the truth about whether it passed" — everything downstream depends on that
 * being a real exit code rather than a model's opinion of its own work.
 * <p>
 * It is also the delegation boundary from DECISIONS.md D-008: a vendor coding agent
 * (Claude Code, OpenHands) plugs in as an alternative implementation without any
 * other part of the system changing.
 */
public interface CodeExecutor {

    /**
     * Write files into the workspace.
     *
     * @throws CodeExecutionException if any path escapes the workspace root
     */
    void applyChanges(String workspaceId, List<FileChange> changes);

    /** Run a command and report its real exit code and output. */
    ExecutionResult run(String workspaceId, ExecutionRequest request);

    /** Prepare an isolated workspace for a wave. Returns its identifier. */
    String prepareWorkspace(String waveId);

    /** Release a workspace and its contents. */
    void discardWorkspace(String workspaceId);

    /**
     * A single file write.
     *
     * @param path    workspace-relative path; must not escape the workspace
     * @param content full new file content
     */
    record FileChange(String path, String content) {
        public FileChange {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("FileChange.path must not be blank");
            }
            if (content == null) content = "";
        }
    }

    /**
     * @param command        the command and its arguments
     * @param timeoutSeconds hard ceiling; a hung build must not hold a stage forever
     * @param env            extra environment variables
     */
    record ExecutionRequest(List<String> command, long timeoutSeconds, Map<String, String> env) {
        public ExecutionRequest {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("command must not be empty");
            }
            if (timeoutSeconds <= 0) timeoutSeconds = 600;
            env = env == null ? Map.of() : Map.copyOf(env);
        }

        public static ExecutionRequest of(List<String> command) {
            return new ExecutionRequest(command, 600, Map.of());
        }
    }

    /**
     * What actually happened.
     *
     * @param exitCode  the process exit code; {@code 0} means success and nothing else does
     * @param output    combined stdout and stderr, truncated
     * @param timedOut  whether the command was killed at its timeout
     * @param durationMs wall-clock duration
     */
    record ExecutionResult(int exitCode, String output, boolean timedOut, long durationMs) {
        public ExecutionResult {
            if (output == null) output = "";
        }

        public boolean succeeded() {
            return exitCode == 0 && !timedOut;
        }

        /** The tail of the output — what a failure feedback loop should show the model. */
        public String tail(int maxChars) {
            if (output.length() <= maxChars) {
                return output;
            }
            return "... output truncated ...\n" + output.substring(output.length() - maxChars);
        }
    }

    class CodeExecutionException extends RuntimeException {
        public CodeExecutionException(String message) {
            super(message);
        }

        public CodeExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
