CREATE TABLE entrants (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state_province VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE entry_credits (
    id UUID PRIMARY KEY,
    entrant_id UUID NOT NULL REFERENCES entrants(id),
    competition_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    external_order_id VARCHAR(255) NOT NULL,
    external_source VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    purchased_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(external_order_id, external_source)
);

CREATE TABLE pending_orders (
    id UUID PRIMARY KEY,
    external_order_id VARCHAR(255) NOT NULL,
    external_source VARCHAR(100) NOT NULL,
    competition_id UUID NOT NULL,
    entrant_id UUID REFERENCES entrants(id),
    raw_payload JSONB NOT NULL,
    reason VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NEEDS_REVIEW',
    resolved_by VARCHAR(255),
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(external_order_id, external_source)
);
