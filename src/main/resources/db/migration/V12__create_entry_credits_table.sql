CREATE TABLE entry_credits (
    id                UUID            PRIMARY KEY,
    division_id       UUID            NOT NULL REFERENCES divisions(id),
    user_id           UUID            NOT NULL REFERENCES users(id),
    amount            INT             NOT NULL,
    source_type       VARCHAR(50)     NOT NULL,
    source_reference  VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_entry_credits_division_user ON entry_credits(division_id, user_id);
