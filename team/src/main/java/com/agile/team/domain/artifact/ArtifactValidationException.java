package com.agile.team.domain.artifact;

/**
 * Thrown when a model-produced artifact violates a domain invariant.
 * <p>
 * Distinct from a transport or parsing failure: this means the model returned
 * something well-formed but unusable (a spec with no acceptance criteria, a review
 * verdict with a severity outside the taxonomy). Callers treat it as a retryable
 * generation failure — feed the message back to the model — rather than as an
 * infrastructure error.
 */
public class ArtifactValidationException extends RuntimeException {

    public ArtifactValidationException(String message) {
        super(message);
    }

    public ArtifactValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
