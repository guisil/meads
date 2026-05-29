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
     * A judge has entered at least one score or comment but has not yet
     * committed the sheet as complete. The round still accepts edits, and
     * admin operations like revertScoringRound treat this as "real work in
     * progress". Auto-save keeps work here; the validating "Save" button
     * promotes DRAFT → FILLED.
     */
    DRAFT,
    /**
     * The judge clicked "Save" and the sheet passed validation (every field
     * scored, every per-criterion comment long enough) — it is ready to be
     * submitted by the round-level Finalize. Not yet final: the total is not
     * computed, and editing any scored content (updateScore /
     * updateOverallComments) demotes it back to DRAFT. Toggling the
     * advance-to-medal flag keeps it FILLED.
     */
    FILLED,
    /**
     * Final — total score computed, committed by the round-level Finalize.
     * Counts toward round-completion + medal cascade. Reversible only via the
     * admin reopen / revert path (SUBMITTED → DRAFT).
     */
    SUBMITTED
}
