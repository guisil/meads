package app.meads.competition;

import java.util.UUID;

public record CompetitionStatusAdvancedEvent(
    UUID competitionId,
    CompetitionStatus previousStatus,
    CompetitionStatus newStatus
) {}
