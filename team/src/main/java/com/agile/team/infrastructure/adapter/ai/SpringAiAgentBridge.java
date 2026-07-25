package com.agile.team.infrastructure.adapter.ai;

import com.agile.team.application.tool.ConfluenceTools;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.port.SkillPort;
import com.agile.team.domain.port.SkillPort.SkillDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiAgentBridge {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAgentBridge.class);

    private final ChatClient chatClientWithTools;
    private final ChatClient chatClientPlain;
    private final SkillPort skillPort;

    public SpringAiAgentBridge(ChatClient.Builder chatClientBuilder, ChatModel chatModel,
                               SkillPort skillPort, ConfluenceTools confluenceTools) {
        this.chatClientWithTools = chatClientBuilder
                .defaultTools(confluenceTools)
                .build();
        // Plain client without MCP tools — for pure text generation
        this.chatClientPlain = ChatClient.create(chatModel);
        this.skillPort = skillPort;
    }

    /**
     * Sends a prompt to the AI model with the agent's role context and returns the response.
     * Uses a plain ChatClient WITHOUT MCP tools — for pure text generation.
     * Skills are resolved via progressive disclosure.
     */
    public String chat(Agent agent, String prompt) {
        // Use chatWithTools for ALL calls because claude-sonnet-5 uses extended thinking
        // (which produces empty text response) when tools are not present in the request.
        // Having tools in the request suppresses thinking and ensures text output.
        return chatWithTools(agent, prompt);
    }

    /**
     * Chat with MCP tools available (Confluence, Jira, GitLab, SonarQube).
     * Use this when the AI needs to call external services via tool use.
     */
    public String chatWithTools(Agent agent, String prompt) {
        String systemPrompt = buildSystemPrompt(agent);

        log.info("AI chat (with tools) request for agent role={}, prompt length={}", agent.getRole(), prompt.length());

        String response = chatClientWithTools.prompt()
                .system(systemPrompt)
                .user(prompt)
                .call()
                .content();

        log.info("AI chat (with tools) response for agent role={}: response is null={}, length={}",
                agent.getRole(), response == null, response != null ? response.length() : -1);

        return response != null ? response : "";
    }

    private String resolveSkillContext(Agent agent) {
        List<SkillDescriptor> skills = skillPort.resolveSkills(agent.getRole());
        if (skills.isEmpty()) {
            return "";
        }

        // Progressive disclosure: load full content for all applicable skills
        // In a more advanced implementation, an LLM pre-filter could select only relevant skills
        // based on the user prompt. For now, we load all applicable skills since the count is small (≤3).
        StringBuilder context = new StringBuilder();
        context.append("## Applicable Skills\n");
        context.append("Follow the procedural knowledge defined in these skills:\n\n");

        for (SkillDescriptor descriptor : skills) {
            String content = skillPort.loadSkillContent(descriptor.name());
            if (!content.isEmpty()) {
                context.append("### Skill: ").append(descriptor.name());
                if (descriptor.governance()) {
                    context.append(" [GOVERNANCE — compliance mandatory]");
                }
                context.append("\n");
                context.append(content).append("\n\n");
            } else {
                // Fallback: include just the description if content can't be loaded
                context.append("### Skill: ").append(descriptor.name()).append("\n");
                context.append(descriptor.description()).append("\n\n");
                log.warn("Could not load full content for skill: {}", descriptor.name());
            }
        }

        return context.toString();
    }

    private String buildSystemPrompt(Agent agent) {
        return switch (agent.getRole()) {
            case PO -> "You are a Product Owner agent responsible for creating well-structured Jira tickets. " +
                       "You analyze Confluence documentation and task descriptions to produce clear, actionable Jira issues. " +
                       "You MUST respond with valid JSON only — no markdown, no commentary, no code fences. " +
                       "The JSON must have exactly three fields: \"summary\", \"description\", and \"acceptanceCriteria\". " +
                       "The acceptanceCriteria field must be a JSON array of strings, preferably in Given/When/Then format.";
            case DEV -> "You are a Developer agent responsible for implementing features. " +
                        "You create branches, write code, and submit merge requests.";
            case REVIEWER -> "You are a Code Reviewer agent responsible for ensuring code quality. " +
                             "You review merge requests and assess adherence to coding standards.";
            case QA -> "You are a QA agent responsible for validating implementations. " +
                       "You verify that acceptance criteria are met and perform final validation. " +
                       "Assess the overall quality and provide a clear PASSED or FAILED verdict.";
        };
    }
}
