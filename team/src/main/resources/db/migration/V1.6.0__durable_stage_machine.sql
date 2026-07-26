-- ============================================================
-- V1.6.0 : Durable, resumable stage machine
--
-- Replaces in-JVM ApplicationEvent + @Async handoffs, which SDLC_AGENT_PLAN.md
-- identifies as the actual orchestration defect: no durability (a restart loses
-- all in-flight work), no retry, no dead-letter, no idempotency, and no way for a
-- second instance to see another's events.
--
-- Deployment context matters here. The target platform is Cloud Foundry with
-- multiple instances sharing one datasource and no Redis, so the database is the
-- only coordination primitive available. A queue table claimed with
-- FOR UPDATE SKIP LOCKED gives multi-instance safety with no new infrastructure.
-- ============================================================

CREATE TABLE stage_run
(
    id              CHAR(36)     NOT NULL,
    wave_id         CHAR(36)     NOT NULL,
    stage           VARCHAR(20)  NOT NULL,
    status          VARCHAR(24)  NOT NULL,
    attempt         INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 3,

    -- Unique per (wave, stage). Enforcing it in the schema is what makes retries
    -- and concurrent enqueues idempotent rather than duplicating work.
    idempotency_key VARCHAR(255) NOT NULL,

    input_artifact  TEXT         NULL,
    output_artifact TEXT         NULL,
    error_message   TEXT         NULL,
    tokens_used     BIGINT       NOT NULL DEFAULT 0,

    -- Backoff: a run is invisible to the claim query until this moment.
    available_at    DATETIME(6)  NOT NULL,

    -- CF_INSTANCE_GUID of the claiming instance. GUID rather than index because an
    -- index can be reused after a restage, which would make ownership ambiguous.
    claimed_by      VARCHAR(255) NULL,
    claimed_at      DATETIME(6)  NULL,

    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_stage_run_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_stage_run_wave FOREIGN KEY (wave_id) REFERENCES waves (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Drives the claim query: find PENDING rows that are due, oldest first.
CREATE INDEX idx_stage_run_claimable ON stage_run (status, available_at);
CREATE INDEX idx_stage_run_wave ON stage_run (wave_id);

-- ----------------------------------------------------------------
-- Per-wave token accounting.
--
-- SDLC_AGENT_PLAN.md §2.4 cites roughly 15x token usage for multi-agent systems,
-- and the mechanism by which that becomes a large bill is an unbounded retry loop.
-- Tracking spend on the wave lets the machine stop rather than discover the cost
-- afterwards on an invoice.
-- ----------------------------------------------------------------

ALTER TABLE waves ADD COLUMN tokens_used BIGINT NOT NULL DEFAULT 0;
