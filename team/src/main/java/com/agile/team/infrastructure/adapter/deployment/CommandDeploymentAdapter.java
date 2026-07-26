package com.agile.team.infrastructure.adapter.deployment;

import com.agile.team.domain.port.CodeExecutor;
import com.agile.team.domain.port.DeploymentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deploys by running a configured command — {@code cf push}, a CI trigger, a script.
 * <p>
 * A command rather than a platform SDK, because the deployment mechanism is the part
 * that differs most between teams and the part this system has least business
 * opinionating about. The command runs through {@link CodeExecutor}, so it inherits
 * the same allowlist and timeout as everything else the agents run.
 * <p>
 * <strong>Unconfigured is a refusal, not a no-op.</strong> A deployment adapter that
 * silently does nothing and reports success is the worst possible failure mode in
 * this system.
 */
@Component
@EnableConfigurationProperties(CommandDeploymentAdapter.DeploymentCommandProperties.class)
public class CommandDeploymentAdapter implements DeploymentPort {

    private static final Logger log = LoggerFactory.getLogger(CommandDeploymentAdapter.class);

    private final CodeExecutor codeExecutor;
    private final DeploymentCommandProperties properties;

    public CommandDeploymentAdapter(CodeExecutor codeExecutor,
                                    DeploymentCommandProperties properties) {
        this.codeExecutor = codeExecutor;
        this.properties = properties;
    }

    @Override
    public Optional<String> currentVersion(String application, String environment) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        try {
            CodeExecutor.ExecutionResult result = codeExecutor.run(
                    properties.workspaceId(),
                    new CodeExecutor.ExecutionRequest(
                            substitute(properties.currentVersionCommand(), application, environment, null),
                            properties.timeoutSeconds(),
                            Map.of()));
            if (!result.succeeded()) {
                log.warn("Could not determine current version of {} in {} (exit {})",
                        application, environment, result.exitCode());
                return Optional.empty();
            }
            String version = result.output().trim();
            return version.isEmpty() ? Optional.empty() : Optional.of(version);
        } catch (Exception e) {
            log.warn("Could not determine current version of {} in {}: {}",
                    application, environment, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public DeploymentResult deploy(String application, String environment, String ref) {
        if (!properties.isConfigured()) {
            throw new DeploymentException(
                    "No deployment command is configured (sdlc.deployment.deploy-command). "
                            + "Refusing rather than reporting a deployment that did not happen.");
        }
        CodeExecutor.ExecutionResult result = codeExecutor.run(
                properties.workspaceId(),
                new CodeExecutor.ExecutionRequest(
                        substitute(properties.deployCommand(), application, environment, ref),
                        properties.timeoutSeconds(),
                        Map.of()));

        if (!result.succeeded()) {
            throw new DeploymentException("Deployment command failed (exit " + result.exitCode()
                    + (result.timedOut() ? ", timed out" : "") + "): " + result.tail(4_000));
        }
        log.info("Deployed {} to {} from {}", application, environment, ref);
        return new DeploymentResult(ref, properties.urlTemplate() != null
                ? properties.urlTemplate().replace("{app}", application).replace("{env}", environment)
                : null);
    }

    @Override
    public void rollback(String application, String environment, String rollbackRef) {
        if (!properties.isConfigured() || properties.rollbackCommand() == null) {
            throw new DeploymentException(
                    "No rollback command is configured; cannot roll " + application
                            + " in " + environment + " back to " + rollbackRef);
        }
        CodeExecutor.ExecutionResult result = codeExecutor.run(
                properties.workspaceId(),
                new CodeExecutor.ExecutionRequest(
                        substitute(properties.rollbackCommand(), application, environment, rollbackRef),
                        properties.timeoutSeconds(), Map.of()));
        if (!result.succeeded()) {
            throw new DeploymentException("Rollback failed (exit " + result.exitCode() + ")");
        }
        log.warn("Rolled {} in {} back to {}", application, environment, rollbackRef);
    }

    private List<String> substitute(String template, String application, String environment, String ref) {
        return Arrays.stream(template.split(","))
                .map(String::trim)
                .map(part -> part
                        .replace("{app}", application != null ? application : "")
                        .replace("{env}", environment != null ? environment : "")
                        .replace("{ref}", ref != null ? ref : ""))
                .toList();
    }

    @ConfigurationProperties(prefix = "sdlc.deployment")
    public record DeploymentCommandProperties(
            String deployCommand,
            String currentVersionCommand,
            String rollbackCommand,
            String urlTemplate,
            String workspaceId,
            long timeoutSeconds
    ) {
        public DeploymentCommandProperties {
            if (timeoutSeconds <= 0) timeoutSeconds = 900;
            if (workspaceId == null || workspaceId.isBlank()) workspaceId = "deployment";
        }

        public boolean isConfigured() {
            return deployCommand != null && !deployCommand.isBlank();
        }
    }
}
