-- Backstop the "one medal round per category" invariant at the DB level.
-- Both creation paths (manual `createMedalRound` and the cascade auto-create
-- on scoring-round completion) already guard against duplicates at the
-- service layer; this index protects against any future regression and any
-- concurrent-transaction race where two cascades happen to fire in parallel
-- on the same category.
--
-- Partial unique index: only applies to MEDAL rounds. SCORING rounds in the
-- same category are intentionally allowed (split-category scenarios).
CREATE UNIQUE INDEX idx_judging_rounds_one_medal_per_category
    ON judging_rounds (judging_id, division_category_id)
    WHERE type = 'MEDAL';
