package app.meads.judging;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Owns scoresheet eager creation, edits, status transitions, and the
 * recategorization sync rule. See §3.3 of judging-module-design.
 */
public interface ScoresheetService {

    void createScoresheetsForTable(@NotNull UUID roundId);

    void ensureScoresheetForEntry(@NotNull UUID entryId);

    void updateScore(@NotNull UUID scoresheetId, @NotNull String fieldName,
                     Integer value, String comment, @NotNull UUID judgeUserId);

    void updateOverallComments(@NotNull UUID scoresheetId, String comments, @NotNull UUID judgeUserId);

    void setAdvancedToMedalRound(@NotNull UUID scoresheetId, boolean advanced, @NotNull UUID judgeUserId);

    void setCommentLanguage(@NotNull UUID scoresheetId, String languageCode, @NotNull UUID judgeUserId);

    void submit(@NotNull UUID scoresheetId, @NotNull UUID judgeUserId);

    void revertToDraft(@NotNull UUID scoresheetId, @NotNull UUID adminUserId);

    void moveToRound(@NotNull UUID scoresheetId, @NotNull UUID newRoundId, @NotNull UUID adminUserId);

    /**
     * Permanently removes a scoresheet from its table. Admin-only, blocked
     * once the category's medal round is active or complete (mirrors the
     * revert-to-draft rule). Used by admins who need to revert an entry's
     * status (RECEIVED → SUBMITTED) or withdraw an entry that already has a
     * scoresheet on a table.
     */
    void deleteScoresheet(@NotNull UUID scoresheetId, @NotNull UUID adminUserId);

    long countByRoundIdAndStatus(@NotNull UUID roundId, @NotNull ScoresheetStatus status);

    /**
     * Bulk-deletes every scoresheet attached to the given round. Used by
     * {@code JudgingService.revertScoringRound} when an admin reverts an
     * ACTIVE scoring round back to READY; the caller has already verified
     * no SUBMITTED scoresheets exist on the round.
     */
    void deleteAllForRound(@NotNull UUID roundId);

    List<Scoresheet> findByRoundId(@NotNull UUID roundId);

    java.util.Optional<java.util.UUID> findNextDraftForJudge(@NotNull UUID judgeUserId);

    java.util.Optional<Scoresheet> findById(@NotNull UUID scoresheetId);

    List<Scoresheet> findByEntryIdOrderBySubmittedAtAsc(@NotNull UUID entryId);
}
