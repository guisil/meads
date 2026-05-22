package app.meads.judging;

import java.util.UUID;

/**
 * Read-model row for {@code MedalRoundView}: one entry eligible for a category's
 * medal round, with its Round 1 total score and current medal award (if any).
 *
 * @param round1Total  the entry's Round 1 {@code Scoresheet.totalScore}.
 * @param medalAwardId {@code null} when no {@link MedalAward} row exists yet; a
 *                     non-null id with {@code currentMedal == null} means an
 *                     explicit withhold.
 * @param currentMedal the awarded medal, or {@code null} for no row / withhold.
 */
public record MedalRoundEntryRow(
        UUID entryId,
        String entryCode,
        String meadName,
        UUID entrantUserId,
        Integer round1Total,
        boolean advancedToMedalRound,
        UUID medalAwardId,
        Medal currentMedal) {
}
