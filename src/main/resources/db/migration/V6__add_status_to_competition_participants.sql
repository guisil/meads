ALTER TABLE competition_participants
    ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';
