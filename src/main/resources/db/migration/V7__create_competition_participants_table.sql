CREATE TABLE competition_participants (
    id             UUID        PRIMARY KEY,
    competition_id UUID        NOT NULL REFERENCES competitions(id),
    user_id        UUID        NOT NULL REFERENCES users(id),
    role           VARCHAR(50) NOT NULL,
    access_code    VARCHAR(8),
    created_at     TIMESTAMP   NOT NULL,
    CONSTRAINT uq_competition_participant UNIQUE (competition_id, user_id)
);

CREATE INDEX idx_participants_competition_id ON competition_participants(competition_id);
CREATE INDEX idx_participants_access_code ON competition_participants(access_code)
    WHERE access_code IS NOT NULL;
