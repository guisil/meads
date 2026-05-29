package app.meads.awards;

import app.meads.BusinessRuleException;
import app.meads.TestcontainersConfiguration;
import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.ScoringSystem;
import app.meads.entry.Carbonation;
import app.meads.entry.EntryService;
import app.meads.entry.Sweetness;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import app.meads.judging.JudgingService;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class AwardsModuleTest {

    @Autowired AwardsService awardsService;
    @Autowired CompetitionService competitionService;
    @Autowired JudgingService judgingService;
    @Autowired ScoresheetService scoresheetService;
    @Autowired EntryService entryService;
    @Autowired UserService userService;
    @Autowired app.meads.judging.internal.JudgingRoundRepository judgingRoundRepository;

    @Test
    void shouldBootstrapAwardsModule() {
        assertThat(awardsService).isNotNull();
    }

    @Test
    void shouldHandlePublishLifecycleWithRevertAndRepublish(PublishedEvents events) {
        var admin = userService.createUser(
                "awards-mod-admin@test.com", "Awards Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        var entrant = userService.createUser(
                "awards-mod-entrant@test.com", "Entrant",
                UserStatus.ACTIVE, Role.USER);
        var judge1 = userService.createUser(
                "awards-mod-judge1@test.com", "Judge One",
                UserStatus.ACTIVE, Role.USER);
        var judge2 = userService.createUser(
                "awards-mod-judge2@test.com", "Judge Two",
                UserStatus.ACTIVE, Role.USER);

        var competition = competitionService.createCompetition(
                "Awards Module Test", "awards-mod-test",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                "Test Location", admin.getId());
        var division = competitionService.createDivision(
                competition.getId(), "Amateur", "awards-mod-div",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());

        competitionService.addParticipantByEmail(competition.getId(), judge1.getEmail(),
                CompetitionRole.JUDGE, admin.getId());
        competitionService.addParticipantByEmail(competition.getId(), judge2.getEmail(),
                CompetitionRole.JUDGE, admin.getId());

        var registrationCategory = competitionService.addCustomCategory(
                division.getId(), "AM1", "Amateur One", "desc", null, admin.getId());

        // DRAFT → REGISTRATION_OPEN
        competitionService.advanceDivisionStatus(division.getId(), admin.getId());

        // Credit + entry for entrant
        entryService.addCredits(division.getId(), entrant.getEmail(), 1, admin.getId());
        var entry = entryService.createEntry(division.getId(), entrant.getId(),
                "Test Mead", registrationCategory.getId(),
                Sweetness.DRY, new BigDecimal("12.0"), Carbonation.STILL,
                "Wildflower", null, false, null, null);
        // DRAFT → SUBMITTED (entrant submits own entry) → RECEIVED (admin)
        entryService.submitEntry(entry.getId(), entrant.getId());
        entryService.advanceEntryStatus(entry.getId(), admin.getId());

        // REGISTRATION_OPEN → REGISTRATION_CLOSED
        competitionService.advanceDivisionStatus(division.getId(), admin.getId());

        // Initialize judging categories + assign final category
        competitionService.initializeJudgingCategories(division.getId(), admin.getId());
        var judgingCategory = competitionService.findJudgingCategories(division.getId()).getFirst();
        entryService.assignFinalCategory(entry.getId(), judgingCategory.getId(), admin.getId());

        // REGISTRATION_CLOSED → JUDGING
        competitionService.advanceDivisionStatus(division.getId(), admin.getId());

        // Run a minimal judging cycle to phase=COMPLETE
        var judging = judgingService.ensureJudgingExists(division.getId());
        var physicalTable = judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());
        var table = judgingService.createRound(judging.getId(), "AM1 Round 1",
                judgingCategory.getId(), null, admin.getId());
        judgingService.assignRoundToPhysicalTable(table.getId(), physicalTable.getId(), admin.getId());
        judgingService.assignJudge(table.getId(), judge1.getId(), admin.getId());
        judgingService.assignJudge(table.getId(), judge2.getId(), admin.getId());
        judgingService.assignEntryToRound(table.getId(), entry.getId(), admin.getId());
        judgingService.startRound(table.getId(), admin.getId());

        // One scoresheet per entry is auto-created when the table starts.
        var sheets = scoresheetService.findByRoundId(table.getId());
        assertThat(sheets).hasSize(1);
        fillAndSubmit(sheets.getFirst(), judge1.getId());

        // Cascade auto-created a medal JudgingRound at READY when the scoring
        // round COMPLETEd. Drive it through ACTIVE → COMPLETE for this test.
        var medalRound = judgingService.findMedalRoundByCategoryId(judgingCategory.getId())
                .orElseThrow(() -> new AssertionError("Cascade didn't create a medal round"));
        // Bring READY → ACTIVE manually (no service method for this yet — admin
        // would normally trigger via a future Start button in MedalRoundView).
        medalRound.start();
        judgingRoundRepository.save(medalRound);
        judgingService.completeMedalRoundById(medalRound.getId(), admin.getId());
        judgingService.startBos(division.getId(), admin.getId());
        judgingService.completeBos(division.getId(), admin.getId());

        // JUDGING → DELIBERATION
        competitionService.advanceDivisionStatus(division.getId(), admin.getId());

        // Publish: DELIBERATION → RESULTS_PUBLISHED + Publication v1
        var publication1 = awardsService.publish(division.getId(), admin.getId());
        assertThat(publication1.getVersion()).isEqualTo(1);
        assertThat(publication1.isInitial()).isTrue();
        assertThat(events.ofType(ResultsPublishedEvent.class)).hasSize(1);

        // Second publish rejected
        assertThatThrownBy(() -> awardsService.publish(division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards");

        // Revert publication: RESULTS_PUBLISHED → DELIBERATION; v1 stays in audit log
        competitionService.revertDivisionStatus(division.getId(), admin.getId());
        assertThat(competitionService.findDivisionById(division.getId()).getStatus().name())
                .isEqualTo("DELIBERATION");
        assertThat(awardsService.getPublicationHistory(division.getId())).hasSize(1);

        // Manual advance from DELIBERATION is blocked — must use publish/republish
        assertThatThrownBy(() -> competitionService.advanceDivisionStatus(division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.use-publish-results-instead");

        // Republish: creates v2 and advances DELIBERATION → RESULTS_PUBLISHED internally
        var publication2 = awardsService.republish(division.getId(),
                "Corrected silver medal in M1A — judge re-scored after spreadsheet error.",
                admin.getId());
        assertThat(publication2.getVersion()).isEqualTo(2);
        assertThat(publication2.isInitial()).isFalse();
        assertThat(events.ofType(ResultsRepublishedEvent.class)).hasSize(1);
        assertThat(competitionService.findDivisionById(division.getId()).getStatus().name())
                .isEqualTo("RESULTS_PUBLISHED");
        assertThat(awardsService.getPublicationHistory(division.getId())).hasSize(2);
    }

    private void fillAndSubmit(Scoresheet sheet, UUID judgeUserId) {
        scoresheetService.updateScore(sheet.getId(), "Appearance", 10, "crystal clear and bright", judgeUserId);
        scoresheetService.updateScore(sheet.getId(), "Aroma/Bouquet", 25, "subtle honey and stone fruit", judgeUserId);
        scoresheetService.updateScore(sheet.getId(), "Flavour and Body", 27, "balanced, medium-bodied", judgeUserId);
        scoresheetService.updateScore(sheet.getId(), "Finish", 11, "clean, lingering", judgeUserId);
        scoresheetService.updateScore(sheet.getId(), "Overall Impression", 10, "well-crafted overall", judgeUserId);
        scoresheetService.updateOverallComments(sheet.getId(),
                "A well-balanced mead with subtle complexity and a clean finish.", judgeUserId);
        scoresheetService.submit(sheet.getId(), judgeUserId);
    }
}
