package app.meads.competition.internal;

import app.meads.competition.DivisionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DivisionCategoryRepository extends JpaRepository<DivisionCategory, UUID> {
    List<DivisionCategory> findByDivisionIdOrderByCode(UUID divisionId);
    boolean existsByDivisionIdAndCode(UUID divisionId, String code);
    boolean existsByDivisionIdAndCatalogCategoryId(UUID divisionId, UUID catalogCategoryId);
    List<DivisionCategory> findByParentId(UUID parentId);
}
