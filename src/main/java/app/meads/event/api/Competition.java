package app.meads.event.api;

import java.time.Instant;
import java.util.UUID;

public record Competition(
    UUID id,
    UUID meadEventId,
    CompetitionType type,
    String name,
    String description,
    int maxEntriesPerEntrant,
    boolean registrationOpen,
    Instant registrationDeadline
) {}
