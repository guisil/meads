CREATE TABLE entries (
    id                      UUID            PRIMARY KEY,
    division_id             UUID            NOT NULL REFERENCES divisions(id),
    user_id                 UUID            NOT NULL REFERENCES users(id),
    entry_number            INT             NOT NULL,
    entry_code              VARCHAR(6)      NOT NULL,
    mead_name               VARCHAR(255)    NOT NULL,
    initial_category_id     UUID            NOT NULL REFERENCES division_categories(id),
    final_category_id       UUID            REFERENCES division_categories(id),
    sweetness               VARCHAR(50)     NOT NULL,
    strength                VARCHAR(50)     NOT NULL,
    abv                     DECIMAL(4,1)    NOT NULL,
    carbonation             VARCHAR(50)     NOT NULL,
    honey_varieties         TEXT            NOT NULL,
    other_ingredients       TEXT,
    wood_aged               BOOLEAN         NOT NULL,
    wood_ageing_details     TEXT,
    additional_information  TEXT,
    status                  VARCHAR(50)     NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_entries_division_number ON entries(division_id, entry_number);
CREATE UNIQUE INDEX uq_entries_division_code ON entries(division_id, entry_code);
CREATE INDEX idx_entries_division_user ON entries(division_id, user_id);
