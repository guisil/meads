package app.meads.entry;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.entry.internal.EntryRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EntryRepositoryTest {

    @Autowired
    EntryRepository entryRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    DivisionCategoryRepository divisionCategoryRepository;

    @Autowired
    UserRepository userRepository;

    private Division createAndSaveDivision() {
        var competition = competitionRepository.save(new Competition("Test Competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        return divisionRepository.save(new Division(competition.getId(),
                "Home", ScoringSystem.MJP));
    }

    private User createAndSaveUser() {
        return userRepository.save(new User("entrant@test.com", "Entrant",
                UserStatus.ACTIVE, Role.USER));
    }

    private DivisionCategory createAndSaveCategory(Division division) {
        return divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Traditional Mead",
                "Traditional mead description", null, 1));
    }

    @Test
    void shouldSaveAndFindById() {
        var division = createAndSaveDivision();
        var user = createAndSaveUser();
        var category = createAndSaveCategory(division);

        var entry = new Entry(division.getId(), user.getId(), 1, "ABC123",
                "My Mead", category.getId(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        entryRepository.save(entry);

        var found = entryRepository.findById(entry.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getMeadName()).isEqualTo("My Mead");
        assertThat(found.get().getEntryNumber()).isEqualTo(1);
        assertThat(found.get().getEntryCode()).isEqualTo("ABC123");
        assertThat(found.get().getStatus()).isEqualTo(EntryStatus.DRAFT);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindByDivisionIdAndUserId() {
        var division = createAndSaveDivision();
        var user = createAndSaveUser();
        var category = createAndSaveCategory(division);

        var entry1 = new Entry(division.getId(), user.getId(), 1, "ABC123",
                "Mead One", category.getId(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        var entry2 = new Entry(division.getId(), user.getId(), 2, "DEF456",
                "Mead Two", category.getId(), Sweetness.SWEET, Strength.SACK,
                new BigDecimal("18.0"), Carbonation.SPARKLING,
                "Orange blossom", "Spices", true, "Oak barrel", null);

        entryRepository.save(entry1);
        entryRepository.save(entry2);

        var found = entryRepository.findByDivisionIdAndUserId(
                division.getId(), user.getId());

        assertThat(found).hasSize(2);
        assertThat(found).extracting(Entry::getMeadName)
                .containsExactlyInAnyOrder("Mead One", "Mead Two");
    }

    @Test
    void shouldFindByDivisionId() {
        var division = createAndSaveDivision();
        var user1 = createAndSaveUser();
        var user2 = userRepository.save(new User("entrant2@test.com", "Entrant 2",
                UserStatus.ACTIVE, Role.USER));
        var category = createAndSaveCategory(division);

        entryRepository.save(new Entry(division.getId(), user1.getId(), 1, "ABC123",
                "Mead One", category.getId(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null));
        entryRepository.save(new Entry(division.getId(), user2.getId(), 2, "DEF456",
                "Mead Two", category.getId(), Sweetness.MEDIUM, Strength.HYDROMEL,
                new BigDecimal("8.0"), Carbonation.PETILLANT,
                "Clover honey", null, false, null, null));

        var found = entryRepository.findByDivisionId(division.getId());

        assertThat(found).hasSize(2);
    }

    @Test
    void shouldPersistAllFields() {
        var division = createAndSaveDivision();
        var user = createAndSaveUser();
        var category = createAndSaveCategory(division);

        var entry = new Entry(division.getId(), user.getId(), 1, "XYZ789",
                "Complex Mead", category.getId(), Sweetness.SWEET, Strength.SACK,
                new BigDecimal("20.0"), Carbonation.SPARKLING,
                "Orange blossom, Acacia", "Cinnamon, Vanilla",
                true, "French oak, 12 months", "Competition special");

        entryRepository.save(entry);

        var found = entryRepository.findById(entry.getId()).orElseThrow();

        assertThat(found.getSweetness()).isEqualTo(Sweetness.SWEET);
        assertThat(found.getStrength()).isEqualTo(Strength.SACK);
        assertThat(found.getAbv()).isEqualByComparingTo(new BigDecimal("20.0"));
        assertThat(found.getCarbonation()).isEqualTo(Carbonation.SPARKLING);
        assertThat(found.getHoneyVarieties()).isEqualTo("Orange blossom, Acacia");
        assertThat(found.getOtherIngredients()).isEqualTo("Cinnamon, Vanilla");
        assertThat(found.isWoodAged()).isTrue();
        assertThat(found.getWoodAgeingDetails()).isEqualTo("French oak, 12 months");
        assertThat(found.getAdditionalInformation()).isEqualTo("Competition special");
    }
}
