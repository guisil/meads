package app.meads.awards;

import app.meads.BusinessRuleException;
import app.meads.TestcontainersConfiguration;
import app.meads.competition.CompetitionService;
import app.meads.competition.ScoringSystem;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class AwardsModuleTest {

    @Autowired AwardsService awardsService;
    @Autowired CompetitionService competitionService;
    @Autowired UserService userService;

    @Test
    void shouldBootstrapAwardsModule() {
        assertThat(awardsService).isNotNull();
    }

    @Test
    void shouldHandlePublishLifecycleWithRevertAndRepublish(PublishedEvents events) {
        var admin = userService.createUser(
                "awards-mod-admin@test.com", "Awards Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        var competition = competitionService.createCompetition(
                "Awards Module Test", "awards-mod-test",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                "Test Location", admin.getId());
        var division = competitionService.createDivision(
                competition.getId(), "Amateur", "awards-mod-div",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());

        // Advance DRAFT → DELIBERATION (4 advances)
        for (int i = 0; i < 4; i++) {
            competitionService.advanceDivisionStatus(division.getId(), admin.getId());
        }

        // Publish: status moves to RESULTS_PUBLISHED + Publication v1 created
        var publication1 = awardsService.publish(division.getId(), admin.getId());
        assertThat(publication1.getVersion()).isEqualTo(1);
        assertThat(publication1.isInitial()).isTrue();
        assertThat(events.ofType(ResultsPublishedEvent.class)).hasSize(1);

        // Latest publication retrievable
        assertThat(awardsService.getLatestPublication(division.getId()))
                .isPresent()
                .get()
                .extracting(Publication::getVersion).isEqualTo(1);

        // Second publish rejected (already published)
        assertThatThrownBy(() -> awardsService.publish(division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards");

        // Revert publication via CompetitionService
        competitionService.revertDivisionStatus(division.getId(), admin.getId());

        // Status reverted; publication v1 still in audit log
        assertThat(competitionService.findDivisionById(division.getId()).getStatus().name())
                .isEqualTo("DELIBERATION");
        assertThat(awardsService.getPublicationHistory(division.getId())).hasSize(1);

        // Advance back to RESULTS_PUBLISHED for republish (advance: DELIBERATION → RESULTS_PUBLISHED)
        competitionService.advanceDivisionStatus(division.getId(), admin.getId());

        // Republish creates v2
        var publication2 = awardsService.republish(division.getId(),
                "Corrected silver medal in M1A — judge re-scored after spreadsheet error.",
                admin.getId());
        assertThat(publication2.getVersion()).isEqualTo(2);
        assertThat(publication2.isInitial()).isFalse();
        assertThat(events.ofType(ResultsRepublishedEvent.class)).hasSize(1);

        // History now has 2 entries
        assertThat(awardsService.getPublicationHistory(division.getId())).hasSize(2);
    }
}
