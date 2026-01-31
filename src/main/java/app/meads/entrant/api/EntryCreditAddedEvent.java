package app.meads.entrant.api;

import java.util.UUID;

public record EntryCreditAddedEvent(
    UUID entrantId,
    UUID creditId,
    UUID competitionId,
    int quantity,
    String entrantEmail,
    String entrantName
) {}
