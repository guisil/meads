CREATE TABLE competitions (
    id             UUID         PRIMARY KEY,
    event_id       UUID         NOT NULL REFERENCES events(id),
    name           VARCHAR(255) NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    scoring_system VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP
);

CREATE INDEX idx_competitions_event_id ON competitions(event_id);
