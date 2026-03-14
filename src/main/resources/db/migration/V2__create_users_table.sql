CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'USER',
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    password_hash VARCHAR(255),
    meadery_name VARCHAR(255),
    country                 VARCHAR(2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_users_email ON users(email);
