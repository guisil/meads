package app.meads.judging;

public enum ScoresheetStatus {
    /**
     * Freshly created — no judge has saved any score or comment yet.
     * Distinguishes a sheet that just exists from one a judge has touched.
     * Mutators (updateScore, updateOverallComments) accept BLANK sheets and
     * promote them to DRAFT on the first successful save.
     */
    BLANK,
    /**
     * A judge has entered at least one score or comment. The round still
     * accepts edits, but admin operations like revertScoringRound now treat
     * this as "real work in progress" — broader than the previous SUBMITTED-only
     * gate.
     */
    DRAFT,
    /**
     * Final — total score computed, judge has committed. Counts toward
     * round-completion + medal cascade. Reversible only via admin revertToDraft.
     */
    SUBMITTED
}
