package app.meads.competition.internal;

import app.meads.competition.EventParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, UUID> {
    List<EventParticipant> findByEventId(UUID eventId);
    Optional<EventParticipant> findByAccessCode(String accessCode);
    Optional<EventParticipant> findByEventIdAndUserId(UUID eventId, UUID userId);
}
