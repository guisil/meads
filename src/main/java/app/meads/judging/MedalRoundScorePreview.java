package app.meads.judging;

import java.util.Set;
import java.util.UUID;

/**
 * Read-side projection for the SCORE_BASED medal-round tie cascade. The score
 * cascade fills Gold → Silver → Bronze by Round 1 total; it stalls when the
 * highest-scoring un-awarded entries tie. Recomputed by {@code MedalRoundView}
 * after every medal action so the tie warning stays live.
 *
 * @param tiedSlotCount number of medal slots blocked by an unresolved tie
 *                      ({@code 0} = the cascade is clear).
 * @param tiedEntryIds  entries tied at the blocked boundary slot — the admin or
 *                      judge must award/withhold to resolve them.
 */
public record MedalRoundScorePreview(int tiedSlotCount, Set<UUID> tiedEntryIds) {
}
