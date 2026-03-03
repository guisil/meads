CREATE TABLE divisions (
    id             UUID         PRIMARY KEY,
    competition_id UUID         NOT NULL REFERENCES competitions(id),
    name           VARCHAR(255) NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    scoring_system VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_divisions_competition_id ON divisions(competition_id);
