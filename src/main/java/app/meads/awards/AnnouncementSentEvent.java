package app.meads.awards;

import java.util.UUID;

public record AnnouncementSentEvent(
        UUID divisionId,
        UUID publicationId,
        int recipientCount,
        boolean usedCustomMessage) {
}
