package app.meads.judging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
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

    /** Assign or clear (null) the physical table for a category's medal round. */
    void assignMedalRoundToPhysicalTable(@NotNull UUID divisionCategoryId, UUID physicalTableId,
                                          @NotNull UUID adminUserId);

    // === Round CRUD ===
    JudgingRound createRound(@NotNull UUID judgingId,
                             @NotBlank String name,
                             @NotNull UUID divisionCategoryId,
                             LocalDate scheduledDate,
                             @NotNull UUID adminUserId);

    void updateRoundName(@NotNull UUID roundId, @NotBlank String name, @NotNull UUID adminUserId);

    void updateRoundScheduledDate(@NotNull UUID roundId, LocalDate date, @NotNull UUID adminUserId);

    void deleteRound(@NotNull UUID roundId, @NotNull UUID adminUserId);

    List<JudgingRound> findRoundsByJudgingId(@NotNull UUID judgingId);

    java.util.Optional<JudgingRound> findRoundById(@NotNull UUID roundId);

    List<JudgingRound> findRoundsByDivisionAndCategory(@NotNull UUID divisionId, @NotNull UUID divisionCategoryId);

    List<JudgingRound> findRoundsByJudgeUserId(@NotNull UUID judgeUserId);

    boolean hasAnyJudgeAssignment(@NotNull UUID judgeUserId);

    boolean isJudgeAssignedToRound(@NotNull UUID roundId, @NotNull UUID judgeUserId);

    /** Judge user IDs assigned to a table — loads the assignment collection in-transaction. */
    List<UUID> findJudgeUserIdsForRound(@NotNull UUID roundId);

    // === Judge assignment ===
    void assignJudge(@NotNull UUID roundId, @NotNull UUID judgeUserId, @NotNull UUID adminUserId);

    void removeJudge(@NotNull UUID roundId, @NotNull UUID judgeUserId, @NotNull UUID adminUserId);

    // === Table state transitions ===
    void startRound(@NotNull UUID roundId, @NotNull UUID adminUserId);

    // === Category medal-round configuration ===
    CategoryJudgingConfig configureCategoryMedalRound(@NotNull UUID divisionCategoryId,
                                                       @NotNull MedalRoundMode mode,
                                                       @NotNull UUID adminUserId);

    List<CategoryJudgingConfig> findCategoryConfigsForDivision(@NotNull UUID divisionId,
                                                                @NotNull UUID adminUserId);

    java.util.Optional<CategoryJudgingConfig> findCategoryConfigByDivisionCategoryId(@NotNull UUID divisionCategoryId);

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
    void startMedalRound(@NotNull UUID divisionCategoryId, @NotNull UUID adminUserId);

    void completeMedalRound(@NotNull UUID divisionCategoryId, @NotNull UUID adminUserId);

    void reopenMedalRound(@NotNull UUID divisionCategoryId, @NotNull UUID adminUserId);

    void resetMedalRound(@NotNull UUID divisionCategoryId, @NotNull UUID adminUserId);

    // === Medal awards (during ACTIVE) ===
    /** A {@code null} medal records an explicit withhold (per design D11). */
    MedalAward recordMedal(@NotNull UUID entryId, Medal medal, @NotNull UUID judgeUserId);

    void updateMedal(@NotNull UUID medalAwardId, Medal newValue, @NotNull UUID judgeUserId);

    void deleteMedalAward(@NotNull UUID medalAwardId, @NotNull UUID judgeUserId);

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
