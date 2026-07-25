package com.agile.team.infrastructure.persistence;

import com.agile.team.domain.conversation.ConversationHistory;
import com.agile.team.domain.conversation.ConversationRepository;
import com.agile.team.domain.wave.WaveId;
import com.agile.team.infrastructure.persistence.entity.ConversationJpaEntity;
import com.agile.team.infrastructure.persistence.mapper.ConversationMapper;
import com.agile.team.infrastructure.persistence.repository.ConversationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ConversationRepositoryAdapter implements ConversationRepository {

    private final ConversationJpaRepository jpaRepository;
    private final ConversationMapper mapper;

    public ConversationRepositoryAdapter(ConversationJpaRepository jpaRepository, ConversationMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ConversationHistory save(ConversationHistory history) {
        ConversationJpaEntity entity = mapper.toEntity(history);
        jpaRepository.save(entity);
        return history;
    }

    @Override
    public Optional<ConversationHistory> findByWaveId(WaveId waveId) {
        return jpaRepository.findByWaveId(waveId.value()).map(mapper::toDomain);
    }
}
