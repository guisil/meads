package app.meads.awards;

import app.meads.entry.EntryStatus;
import app.meads.judging.Medal;

import java.util.UUID;

public record EntrantResultRow(
        UUID entryId,
        String entryNumber,
        String meadName,
        String categoryCode,
        String categoryName,
        EntryStatus status,
        Integer round1Total,
        boolean advancedToMedalRound,
        Medal medal,
        Integer bosPlace,
        UUID scoresheetId) {
}
