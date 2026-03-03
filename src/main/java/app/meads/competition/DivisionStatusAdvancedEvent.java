package app.meads.competition;

import java.util.UUID;

public record DivisionStatusAdvancedEvent(
    UUID divisionId,
    DivisionStatus previousStatus,
    DivisionStatus newStatus
) {}
