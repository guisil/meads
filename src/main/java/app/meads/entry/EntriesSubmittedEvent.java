package app.meads.entry;

import java.util.List;
import java.util.UUID;

public record EntriesSubmittedEvent(UUID divisionId, UUID userId,
                                     List<EntryDetail> entryDetails) {}
