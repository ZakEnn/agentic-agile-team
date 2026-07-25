package com.agile.team.domain.port;

import com.agile.team.domain.agent.AgentRole;

/**
 * Outbound port for the one thing an LLM is legitimately used for in this system:
 * <strong>judgment</strong>, returned as a validated typed artifact.
 * <p>
 * This is deliberately the <em>only</em> port backed by a language model. Every
 * other port ({@link GitLabPort}, {@link SonarQubePort}, {@link JiraPort},
 * {@link ConfluencePort}) is a deterministic API client. The original codebase
 * inverted this — {@code GitLabAdapter} implemented {@code GitLabPort} by asking
 * a model to "create a branch" in prose and treating the reply as fact — which
 * meant an adapter could hallucinate while every caller assumed it could not.
 * <p>
 * Having an explicit LLM port makes the seam honest: callers can see exactly
 * where non-determinism enters the system, and tests can replace it wholesale.
 *
 * @see com.agile.team.infrastructure.adapter.ai.SpringAiLlmGateway real implementation
 */
public interface LlmGateway {

    /**
     * Ask the model for a structured artifact.
     *
     * @param request      role, prompts and correlation metadata
     * @param responseType the artifact type to bind the response to
     * @return the parsed artifact plus token accounting
     * @throws LlmGatewayException if the model cannot be reached or the response
     *                             cannot be bound to {@code responseType}
     */
    <T> LlmResult<T> generate(LlmRequest request, Class<T> responseType);

    /** Thrown when generation or binding fails. Never swallowed silently. */
    class LlmGatewayException extends RuntimeException {
        public LlmGatewayException(String message, Throwable cause) {
            super(message, cause);
        }

        public LlmGatewayException(String message) {
            super(message);
        }
    }

    /**
     * A request for judgment.
     *
     * @param role         which agent is asking (selects the system prompt persona)
     * @param systemPrompt the governed system prompt, including any governance skills
     * @param userPrompt   the task-specific prompt
     * @param waveId       correlation id, carried into logs and metrics
     * @param stage        the SDLC stage, carried into logs and metrics
     */
    record LlmRequest(
            AgentRole role,
            String systemPrompt,
            String userPrompt,
            String waveId,
            String stage
    ) {
        public LlmRequest {
            if (role == null) throw new IllegalArgumentException("role must not be null");
            if (userPrompt == null || userPrompt.isBlank()) {
                throw new IllegalArgumentException("userPrompt must not be blank");
            }
            if (systemPrompt == null) systemPrompt = "";
        }
    }

    /**
     * The parsed artifact plus what it cost.
     * <p>
     * Token usage is part of the result rather than a side channel because budget
     * enforcement is a first-class concern: the plan cites roughly 15x token usage
     * for multi-agent systems, and an unbounded retry loop is how that materialises
     * as a bill.
     */
    record LlmResult<T>(T value, TokenUsage usage) {
        public LlmResult {
            if (value == null) throw new IllegalArgumentException("value must not be null");
            if (usage == null) usage = TokenUsage.unknown();
        }
    }

    /** Token accounting for a single model call. */
    record TokenUsage(long inputTokens, long outputTokens) {

        public static TokenUsage unknown() {
            return new TokenUsage(0, 0);
        }

        public static TokenUsage of(long inputTokens, long outputTokens) {
            return new TokenUsage(Math.max(0, inputTokens), Math.max(0, outputTokens));
        }

        public long total() {
            return inputTokens + outputTokens;
        }

        public TokenUsage plus(TokenUsage other) {
            if (other == null) return this;
            return new TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens);
        }
    }
}
