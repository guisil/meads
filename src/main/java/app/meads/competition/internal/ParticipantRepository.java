package app.meads.competition.internal;

import app.meads.competition.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
    List<Participant> findByCompetitionId(UUID competitionId);
    Optional<Participant> findByAccessCode(String accessCode);
    boolean existsByAccessCode(String accessCode);
    Optional<Participant> findByCompetitionIdAndUserId(UUID competitionId, UUID userId);
    List<Participant> findByUserId(UUID userId);
}
