package com.agile.team.infrastructure.persistence.mapper;

import com.agile.team.domain.agent.AgentId;
import com.agile.team.domain.conversation.*;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.infrastructure.persistence.entity.AgentMessageJpaEntity;
import com.agile.team.infrastructure.persistence.entity.ConversationJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

    public ConversationJpaEntity toEntity(ConversationHistory history) {
        ConversationJpaEntity entity = new ConversationJpaEntity(
                history.getId(),
                history.getWaveId().value()
        );
        history.getMessages().forEach(msg -> {
            AgentMessageJpaEntity msgEntity = new AgentMessageJpaEntity(
                    msg.id(),
                    msg.fromAgent().value(),
                    msg.toAgent().value(),
                    msg.type().name(),
                    msg.payload(),
                    msg.timestamp()
            );
            msgEntity.setConversation(entity);
            entity.getMessages().add(msgEntity);
        });
        return entity;
    }

    public ConversationHistory toDomain(ConversationJpaEntity entity) {
        ConversationHistory history = new ConversationHistory(
                entity.getId(),
                WaveId.of(entity.getWaveId())
        );
        entity.getMessages().forEach(msgEntity -> {
            AgentMessage msg = new AgentMessage(
                    msgEntity.getId(),
                    AgentId.of(msgEntity.getFromAgentId()),
                    AgentId.of(msgEntity.getToAgentId()),
                    MessageType.valueOf(msgEntity.getMessageType()),
                    msgEntity.getPayload(),
                    msgEntity.getTimestamp()
            );
            history.record(msg);
        });
        return history;
    }
}
