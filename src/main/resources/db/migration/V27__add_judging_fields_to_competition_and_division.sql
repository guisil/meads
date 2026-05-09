-- Per-competition comment language list (judges' scoresheet dropdown source)
CREATE TABLE competition_comment_languages (
    competition_id  UUID NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    language_code   VARCHAR(5) NOT NULL,
    PRIMARY KEY (competition_id, language_code)
);

-- Division judging knobs
ALTER TABLE divisions
    ADD COLUMN bos_places            INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN min_judges_per_table  INTEGER NOT NULL DEFAULT 2;
