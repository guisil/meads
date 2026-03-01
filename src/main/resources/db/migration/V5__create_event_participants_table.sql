CREATE TABLE event_participants (
    id          UUID        PRIMARY KEY,
    event_id    UUID        NOT NULL REFERENCES mead_events(id),
    user_id     UUID        NOT NULL REFERENCES users(id),
    status      VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    access_code VARCHAR(8),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_event_participant UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_participants_event_id ON event_participants(event_id);
CREATE INDEX idx_event_participants_access_code ON event_participants(access_code)
    WHERE access_code IS NOT NULL;
