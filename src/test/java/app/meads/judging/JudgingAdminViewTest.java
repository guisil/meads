package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CategoryScope;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.entry.Carbonation;
import app.meads.entry.EntryService;
import app.meads.entry.Sweetness;
import app.meads.judging.internal.BosPlacementRepository;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingAdminView;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingTableRepository;
import app.meads.judging.internal.MedalAwardRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class JudgingAdminViewTest {

    private static final String ADMIN_EMAIL = "judging-admin-test@example.com";

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    DivisionCategoryRepository divisionCategoryRepository;

    @Autowired
    JudgingService judgingService;

    @Autowired
    CompetitionService competitionService;

    @Autowired
    JudgingTableRepository judgingTableRepository;

    @Autowired
    CategoryJudgingConfigRepository categoryJudgingConfigRepository;

    @Autowired
    JudgingRepository judgingRepository;

    @Autowired
    MedalAwardRepository medalAwardRepository;

    @Autowired
    BosPlacementRepository bosPlacementRepository;

    @Autowired
    EntryService entryService;

    private Competition competition;
    private Division division;

    @BeforeEach
    void setup(TestInfo testInfo) {
        userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ADMIN_EMAIL, "Judging Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "Judging Admin Test Competition", "judging-admin-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        division = divisionRepository.save(new Division(
                competition.getId(), "Judging Division", "judging-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));

        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);

        var authentication = resolveAuthentication(testInfo);
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        propagateSecurityContext(authentication);
    }

    private Authentication resolveAuthentication(TestInfo testInfo) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth;
        }
        var method = testInfo.getTestMethod().orElse(null);
        if (method == null) {
            return null;
        }
        var withMockUser = method.getAnnotation(WithMockUser.class);
        if (withMockUser == null) {
            return null;
        }
        var username = withMockUser.username().isEmpty() ? withMockUser.value() : withMockUser.username();
        if (username.isEmpty()) {
            username = "user";
        }
        var authorities = Arrays.stream(withMockUser.roles())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password("password")
                .authorities(authorities)
                .build();
        return new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    }

    private void propagateSecurityContext(Authentication authentication) {
        if (authentication != null) {
            var fakeRequest = (FakeRequest) VaadinServletRequest.getCurrent().getRequest();
            fakeRequest.setUserPrincipalInt(authentication);
            fakeRequest.setUserInRole((principal, role) ->
                    authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role)));
        }
    }

    private void advanceDivisionToJudging() {
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division.advanceStatus(); // → REGISTRATION_CLOSED
        division.advanceStatus(); // → JUDGING
        division = divisionRepository.save(division);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderHeaderAndThreeTabsWhenDivisionInJudgingStatus() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("Judging Division");
        assertThat(heading.getText()).contains("Judging Admin");

        var tabSheet = _get(TabSheet.class);
        assertThat(tabSheet.getTabCount()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderTablesGridAndAddTableButtonOnTablesTab() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(0); // Tables tab

        var addButton = _get(Button.class, spec -> spec.withText("Add Table"));
        assertThat(addButton).isNotNull();

        var grids = _find(Grid.class);
        var tablesGrid = grids.stream()
                .filter(g -> "tables-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tables grid not found"));

        var headers = tablesGrid.getColumns().stream()
                .map(c -> ((Grid.Column<?>) c).getHeaderText())
                .toList();
        assertThat(headers).containsExactly("Name", "Category", "Status",
                "Judges", "Scheduled", "Scoresheets", "Actions");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldStartMedalRoundWhenStartDialogConfirmed() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        // The view's render lazily created a default-COMPARATIVE PENDING config; advance it to READY.
        var config = categoryJudgingConfigRepository.findByDivisionCategoryId(category.getId())
                .orElseThrow();
        config.markReady();
        config = categoryJudgingConfigRepository.save(config);

        var view = _get(JudgingAdminView.class);
        view.openStartMedalRoundDialog(config);
        _click(_get(Button.class, spec -> spec.withText("Start")));

        var refreshed = categoryJudgingConfigRepository.findByDivisionCategoryId(category.getId())
                .orElseThrow();
        assertThat(refreshed.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.ACTIVE);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldBlockResetMedalRoundUntilTypingResetExactly() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        // Reset requires Judging.phase=ACTIVE.
        var judging = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        judging.markActive();
        judgingRepository.save(judging);

        var config = categoryJudgingConfigRepository.findByDivisionCategoryId(category.getId())
                .orElseThrow();
        config.markReady();
        config.startMedalRound();
        config = categoryJudgingConfigRepository.save(config);

        var view = _get(JudgingAdminView.class);
        view.openResetMedalRoundDialog(config);

        // Click Reset without typing — should NOT call service (status stays ACTIVE).
        _click(_get(Button.class, spec -> spec.withText("Reset")));
        var stillActive = categoryJudgingConfigRepository.findByDivisionCategoryId(category.getId())
                .orElseThrow();
        assertThat(stillActive.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.ACTIVE);

        // Type RESET and click — now resets to READY.
        var confirmField = _get(TextField.class, spec -> spec.withId("reset-confirm-field"));
        confirmField.setValue("RESET");
        _click(_get(Button.class, spec -> spec.withText("Reset")));
        var afterReset = categoryJudgingConfigRepository.findByDivisionCategoryId(category.getId())
                .orElseThrow();
        assertThat(afterReset.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.READY);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderMedalRoundsGridWithCategoryConfigs() {
        advanceDivisionToJudging();
        divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1B", "Medium Mead", "Desc",
                null, 2, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Medal Rounds tab

        var grids = _find(Grid.class);
        var medalGrid = grids.stream()
                .filter(g -> "medal-rounds-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Medal rounds grid not found"));
        var rows = medalGrid.getGenericDataView().getItems().toList();
        assertThat(rows).hasSize(2);

        var headers = medalGrid.getColumns().stream()
                .map(c -> ((Grid.Column<?>) c).getHeaderText())
                .toList();
        assertThat(headers).containsExactly("Category", "Mode", "Status", "Tables", "Awards", "Actions");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAssignJudgesWhenAssignDialogSaved() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        competitionService.addParticipantByEmail(competition.getId(),
                "judge-a@example.com", CompetitionRole.JUDGE, admin.getId());
        competitionService.addParticipantByEmail(competition.getId(),
                "judge-b@example.com", CompetitionRole.JUDGE, admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table 1",
                category.getId(), null, admin.getId());

        view.openAssignJudgesDialog(table);

        @SuppressWarnings("unchecked")
        var judgesGrid = (Grid<User>) _get(Grid.class, spec -> spec.withId("assign-judges-grid"));
        var allJudges = judgesGrid.getGenericDataView().getItems().toList();
        judgesGrid.asMultiSelect().select(allJudges.toArray(new User[0]));

        _click(_get(Button.class, spec -> spec.withText("Save")));

        assertThat(judgingTableRepository.countAssignmentsByTableId(table.getId())).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldStartTableWhenJudgesMeetMinimum() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));
        var judge1 = userRepository.save(new User("judge1@example.com", "Judge 1",
                UserStatus.ACTIVE, Role.USER));
        var judge2 = userRepository.save(new User("judge2@example.com", "Judge 2",
                UserStatus.ACTIVE, Role.USER));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var table = judgingService.createTable(judging.getId(), "Table 1",
                category.getId(), null, admin.getId());
        judgingService.assignJudge(table.getId(), judge1.getId(), admin.getId());
        judgingService.assignJudge(table.getId(), judge2.getId(), admin.getId());

        view.openStartTableDialog(table);

        _click(_get(Button.class, spec -> spec.withText("Start")));

        var refreshed = judgingService.findTablesByJudgingId(judging.getId()).get(0);
        assertThat(refreshed.getStatus().name()).isEqualTo("ROUND_1");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRejectStartWhenJudgesBelowMinimum() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var table = judgingService.createTable(judging.getId(), "Empty Table",
                category.getId(), null, admin.getId());

        view.openStartTableDialog(table);
        _click(_get(Button.class, spec -> spec.withText("Start")));

        var refreshed = judgingService.findTablesByJudgingId(judging.getId()).get(0);
        assertThat(refreshed.getStatus().name()).isEqualTo("NOT_STARTED");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDeleteTableWhenDeleteDialogConfirmed() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var table = judgingService.createTable(judging.getId(), "Doomed Table",
                category.getId(), null, admin.getId());

        view.openDeleteTableDialog(table);

        _click(_get(Button.class, spec -> spec.withText("Delete")));

        assertThat(judgingService.findTablesByJudgingId(judging.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldUpdateTableWhenEditDialogSaved() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var table = judgingService.createTable(judging.getId(), "Original Name",
                category.getId(), LocalDate.of(2026, 7, 1), admin.getId());

        view.openEditTableDialog(table);

        var nameField = _get(TextField.class, spec -> spec.withId("edit-table-name"));
        nameField.setValue("Renamed Table");
        var datePicker = _get(DatePicker.class, spec -> spec.withId("edit-table-scheduled"));
        datePicker.setValue(LocalDate.of(2026, 8, 15));

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var refreshed = judgingService.findTablesByJudgingId(judging.getId()).get(0);
        assertThat(refreshed.getName()).isEqualTo("Renamed Table");
        assertThat(refreshed.getScheduledDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldCreateTableWhenAddTableDialogSaved() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(0);

        _click(_get(Button.class, spec -> spec.withText("Add Table")));

        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        var nameField = _get(TextField.class, spec -> spec.withId("add-table-name"));
        nameField.setValue("Table 1");

        var categorySelect = _get(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("add-table-category"));
        categorySelect.setValue(category);

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var judging = judgingService.ensureJudgingExists(division.getId());
        var tables = judgingService.findTablesByJudgingId(judging.getId());
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).getName()).isEqualTo("Table 1");
        assertThat(tables.get(0).getDivisionCategoryId()).isEqualTo(category.getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderDisabledMessageOnBosTabWhenJudgingPhaseNotStarted() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(2); // BOS tab

        var spans = _find(com.vaadin.flow.component.html.Span.class);
        var hasDisabledMessage = spans.stream()
                .anyMatch(s -> s.getText() != null && s.getText().contains("BOS round is unavailable"));
        assertThat(hasDisabledMessage).isTrue();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderBosPlacementsGridWithEmptySlotsWhenPhaseActive() {
        // Configure 3 BOS places while still in DRAFT
        division.updateBosPlaces(3);
        division = divisionRepository.save(division);
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        // Force phase to ACTIVE
        var judging = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        judging.markActive();
        judgingRepository.save(judging);

        // Re-render
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(2); // BOS tab

        var grids = _find(Grid.class);
        var placementsGrid = grids.stream()
                .filter(g -> "bos-placements-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("BOS placements grid not found"));

        var rows = placementsGrid.getGenericDataView().getItems().toList();
        assertThat(rows).hasSize(3); // bosPlaces=3 — all rows rendered (all empty)
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderEmptyCandidatesMessageWhenNoGoldMedals() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var judging = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        judging.markActive();
        judgingRepository.save(judging);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(2);

        var emptyMsg = _get(com.vaadin.flow.component.html.Span.class,
                spec -> spec.withId("bos-candidates-empty"));
        assertThat(emptyMsg.getText()).contains("No GOLD medals");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldFinalizeBosWhenFinalizeDialogConfirmed() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        // Force phase to BOS via repo so we don't have to set up medal-rounds.
        var judging = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        judging.markActive();
        judging.startBos();
        judgingRepository.save(judging);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        view.openFinalizeBosDialog();

        _click(_get(Button.class, spec -> spec.withText("Finalize BOS")));

        var refreshed = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        assertThat(refreshed.getPhase()).isEqualTo(JudgingPhase.COMPLETE);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldResetBosWhenNoPlacementsExist() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var judging = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        judging.markActive();
        judging.startBos();
        judgingRepository.save(judging);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        view.openResetBosDialog();

        _click(_get(Button.class, spec -> spec.withText("Reset BOS")));

        var refreshed = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        assertThat(refreshed.getPhase()).isEqualTo(JudgingPhase.ACTIVE);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldReopenBosWhenReopenDialogConfirmed() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var judging = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        judging.markActive();
        judging.startBos();
        judging.completeBos();
        judgingRepository.save(judging);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        view.openReopenBosDialog();

        _click(_get(Button.class, spec -> spec.withText("Reopen BOS")));

        var refreshed = judgingRepository.findByDivisionId(division.getId()).orElseThrow();
        assertThat(refreshed.getPhase()).isEqualTo(JudgingPhase.BOS);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderManagePlacementsDeepLinkOnBosTab() {
        advanceDivisionToJudging();
        var judging = judgingRepository.findByDivisionId(division.getId())
                .orElseGet(() -> judgingService.ensureJudgingExists(division.getId()));
        judging.markActive();
        judgingRepository.save(judging);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(2); // BOS tab

        var expectedHref = "competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/bos";
        var managePlacements = _find(Anchor.class).stream()
                .filter(a -> expectedHref.equals(a.getHref()))
                .findFirst();
        assertThat(managePlacements).as("Manage placements deep link").isPresent();
    }
}
