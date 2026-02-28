package app.meads.competition.internal;

import app.meads.competition.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {
    List<Competition> findByEventId(UUID eventId);
}
