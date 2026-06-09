-- Physical judging stations within a division (e.g. "Table 1", "Table 2").
-- A JudgingRound happens AT one physical table; over time the same physical
-- table can host several rounds, but only one round can be active at a time
-- (service-level validation in JudgingService).
CREATE TABLE physical_tables (
    id           UUID PRIMARY KEY,
    division_id  UUID NOT NULL REFERENCES divisions(id) ON DELETE CASCADE,
    label        VARCHAR(50) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE,
    UNIQUE (division_id, label)
);
CREATE INDEX idx_physical_tables_division_id ON physical_tables(division_id);

-- Each round (scoring or medal) is hosted by a physical table. Nullable in
-- DB; service enforces not-null on createRound so existing seeded/test data
-- without a physical table doesn't break the migration.
ALTER TABLE judging_rounds
    ADD COLUMN physical_table_id UUID REFERENCES physical_tables(id);
