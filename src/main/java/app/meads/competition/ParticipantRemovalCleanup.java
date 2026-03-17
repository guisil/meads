package app.meads.competition;

import java.util.UUID;

/**
 * Cleanup interface for participant removal operations.
 * Modules that own data tied to participants (e.g., entries, credits)
 * implement this to clean up associated data before the participant is removed.
 */
public interface ParticipantRemovalCleanup {

    /**
     * Called before a participant is removed from a competition.
     * Implementations should delete all data associated with the user in that competition.
     */
    void cleanupForParticipant(UUID competitionId, UUID userId);
}
