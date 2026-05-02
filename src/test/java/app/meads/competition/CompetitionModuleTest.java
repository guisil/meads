package app.meads.competition;

import app.meads.BusinessRuleException;
import app.meads.TestcontainersConfiguration;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class CompetitionModuleTest {

    @Autowired
    CompetitionService competitionService;

    @Autowired
    UserService userService;

    @Test
    void shouldBootstrapCompetitionModule() {
        assertThat(competitionService).isNotNull();
    }

    @Test
    void shouldInitializeJudgingCategoriesFromRegistrationCategories() {
        var admin = userService.createUser("admin-judging@test.com", "Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        var competition = competitionService.createCompetition("Judging Test", "judging-test",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, admin.getId());
        // createDivision auto-populates all MJP catalog categories as REGISTRATION scope
        var division = competitionService.createDivision(competition.getId(), "Home", "home-judging",
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());

        // Get registration categories before advancing (no JUDGING exist yet)
        var registrationCategories = competitionService.findDivisionCategories(division.getId());
        var registrationCodes = registrationCategories.stream().map(DivisionCategory::getCode).toList();

        competitionService.advanceDivisionStatus(division.getId(), admin.getId()); // DRAFT → REGISTRATION_OPEN
        competitionService.advanceDivisionStatus(division.getId(), admin.getId()); // → REGISTRATION_CLOSED

        var judging = competitionService.initializeJudgingCategories(division.getId(), admin.getId());

        assertThat(judging).hasSize(registrationCategories.size());
        assertThat(judging).allMatch(c -> c.getScope() == CategoryScope.JUDGING);
        assertThat(judging).extracting(DivisionCategory::getCode)
                .containsExactlyInAnyOrderElementsOf(registrationCodes);
    }

    @Test
    void shouldManageJudgingCategoryLifecycle() {
        var admin = userService.createUser("admin-lifecycle@test.com", "Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        var competition = competitionService.createCompetition("Lifecycle Test", "lifecycle-test",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, admin.getId());
        var division = competitionService.createDivision(competition.getId(), "Home", "home-lifecycle",
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());

        competitionService.advanceDivisionStatus(division.getId(), admin.getId()); // → REGISTRATION_OPEN
        competitionService.advanceDivisionStatus(division.getId(), admin.getId()); // → REGISTRATION_CLOSED

        // Add a judging category (CX code won't conflict with MJP catalog codes)
        var cat = competitionService.addJudgingCategory(division.getId(), "CX-COMBINED",
                "Combined Melomel", "Combined category for melomel", null, admin.getId());

        assertThat(cat.getCode()).isEqualTo("CX-COMBINED");
        assertThat(cat.getScope()).isEqualTo(CategoryScope.JUDGING);

        // Update it
        var updated = competitionService.updateJudgingCategory(division.getId(), cat.getId(),
                "CX-COMBINED", "Combined Melomel (Updated)", "Updated description", admin.getId());

        assertThat(updated.getName()).isEqualTo("Combined Melomel (Updated)");

        // findJudgingCategories returns it
        var all = competitionService.findJudgingCategories(division.getId());
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getId()).isEqualTo(cat.getId());

        // Remove it
        competitionService.removeJudgingCategory(division.getId(), cat.getId(), admin.getId());

        assertThat(competitionService.findJudgingCategories(division.getId())).isEmpty();
    }

    @Test
    void shouldRejectInitializeJudgingCategoriesBeforeRegistrationClosed() {
        var admin = userService.createUser("admin-early@test.com", "Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        var competition = competitionService.createCompetition("Early Test", "early-test",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, admin.getId());
        var division = competitionService.createDivision(competition.getId(), "Home", "home-early",
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());

        // DRAFT — should reject
        assertThatThrownBy(() -> competitionService.initializeJudgingCategories(
                division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-not-allowed-status");

        competitionService.advanceDivisionStatus(division.getId(), admin.getId()); // → REGISTRATION_OPEN

        // REGISTRATION_OPEN — should still reject
        assertThatThrownBy(() -> competitionService.initializeJudgingCategories(
                division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-not-allowed-status");
    }

    @Test
    void shouldRejectDuplicateInitialization() {
        var admin = userService.createUser("admin-dup@test.com", "Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        var competition = competitionService.createCompetition("Dup Test", "dup-init-test",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, admin.getId());
        var division = competitionService.createDivision(competition.getId(), "Home", "home-dup",
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());
        competitionService.advanceDivisionStatus(division.getId(), admin.getId()); // → REGISTRATION_OPEN
        competitionService.advanceDivisionStatus(division.getId(), admin.getId()); // → REGISTRATION_CLOSED

        competitionService.initializeJudgingCategories(division.getId(), admin.getId());

        assertThatThrownBy(() -> competitionService.initializeJudgingCategories(
                division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-already-initialized");
    }
}
