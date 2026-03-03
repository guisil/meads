package app.meads.entry;

import java.util.UUID;

public record EntriesSubmittedEvent(UUID divisionId, UUID userId, int entryCount) {}
