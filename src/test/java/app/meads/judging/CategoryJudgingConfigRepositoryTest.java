package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CategoryScope;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CategoryJudgingConfigRepositoryTest {

    @Autowired
    CategoryJudgingConfigRepository categoryJudgingConfigRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    DivisionCategoryRepository divisionCategoryRepository;

    private DivisionCategory createAndSaveJudgingCategory(String code) {
        var competition = competitionRepository.save(new Competition("Test Competition",
                "test-" + code.toLowerCase(),
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        var division = divisionRepository.save(new Division(competition.getId(),
                "Home", "home-" + code.toLowerCase(), ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
        return divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, code, "Category " + code,
                "Description", null, 1, CategoryScope.JUDGING));
    }

    @Test
    void shouldSaveAndFindByDivisionCategoryId() {
        var category = createAndSaveJudgingCategory("M1A");

        var config = new CategoryJudgingConfig(category.getId());

        categoryJudgingConfigRepository.save(config);

        var found = categoryJudgingConfigRepository.findByDivisionCategoryId(category.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getDivisionCategoryId()).isEqualTo(category.getId());
        assertThat(found.get().getMedalRoundMode()).isEqualTo(MedalRoundMode.COMPARATIVE);
        assertThat(found.get().getMedalRoundStatus()).isEqualTo(MedalRoundStatus.PENDING);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNull();
    }

    @Test
    void shouldPersistExplicitMode() {
        var category = createAndSaveJudgingCategory("M2B");

        var config = new CategoryJudgingConfig(category.getId(), MedalRoundMode.SCORE_BASED);

        categoryJudgingConfigRepository.save(config);

        var found = categoryJudgingConfigRepository.findByDivisionCategoryId(category.getId()).orElseThrow();
        assertThat(found.getMedalRoundMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
    }
}
