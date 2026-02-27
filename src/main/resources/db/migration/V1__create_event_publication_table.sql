-- Spring Modulith event publication table
-- Required for tracking published application events

CREATE TABLE event_publication (
    id UUID NOT NULL,
    listener_id VARCHAR(512),
    event_type VARCHAR(512),
    serialized_event TEXT,
    publication_date TIMESTAMP WITH TIME ZONE,
    completion_date TIMESTAMP WITH TIME ZONE,
    completion_attempts INTEGER,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50),
    PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_by_completion_date
    ON event_publication (completion_date);
