package com.agile.team.application.release;

import com.agile.team.infrastructure.config.SdlcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Policy-as-code guardrails on deployment.
 * <p>
 * SDLC_AGENT_PLAN.md §2.6 records the operational consensus: audit logging, blast
 * radius limits, rollback infrastructure and policy-as-code must exist <em>before</em>
 * an agent acts in production. This is the component that sits between the agent's
 * decision and the execution engine and can say no.
 * <p>
 * Every check fails closed. An unlisted environment is refused, not permitted.
 */
@Component
public class DeploymentPolicy {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPolicy.class);

    private final SdlcProperties properties;

    public DeploymentPolicy(SdlcProperties properties) {
        this.properties = properties;
    }

    /**
     * @return empty when the deployment is allowed, or the reason it is refused
     */
    public Optional<String> refuse(String environment, ZonedDateTime at) {
        SdlcProperties.Deployment deployment = properties.getDeployment();

        List<String> allowed = deployment.getAllowedEnvironments();
        if (environment == null || allowed.stream().noneMatch(environment::equalsIgnoreCase)) {
            return Optional.of("Environment '" + environment + "' is not in the allowed list "
                    + allowed + ". An unlisted environment is refused, not permitted.");
        }

        if (deployment.getFrozenEnvironments().stream().anyMatch(environment::equalsIgnoreCase)) {
            return Optional.of("Environment '" + environment + "' is frozen.");
        }

        if (deployment.isBlockOutsideWindow() && isProductionLike(environment, deployment)) {
            Optional<String> windowRefusal = refuseOutsideWindow(at, deployment);
            if (windowRefusal.isPresent()) {
                return windowRefusal;
            }
        }
        return Optional.empty();
    }

    private Optional<String> refuseOutsideWindow(ZonedDateTime at, SdlcProperties.Deployment deployment) {
        ZonedDateTime local = at.withZoneSameInstant(ZoneId.of(deployment.getTimeZone()));
        DayOfWeek day = local.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            // Not superstition: a deployment nobody is around to watch is a deployment
            // whose failure is discovered by users.
            return Optional.of("Production deployments are blocked at weekends (" + day + ").");
        }
        LocalTime time = local.toLocalTime();
        LocalTime start = LocalTime.parse(deployment.getWindowStart());
        LocalTime end = LocalTime.parse(deployment.getWindowEnd());
        if (time.isBefore(start) || time.isAfter(end)) {
            return Optional.of("Production deployments are blocked outside %s-%s %s (now %s)."
                    .formatted(start, end, deployment.getTimeZone(), time));
        }
        return Optional.empty();
    }

    private boolean isProductionLike(String environment, SdlcProperties.Deployment deployment) {
        return deployment.getProductionEnvironments().stream().anyMatch(environment::equalsIgnoreCase);
    }

    public boolean isProduction(String environment) {
        return properties.getDeployment().getProductionEnvironments().stream()
                .anyMatch(e -> e.equalsIgnoreCase(environment));
    }
}
