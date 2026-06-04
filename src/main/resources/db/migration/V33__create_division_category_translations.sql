-- Per-language name/description overrides for division categories (custom or catalog,
-- registration or judging scope). English base values stay on division_categories;
-- a row here exists only for a non-English locale that an admin has translated.
CREATE TABLE division_category_translations (
    id                   UUID         PRIMARY KEY,
    division_category_id UUID         NOT NULL REFERENCES division_categories(id) ON DELETE CASCADE,
    locale               VARCHAR(10)  NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    description          TEXT         NOT NULL,
    CONSTRAINT uq_division_category_translations_locale UNIQUE (division_category_id, locale)
);

CREATE INDEX idx_division_category_translations_category
    ON division_category_translations(division_category_id);
