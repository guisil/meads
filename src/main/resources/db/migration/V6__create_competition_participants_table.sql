CREATE TABLE competition_participants (
    id                   UUID        PRIMARY KEY,
    competition_id       UUID        NOT NULL REFERENCES competitions(id),
    event_participant_id UUID        NOT NULL REFERENCES event_participants(id),
    role                 VARCHAR(50) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_competition_participant_role UNIQUE (competition_id, event_participant_id, role)
);

CREATE INDEX idx_competition_participants_competition_id ON competition_participants(competition_id);
