package app.meads.competition.internal;

import app.meads.competition.CompetitionRole;
import app.meads.competition.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParticipantRoleRepository extends JpaRepository<ParticipantRole, UUID> {
    List<ParticipantRole> findByParticipantId(UUID participantId);
    boolean existsByParticipantIdAndRole(UUID participantId, CompetitionRole role);
}
