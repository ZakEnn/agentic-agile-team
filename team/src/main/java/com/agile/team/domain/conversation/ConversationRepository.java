package com.agile.team.domain.conversation;

import com.agile.team.domain.wave.WaveId;

import java.util.Optional;

public interface ConversationRepository {

    ConversationHistory save(ConversationHistory history);

    Optional<ConversationHistory> findByWaveId(WaveId waveId);
}
