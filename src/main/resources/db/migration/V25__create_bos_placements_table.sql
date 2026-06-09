CREATE TABLE bos_placements (
    id           UUID PRIMARY KEY,
    division_id  UUID NOT NULL REFERENCES divisions(id),
    entry_id     UUID NOT NULL REFERENCES entries(id),
    place        INTEGER NOT NULL,
    awarded_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    awarded_by   UUID NOT NULL REFERENCES users(id),
    updated_at   TIMESTAMP WITH TIME ZONE,
    UNIQUE (division_id, place),
    UNIQUE (division_id, entry_id)
);
