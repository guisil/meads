package app.meads.awards;

import java.time.Instant;
import java.util.UUID;

public record ResultsRepublishedEvent(
        UUID divisionId,
        UUID publicationId,
        int version,
        Instant publishedAt,
        UUID publishedBy,
        String justification) {
}
