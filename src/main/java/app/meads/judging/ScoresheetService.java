package app.meads.judging;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Owns scoresheet eager creation, edits, status transitions, and the
 * recategorization sync rule. See §3.3 of judging-module-design.
 */
public interface ScoresheetService {

    void createScoresheetsForTable(@NotNull UUID tableId);

    void ensureScoresheetForEntry(@NotNull UUID entryId);

    void updateScore(@NotNull UUID scoresheetId, @NotNull String fieldName,
                     Integer value, String comment, @NotNull UUID judgeUserId);

    void updateOverallComments(@NotNull UUID scoresheetId, String comments, @NotNull UUID judgeUserId);

    void setAdvancedToMedalRound(@NotNull UUID scoresheetId, boolean advanced, @NotNull UUID judgeUserId);

    void setCommentLanguage(@NotNull UUID scoresheetId, String languageCode, @NotNull UUID judgeUserId);

    void submit(@NotNull UUID scoresheetId, @NotNull UUID judgeUserId);

    void revertToDraft(@NotNull UUID scoresheetId, @NotNull UUID adminUserId);

    void moveToTable(@NotNull UUID scoresheetId, @NotNull UUID newTableId, @NotNull UUID adminUserId);

    long countByTableIdAndStatus(@NotNull UUID tableId, @NotNull ScoresheetStatus status);

    List<Scoresheet> findByTableId(@NotNull UUID tableId);
}
