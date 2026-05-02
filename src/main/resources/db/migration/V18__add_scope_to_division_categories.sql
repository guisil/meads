ALTER TABLE division_categories
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'REGISTRATION';

ALTER TABLE division_categories
    DROP CONSTRAINT uq_division_categories_division_code;

ALTER TABLE division_categories
    ADD CONSTRAINT uq_division_categories_division_code_scope
        UNIQUE (division_id, code, scope);
