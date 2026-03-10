CREATE TABLE competition_documents (
    id UUID PRIMARY KEY,
    competition_id UUID NOT NULL REFERENCES competitions(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    data BYTEA,
    content_type VARCHAR(100),
    url TEXT,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (competition_id, name)
);

CREATE INDEX idx_competition_documents_competition_id ON competition_documents(competition_id);
