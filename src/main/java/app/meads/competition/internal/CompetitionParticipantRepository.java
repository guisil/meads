package app.meads.competition.internal;

import app.meads.competition.CompetitionParticipant;
import app.meads.competition.CompetitionRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompetitionParticipantRepository extends JpaRepository<CompetitionParticipant, UUID> {
    List<CompetitionParticipant> findByCompetitionId(UUID competitionId);
    boolean existsByCompetitionIdAndEventParticipantIdAndRole(
            UUID competitionId, UUID eventParticipantId, CompetitionRole role);
    List<CompetitionParticipant> findByCompetitionIdAndEventParticipantId(
            UUID competitionId, UUID eventParticipantId);
    List<CompetitionParticipant> findByEventParticipantIdAndRole(
            UUID eventParticipantId, CompetitionRole role);
    boolean existsByEventParticipantIdAndRole(
            UUID eventParticipantId, CompetitionRole role);
}
