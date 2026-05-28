package app.meads.judging;

import java.util.UUID;

/**
 * Read-model row for {@code MedalRoundView}: one entry eligible for a category's
 * medal round, with its scoresheet total (when SUBMITTED) and current medal
 * award (if any).
 *
 * @param round1Total  the entry's {@code Scoresheet.totalScore} once SUBMITTED;
 *                     null while BLANK/DRAFT (small-category flow) or when no
 *                     sheet exists at all yet.
 * @param scoresheetId the entry's scoresheet id when one exists (any status —
 *                     used by MedalRoundView to expose an Open scoresheet
 *                     drill-in); null when the entry has no sheet yet.
 * @param medalAwardId {@code null} when no {@link MedalAward} row exists yet; a
 *                     non-null id with {@code currentMedal == null} means an
 *                     explicit withhold.
 * @param currentMedal the awarded medal, or {@code null} for no row / withhold.
 */
public record MedalRoundEntryRow(
        UUID entryId,
        int entryNumber,
        String entryCode,
        String meadName,
        UUID entrantUserId,
        Integer round1Total,
        boolean advancedToMedalRound,
        UUID scoresheetId,
        UUID medalAwardId,
        Medal currentMedal) {
}
