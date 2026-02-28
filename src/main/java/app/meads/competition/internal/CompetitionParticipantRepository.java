package app.meads.competition.internal;

import app.meads.competition.CompetitionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionParticipantRepository extends JpaRepository<CompetitionParticipant, UUID> {
    List<CompetitionParticipant> findByCompetitionId(UUID competitionId);
    Optional<CompetitionParticipant> findByCompetitionIdAndUserId(UUID competitionId, UUID userId);
    boolean existsByCompetitionIdAndUserId(UUID competitionId, UUID userId);
    List<CompetitionParticipant> findByAccessCode(String accessCode);
}
