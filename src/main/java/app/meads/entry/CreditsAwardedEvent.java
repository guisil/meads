package app.meads.entry;

import java.util.UUID;

public record CreditsAwardedEvent(UUID divisionId, UUID userId, int amount, String source) {}
