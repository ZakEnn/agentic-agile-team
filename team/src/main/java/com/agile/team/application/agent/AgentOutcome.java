package com.agile.team.application.agent;

import com.agile.team.domain.port.LlmGateway.TokenUsage;

import java.util.List;

/**
 * What every agent returns: a typed artifact, what it cost, and any notes worth
 * putting in the audit trail.
 * <p>
 * A uniform outcome type is what lets the orchestrator treat all six agents
 * identically — record the artifact, add the tokens to the wave budget, append the
 * notes to the conversation, advance the stage — without knowing which agent ran.
 *
 * @param artifact the validated stage output
 * @param usage    tokens consumed producing it, for budget enforcement
 * @param notes    human-readable observations recorded in ConversationHistory
 */
public record AgentOutcome<T>(T artifact, TokenUsage usage, List<String> notes) {

    public AgentOutcome {
        if (artifact == null) throw new IllegalArgumentException("artifact must not be null");
        if (usage == null) usage = TokenUsage.unknown();
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public static <T> AgentOutcome<T> of(T artifact, TokenUsage usage, String... notes) {
        return new AgentOutcome<>(artifact, usage, List.of(notes));
    }
}
