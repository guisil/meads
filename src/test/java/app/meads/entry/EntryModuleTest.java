package app.meads.entry;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class EntryModuleTest {

    @Autowired
    EntryService entryService;

    @Autowired
    WebhookService webhookService;

    @Autowired
    CompetitionService competitionService;

    @Autowired
    UserService userService;

    @Test
    void shouldBootstrapEntryModule() {
        assertThat(entryService).isNotNull();
    }

    @Test
    void shouldCreateEntryAfterAddingCredits(PublishedEvents events) {
        // Setup: admin user + competition + division + category
        var admin = userService.createUser(
                "admin-integration@test.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        var competition = competitionService.createCompetition(
                "Integration Test Competition", "integration-test-competition",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                "Test Location", admin.getId());
        var division = competitionService.createDivision(
                competition.getId(), "Test Division", "test-division", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());
        var category = competitionService.addCustomCategory(
                division.getId(), "INT1", "Integration Test Category", "For integration testing",
                null, admin.getId());

        // Advance to REGISTRATION_OPEN
        competitionService.advanceDivisionStatus(division.getId(), admin.getId());

        // Setup: entrant user
        var entrant = userService.createUser(
                "entrant-integration@test.com", "Entrant", UserStatus.ACTIVE, Role.USER);

        // Add credits for the entrant (1 credit so submission completes)
        entryService.addCredits(
                division.getId(), entrant.getEmail(), 1, admin.getId());

        // Verify CreditsAwardedEvent was published
        var creditEvents = events.ofType(CreditsAwardedEvent.class);
        assertThat(creditEvents).hasSize(1);
        assertThat(creditEvents)
                .element(0)
                .satisfies(e -> {
                    assertThat(e.divisionId()).isEqualTo(division.getId());
                    assertThat(e.userId()).isEqualTo(entrant.getId());
                    assertThat(e.amount()).isEqualTo(1);
                    assertThat(e.source()).isEqualTo("ADMIN");
                });

        // Create an entry
        var entry = entryService.createEntry(
                division.getId(), entrant.getId(), "My Traditional Mead",
                category.getId(), Sweetness.MEDIUM, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getEntryNumber()).isEqualTo(1);
        assertThat(entry.getEntryCode()).hasSize(6);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.DRAFT);

        // Submit all drafts
        entryService.submitAllDrafts(division.getId(), entrant.getId());

        // Verify EntriesSubmittedEvent was published
        var submitEvents = events.ofType(EntriesSubmittedEvent.class);
        assertThat(submitEvents).hasSize(1);
        assertThat(submitEvents)
                .element(0)
                .satisfies(e -> {
                    assertThat(e.divisionId()).isEqualTo(division.getId());
                    assertThat(e.userId()).isEqualTo(entrant.getId());
                    assertThat(e.entryDetails()).hasSize(1);
                    assertThat(e.entryDetails().getFirst().meadName())
                            .isEqualTo("My Traditional Mead");
                });

        // Verify entry is now SUBMITTED
        var updated = entryService.findEntryById(entry.getId());
        assertThat(updated.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
    }
}
