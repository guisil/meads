package app.meads.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CompetitionService;
import app.meads.competition.DivisionStatus;
import app.meads.entry.EntryService;
import app.meads.entry.WebhookService;
import app.meads.identity.UserService;
import app.meads.judging.JudgeProfileService;
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
    @Autowired WebhookService webhookService;
    @Autowired UserService userService;
    @Autowired JudgeProfileService judgeProfileService;
    @Autowired app.meads.judging.JudgingService judgingService;
    @Autowired app.meads.judging.ScoresheetService scoresheetService;
    @Autowired app.meads.awards.AwardsService awardsService;

    @Test
    void shouldSeedDevDataOnStartup() {
        var competitions = competitionService.findAllCompetitions();
        assertThat(competitions).hasSize(3);

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

        // Amadora entries — 5 from user + 3 from entrant + 2 admin-added for buyer1
        //   + 1 hard-COI entry for judge3 + 1 verbose all-fields demo entry = 12
        var userEntries = entryService.findEntriesByDivisionAndUser(
                amadora.getId(), devUser.getId());
        assertThat(userEntries).hasSize(5);

        var entrantEntries = entryService.findEntriesByDivisionAndUser(
                amadora.getId(), devEntrant.getId());
        assertThat(entrantEntries).hasSize(3);

        var allAmadora = entryService.findEntriesByDivision(amadora.getId());
        assertThat(allAmadora).hasSize(12);

        // 6 JUDGE participants in CHIP 2026
        var judgeParticipants = competitionService.findRolesByCompetition(chip.getId()).stream()
                .filter(r -> r.getRole() == app.meads.competition.CompetitionRole.JUDGE)
                .count();
        assertThat(judgeParticipants).isEqualTo(6L);

        // Profissional — pre-staged at JUDGING with 20 entries + 1 verbose
        // all-fields demo entry = 21 RECEIVED
        var profissional = chipDivisions.stream()
                .filter(d -> "Profissional".equals(d.getName()))
                .findFirst().orElseThrow();
        assertThat(profissional.getStatus()).isEqualTo(DivisionStatus.JUDGING);
        var profEntries = entryService.findEntriesByDivision(profissional.getId());
        assertThat(profEntries).hasSize(21);
        assertThat(profEntries).allSatisfy(e -> {
            assertThat(e.getStatus().name()).isEqualTo("RECEIVED");
            assertThat(e.getFinalCategoryId()).isNotNull();
        });

        // Test Competition 2026
        var testComp = competitions.stream()
                .filter(c -> "Test Competition 2026".equals(c.getName()))
                .findFirst().orElseThrow();
        var testDivisions = competitionService.findDivisionsByCompetition(testComp.getId());
        assertThat(testDivisions).hasSize(1);
        assertThat(testDivisions.getFirst().getName()).isEqualTo("Open");
        assertThat(testDivisions.getFirst().getStatus()).isEqualTo(DivisionStatus.DRAFT);

        // Fast Track 2026 — driven all the way to RESULTS_PUBLISHED with two
        // fully-scored entries, so a fresh dev DB lands directly on a published
        // entrant scoresheet (fast-path for iterating the scoresheet redesign).
        var fastTrack = competitions.stream()
                .filter(c -> "Fast Track 2026".equals(c.getName()))
                .findFirst().orElseThrow();
        var fastTrackDivisions = competitionService.findDivisionsByCompetition(fastTrack.getId());
        assertThat(fastTrackDivisions).hasSize(1);
        var mostra = fastTrackDivisions.getFirst();
        assertThat(mostra.getName()).isEqualTo("Mostra");
        assertThat(mostra.getStatus()).isEqualTo(DivisionStatus.RESULTS_PUBLISHED);

        var mostraEntries = entryService.findEntriesByDivision(mostra.getId());
        assertThat(mostraEntries).hasSize(3);
        assertThat(mostraEntries).allSatisfy(e -> {
            assertThat(e.getStatus().name()).isEqualTo("RECEIVED");
            assertThat(e.getFinalCategoryId()).isNotNull();
        });

        // Each entry has a SUBMITTED scoresheet carrying a total score.
        assertThat(mostraEntries).allSatisfy(e -> {
            var sheets = scoresheetService.findByEntryIdOrderBySubmittedAtAsc(e.getId());
            assertThat(sheets).hasSize(1);
            assertThat(sheets.getFirst().getTotalScore()).isNotNull();
        });

        // A publication exists and the entrant can read their results.
        assertThat(awardsService.getLatestPublication(mostra.getId())).isPresent();
        var fastTrackEntrant = userService.findByEmail("entrant@example.com");
        assertThat(awardsService.getResultsForEntrant(fastTrackEntrant.getId(), mostra.getId()))
                .hasSize(3);
    }

    @Test
    void shouldBeIdempotent() {
        // DevDataInitializer already ran on startup.
        // Running it again should not create duplicate data.
        var initializer = new DevDataInitializer(userService, competitionService, entryService, webhookService, judgeProfileService, judgingService, scoresheetService, awardsService);
        initializer.initializeDevData();

        var competitions = competitionService.findAllCompetitions();
        assertThat(competitions).hasSize(3);
    }
}
