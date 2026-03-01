package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CategoryRepository;
import app.meads.competition.internal.CompetitionCategoryRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.MeadEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CompetitionCategoryRepositoryTest {

    @Autowired
    CompetitionCategoryRepository competitionCategoryRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    MeadEventRepository meadEventRepository;

    @Autowired
    CategoryRepository categoryRepository;

    private MeadEvent createAndSaveEvent() {
        return meadEventRepository.save(new MeadEvent("Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
    }

    private Competition createAndSaveCompetition(UUID eventId) {
        return competitionRepository.save(new Competition(eventId,
                "Home", ScoringSystem.MJP));
    }

    @Test
    void shouldSaveAndFindByCompetitionId() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());

        var cat1 = new CompetitionCategory(competition.getId(), null,
                "M1A", "Traditional Mead", "A mead made with honey and water", null, 1);
        var cat2 = new CompetitionCategory(competition.getId(), null,
                "M1B", "Semi-Sweet Mead", "A semi-sweet traditional mead", null, 2);
        competitionCategoryRepository.save(cat1);
        competitionCategoryRepository.save(cat2);

        var found = competitionCategoryRepository
                .findByCompetitionIdOrderBySortOrder(competition.getId());

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getCode()).isEqualTo("M1A");
        assertThat(found.get(0).getName()).isEqualTo("Traditional Mead");
        assertThat(found.get(0).getDescription()).isEqualTo("A mead made with honey and water");
        assertThat(found.get(0).getCompetitionId()).isEqualTo(competition.getId());
        assertThat(found.get(0).getCatalogCategoryId()).isNull();
        assertThat(found.get(0).getParentId()).isNull();
        assertThat(found.get(0).getCreatedAt()).isNotNull();
        assertThat(found.get(1).getCode()).isEqualTo("M1B");
    }

    @Test
    void shouldCheckExistsByCompetitionIdAndCode() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());

        competitionCategoryRepository.save(new CompetitionCategory(
                competition.getId(), null,
                "M1A", "Traditional Mead", "A traditional mead", null, 1));

        assertThat(competitionCategoryRepository.existsByCompetitionIdAndCode(
                competition.getId(), "M1A")).isTrue();
        assertThat(competitionCategoryRepository.existsByCompetitionIdAndCode(
                competition.getId(), "M1B")).isFalse();
    }

    @Test
    void shouldCheckExistsByCompetitionIdAndCatalogCategoryId() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var catalogCategories = categoryRepository.findByScoringSystem(ScoringSystem.MJP);
        var catalogCat = catalogCategories.getFirst();

        competitionCategoryRepository.save(new CompetitionCategory(
                competition.getId(), catalogCat.getId(),
                catalogCat.getCode(), catalogCat.getName(), catalogCat.getDescription(),
                null, 1));

        assertThat(competitionCategoryRepository.existsByCompetitionIdAndCatalogCategoryId(
                competition.getId(), catalogCat.getId())).isTrue();
        assertThat(competitionCategoryRepository.existsByCompetitionIdAndCatalogCategoryId(
                competition.getId(), UUID.randomUUID())).isFalse();
    }

    @Test
    void shouldFindByParentId() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());

        var parent = competitionCategoryRepository.save(new CompetitionCategory(
                competition.getId(), null,
                "M2E", "Other Fruit Melomel", "Other fruits", null, 1));
        competitionCategoryRepository.save(new CompetitionCategory(
                competition.getId(), null,
                "M2E-T", "Tropical", "Tropical fruit melomel", parent.getId(), 1));
        competitionCategoryRepository.save(new CompetitionCategory(
                competition.getId(), null,
                "M2E-C", "Citrus", "Citrus fruit melomel", parent.getId(), 2));

        var subcategories = competitionCategoryRepository.findByParentId(parent.getId());

        assertThat(subcategories).hasSize(2);
        assertThat(subcategories).extracting(CompetitionCategory::getParentId)
                .containsOnly(parent.getId());
    }
}
