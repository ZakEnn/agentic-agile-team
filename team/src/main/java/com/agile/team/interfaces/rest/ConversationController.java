package com.agile.team.interfaces.rest;

import com.agile.team.domain.conversation.AgentMessage;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.wave.WaveId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Makes a wave's audit trail readable.
 * <p>
 * {@code ConversationHistory} was previously write-only: handlers recorded messages,
 * but nothing ever read them back, no endpoint exposed them, and no agent used them
 * as context. The traceability the design claimed was unobservable in practice.
 */
@RestController
@RequestMapping("/api/waves/{waveId}/conversation")
public class ConversationController {

    private final ConversationRepository conversationRepository;

    public ConversationController(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @GetMapping
    public ResponseEntity<List<MessageView>> get(@PathVariable UUID waveId) {
        return conversationRepository.findByWaveId(WaveId.of(waveId))
                .map(history -> ResponseEntity.ok(
                        history.getMessages().stream().map(MessageView::from).toList()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record MessageView(String from, String type, String payload, Instant timestamp) {
        static MessageView from(AgentMessage message) {
            return new MessageView(
                    message.fromAgent().value().toString(),
                    message.type().name(),
                    message.payload(),
                    message.timestamp());
        }
    }
}
