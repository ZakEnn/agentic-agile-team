package com.agile.team.support;

import com.agile.team.domain.port.CodeExecutor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A {@link CodeExecutor} whose command results are scripted.
 * <p>
 * Lets the agent tests assert the behaviour that matters — that a non-zero exit code
 * fails the stage and a zero exit code is required to claim success — without
 * running a real build in every test.
 */
public class ScriptedCodeExecutor implements CodeExecutor {

    private final Deque<ExecutionResult> results = new ArrayDeque<>();
    private final List<List<String>> commandsRun = new ArrayList<>();
    private final List<FileChange> appliedChanges = new ArrayList<>();
    private final List<String> preparedWorkspaces = new ArrayList<>();
    private final List<String> discardedWorkspaces = new ArrayList<>();

    public ScriptedCodeExecutor succeedsWith(String output) {
        results.add(new ExecutionResult(0, output, false, 10));
        return this;
    }

    public ScriptedCodeExecutor failsWith(int exitCode, String output) {
        results.add(new ExecutionResult(exitCode, output, false, 10));
        return this;
    }

    public ScriptedCodeExecutor timesOut() {
        results.add(new ExecutionResult(-1, "killed at timeout", true, 900_000));
        return this;
    }

    @Override
    public String prepareWorkspace(String waveId) {
        String id = "ws-" + (preparedWorkspaces.size() + 1);
        preparedWorkspaces.add(id);
        return id;
    }

    @Override
    public void discardWorkspace(String workspaceId) {
        discardedWorkspaces.add(workspaceId);
    }

    @Override
    public void applyChanges(String workspaceId, List<FileChange> changes) {
        appliedChanges.addAll(changes);
    }

    @Override
    public ExecutionResult run(String workspaceId, ExecutionRequest request) {
        commandsRun.add(request.command());
        if (results.isEmpty()) {
            throw new IllegalStateException(
                    "ScriptedCodeExecutor has no queued result for " + request.command());
        }
        return results.poll();
    }

    public List<List<String>> commandsRun() {
        return List.copyOf(commandsRun);
    }

    public List<FileChange> appliedChanges() {
        return List.copyOf(appliedChanges);
    }

    public List<String> discardedWorkspaces() {
        return List.copyOf(discardedWorkspaces);
    }

    /** Spring caches one context across tests, so this instance is shared. */
    public void reset() {
        results.clear();
        commandsRun.clear();
        appliedChanges.clear();
        preparedWorkspaces.clear();
        discardedWorkspaces.clear();
    }
}
