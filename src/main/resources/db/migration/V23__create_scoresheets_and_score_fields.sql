CREATE TABLE scoresheets (
    id                          UUID PRIMARY KEY,
    table_id                    UUID NOT NULL REFERENCES judging_tables(id),
    entry_id                    UUID NOT NULL UNIQUE REFERENCES entries(id),
    filled_by_judge_user_id     UUID REFERENCES users(id),
    status                      VARCHAR(20) NOT NULL,
    total_score                 INTEGER,
    overall_comments            VARCHAR(2000),
    advanced_to_medal_round     BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at                TIMESTAMP WITH TIME ZONE,
    comment_language            VARCHAR(5),
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_scoresheets_table_id ON scoresheets(table_id);

CREATE TABLE score_fields (
    id              UUID PRIMARY KEY,
    scoresheet_id   UUID NOT NULL REFERENCES scoresheets(id) ON DELETE CASCADE,
    field_name      VARCHAR(50) NOT NULL,
    max_value       INTEGER NOT NULL,
    value           INTEGER,
    comment         VARCHAR(2000),
    UNIQUE (scoresheet_id, field_name)
);
