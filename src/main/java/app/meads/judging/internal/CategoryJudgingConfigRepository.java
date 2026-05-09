package app.meads.judging.internal;

import app.meads.judging.CategoryJudgingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryJudgingConfigRepository extends JpaRepository<CategoryJudgingConfig, UUID> {
    Optional<CategoryJudgingConfig> findByDivisionCategoryId(UUID divisionCategoryId);
}
