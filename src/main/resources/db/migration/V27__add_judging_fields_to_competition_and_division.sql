-- Division judging knobs
ALTER TABLE divisions
    ADD COLUMN bos_places            INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN min_judges_per_round  INTEGER NOT NULL DEFAULT 2;
