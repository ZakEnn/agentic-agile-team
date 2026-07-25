-- ============================================================
-- V1.2.0 : Add specifications and tasks tables
-- ============================================================

CREATE TABLE specifications
(
    id                  CHAR(36)     NOT NULL,
    wave_id             CHAR(36)     NOT NULL,
    title               VARCHAR(500) NOT NULL,
    content             TEXT         NOT NULL,
    source_page_id      VARCHAR(255) NULL,
    acceptance_criteria TEXT         NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_spec_wave FOREIGN KEY (wave_id) REFERENCES waves (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ----------------------------------------------------------------

CREATE TABLE tasks
(
    id          CHAR(36)     NOT NULL,
    wave_id     CHAR(36)     NOT NULL,
    title       VARCHAR(500) NOT NULL,
    description TEXT         NULL,
    status      VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    jira_key    VARCHAR(50)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_wave FOREIGN KEY (wave_id) REFERENCES waves (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
