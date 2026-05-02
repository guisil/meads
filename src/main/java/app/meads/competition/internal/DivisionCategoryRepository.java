package app.meads.competition.internal;

import app.meads.competition.CategoryScope;
import app.meads.competition.DivisionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DivisionCategoryRepository extends JpaRepository<DivisionCategory, UUID> {
    List<DivisionCategory> findByDivisionIdOrderByCode(UUID divisionId);
    List<DivisionCategory> findByDivisionIdAndScopeOrderByCode(UUID divisionId, CategoryScope scope);
    boolean existsByDivisionIdAndCode(UUID divisionId, String code);
    boolean existsByDivisionIdAndCodeAndScope(UUID divisionId, String code, CategoryScope scope);
    boolean existsByDivisionIdAndCatalogCategoryId(UUID divisionId, UUID catalogCategoryId);
    List<DivisionCategory> findByParentId(UUID parentId);
}
