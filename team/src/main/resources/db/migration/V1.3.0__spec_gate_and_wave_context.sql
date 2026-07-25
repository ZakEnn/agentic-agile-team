-- ============================================================
-- V1.3.0 : SPEC_APPROVAL gate state + per-wave targeting context
--
-- Two changes, both from M1 of SDLC_AGENT_PLAN.md:
--
-- 1. Specifications gain a lifecycle. Previously a spec was write-once text with
--    no record of whether a human had agreed to it, which is why the pipeline had
--    to be paused by commenting out code instead of by a gate.
--
-- 2. Waves gain targeting context. Confluence space, GitLab project, Jira project
--    and language were compile-time constants ("EPE", "epe-rating-ftth-passive",
--    "SCA", "French") scattered across handlers, making the system a bespoke
--    script for one project rather than a platform.
--
-- Statements are issued one per ALTER so they run on both MySQL and H2 (MySQL
-- mode), which is what lets the test tier execute the real migration chain.
-- ============================================================

ALTER TABLE specifications ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE specifications ADD COLUMN out_of_scope TEXT NULL;
ALTER TABLE specifications ADD COLUMN decided_by VARCHAR(255) NULL;
ALTER TABLE specifications ADD COLUMN decision_reason TEXT NULL;
ALTER TABLE specifications ADD COLUMN decided_at DATETIME(6) NULL;

-- ----------------------------------------------------------------

ALTER TABLE waves ADD COLUMN confluence_space_key VARCHAR(100) NULL;
ALTER TABLE waves ADD COLUMN gitlab_project VARCHAR(500) NULL;
ALTER TABLE waves ADD COLUMN jira_project_key VARCHAR(50) NULL;
ALTER TABLE waves ADD COLUMN language VARCHAR(50) NULL;
