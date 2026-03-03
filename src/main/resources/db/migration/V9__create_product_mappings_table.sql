CREATE TABLE product_mappings (
    id              UUID            PRIMARY KEY,
    division_id     UUID            NOT NULL REFERENCES divisions(id),
    jumpseller_product_id VARCHAR(255) NOT NULL,
    jumpseller_sku  VARCHAR(255),
    product_name    VARCHAR(255)    NOT NULL,
    credits_per_unit INT            NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_product_mappings_division_product
        UNIQUE (division_id, jumpseller_product_id)
);

CREATE INDEX idx_product_mappings_division_id ON product_mappings(division_id);
CREATE INDEX idx_product_mappings_jumpseller_product_id ON product_mappings(jumpseller_product_id);
