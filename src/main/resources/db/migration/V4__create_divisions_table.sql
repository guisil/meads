CREATE TABLE divisions (
    id             UUID         PRIMARY KEY,
    competition_id UUID         NOT NULL REFERENCES competitions(id),
    name           VARCHAR(255) NOT NULL,
    short_name     VARCHAR(100) NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    scoring_system VARCHAR(50)  NOT NULL,
    max_entries_per_subcategory    INT,
    max_entries_per_main_category  INT,
    max_entries_total              INT,
    entry_prefix   VARCHAR(5),
    meadery_name_required           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE,
    UNIQUE (competition_id, short_name)
);

CREATE INDEX idx_divisions_competition_id ON divisions(competition_id);
