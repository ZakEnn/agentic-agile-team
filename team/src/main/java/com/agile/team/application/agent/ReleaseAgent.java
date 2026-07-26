package com.agile.team.application.agent;

import com.agile.team.application.release.DeploymentPolicy;
import com.agile.team.domain.artifact.DeployVerdict;
import com.agile.team.domain.port.DeploymentPort;
import com.agile.team.domain.port.LlmGateway.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Release agent: executes a deployment, within policy, with a recorded way back.
 * <p>
 * <strong>This agent uses no language model, and that is deliberate.</strong> Every
 * input to a deployment decision is a fact — is the environment allowed, is it
 * frozen, are we inside the window, what version is running now. There is no
 * judgment for a model to add, and adding one would insert non-determinism into the
 * single most irreversible step in the pipeline. The plan's principle is "the LLM
 * judges; it never integrates"; here there is nothing to judge.
 * <p>
 * The ordering matters: the rollback reference is captured <em>before</em> the new
 * version goes out. Capturing it afterwards would record the thing you are trying
 * to escape from.
 */
@Component
public class ReleaseAgent {

    private static final Logger log = LoggerFactory.getLogger(ReleaseAgent.class);

    private final DeploymentPort deploymentPort;
    private final DeploymentPolicy policy;

    public ReleaseAgent(DeploymentPort deploymentPort, DeploymentPolicy policy) {
        this.deploymentPort = deploymentPort;
        this.policy = policy;
    }

    public AgentOutcome<DeployVerdict> run(ReleaseRequest request) {
        List<String> notes = new ArrayList<>();

        Optional<String> refusal = policy.refuse(request.environment(), ZonedDateTime.now());
        if (refusal.isPresent()) {
            // A policy refusal is a terminal outcome, not a retryable failure: waiting
            // will not make a frozen environment unfrozen.
            log.warn("[RELEASE] refused by policy: {}", refusal.get());
            notes.add("Refused by deployment policy");
            return new AgentOutcome<>(
                    DeployVerdict.failed(request.environment(), refusal.get()),
                    TokenUsage.unknown(), notes);
        }

        String rollbackRef = deploymentPort
                .currentVersion(request.application(), request.environment())
                .orElse(null);

        if (rollbackRef == null) {
            // First deployment to this environment. Legitimate, but it must be visible:
            // there is nothing to roll back to.
            notes.add("No current version found — this appears to be the first deployment to "
                    + request.environment() + ", so no rollback target exists");
            rollbackRef = "NONE-FIRST-DEPLOYMENT";
        } else {
            notes.add("Rollback reference captured before deploying: " + rollbackRef);
        }

        try {
            DeploymentPort.DeploymentResult result = deploymentPort.deploy(
                    request.application(), request.environment(), request.ref());

            notes.add("Deployed %s to %s as version %s"
                    .formatted(request.ref(), request.environment(), result.version()));
            log.info("[RELEASE] wave={} env={} version={} rollbackRef={}",
                    request.waveId(), request.environment(), result.version(), rollbackRef);

            return new AgentOutcome<>(new DeployVerdict(
                    request.environment(), true, result.url(), rollbackRef,
                    "Deployed %s to %s".formatted(request.ref(), request.environment())),
                    TokenUsage.unknown(), notes);

        } catch (RuntimeException e) {
            log.error("[RELEASE] deployment failed for wave={}: {}", request.waveId(), e.getMessage());
            notes.add("Deployment failed: " + e.getMessage());
            return new AgentOutcome<>(
                    DeployVerdict.failed(request.environment(),
                            "Deployment failed: " + e.getMessage()),
                    TokenUsage.unknown(), notes);
        }
    }

    /**
     * @param waveId      correlation id
     * @param application application name on the platform
     * @param environment target environment
     * @param ref         the git ref or artifact version to deploy
     */
    public record ReleaseRequest(String waveId, String application, String environment, String ref) {
        public ReleaseRequest {
            if (application == null || application.isBlank()) {
                throw new IllegalArgumentException("application must not be blank");
            }
            if (environment == null || environment.isBlank()) {
                throw new IllegalArgumentException("environment must not be blank");
            }
            if (ref == null || ref.isBlank()) {
                throw new IllegalArgumentException("ref must not be blank");
            }
        }
    }
}
