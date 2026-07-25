package com.agile.team.interfaces.rest;

import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.agent.AgentRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRepository agentRepository;

    public AgentController(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listAgents() {
        List<Map<String, String>> agents = agentRepository.findAll().stream()
                .map(agent -> Map.of(
                        "id", agent.getId().value().toString(),
                        "name", agent.getName(),
                        "role", agent.getRole().name(),
                        "taskCount", String.valueOf(agent.getAssignedTasks().size())
                ))
                .toList();
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<Map<String, String>>> getAgentsByRole(@PathVariable String role) {
        AgentRole agentRole = AgentRole.valueOf(role.toUpperCase());
        List<Map<String, String>> agents = agentRepository.findByRole(agentRole).stream()
                .map(agent -> Map.of(
                        "id", agent.getId().value().toString(),
                        "name", agent.getName(),
                        "role", agent.getRole().name()
                ))
                .toList();
        return ResponseEntity.ok(agents);
    }
}
