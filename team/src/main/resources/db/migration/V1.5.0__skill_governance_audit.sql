-- ============================================================
-- V1.5.0 : Persist the governance skill audit trail
--
-- SkillGovernanceUseCase recorded skill usage into an in-memory
-- Collections.synchronizedList(new ArrayList<>()), so the "traceability between
-- the skill version used and the review outcome" it documents was lost on every
-- restart and invisible to any other instance.
--
-- An audit trail that does not survive a restart is not an audit trail.
-- ============================================================

CREATE TABLE skill_governance_audit
(
    id         CHAR(36)     NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    wave_id    CHAR(36)     NULL,
    agent_id   VARCHAR(255) NULL,
    used_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_skill_audit_wave ON skill_governance_audit (wave_id);
CREATE INDEX idx_skill_audit_skill ON skill_governance_audit (skill_name);
