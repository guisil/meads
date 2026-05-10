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

    // === Table CRUD ===
    JudgingTable createTable(@NotNull UUID judgingId,
                             @NotBlank String name,
                             @NotNull UUID divisionCategoryId,
                             LocalDate scheduledDate,
                             @NotNull UUID adminUserId);

    void updateTableName(@NotNull UUID tableId, @NotBlank String name, @NotNull UUID adminUserId);

    void updateTableScheduledDate(@NotNull UUID tableId, LocalDate date, @NotNull UUID adminUserId);

    void deleteTable(@NotNull UUID tableId, @NotNull UUID adminUserId);

    List<JudgingTable> findTablesByJudgingId(@NotNull UUID judgingId);

    List<JudgingTable> findTablesByJudgeUserId(@NotNull UUID judgeUserId);

    boolean hasAnyJudgeAssignment(@NotNull UUID judgeUserId);

    // === Judge assignment ===
    void assignJudge(@NotNull UUID tableId, @NotNull UUID judgeUserId, @NotNull UUID adminUserId);

    void removeJudge(@NotNull UUID tableId, @NotNull UUID judgeUserId, @NotNull UUID adminUserId);

    // === Table state transitions ===
    void startTable(@NotNull UUID tableId, @NotNull UUID adminUserId);

    // === Category medal-round configuration ===
    CategoryJudgingConfig configureCategoryMedalRound(@NotNull UUID divisionCategoryId,
                                                       @NotNull MedalRoundMode mode,
                                                       @NotNull UUID adminUserId);

    List<CategoryJudgingConfig> findCategoryConfigsForDivision(@NotNull UUID divisionId,
                                                                @NotNull UUID adminUserId);

    // === Medal round transitions ===
    void startMedalRound(@NotNull UUID divisionCategoryId, @NotNull UUID adminUserId);

    void completeMedalRound(@NotNull UUID divisionCategoryId, @NotNull UUID adminUserId);

    void reopenMedalRound(@NotNull UUID divisionCategoryId, @NotNull UUID adminUserId);

    void resetMedalRound(@NotNull UUID divisionCategoryId, @NotNull UUID adminUserId);

    // === Medal awards (during ACTIVE) ===
    MedalAward recordMedal(@NotNull UUID entryId, @NotNull Medal medal, @NotNull UUID judgeUserId);

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
