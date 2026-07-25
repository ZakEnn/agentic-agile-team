package com.agile.team.infrastructure.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.agile.team.infrastructure.persistence.repository")
public class JpaConfiguration {

    /**
     * Ensures JPA EntityManagerFactory waits for Flyway migrations to complete.
     * <p>
     * Guarded on the presence of a {@code flyway} bean: test slices run against H2
     * with {@code spring.flyway.enabled=false} (see DECISIONS.md D-005), and an
     * unconditional {@code dependsOn("flyway")} would fail those contexts with
     * NoSuchBeanDefinitionException.
     */
    @Bean
    public static BeanFactoryPostProcessor flywayDependencyPostProcessor() {
        return beanFactory -> {
            if (!beanFactory.containsBeanDefinition("flyway")) {
                return;
            }
            String[] emfBeanNames = beanFactory.getBeanNamesForType(
                    jakarta.persistence.EntityManagerFactory.class, true, false);
            for (String emfBeanName : emfBeanNames) {
                BeanDefinition bd = beanFactory.getBeanDefinition(emfBeanName);
                String[] existing = bd.getDependsOn();
                String[] updated;
                if (existing == null) {
                    updated = new String[]{"flyway"};
                } else {
                    updated = new String[existing.length + 1];
                    System.arraycopy(existing, 0, updated, 0, existing.length);
                    updated[existing.length] = "flyway";
                }
                bd.setDependsOn(updated);
            }
        };
    }
}
