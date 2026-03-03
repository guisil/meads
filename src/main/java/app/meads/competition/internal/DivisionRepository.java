package app.meads.competition.internal;

import app.meads.competition.Division;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DivisionRepository extends JpaRepository<Division, UUID> {
    List<Division> findByCompetitionId(UUID competitionId);
}
