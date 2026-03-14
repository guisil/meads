CREATE TABLE jumpseller_order_line_items (
    id                      UUID            PRIMARY KEY,
    order_id                UUID            NOT NULL REFERENCES jumpseller_orders(id),
    jumpseller_product_id   VARCHAR(255)    NOT NULL,
    jumpseller_sku          VARCHAR(255),
    product_name            VARCHAR(255)    NOT NULL,
    quantity                INT             NOT NULL,
    status                  VARCHAR(50)     NOT NULL,
    division_id             UUID            REFERENCES divisions(id),
    credits_awarded         INT             NOT NULL DEFAULT 0,
    review_reason           TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_jumpseller_order_line_items_order_id ON jumpseller_order_line_items(order_id);
