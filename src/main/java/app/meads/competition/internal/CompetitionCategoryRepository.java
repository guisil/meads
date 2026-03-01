package app.meads.competition.internal;

import app.meads.competition.CompetitionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompetitionCategoryRepository extends JpaRepository<CompetitionCategory, UUID> {
    List<CompetitionCategory> findByCompetitionIdOrderBySortOrder(UUID competitionId);
    boolean existsByCompetitionIdAndCode(UUID competitionId, String code);
    boolean existsByCompetitionIdAndCatalogCategoryId(UUID competitionId, UUID catalogCategoryId);
    List<CompetitionCategory> findByParentId(UUID parentId);
}
