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
import app.meads.entry.Carbonation;
import app.meads.entry.Entry;
import app.meads.entry.Sweetness;
import app.meads.entry.internal.EntryRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingTableRepository;
import app.meads.judging.internal.ScoresheetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ScoresheetRepositoryTest {

    @Autowired
    JudgingRepository judgingRepository;

    @Autowired
    JudgingTableRepository judgingTableRepository;

    @Autowired
    ScoresheetRepository scoresheetRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    DivisionCategoryRepository divisionCategoryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntryRepository entryRepository;

    private static class Fixtures {
        Division division;
        DivisionCategory category;
        Judging judging;
        JudgingTable table;
        Entry entry;
    }

    private Fixtures createFixtures() {
        var fx = new Fixtures();
        var competition = competitionRepository.save(new Competition("Test Competition", "test-competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        fx.division = divisionRepository.save(new Division(competition.getId(),
                "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
        fx.category = divisionCategoryRepository.save(new DivisionCategory(
                fx.division.getId(), null, "M1A", "Traditional Mead",
                "desc", null, 1, CategoryScope.JUDGING));
        fx.judging = judgingRepository.save(new Judging(fx.division.getId()));
        fx.table = judgingTableRepository.save(new JudgingTable(
                fx.judging.getId(), "Table A", fx.category.getId(), null));
        var user = userRepository.save(new User("entrant@test.com", "Entrant",
                UserStatus.ACTIVE, Role.USER));
        fx.entry = entryRepository.save(new Entry(fx.division.getId(), user.getId(), 1, "ABC123",
                "My Mead", fx.category.getId(), Sweetness.DRY, new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null));
        return fx;
    }

    @Test
    void shouldSaveAndFindScoresheetWithFiveScoreFields() {
        var fx = createFixtures();

        var scoresheet = new Scoresheet(fx.table.getId(), fx.entry.getId());

        scoresheetRepository.save(scoresheet);

        var found = scoresheetRepository.findById(scoresheet.getId()).orElseThrow();

        assertThat(found.getTableId()).isEqualTo(fx.table.getId());
        assertThat(found.getEntryId()).isEqualTo(fx.entry.getId());
        assertThat(found.getStatus()).isEqualTo(ScoresheetStatus.DRAFT);
        assertThat(found.getTotalScore()).isNull();
        assertThat(found.getSubmittedAt()).isNull();
        assertThat(found.isAdvancedToMedalRound()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();

        // Five MJP fields with their canonical English names and max values
        assertThat(found.getFields()).hasSize(5);
        assertThat(found.getFields())
                .extracting(f -> f.getFieldName())
                .containsExactlyInAnyOrder(
                        "Appearance", "Aroma/Bouquet", "Flavour and Body",
                        "Finish", "Overall Impression");
        // Total max value across all 5 fields = 100
        assertThat(found.getFields().stream().mapToInt(f -> f.getMaxValue()).sum()).isEqualTo(100);
        assertThat(found.getFields()).allSatisfy(f -> {
            assertThat(f.getValue()).isNull();
            assertThat(f.getComment()).isNull();
        });
    }

    @Test
    void shouldFindByEntryId() {
        var fx = createFixtures();

        var scoresheet = new Scoresheet(fx.table.getId(), fx.entry.getId());
        scoresheetRepository.save(scoresheet);

        var found = scoresheetRepository.findByEntryId(fx.entry.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(scoresheet.getId());
    }

    @Test
    void shouldFindByTableId() {
        var fx = createFixtures();

        scoresheetRepository.save(new Scoresheet(fx.table.getId(), fx.entry.getId()));

        var found = scoresheetRepository.findByTableId(fx.table.getId());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getEntryId()).isEqualTo(fx.entry.getId());
    }

    @Test
    void shouldPersistScoreUpdatesAndSubmit() {
        var fx = createFixtures();

        var scoresheet = new Scoresheet(fx.table.getId(), fx.entry.getId());
        scoresheet.updateScore("Appearance", 10, "looks good");
        scoresheet.updateScore("Aroma/Bouquet", 25, "");
        scoresheet.updateScore("Flavour and Body", 30, null);
        scoresheet.updateScore("Finish", 12, null);
        scoresheet.updateScore("Overall Impression", 11, null);
        scoresheet.updateOverallComments("Solid traditional mead");
        scoresheet.submit();
        scoresheetRepository.save(scoresheet);

        var found = scoresheetRepository.findById(scoresheet.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ScoresheetStatus.SUBMITTED);
        assertThat(found.getTotalScore()).isEqualTo(88);
        assertThat(found.getSubmittedAt()).isNotNull();
        assertThat(found.getOverallComments()).isEqualTo("Solid traditional mead");

        var appearance = found.getFields().stream()
                .filter(f -> f.getFieldName().equals("Appearance"))
                .findFirst().orElseThrow();
        assertThat(appearance.getValue()).isEqualTo(10);
        assertThat(appearance.getComment()).isEqualTo("looks good");
    }
}
