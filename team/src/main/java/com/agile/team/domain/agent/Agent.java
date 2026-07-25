package com.agile.team.domain.agent;

import com.agile.team.domain.task.Task;
import com.agile.team.domain.task.TaskId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Agent {

    private final AgentId id;
    private final String name;
    private final AgentRole role;
    private final List<Task> assignedTasks;

    public Agent(AgentId id, String name, AgentRole role) {
        if (id == null) throw new IllegalArgumentException("Agent id must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Agent name must not be blank");
        if (role == null) throw new IllegalArgumentException("Agent role must not be null");
        this.id = id;
        this.name = name;
        this.role = role;
        this.assignedTasks = new ArrayList<>();
    }

    public void assignTask(Task task) {
        if (task == null) throw new IllegalArgumentException("Task must not be null");
        assignedTasks.add(task);
    }

    public void completeTask(TaskId taskId) {
        assignedTasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .ifPresent(Task::markDone);
    }

    public AgentId getId() { return id; }
    public String getName() { return name; }
    public AgentRole getRole() { return role; }
    public List<Task> getAssignedTasks() { return Collections.unmodifiableList(assignedTasks); }
}
