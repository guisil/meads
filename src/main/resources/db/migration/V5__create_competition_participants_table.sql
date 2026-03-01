CREATE TABLE competition_participants (
    id             UUID        PRIMARY KEY,
    competition_id UUID        NOT NULL REFERENCES competitions(id),
    user_id        UUID        NOT NULL REFERENCES users(id),
    role           VARCHAR(50) NOT NULL,
    status         VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    access_code    VARCHAR(8),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_competition_participant UNIQUE (competition_id, user_id)
);

CREATE INDEX idx_participants_competition_id ON competition_participants(competition_id);
CREATE INDEX idx_participants_access_code ON competition_participants(access_code)
    WHERE access_code IS NOT NULL;
