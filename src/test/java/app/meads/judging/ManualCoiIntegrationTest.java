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
import app.meads.judging.internal.ManualCoi;
import app.meads.judging.internal.ManualCoiRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires the real {@link CoiCheckService} + {@link ManualCoiRepository} +
 * CompetitionService against a real database to prove that a declared manual COI
 * hard-blocks a judge through the actual entry-to-competition resolution path
 * (the unit test mocks that hop). assignJudge / recordMedal funnel through the
 * same {@code check()}, so blocking here blocks them too.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ManualCoiIntegrationTest {

    @Autowired
    CoiCheckService coiCheckService;

    @Autowired
    ManualCoiRepository manualCoiRepository;

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
        Competition competition;
        User entrant;
        User judge;
        Entry entry;
    }

    private Fixtures createFixtures(String suffix) {
        var fx = new Fixtures();
        fx.competition = competitionRepository.save(new Competition("Test Competition", "test-" + suffix,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        var division = divisionRepository.save(new Division(fx.competition.getId(),
                "Home", "home-" + suffix, ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Traditional Mead",
                "desc", null, 1, CategoryScope.JUDGING));
        fx.entrant = userRepository.save(new User("entrant-" + suffix + "@test.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        fx.judge = userRepository.save(new User("judge-" + suffix + "@test.com",
                "Judge", UserStatus.ACTIVE, Role.USER));
        fx.entry = entryRepository.save(new Entry(division.getId(), fx.entrant.getId(), 1,
                "ENT-" + suffix.toUpperCase(),
                "My Mead", category.getId(), Sweetness.DRY, new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null));
        return fx;
    }

    @Test
    void shouldHardBlockJudgeWhenManualCoiDeclaredForTheCompetition() {
        var fx = createFixtures("i1");
        manualCoiRepository.save(new ManualCoi(fx.competition.getId(),
                fx.judge.getId(), fx.entrant.getId(), fx.judge.getId()));

        var result = coiCheckService.check(fx.judge.getId(), fx.entry.getId());

        assertThat(result.hardBlock()).isTrue();
    }

    @Test
    void shouldNotBlockJudgeWhenNoManualCoiDeclared() {
        var fx = createFixtures("i2");

        var result = coiCheckService.check(fx.judge.getId(), fx.entry.getId());

        assertThat(result.hardBlock()).isFalse();
    }
}
