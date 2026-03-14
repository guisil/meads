package app.meads.competition.internal;

import app.meads.competition.CompetitionDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompetitionDocumentRepository extends JpaRepository<CompetitionDocument, UUID> {
    List<CompetitionDocument> findByCompetitionIdOrderByDisplayOrder(UUID competitionId);
    int countByCompetitionId(UUID competitionId);
    boolean existsByCompetitionIdAndName(UUID competitionId, String name);
}
