CREATE TABLE judgings (
    id           UUID PRIMARY KEY,
    division_id  UUID NOT NULL UNIQUE REFERENCES divisions(id),
    phase        VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE
);
