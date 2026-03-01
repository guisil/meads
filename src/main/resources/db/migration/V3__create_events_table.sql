CREATE TABLE events (
    id                 UUID         PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL,
    location           VARCHAR(500),
    logo               BYTEA,
    logo_content_type  VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP
);
