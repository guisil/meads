CREATE TABLE competitions (
    id             UUID         PRIMARY KEY,
    event_id       UUID         NOT NULL REFERENCES mead_events(id),
    name           VARCHAR(255) NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    scoring_system VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_competitions_event_id ON competitions(event_id);
