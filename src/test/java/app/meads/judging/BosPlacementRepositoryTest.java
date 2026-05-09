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
import app.meads.judging.internal.BosPlacementRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BosPlacementRepositoryTest {

    @Autowired
    BosPlacementRepository bosPlacementRepository;

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

    private Division division;
    private DivisionCategory category;
    private User judge;

    private Entry createEntry(String suffix, int entryNumber) {
        if (division == null) {
            var competition = competitionRepository.save(new Competition("Test Competition", "test-bos-" + suffix,
                    LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
            division = divisionRepository.save(new Division(competition.getId(),
                    "Home", "home-bos-" + suffix, ScoringSystem.MJP,
                    LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
            category = divisionCategoryRepository.save(new DivisionCategory(
                    division.getId(), null, "M1A", "Traditional Mead",
                    "desc", null, 1, CategoryScope.JUDGING));
            judge = userRepository.save(new User("judge-bos-" + suffix + "@test.com",
                    "Judge", UserStatus.ACTIVE, Role.USER));
        }
        var entrant = userRepository.save(new User("entrant-" + suffix + "-" + entryNumber + "@test.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        return entryRepository.save(new Entry(division.getId(), entrant.getId(), entryNumber,
                "ENT-" + suffix.toUpperCase() + entryNumber,
                "Mead " + entryNumber, category.getId(), Sweetness.DRY,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null));
    }

    @Test
    void shouldSaveAndFindBosPlacementByDivisionId() {
        var entry = createEntry("a", 1);

        var placement = new BosPlacement(division.getId(), entry.getId(), 1, judge.getId());
        bosPlacementRepository.save(placement);

        var found = bosPlacementRepository.findByDivisionIdOrderByPlace(division.getId());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getEntryId()).isEqualTo(entry.getId());
        assertThat(found.get(0).getPlace()).isEqualTo(1);
        assertThat(found.get(0).getAwardedBy()).isEqualTo(judge.getId());
        assertThat(found.get(0).getAwardedAt()).isNotNull();
    }

    @Test
    void shouldOrderByPlaceAscending() {
        var entry1 = createEntry("b", 1);
        var entry2 = createEntry("b", 2);
        var entry3 = createEntry("b", 3);

        bosPlacementRepository.save(new BosPlacement(division.getId(), entry2.getId(), 2, judge.getId()));
        bosPlacementRepository.save(new BosPlacement(division.getId(), entry3.getId(), 3, judge.getId()));
        bosPlacementRepository.save(new BosPlacement(division.getId(), entry1.getId(), 1, judge.getId()));

        var found = bosPlacementRepository.findByDivisionIdOrderByPlace(division.getId());

        assertThat(found).extracting(BosPlacement::getPlace).containsExactly(1, 2, 3);
    }

    @Test
    void shouldFindByEntryId() {
        var entry = createEntry("c", 1);
        var saved = bosPlacementRepository.save(
                new BosPlacement(division.getId(), entry.getId(), 1, judge.getId()));

        var found = bosPlacementRepository.findByEntryId(entry.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldUpdatePlaceViaDomainMethod() {
        var entry = createEntry("d", 1);
        var saved = bosPlacementRepository.save(
                new BosPlacement(division.getId(), entry.getId(), 3, judge.getId()));

        saved.updatePlace(1, judge.getId());
        bosPlacementRepository.save(saved);

        var refound = bosPlacementRepository.findById(saved.getId()).orElseThrow();
        assertThat(refound.getPlace()).isEqualTo(1);
    }

    @Test
    void shouldRejectNonPositivePlace() {
        var entry = createEntry("e", 1);

        Assertions.assertThatThrownBy(() ->
                new BosPlacement(division.getId(), entry.getId(), 0, judge.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnEmptyForNoPlacements() {
        var found = bosPlacementRepository.findByDivisionIdOrderByPlace(UUID.randomUUID());
        assertThat(found).isEmpty();
    }
}
