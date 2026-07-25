-- ============================================================
-- V1 : Initial schema
-- Entities: agents, waves, conversations, agent_messages,
--           wave_transitions (ElementCollection)
-- ============================================================

CREATE TABLE agents
(
    id   CHAR(36)     NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------

CREATE TABLE waves
(
    id         CHAR(36)     NOT NULL,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------

CREATE TABLE conversations
(
    id      CHAR(36) NOT NULL,
    wave_id CHAR(36) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------

CREATE TABLE agent_messages
(
    id              CHAR(36)     NOT NULL,
    from_agent_id   CHAR(36)     NOT NULL,
    to_agent_id     CHAR(36)     NOT NULL,
    message_type    VARCHAR(255) NOT NULL,
    payload         TEXT         NOT NULL,
    timestamp       DATETIME(6)  NOT NULL,
    conversation_id CHAR(36)     NULL,
    wave_id         CHAR(36)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_am_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_am_wave         FOREIGN KEY (wave_id)         REFERENCES waves (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------
-- @ElementCollection of StateTransitionEmbeddable on WaveJpaEntity
-- ----------------------------------------------------------------

CREATE TABLE wave_transitions
(
    wave_id         CHAR(36)     NOT NULL,
    from_status     VARCHAR(255) NULL,
    to_status       VARCHAR(255) NULL,
    authorized_by   VARCHAR(255) NULL,
    transitioned_at DATETIME(6)  NULL,
    CONSTRAINT fk_wt_wave FOREIGN KEY (wave_id) REFERENCES waves (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
