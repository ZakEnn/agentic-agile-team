package com.agile.team.domain.port;

import java.util.Optional;

/**
 * Triggers deployments and reports what is currently running.
 * <p>
 * Deliberately deterministic, like every other port except {@link LlmGateway}: the
 * Release agent decides <em>whether</em> to deploy; this port <em>does</em> it and
 * reports the truth. {@link #currentVersion} exists so a rollback reference is
 * captured <strong>before</strong> the new version goes out — capturing it after
 * would record the thing you are trying to escape from.
 */
public interface DeploymentPort {

    /** The version currently running, used as the rollback reference. */
    Optional<String> currentVersion(String application, String environment);

    /** Deploy a ref. Throws on failure rather than reporting a false success. */
    DeploymentResult deploy(String application, String environment, String ref);

    /** Roll back to a previously recorded version. */
    void rollback(String application, String environment, String rollbackRef);

    record DeploymentResult(String version, String url) {
    }

    class DeploymentException extends RuntimeException {
        public DeploymentException(String message) {
            super(message);
        }

        public DeploymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
