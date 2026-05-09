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
import app.meads.judging.internal.MedalAwardRepository;
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
class MedalAwardRepositoryTest {

    @Autowired
    MedalAwardRepository medalAwardRepository;

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
        User entrant;
        User judge;
        Entry entry;
    }

    private Fixtures createFixtures(String suffix) {
        var fx = new Fixtures();
        var competition = competitionRepository.save(new Competition("Test Competition", "test-" + suffix,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        fx.division = divisionRepository.save(new Division(competition.getId(),
                "Home", "home-" + suffix, ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
        fx.category = divisionCategoryRepository.save(new DivisionCategory(
                fx.division.getId(), null, "M1A", "Traditional Mead",
                "desc", null, 1, CategoryScope.JUDGING));
        fx.entrant = userRepository.save(new User("entrant-" + suffix + "@test.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        fx.judge = userRepository.save(new User("judge-" + suffix + "@test.com",
                "Judge", UserStatus.ACTIVE, Role.USER));
        fx.entry = entryRepository.save(new Entry(fx.division.getId(), fx.entrant.getId(), 1,
                "ENT-" + suffix.toUpperCase(),
                "My Mead", fx.category.getId(), Sweetness.DRY, new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null));
        return fx;
    }

    @Test
    void shouldSaveAndFindByEntryId() {
        var fx = createFixtures("a1");

        var award = new MedalAward(fx.entry.getId(), fx.division.getId(),
                fx.category.getId(), Medal.GOLD, fx.judge.getId());
        medalAwardRepository.save(award);

        var found = medalAwardRepository.findByEntryId(fx.entry.getId()).orElseThrow();
        assertThat(found.getEntryId()).isEqualTo(fx.entry.getId());
        assertThat(found.getDivisionId()).isEqualTo(fx.division.getId());
        assertThat(found.getFinalCategoryId()).isEqualTo(fx.category.getId());
        assertThat(found.getMedal()).isEqualTo(Medal.GOLD);
        assertThat(found.getAwardedBy()).isEqualTo(fx.judge.getId());
        assertThat(found.getAwardedAt()).isNotNull();
    }

    @Test
    void shouldPersistWithdrawnAwardAsNullMedal() {
        var fx = createFixtures("a2");

        var award = new MedalAward(fx.entry.getId(), fx.division.getId(),
                fx.category.getId(), null, fx.judge.getId());
        medalAwardRepository.save(award);

        var found = medalAwardRepository.findByEntryId(fx.entry.getId()).orElseThrow();
        assertThat(found.getMedal()).isNull();
    }

    @Test
    void shouldUpdateMedalViaDomainMethod() {
        var fx = createFixtures("a3");

        var award = new MedalAward(fx.entry.getId(), fx.division.getId(),
                fx.category.getId(), Medal.SILVER, fx.judge.getId());
        var saved = medalAwardRepository.save(award);

        saved.updateMedal(Medal.GOLD, fx.judge.getId());
        medalAwardRepository.save(saved);

        var found = medalAwardRepository.findByEntryId(fx.entry.getId()).orElseThrow();
        assertThat(found.getMedal()).isEqualTo(Medal.GOLD);
    }

    @Test
    void shouldFindByDivisionIdAndFinalCategoryId() {
        var fx = createFixtures("a4");

        medalAwardRepository.save(new MedalAward(fx.entry.getId(), fx.division.getId(),
                fx.category.getId(), Medal.BRONZE, fx.judge.getId()));

        var byDivision = medalAwardRepository.findByDivisionId(fx.division.getId());
        assertThat(byDivision).hasSize(1);

        var byCategory = medalAwardRepository.findByFinalCategoryId(fx.category.getId());
        assertThat(byCategory).hasSize(1);
    }
}
