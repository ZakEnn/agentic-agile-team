-- ============================================================
-- V1.4.0 : Seed the two agents that complete the SDLC roster
--
-- The original seed covered PO, DEV, REVIEWER and QA. Architecture/design and
-- deployment/ops had no agent at all, which is why a "spec to deployed code"
-- pipeline could not actually reach either end.
-- ============================================================

INSERT INTO agents (id, name, role) VALUES
    ('00000000-0000-0000-0000-000000000005', 'Architect Agent', 'ARCHITECT'),
    ('00000000-0000-0000-0000-000000000006', 'Release Agent', 'RELEASE');
