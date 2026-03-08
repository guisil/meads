CREATE TABLE competitions (
    id                 UUID         PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    short_name         VARCHAR(100) NOT NULL UNIQUE,
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL,
    location           VARCHAR(500),
    logo               BYTEA,
    logo_content_type  VARCHAR(100),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE
);
