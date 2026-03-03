CREATE TABLE jumpseller_orders (
    id                  UUID            PRIMARY KEY,
    jumpseller_order_id VARCHAR(255)    NOT NULL UNIQUE,
    customer_email      VARCHAR(255)    NOT NULL,
    customer_name       VARCHAR(255)    NOT NULL,
    raw_payload         TEXT            NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    admin_note          TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_jumpseller_orders_status ON jumpseller_orders(status);
