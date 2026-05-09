CREATE TABLE medal_awards (
    id                  UUID PRIMARY KEY,
    entry_id            UUID NOT NULL UNIQUE REFERENCES entries(id),
    division_id         UUID NOT NULL REFERENCES divisions(id),
    final_category_id   UUID NOT NULL REFERENCES division_categories(id),
    medal               VARCHAR(10),
    awarded_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    awarded_by          UUID NOT NULL REFERENCES users(id),
    updated_at          TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_medal_awards_division_id        ON medal_awards(division_id);
CREATE INDEX idx_medal_awards_final_category_id  ON medal_awards(final_category_id);
