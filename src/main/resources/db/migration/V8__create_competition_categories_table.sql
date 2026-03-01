CREATE TABLE competition_categories (
    id                  UUID         PRIMARY KEY,
    competition_id      UUID         NOT NULL REFERENCES competitions(id),
    catalog_category_id UUID         REFERENCES categories(id),
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT         NOT NULL,
    parent_id           UUID         REFERENCES competition_categories(id),
    sort_order          INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (competition_id, code)
);
