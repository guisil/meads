package app.meads.judging;

import java.util.Set;
import java.util.UUID;

/**
 * Read-side projection for the SCORE_BASED medal-round tie cascade. The score
 * cascade fills Gold → Silver → Bronze by Round 1 total; it stalls when the
 * highest-scoring un-awarded entries tie. Recomputed by {@code MedalRoundView}
 * after every medal action so the tie warning stays live.
 *
 * @param tiedEntryCount number of entries tied at the blocked boundary
 *                       ({@code 0} = the cascade is clear). This is the count
 *                       the tie banner shows — NOT the number of remaining
 *                       medal slots, which stays at the full count whenever the
 *                       tie sits at the top boundary.
 * @param tiedEntryIds   entries tied at the blocked boundary slot — the admin or
 *                       judge resolves them by awarding the medal to one (or
 *                       clearing awards) until the tie is broken.
 */
public record MedalRoundScorePreview(int tiedEntryCount, Set<UUID> tiedEntryIds) {
}
