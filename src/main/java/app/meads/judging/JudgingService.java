package app.meads.judging;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Owns table CRUD, judge assignment, table/medal-round/BOS state transitions,
 * medal awards, and BOS placements. See §3.2 of judging-module-design.
 */
public interface JudgingService {

    // === Lazy bootstrap ===
    Judging ensureJudgingExists(UUID divisionId);

    // === Table CRUD ===
    JudgingTable createTable(UUID judgingId,
                             String name,
                             UUID divisionCategoryId,
                             LocalDate scheduledDate,
                             UUID adminUserId);

    void updateTableName(UUID tableId, String name, UUID adminUserId);

    void updateTableScheduledDate(UUID tableId, LocalDate date, UUID adminUserId);

    void deleteTable(UUID tableId, UUID adminUserId);

    List<JudgingTable> findTablesByJudgingId(UUID judgingId);

    List<JudgingTable> findTablesByJudgeUserId(UUID judgeUserId);

    boolean hasAnyJudgeAssignment(UUID judgeUserId);

    // === Judge assignment ===
    void assignJudge(UUID tableId, UUID judgeUserId, UUID adminUserId);

    void removeJudge(UUID tableId, UUID judgeUserId, UUID adminUserId);

    // === Table state transitions ===
    void startTable(UUID tableId, UUID adminUserId);

    // === Category medal-round configuration ===
    CategoryJudgingConfig configureCategoryMedalRound(UUID divisionCategoryId,
                                                       MedalRoundMode mode,
                                                       UUID adminUserId);

    // === Medal round transitions ===
    void startMedalRound(UUID divisionCategoryId, UUID adminUserId);

    void completeMedalRound(UUID divisionCategoryId, UUID adminUserId);

    void reopenMedalRound(UUID divisionCategoryId, UUID adminUserId);

    void resetMedalRound(UUID divisionCategoryId, UUID adminUserId);

    // === Medal awards (during ACTIVE) ===
    MedalAward recordMedal(UUID entryId, Medal medal, UUID judgeUserId);

    void updateMedal(UUID medalAwardId, Medal newValue, UUID judgeUserId);

    void deleteMedalAward(UUID medalAwardId, UUID judgeUserId);

    // === BOS lifecycle ===
    void startBos(UUID divisionId, UUID adminUserId);

    void completeBos(UUID divisionId, UUID adminUserId);

    void reopenBos(UUID divisionId, UUID adminUserId);

    void resetBos(UUID divisionId, UUID adminUserId);

    // === BOS placements (during BOS) ===
    BosPlacement recordBosPlacement(UUID divisionId, UUID entryId,
                                    int place, UUID adminUserId);

    void updateBosPlacement(UUID placementId, int place, UUID adminUserId);

    void deleteBosPlacement(UUID placementId, UUID adminUserId);
}
