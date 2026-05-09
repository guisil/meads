package app.meads.judging;

import java.util.UUID;

/**
 * Owns scoresheet eager creation, edits, status transitions, and the
 * recategorization sync rule. See §3.3 of judging-module-design.
 */
public interface ScoresheetService {

    void createScoresheetsForTable(UUID tableId);

    void ensureScoresheetForEntry(UUID entryId);

    void updateScore(UUID scoresheetId, String fieldName,
                     Integer value, String comment, UUID judgeUserId);

    void updateOverallComments(UUID scoresheetId, String comments, UUID judgeUserId);

    void setAdvancedToMedalRound(UUID scoresheetId, boolean advanced, UUID judgeUserId);

    void setCommentLanguage(UUID scoresheetId, String languageCode, UUID judgeUserId);

    void submit(UUID scoresheetId, UUID judgeUserId);

    void revertToDraft(UUID scoresheetId, UUID adminUserId);

    void moveToTable(UUID scoresheetId, UUID newTableId, UUID adminUserId);
}
