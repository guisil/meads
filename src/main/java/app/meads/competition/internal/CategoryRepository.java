package app.meads.competition.internal;

import app.meads.competition.Category;
import app.meads.competition.ScoringSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByScoringSystem(ScoringSystem scoringSystem);
}
