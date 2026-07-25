package com.agile.team.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Manual Flyway configuration.
 * Spring Boot 4.1 no longer provides Flyway auto-configuration in its core modules.
 * This ensures migrations run before JPA/Hibernate schema validation.
 * <p>
 * The {@link ConditionalOnProperty} guard was added in M0: without it this bean
 * ran migrations unconditionally, so {@code spring.flyway.enabled=false} silently
 * had no effect. That is a trap for anyone configuring a Flyway-less context, and
 * it made the property a lie.
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfiguration {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}
