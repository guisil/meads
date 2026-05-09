CREATE TABLE category_judging_configs (
    id                    UUID PRIMARY KEY,
    division_category_id  UUID NOT NULL UNIQUE REFERENCES division_categories(id),
    medal_round_mode      VARCHAR(20) NOT NULL,
    medal_round_status    VARCHAR(20) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE
);
