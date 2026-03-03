-- Phase 1: Rename competitions → divisions (frees up "competitions" name)
ALTER TABLE competitions RENAME TO divisions;
ALTER TABLE divisions RENAME COLUMN event_id TO competition_id;
ALTER INDEX idx_competitions_event_id RENAME TO idx_divisions_competition_id;

-- Phase 2: Rename mead_events → competitions
ALTER TABLE mead_events RENAME TO competitions;

-- Phase 3: Rename event_participants → participants
ALTER TABLE event_participants RENAME TO participants;
ALTER TABLE participants RENAME COLUMN event_id TO competition_id;
ALTER INDEX idx_event_participants_event_id RENAME TO idx_participants_competition_id;
ALTER INDEX idx_event_participants_access_code RENAME TO idx_participants_access_code;
ALTER TABLE participants RENAME CONSTRAINT uq_event_participant TO uq_participant;

-- Phase 4: Restructure competition_participants → participant_roles
-- Roles are now competition-scoped (via participant → competition), so drop the
-- division-level competition_id column
ALTER TABLE competition_participants
    DROP CONSTRAINT uq_competition_participant_role;
DROP INDEX idx_competition_participants_competition_id;
ALTER TABLE competition_participants DROP COLUMN competition_id;
ALTER TABLE competition_participants RENAME TO participant_roles;
ALTER TABLE participant_roles RENAME COLUMN event_participant_id TO participant_id;
ALTER TABLE participant_roles
    ADD CONSTRAINT uq_participant_role UNIQUE (participant_id, role);
CREATE INDEX idx_participant_roles_participant_id ON participant_roles(participant_id);

-- Update COMPETITION_ADMIN → ADMIN
UPDATE participant_roles SET role = 'ADMIN' WHERE role = 'COMPETITION_ADMIN';

-- Phase 5: Rename competition_categories → division_categories
ALTER TABLE competition_categories RENAME TO division_categories;
ALTER TABLE division_categories RENAME COLUMN competition_id TO division_id;
ALTER TABLE division_categories
    DROP CONSTRAINT competition_categories_competition_id_code_key;
ALTER TABLE division_categories
    ADD CONSTRAINT uq_division_categories_division_code UNIQUE (division_id, code);
