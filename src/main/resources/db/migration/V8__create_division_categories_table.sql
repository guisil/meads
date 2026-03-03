CREATE TABLE division_categories (
    id                  UUID         PRIMARY KEY,
    division_id         UUID         NOT NULL REFERENCES divisions(id),
    catalog_category_id UUID         REFERENCES categories(id),
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT         NOT NULL,
    parent_id           UUID         REFERENCES division_categories(id),
    sort_order          INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_division_categories_division_code UNIQUE (division_id, code)
);
