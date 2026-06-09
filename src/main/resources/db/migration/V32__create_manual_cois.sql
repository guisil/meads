-- Admin-declared conflicts of interest between a judge and an entrant within a
-- competition. Automatic COI detection is account/meadery based and misses the
-- case where one person registers entries and judges under two separate accounts
-- (e.g. business email for entries, personal email for judging). A manual COI
-- hard-blocks the judge from judging that entrant's entries (consulted in
-- CoiCheckService.check). Scoped per competition.
CREATE TABLE manual_cois (
    id              UUID PRIMARY KEY,
    competition_id  UUID NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    judge_user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entrant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID NOT NULL REFERENCES users(id),
    UNIQUE (competition_id, judge_user_id, entrant_user_id)
);
CREATE INDEX idx_manual_cois_competition_id ON manual_cois(competition_id);
