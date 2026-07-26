package com.agile.team.infrastructure.persistence.mapper;

import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.specification.SpecificationStatus;
import com.agile.team.domain.task.Task;
import com.agile.team.domain.task.TaskId;
import com.agile.team.domain.task.TaskStatus;
import com.agile.team.domain.wave.*;
import com.agile.team.infrastructure.persistence.entity.SpecificationJpaEntity;
import com.agile.team.infrastructure.persistence.entity.StateTransitionEmbeddable;
import com.agile.team.infrastructure.persistence.entity.TaskJpaEntity;
import com.agile.team.infrastructure.persistence.entity.WaveJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class WaveMapper {

    private static final Logger log = LoggerFactory.getLogger(WaveMapper.class);

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    /**
     * Jackson 3 ({@code tools.jackson}), not Jackson 2.
     * <p>
     * Spring Boot 4.1 ships Jackson 3 as its JSON binding, so no
     * {@code com.fasterxml.jackson.databind.ObjectMapper} bean exists to inject even
     * though the Jackson 2 classes are still on the classpath transitively.
     */
    private final JsonMapper jsonMapper;

    public WaveMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public WaveJpaEntity toEntity(Wave wave) {
        WaveJpaEntity entity = new WaveJpaEntity(
                wave.getId().value(),
                wave.getName(),
                wave.getStatus().name(),
                wave.getCreatedAt()
        );

        WaveContext context = wave.getContext();
        if (context != null) {
            entity.setConfluenceSpaceKey(context.confluenceSpaceKey());
            entity.setGitLabProject(context.gitLabProject());
            entity.setJiraProjectKey(context.jiraProjectKey());
            entity.setLanguage(context.language());
        }
        entity.setTokensUsed(wave.getTokensUsed());

        entity.setTransitions(
                wave.getTransitions().stream()
                        .map(t -> new StateTransitionEmbeddable(
                                t.fromStatus().name(),
                                t.toStatus().name(),
                                t.authorizedBy(),
                                t.timestamp()
                        ))
                        .collect(Collectors.toList())
        );
        entity.setSpecifications(
                wave.getSpecifications().stream()
                        .map(s -> {
                            SpecificationJpaEntity se = new SpecificationJpaEntity(
                                    s.getId().value(),
                                    s.getTitle(),
                                    s.getContent(),
                                    s.getSourcePageId(),
                                    writeList(s.getAcceptanceCriteria()),
                                    s.getCreatedAt()
                            );
                            se.setOutOfScope(writeList(s.getOutOfScope()));
                            se.setStatus(s.getStatus().name());
                            se.setDecidedBy(s.getDecidedBy());
                            se.setDecisionReason(s.getDecisionReason());
                            se.setDecidedAt(s.getDecidedAt());
                            se.setWave(entity);
                            return se;
                        })
                        .collect(Collectors.toList())
        );
        entity.setTasks(
                wave.getTasks().stream()
                        .map(t -> {
                            TaskJpaEntity te = new TaskJpaEntity(
                                    t.getId().value(),
                                    t.getTitle(),
                                    t.getDescription(),
                                    t.getStatus().name(),
                                    t.getJiraKey(),
                                    t.getCreatedAt()
                            );
                            te.setWave(entity);
                            return te;
                        })
                        .collect(Collectors.toList())
        );
        return entity;
    }

    public Wave toDomain(WaveJpaEntity entity) {
        List<Specification> specifications = entity.getSpecifications().stream()
                .map(se -> new Specification(
                        SpecificationId.of(se.getId()),
                        se.getTitle(),
                        se.getContent(),
                        se.getSourcePageId(),
                        readList(se.getAcceptanceCriteria()),
                        readList(se.getOutOfScope()),
                        se.getCreatedAt(),
                        se.getStatus() != null
                                ? SpecificationStatus.valueOf(se.getStatus())
                                : SpecificationStatus.DRAFT,
                        se.getDecidedBy(),
                        se.getDecisionReason(),
                        se.getDecidedAt()
                ))
                .collect(Collectors.toList());

        List<Task> tasks = entity.getTasks().stream()
                .map(te -> new Task(
                        TaskId.of(te.getId()),
                        te.getTitle(),
                        te.getDescription(),
                        TaskStatus.valueOf(te.getStatus()),
                        te.getJiraKey(),
                        te.getCreatedAt()
                ))
                .collect(Collectors.toList());

        List<StateTransition> transitions = entity.getTransitions().stream()
                .map(t -> StateTransition.create(
                        WaveStatus.valueOf(t.getFromStatus()),
                        WaveStatus.valueOf(t.getToStatus()),
                        t.getAuthorizedBy()
                ))
                .collect(Collectors.toList());

        WaveContext context = new WaveContext(
                entity.getConfluenceSpaceKey(),
                entity.getGitLabProject(),
                entity.getJiraProjectKey(),
                entity.getLanguage()
        );

        Wave wave = new Wave(
                WaveId.of(entity.getId()),
                entity.getName(),
                WaveStatus.valueOf(entity.getStatus()),
                specifications,
                tasks,
                transitions,
                entity.getCreatedAt(),
                context
        );
        wave.restoreTokensUsed(entity.getTokensUsed());
        return wave;
    }

    /**
     * Lists are stored as JSON rather than newline-joined.
     * <p>
     * The previous {@code String.join("\n", ...)} / {@code split("\n")} round trip
     * silently split any acceptance criterion that contained a newline into two
     * criteria — and multi-line Given/When/Then criteria are exactly what the Spec
     * Agent is asked to produce.
     */
    private String writeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise list to JSON", e);
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            // Tolerate rows written before V1.3.0, which used newline-joined text.
            log.debug("Value is not JSON, falling back to newline split: {}", e.getMessage());
            return List.of(json.split("\n"));
        }
    }
}
