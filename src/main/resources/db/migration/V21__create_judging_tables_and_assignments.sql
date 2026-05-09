CREATE TABLE judging_tables (
    id                    UUID PRIMARY KEY,
    judging_id            UUID NOT NULL REFERENCES judgings(id),
    name                  VARCHAR(120) NOT NULL,
    division_category_id  UUID NOT NULL REFERENCES division_categories(id),
    scheduled_date        DATE,
    status                VARCHAR(20) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_judging_tables_judging_id            ON judging_tables(judging_id);
CREATE INDEX idx_judging_tables_division_category_id  ON judging_tables(division_category_id);

CREATE TABLE judge_assignments (
    id                UUID PRIMARY KEY,
    judging_table_id  UUID NOT NULL REFERENCES judging_tables(id) ON DELETE CASCADE,
    judge_user_id     UUID NOT NULL REFERENCES users(id),
    assigned_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (judging_table_id, judge_user_id)
);
CREATE INDEX idx_judge_assignments_judge_user_id ON judge_assignments(judge_user_id);
