package com.agile.team.infrastructure.persistence.mapper;

import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.task.Task;
import com.agile.team.domain.task.TaskId;
import com.agile.team.domain.task.TaskStatus;
import com.agile.team.domain.wave.*;
import com.agile.team.infrastructure.persistence.entity.SpecificationJpaEntity;
import com.agile.team.infrastructure.persistence.entity.StateTransitionEmbeddable;
import com.agile.team.infrastructure.persistence.entity.TaskJpaEntity;
import com.agile.team.infrastructure.persistence.entity.WaveJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class WaveMapper {

    public WaveJpaEntity toEntity(Wave wave) {
        WaveJpaEntity entity = new WaveJpaEntity(
                wave.getId().value(),
                wave.getName(),
                wave.getStatus().name(),
                wave.getCreatedAt()
        );
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
                                    String.join("\n", s.getAcceptanceCriteria()),
                                    s.getCreatedAt()
                            );
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
                        se.getAcceptanceCriteria() != null && !se.getAcceptanceCriteria().isBlank()
                                ? Arrays.asList(se.getAcceptanceCriteria().split("\n"))
                                : List.of(),
                        se.getCreatedAt()
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

        return new Wave(
                WaveId.of(entity.getId()),
                entity.getName(),
                WaveStatus.valueOf(entity.getStatus()),
                specifications,
                tasks,
                transitions,
                entity.getCreatedAt()
        );
    }
}
