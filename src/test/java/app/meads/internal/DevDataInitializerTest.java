package app.meads.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CompetitionService;
import app.meads.competition.DivisionStatus;
import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@DirtiesContext
class DevDataInitializerTest {

    @Autowired CompetitionService competitionService;
    @Autowired EntryService entryService;
    @Autowired UserService userService;

    @Test
    void shouldSeedDevDataOnStartup() {
        var competitions = competitionService.findAllCompetitions();
        assertThat(competitions).hasSize(2);

        // CHIP 2026
        var chip = competitions.stream()
                .filter(c -> "CHIP 2026".equals(c.getName()))
                .findFirst().orElseThrow();
        assertThat(chip.getLocation()).isEqualTo("Amarante, Portugal");

        var chipDivisions = competitionService.findDivisionsByCompetition(chip.getId());
        assertThat(chipDivisions).hasSize(2);

        var amadora = chipDivisions.stream()
                .filter(d -> "Amadora".equals(d.getName()))
                .findFirst().orElseThrow();
        assertThat(amadora.getStatus()).isEqualTo(DivisionStatus.REGISTRATION_OPEN);
        assertThat(amadora.getMaxEntriesPerSubcategory()).isEqualTo(3);
        assertThat(amadora.getMaxEntriesPerMainCategory()).isEqualTo(5);

        // Categories: M4B and M4D should be removed
        var amadoraCategories = competitionService.findDivisionCategories(amadora.getId());
        assertThat(amadoraCategories.stream().noneMatch(c -> "M4B".equals(c.getCode()))).isTrue();
        assertThat(amadoraCategories.stream().noneMatch(c -> "M4D".equals(c.getCode()))).isTrue();

        // Participants
        var participants = competitionService.findParticipantsByCompetition(chip.getId());
        assertThat(participants).hasSizeGreaterThanOrEqualTo(5);

        // Credits
        var devUser = userService.findByEmail("user@example.com");
        var devEntrant = userService.findByEmail("entrant@example.com");
        assertThat(entryService.getCreditBalance(amadora.getId(), devUser.getId())).isEqualTo(5);
        assertThat(entryService.getCreditBalance(amadora.getId(), devEntrant.getId())).isEqualTo(3);

        // Entries
        var userEntries = entryService.findEntriesByDivisionAndUser(
                amadora.getId(), devUser.getId());
        assertThat(userEntries).hasSize(3);

        var entrantEntries = entryService.findEntriesByDivisionAndUser(
                amadora.getId(), devEntrant.getId());
        assertThat(entrantEntries).hasSize(1);

        // Test Competition 2026
        var testComp = competitions.stream()
                .filter(c -> "Test Competition 2026".equals(c.getName()))
                .findFirst().orElseThrow();
        var testDivisions = competitionService.findDivisionsByCompetition(testComp.getId());
        assertThat(testDivisions).hasSize(1);
        assertThat(testDivisions.getFirst().getName()).isEqualTo("Open");
        assertThat(testDivisions.getFirst().getStatus()).isEqualTo(DivisionStatus.DRAFT);
    }

    @Test
    void shouldBeIdempotent() {
        // DevDataInitializer already ran on startup.
        // Running it again should not create duplicate data.
        var initializer = new DevDataInitializer(userService, competitionService, entryService);
        initializer.initializeDevData();

        var competitions = competitionService.findAllCompetitions();
        assertThat(competitions).hasSize(2);
    }
}
