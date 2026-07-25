package com.agile.team.application.handler;

import com.agile.team.application.event.AgentTaskAssignedEvent;
import com.agile.team.application.event.SpecificationReadyEvent;
import com.agile.team.domain.agent.Agent;
import com.agile.team.domain.agent.AgentRepository;
import com.agile.team.domain.agent.AgentRole;
import com.agile.team.domain.conversation.AgentMessage;
import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.conversation.MessageType;
import com.agile.team.domain.port.ConfluencePort;
import com.agile.team.domain.port.JiraPort;
import com.agile.team.domain.specification.Specification;
import com.agile.team.domain.specification.SpecificationId;
import com.agile.team.domain.wave.Wave;
import com.agile.team.domain.wave.WaveRepository;
import com.agile.team.infrastructure.adapter.ai.SpringAiAgentBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PoAgentHandler {

    private static final Logger log = LoggerFactory.getLogger(PoAgentHandler.class);
    private static final String CONFLUENCE_SPACE_KEY = "EPE";

    private final ConfluencePort confluencePort;
    private final JiraPort jiraPort;
    private final WaveRepository waveRepository;
    private final AgentRepository agentRepository;
    private final ConversationRepository conversationRepository;
    private final SpringAiAgentBridge agentBridge;
    private final ApplicationEventPublisher eventPublisher;

    public PoAgentHandler(ConfluencePort confluencePort,
                          JiraPort jiraPort,
                          WaveRepository waveRepository,
                          AgentRepository agentRepository,
                          ConversationRepository conversationRepository,
                          SpringAiAgentBridge agentBridge,
                          ApplicationEventPublisher eventPublisher) {
        this.confluencePort = confluencePort;
        this.jiraPort = jiraPort;
        this.waveRepository = waveRepository;
        this.agentRepository = agentRepository;
        this.conversationRepository = conversationRepository;
        this.agentBridge = agentBridge;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener
    public void handleTaskAssigned(AgentTaskAssignedEvent event) {
        Agent poAgent = agentRepository.findByRole(AgentRole.PO).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No PO agent registered"));

        Wave wave = waveRepository.findById(event.waveId())
                .orElseThrow(() -> new IllegalStateException("Wave not found: " + event.waveId()));

        String taskDescription = event.taskDescription();
        String keyword = event.keyword();
        String jiraTextLanguage = event.jiraTextLanguage();

        log.info("[PO Agent] Starting task processing. keyword={}, language={}", keyword, jiraTextLanguage);

        // --- Step 1: Gather Confluence context via keyword search in space EPE ---
        String confluenceContext = "";
        if (keyword != null && !keyword.isBlank()) {
            log.info("[PO Agent] Searching Confluence space '{}' with keyword '{}'", CONFLUENCE_SPACE_KEY, keyword);
            Optional<String> searchResult = confluencePort.searchPages(CONFLUENCE_SPACE_KEY, keyword);
            if (searchResult.isPresent()) {
                confluenceContext = searchResult.get();
                log.info("[PO Agent] Confluence search returned {} characters of content", confluenceContext.length());
            } else {
                log.warn("[PO Agent] No Confluence pages found for keyword '{}' in space '{}'. Proceeding with taskDescription only.",
                        keyword, CONFLUENCE_SPACE_KEY);
            }
        } else {
            log.info("[PO Agent] No keyword provided — skipping Confluence search.");
        }

        // --- Step 2: Build combined context and call LLM ---
        String prompt = buildJiraTicketPrompt(taskDescription, confluenceContext, jiraTextLanguage);

        String llmResponse;
        try {
            llmResponse = agentBridge.chatWithTools(poAgent, prompt);
        } catch (Exception e) {
            log.error("[PO Agent] LLM call failed: {}", e.getMessage(), e);
            return;
        }

        if (llmResponse == null || llmResponse.isBlank()) {
            log.error("[PO Agent] LLM returned empty response. Aborting.");
            return;
        }

        // --- Step 3: Log the generated Jira ticket for validation ---
        log.info("[PO Agent] Generated Jira ticket (pending validation):\n{}", llmResponse);

        // --- Step 4: Store as specification in wave ---
        Specification specification = new Specification(
                SpecificationId.generate(),
                "PO-Generated-Ticket",
                llmResponse,
                CONFLUENCE_SPACE_KEY + ":" + keyword,
                List.of()
        );
        wave.addSpecification(specification);
        waveRepository.save(wave);

        // --- Step 5: Jira ticket creation (commented out — validation phase) ---
        // TODO: re-enable once PO Agent output is validated
        // String jiraKey = jiraPort.createIssue(
        //         "SCA",
        //         extractSummaryFromResponse(llmResponse),
        //         llmResponse,
        //         "Story"
        // );
        // log.info("[PO Agent] Created Jira issue {}", jiraKey);

        // Record conversation
        ConversationHistory history = conversationRepository.findByWaveId(event.waveId())
                .orElse(ConversationHistory.startForWave(event.waveId()));
        history.record(AgentMessage.create(
                poAgent.getId(), poAgent.getId(),
                MessageType.SPECIFICATION_READY,
                "[PO Agent] Jira ticket generated (pending validation). Language: " + jiraTextLanguage
        ));
        conversationRepository.save(history);

        // TODO: re-enable once PO Agent output is validated
        // eventPublisher.publishEvent(new SpecificationReadyEvent(
        //         event.waveId(),
        //         specification.getId()
        // ));

        log.info("[PO Agent] Pipeline stopped here — awaiting manual validation of Jira ticket output.");
    }

    private String buildJiraTicketPrompt(String taskDescription, String confluenceContext, String language) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Based on the following context, generate a well-structured Jira ticket.\n\n");
        prompt.append("=== TASK DESCRIPTION ===\n");
        prompt.append(taskDescription).append("\n\n");

        if (!confluenceContext.isEmpty()) {
            prompt.append("=== CONFLUENCE DOCUMENTATION ===\n");
            prompt.append(confluenceContext).append("\n\n");
        }

        prompt.append("=== INSTRUCTIONS ===\n");
        prompt.append("Produce the Jira ticket content ENTIRELY in ").append(language).append(".\n");
        prompt.append("Respond with valid JSON only (no markdown fences, no commentary). ");
        prompt.append("The JSON must have exactly these three fields:\n");
        prompt.append("- \"summary\": a concise ticket title\n");
        prompt.append("- \"description\": a detailed description of what needs to be done\n");
        prompt.append("- \"acceptanceCriteria\": an array of strings, each being a clear acceptance criterion ");
        prompt.append("(preferably in Given/When/Then format where applicable)\n\n");
        prompt.append("Example format:\n");
        prompt.append("{\"summary\":\"...\",\"description\":\"...\",\"acceptanceCriteria\":[\"...\",\"...\"]}\n");

        return prompt.toString();
    }
}
