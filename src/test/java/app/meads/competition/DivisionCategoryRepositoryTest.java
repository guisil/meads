package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CategoryRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
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
class DivisionCategoryRepositoryTest {

    @Autowired
    DivisionCategoryRepository divisionCategoryRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    private Competition createAndSaveCompetition() {
        return competitionRepository.save(new Competition("Test Competition", "test-competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
    }

    private Division createAndSaveDivision(UUID competitionId) {
        return divisionRepository.save(new Division(competitionId,
                "Home", "home", ScoringSystem.MJP));
    }

    @Test
    void shouldSaveAndFindByDivisionId() {
        var competition = createAndSaveCompetition();
        var division = createAndSaveDivision(competition.getId());

        var cat1 = new DivisionCategory(division.getId(), null,
                "M1A", "Traditional Mead", "A mead made with honey and water", null, 1);
        var cat2 = new DivisionCategory(division.getId(), null,
                "M1B", "Semi-Sweet Mead", "A semi-sweet traditional mead", null, 2);
        divisionCategoryRepository.save(cat1);
        divisionCategoryRepository.save(cat2);

        var found = divisionCategoryRepository
                .findByDivisionIdOrderByCode(division.getId());

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getCode()).isEqualTo("M1A");
        assertThat(found.get(0).getName()).isEqualTo("Traditional Mead");
        assertThat(found.get(0).getDescription()).isEqualTo("A mead made with honey and water");
        assertThat(found.get(0).getDivisionId()).isEqualTo(division.getId());
        assertThat(found.get(0).getCatalogCategoryId()).isNull();
        assertThat(found.get(0).getParentId()).isNull();
        assertThat(found.get(0).getCreatedAt()).isNotNull();
        assertThat(found.get(1).getCode()).isEqualTo("M1B");
    }

    @Test
    void shouldCheckExistsByDivisionIdAndCode() {
        var competition = createAndSaveCompetition();
        var division = createAndSaveDivision(competition.getId());

        divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null,
                "M1A", "Traditional Mead", "A traditional mead", null, 1));

        assertThat(divisionCategoryRepository.existsByDivisionIdAndCode(
                division.getId(), "M1A")).isTrue();
        assertThat(divisionCategoryRepository.existsByDivisionIdAndCode(
                division.getId(), "M1B")).isFalse();
    }

    @Test
    void shouldCheckExistsByDivisionIdAndCatalogCategoryId() {
        var competition = createAndSaveCompetition();
        var division = createAndSaveDivision(competition.getId());
        var catalogCategories = categoryRepository.findByScoringSystemOrderByCode(ScoringSystem.MJP);
        var catalogCat = catalogCategories.getFirst();

        divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), catalogCat.getId(),
                catalogCat.getCode(), catalogCat.getName(), catalogCat.getDescription(),
                null, 1));

        assertThat(divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                division.getId(), catalogCat.getId())).isTrue();
        assertThat(divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                division.getId(), UUID.randomUUID())).isFalse();
    }

    @Test
    void shouldFindByParentId() {
        var competition = createAndSaveCompetition();
        var division = createAndSaveDivision(competition.getId());

        var parent = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null,
                "M2E", "Other Fruit Melomel", "Other fruits", null, 1));
        divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null,
                "M2E-T", "Tropical", "Tropical fruit melomel", parent.getId(), 1));
        divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null,
                "M2E-C", "Citrus", "Citrus fruit melomel", parent.getId(), 2));

        var subcategories = divisionCategoryRepository.findByParentId(parent.getId());

        assertThat(subcategories).hasSize(2);
        assertThat(subcategories).extracting(DivisionCategory::getParentId)
                .containsOnly(parent.getId());
    }
}
