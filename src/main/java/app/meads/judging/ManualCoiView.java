package app.meads.judging;

import java.util.UUID;

/**
 * Read model for an admin-declared manual conflict of interest, enriched with
 * the judge and entrant display names/emails for the admin UI.
 */
public record ManualCoiView(
        UUID id,
        UUID judgeUserId,
        String judgeName,
        String judgeEmail,
        UUID entrantUserId,
        String entrantName,
        String entrantEmail) {
}
