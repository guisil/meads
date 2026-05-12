CREATE TABLE publications (
    id UUID PRIMARY KEY,
    division_id UUID NOT NULL REFERENCES divisions(id),
    version INT NOT NULL CHECK (version >= 1),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_by UUID NOT NULL REFERENCES users(id),
    justification TEXT,
    is_initial BOOLEAN NOT NULL,
    UNIQUE (division_id, version)
);

CREATE INDEX idx_publications_division_id ON publications(division_id);
