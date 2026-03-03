CREATE TABLE participant_roles (
    id             UUID        PRIMARY KEY,
    participant_id UUID        NOT NULL REFERENCES participants(id),
    role           VARCHAR(50) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_participant_role UNIQUE (participant_id, role)
);

CREATE INDEX idx_participant_roles_participant_id ON participant_roles(participant_id);
