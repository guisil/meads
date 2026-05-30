package app.meads.judging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Owns table CRUD, judge assignment, table/medal-round/BOS state transitions,
 * medal awards, and BOS placements. See §3.2 of judging-module-design.
 */
public interface JudgingService {

    // === Lazy bootstrap ===
    Judging ensureJudgingExists(@NotNull UUID divisionId);

    // === Physical tables ===
    PhysicalTable createPhysicalTable(@NotNull UUID divisionId, @NotBlank String label,
                                       @NotNull UUID adminUserId);

    void updatePhysicalTableLabel(@NotNull UUID physicalTableId, @NotBlank String label,
                                    @NotNull UUID adminUserId);

    void deletePhysicalTable(@NotNull UUID physicalTableId, @NotNull UUID adminUserId);

    List<PhysicalTable> findPhysicalTablesByDivision(@NotNull UUID divisionId);

    java.util.Optional<PhysicalTable> findPhysicalTableById(@NotNull UUID physicalTableId);

    /** Assign or change the physical table for a round. Allowed only while the round is NOT_STARTED. */
    void assignRoundToPhysicalTable(@NotNull UUID roundId, @NotNull UUID physicalTableId,
                                     @NotNull UUID adminUserId);

    // === Round CRUD ===
    JudgingRound createRound(@NotNull UUID judgingId,
                             @NotBlank String name,
                             @NotNull UUID divisionCategoryId,
                             LocalDateTime scheduledAt,
                             @NotNull UUID adminUserId);

    /**
     * Creates a medal-typed {@link JudgingRound} for the given category. The
     * {@link MedalRoundMode} is sourced from the category's {@link
     * CategoryJudgingConfig#getMedalRoundMode()} — that config must already
     * exist (e.g. from prior {@code configureCategoryMedalRound}).
     */
    JudgingRound createMedalRound(@NotNull UUID judgingId,
                                  @NotNull UUID divisionCategoryId,
                                  @NotNull UUID adminUserId);

    void updateRoundName(@NotNull UUID roundId, @NotBlank String name, @NotNull UUID adminUserId);

    void updateRoundScheduledAt(@NotNull UUID roundId, LocalDateTime scheduledAt, @NotNull UUID adminUserId);

    void deleteRound(@NotNull UUID roundId, @NotNull UUID adminUserId);

    List<JudgingRound> findRoundsByJudgingId(@NotNull UUID judgingId);

    java.util.Optional<JudgingRound> findRoundById(@NotNull UUID roundId);

    List<JudgingRound> findRoundsByDivisionAndCategory(@NotNull UUID divisionId, @NotNull UUID divisionCategoryId);

    List<JudgingRound> findRoundsByJudgeUserId(@NotNull UUID judgeUserId);

    /**
     * The single ACTIVE round (SCORING or MEDAL) the judge is assigned to, or
     * empty if they have no live work right now. The service guarantees
     * at-most-one ACTIVE assignment per judge via {@link #assignJudge}'s
     * active-conflict check; if multiple show up (shouldn't), the first one
     * is returned and the situation logged.
     */
    java.util.Optional<JudgingRound> findActiveRoundForJudge(@NotNull UUID judgeUserId);

    boolean hasAnyJudgeAssignment(@NotNull UUID judgeUserId);

    boolean isJudgeAssignedToRound(@NotNull UUID roundId, @NotNull UUID judgeUserId);

    /** Judge user IDs assigned to a table — loads the assignment collection in-transaction. */
    List<UUID> findJudgeUserIdsForRound(@NotNull UUID roundId);

    // === Entry assignment ===
    /**
     * Assigns an entry to a round (1:1 — an entry can sit on at most one round
     * at a time; uniqueness is enforced at the DB by {@code judging_round_entries.entry_id}).
     */
    void assignEntryToRound(@NotNull UUID roundId, @NotNull UUID entryId, @NotNull UUID adminUserId);

    /** Removes an entry from a round. No-op if the entry isn't currently assigned to this round. */
    void unassignEntryFromRound(@NotNull UUID roundId, @NotNull UUID entryId, @NotNull UUID adminUserId);

    /**
     * Reconciles a SCORE_BASED medal round's entries to include every RECEIVED
     * entry in its category. Idempotent — already-assigned entries are skipped;
     * only missing ones are added. Non-RECEIVED entries are not touched here
     * (removal is admin-driven via entry status changes). Rejects when the
     * round is not a SCORE_BASED medal round (force-all invariant only applies
     * to SCORE_BASED — COMPARATIVE keeps admin-chosen advance flags).
     */
    void syncScoreBasedMedalRoundEntries(@NotNull UUID roundId, @NotNull UUID adminUserId);

    // === Judge assignment ===
    void assignJudge(@NotNull UUID roundId, @NotNull UUID judgeUserId, @NotNull UUID adminUserId);

    void removeJudge(@NotNull UUID roundId, @NotNull UUID judgeUserId, @NotNull UUID adminUserId);

    // === Table state transitions ===
    void startRound(@NotNull UUID roundId, @NotNull UUID adminUserId);

    /**
     * Reverts an ACTIVE scoring round back to READY and deletes all of its
     * scoresheets. Rejected if any scoresheet on the round has been SUBMITTED —
     * SUBMITTED scoresheets carry committed judging work that revert would
     * destroy. Medal rounds use {@link #resetMedalRoundById} instead.
     */
    void revertScoringRound(@NotNull UUID roundId, @NotNull UUID adminUserId);

    /**
     * Recomputes PENDING ↔ READY status for every SCORING round in the division.
     * Called by the division-advance listener when a division reaches JUDGING:
     * scoring rounds that were "configuration-ready" but blocked on the
     * division-status condition can now flip to READY without further admin
     * action. Medal rounds and rounds in ACTIVE/COMPLETE are left untouched.
     */
    void recomputeReadinessForDivision(@NotNull UUID divisionId);

    // === Category medal-round configuration ===
    CategoryJudgingConfig configureCategoryMedalRound(@NotNull UUID divisionCategoryId,
                                                       @NotNull MedalRoundMode mode,
                                                       @NotNull UUID adminUserId);

    List<CategoryJudgingConfig> findCategoryConfigsForDivision(@NotNull UUID divisionId,
                                                                @NotNull UUID adminUserId);

    java.util.Optional<CategoryJudgingConfig> findCategoryConfigByDivisionCategoryId(@NotNull UUID divisionCategoryId);

    /**
     * Finds the medal {@link JudgingRound} (type = MEDAL) for a given division
     * category. Returns empty if no medal round has been created for this
     * category yet. There is at most one medal round per category (decision #5
     * in the round-model redesign).
     */
    java.util.Optional<JudgingRound> findMedalRoundByCategoryId(@NotNull UUID divisionCategoryId);

    /**
     * Updates the mode (COMPARATIVE / SCORE_BASED) of an existing medal
     * {@link JudgingRound}. Editable while PENDING or READY; rejected once the
     * round is ACTIVE. The category-level {@link CategoryJudgingConfig} is the
     * pre-creation default; this method changes the round's own mode after it
     * exists (e.g. cascade-auto-created rounds inheriting COMPARATIVE).
     */
    void updateMedalRoundMode(@NotNull UUID roundId, @NotNull MedalRoundMode mode,
                              @NotNull UUID adminUserId);

    /**
     * Medal-round status for a category, sourced from the medal
     * {@link JudgingRound}. Returns empty when no medal round exists yet.
     */
    java.util.Optional<JudgingRoundStatus> getEffectiveMedalRoundStatus(@NotNull UUID divisionCategoryId);

    List<CategoryJudgingConfig> findActiveCategoryConfigsForJudge(@NotNull UUID judgeUserId);

    List<MedalAward> findMedalAwardsForCategory(@NotNull UUID divisionCategoryId);

    /**
     * Read-model rows for the medal round of a category. COMPARATIVE mode includes
     * only entries whose Round 1 scoresheet is SUBMITTED and flagged
     * {@code advancedToMedalRound}; SCORE_BASED includes every entry with a
     * SUBMITTED scoresheet, ranked by Round 1 total descending.
     */
    List<MedalRoundEntryRow> findMedalRoundEntries(@NotNull UUID divisionCategoryId,
                                                   @NotNull MedalRoundMode mode);

    /**
     * Re-evaluates the SCORE_BASED tie cascade for a category against the current
     * medal awards. Used by {@code MedalRoundView} to warn when the score cascade
     * is blocked by a tie that needs manual resolution.
     */
    MedalRoundScorePreview recomputeScorePreview(@NotNull UUID divisionCategoryId);

    List<MedalAward> findGoldMedalAwardsForDivision(@NotNull UUID divisionId, @NotNull UUID adminUserId);

    List<BosPlacement> findBosPlacementsForDivision(@NotNull UUID divisionId, @NotNull UUID adminUserId);

    java.util.Optional<MedalAward> findMedalAwardByEntryId(@NotNull UUID entryId);

    java.util.Optional<BosPlacement> findBosPlacementByEntryId(@NotNull UUID entryId);

    // === Medal round transitions ===

    /**
     * Marks a medal {@link JudgingRound} COMPLETE. Admin-triggered Finalize.
     */
    void completeMedalRoundById(@NotNull UUID roundId, @NotNull UUID adminUserId);

    /**
     * Judge-or-admin Finalize for a SCORE_BASED medal round (small-category flow,
     * where the medal round owns the scoresheets). Requires the round to be
     * ACTIVE with every scoresheet FILLED and no unresolved score tie; submits
     * all sheets, ensures medals are populated from the totals, and completes the
     * round — all in one judge-driven step (no admin hand-off needed). Non-medal
     * entries simply place below the medals; unlike the COMPARATIVE Finalize this
     * does not require an explicit decision per entry.
     */
    void finalizeMedalRound(@NotNull UUID roundId, @NotNull UUID userId);

    /** Reopens a COMPLETE medal round back to ACTIVE. */
    void reopenMedalRoundById(@NotNull UUID roundId, @NotNull UUID adminUserId);

    /**
     * Resets a medal round back to READY and wipes its medal awards. Medal-only —
     * scoring rounds don't support reset.
     */
    void resetMedalRoundById(@NotNull UUID roundId, @NotNull UUID adminUserId);

    // === Medal awards (during ACTIVE) ===
    /** A {@code null} medal records an explicit withhold (per design D11). */
    MedalAward recordMedal(@NotNull UUID entryId, Medal medal, @NotNull UUID judgeUserId);

    void updateMedal(@NotNull UUID medalAwardId, Medal newValue, @NotNull UUID judgeUserId);

    void deleteMedalAward(@NotNull UUID medalAwardId, @NotNull UUID judgeUserId);

    /**
     * Marks a medal award as confirmed. Auto-fill on SCORE_BASED medal rounds
     * writes {@code confirmed = false} rows; the admin / medal-round judge
     * confirms each row to unlock results + BOS eligibility.
     */
    void confirmMedalAward(@NotNull UUID medalAwardId, @NotNull UUID adminUserId);

    // === BOS lifecycle ===
    void startBos(@NotNull UUID divisionId, @NotNull UUID adminUserId);

    void completeBos(@NotNull UUID divisionId, @NotNull UUID adminUserId);

    void reopenBos(@NotNull UUID divisionId, @NotNull UUID adminUserId);

    void resetBos(@NotNull UUID divisionId, @NotNull UUID adminUserId);

    // === BOS placements (during BOS) ===
    BosPlacement recordBosPlacement(@NotNull UUID divisionId, @NotNull UUID entryId,
                                    int place, @NotNull UUID adminUserId);

    void updateBosPlacement(@NotNull UUID placementId, int place, @NotNull UUID adminUserId);

    void deleteBosPlacement(@NotNull UUID placementId, @NotNull UUID adminUserId);
}
