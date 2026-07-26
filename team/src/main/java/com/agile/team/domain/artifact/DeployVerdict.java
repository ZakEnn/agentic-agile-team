package com.agile.team.domain.artifact;

/**
 * The Release agent's output.
 * <p>
 * {@code rollbackRef} is mandatory on a successful deployment. SDLC_AGENT_PLAN.md
 * §3.2 lists it as a guardrail and §6 asks who owns the incident when an
 * agent-authored change fails in production — the least this system can do is
 * guarantee that the way back is recorded before the way forward is taken.
 *
 * @param environment  where it went
 * @param deployed     whether the deployment succeeded
 * @param url          the deployed application URL, when known
 * @param rollbackRef  the version to roll back to; required when {@code deployed}
 * @param summary      what happened
 */
public record DeployVerdict(
        String environment,
        boolean deployed,
        String url,
        String rollbackRef,
        String summary
) {
    public DeployVerdict {
        if (environment == null || environment.isBlank()) {
            throw new ArtifactValidationException("DeployVerdict.environment must not be blank");
        }
        if (deployed && (rollbackRef == null || rollbackRef.isBlank())) {
            throw new ArtifactValidationException(
                    "A successful deployment must record a rollbackRef — deploying without a "
                            + "recorded way back is not a deployment anyone can operate");
        }
        if (summary == null) summary = "";
    }

    public static DeployVerdict failed(String environment, String reason) {
        return new DeployVerdict(environment, false, null, null, reason);
    }
}
