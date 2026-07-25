package com.agile.team.support;

import com.agile.team.domain.port.LlmGateway;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A deterministic {@link LlmGateway} for tests.
 * <p>
 * This is the payoff of DECISIONS.md D-003. Because every agent depends on the
 * {@code LlmGateway} port rather than on Spring AI directly, the entire pipeline —
 * agents, validation, gates, orchestration — is testable with no API key, no
 * network, and no cost. Responses are queued in order; the recorded requests are
 * available for assertions about what the agent actually asked.
 */
public class ScriptedLlmGateway implements LlmGateway {

    private final Deque<Object> responses = new ArrayDeque<>();
    private final Deque<RuntimeException> failures = new ArrayDeque<>();
    private final List<LlmRequest> requests = new ArrayList<>();
    private TokenUsage usagePerCall = TokenUsage.of(100, 50);

    /** Queue an artifact to return from the next call. */
    public ScriptedLlmGateway respondWith(Object artifact) {
        responses.add(artifact);
        return this;
    }

    /** Queue a failure for the next call. Failures take precedence over responses. */
    public ScriptedLlmGateway failWith(RuntimeException exception) {
        failures.add(exception);
        return this;
    }

    public ScriptedLlmGateway withUsagePerCall(TokenUsage usage) {
        this.usagePerCall = usage;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> LlmResult<T> generate(LlmRequest request, Class<T> responseType) {
        requests.add(request);

        if (!failures.isEmpty()) {
            throw failures.poll();
        }
        if (responses.isEmpty()) {
            throw new LlmGatewayException(
                    "ScriptedLlmGateway has no queued response for " + responseType.getSimpleName()
                            + " (call " + requests.size() + ")");
        }

        Object next = responses.poll();
        if (!responseType.isInstance(next)) {
            throw new LlmGatewayException(
                    "Queued response is a " + next.getClass().getSimpleName()
                            + " but a " + responseType.getSimpleName() + " was requested");
        }
        return new LlmResult<>((T) next, usagePerCall);
    }

    public List<LlmRequest> requests() {
        return List.copyOf(requests);
    }

    public LlmRequest lastRequest() {
        if (requests.isEmpty()) {
            throw new IllegalStateException("No requests were made");
        }
        return requests.get(requests.size() - 1);
    }

    public int callCount() {
        return requests.size();
    }
}
