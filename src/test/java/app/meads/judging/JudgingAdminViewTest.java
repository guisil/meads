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
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.entry.Sweetness;
import app.meads.entry.internal.EntryRepository;
import app.meads.judging.internal.BosPlacementRepository;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingAdminView;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingRoundRepository;
import app.meads.judging.internal.MedalAwardRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
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

import java.math.BigDecimal;
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
    JudgingRoundRepository judgingRoundRepository;

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

    @Autowired
    EntryRepository entryRepository;

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

    private void advanceDivisionToRegistrationClosed() {
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division.advanceStatus(); // → REGISTRATION_CLOSED
        division = divisionRepository.save(division);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderViewAtRegistrationClosedSoAdminsCanSetUpBeforeJudgingStarts() {
        // Admins set up rounds / judges / entries at REGISTRATION_CLOSED, then
        // advance the division to JUDGING and start the rounds.
        advanceDivisionToRegistrationClosed();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("Judging Admin");
        var tabSheet = _get(TabSheet.class);
        assertThat(tabSheet.getTabCount()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderHeaderAndFourTabsWhenDivisionInJudgingStatus() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("Judging Division");
        assertThat(heading.getText()).contains("Judging Admin");

        var tabSheet = _get(TabSheet.class);
        // Final tab layout after cycle 6c: Physical Tables / Rounds / Results / Best of Show.
        assertThat(tabSheet.getTabCount()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderResultsGridShowingCompleteRoundsOnly() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));

        var judging = judgingService.ensureJudgingExists(division.getId());

        var completeRound = new JudgingRound(judging.getId(), "Complete Round",
                category.getId(), LocalDateTime.of(2026, 7, 1, 0, 0));
        completeRound.markReady();
        completeRound.start();
        completeRound.markComplete();
        judgingRoundRepository.save(completeRound);

        var pendingRound = new JudgingRound(judging.getId(), "Pending Round",
                category.getId(), LocalDateTime.of(2026, 7, 2, 0, 0));
        judgingRoundRepository.save(pendingRound);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(2); // Results tab (after cycle 6c renumbering)

        var grids = _find(Grid.class);
        var resultsGrid = grids.stream()
                .filter(g -> "results-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Results grid not found"));

        var headers = resultsGrid.getColumns().stream()
                .map(c -> ((Grid.Column<?>) c).getHeaderText())
                .toList();
        assertThat(headers).containsExactly("Type", "Name", "Category",
                "Table", "Outcome", "Actions");

        var rendered = resultsGrid.getGenericDataView().getItems().toList();
        assertThat(rendered).hasSize(1);
        assertThat(((JudgingRound) rendered.get(0)).getName()).isEqualTo("Complete Round");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderRoundsGridAndTypeFilterOnRoundsTab() {
        advanceDivisionToJudging();
        divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Rounds tab (after cycle 6c renumbering)

        var grids = _find(Grid.class);
        var roundsGrid = grids.stream()
                .filter(g -> "rounds-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Rounds grid not found"));

        var headers = roundsGrid.getColumns().stream()
                .map(c -> ((Grid.Column<?>) c).getHeaderText())
                .toList();
        assertThat(headers).containsExactly("Type", "Name", "Category",
                "Table", "Status", "Judges", "Entries", "Scheduled", "Actions");

        var typeFilter = _get(com.vaadin.flow.component.combobox.ComboBox.class,
                spec -> spec.withId("rounds-type-filter"));
        assertThat(typeFilter).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderEntriesCountColumnNextToJudgesOnRoundsGrid() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));

        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var round = judgingService.createRound(judging.getId(), "M1A Panel",
                category.getId(), null, admin.getId());

        for (int i = 1; i <= 3; i++) {
            var entrant = userRepository.save(new User(
                    "entries-col-entrant-" + i + "-" + UUID.randomUUID() + "@example.com",
                    "Entrant " + i, UserStatus.ACTIVE, Role.USER));
            var entry = new Entry(division.getId(), entrant.getId(), i, "AMA-" + i, "Mead " + i,
                    category.getId(), Sweetness.DRY, BigDecimal.valueOf(11.0), Carbonation.STILL,
                    "Honey", null, false, null, null);
            entry.submit();
            entry.markReceived();
            entryRepository.save(entry);
            judgingService.assignEntryToRound(round.getId(), entry.getId(), admin.getId());
        }

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var roundsGrid = (Grid<JudgingRound>) _find(Grid.class).stream()
                .filter(g -> "rounds-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Rounds grid not found"));

        var headers = roundsGrid.getColumns().stream()
                .map(c -> ((Grid.Column<?>) c).getHeaderText())
                .toList();
        assertThat(headers).containsSequence("Judges", "Entries");

        var refreshed = judgingService.findRoundsByJudgingId(judging.getId()).get(0);
        assertThat(refreshed.getEntries()).hasSize(3);
    }

    // === Physical Tables tab UI tests ===

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderPhysicalTablesGridAndAddButtonOnDefaultTab() {
        advanceDivisionToJudging();
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var addButton = _get(Button.class, spec -> spec.withId("add-physical-table-button"));
        assertThat(addButton.getText()).contains("Add Table");

        var grid = _get(Grid.class, spec -> spec.withId("physical-tables-grid"));
        assertThat(grid).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAddPhysicalTableViaDialog() {
        advanceDivisionToJudging();
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        _click(_get(Button.class, spec -> spec.withId("add-physical-table-button")));

        var labelField = _get(TextField.class, spec -> spec.withId("add-physical-table-label"));
        labelField.setValue("Table 7");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        var tables = judgingService.findPhysicalTablesByDivision(division.getId());
        assertThat(tables).extracting(app.meads.judging.PhysicalTable::getLabel).contains("Table 7");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowErrorWhenAddingDuplicatePhysicalTableViaDialog() {
        advanceDivisionToJudging();
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        // With a physical table already present + no rounds, the default tab
        // is Rounds — open the dialog directly via the view method rather than
        // depending on the Tables-tab button being in DOM.
        _get(JudgingAdminView.class).openAddPhysicalTableDialog();
        _get(TextField.class, spec -> spec.withId("add-physical-table-label")).setValue("Table 1");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        var notification = _get(Notification.class);
        assertThat(notification.getElement().getProperty("text")).contains("already exists");
        // Only the seeded one is in the DB; the duplicate save was rejected.
        assertThat(judgingService.findPhysicalTablesByDivision(division.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldUpdatePhysicalTableLabelViaEditDialog() {
        advanceDivisionToJudging();
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var pt = judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");
        var view = _get(JudgingAdminView.class);
        view.openEditPhysicalTableDialog(pt);

        var labelFields = _find(TextField.class);
        var labelField = labelFields.stream()
                .filter(f -> "Table 1".equals(f.getValue()))
                .findFirst().orElseThrow();
        labelField.setValue("Renamed Table");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        var refreshed = judgingService.findPhysicalTableById(pt.getId()).orElseThrow();
        assertThat(refreshed.getLabel()).isEqualTo("Renamed Table");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDeleteUnusedPhysicalTableViaDeleteDialog() {
        advanceDivisionToJudging();
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var pt = judgingService.createPhysicalTable(division.getId(), "Disposable", admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");
        var view = _get(JudgingAdminView.class);
        view.openDeletePhysicalTableDialog(pt);

        _click(_get(Button.class, spec -> spec.withText("Delete")));

        assertThat(judgingService.findPhysicalTableById(pt.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowErrorWhenDeletingPhysicalTableInUseByRound() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var pt = judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());
        var judging = judgingService.ensureJudgingExists(division.getId());
        var round = judgingService.createRound(judging.getId(), "R1",
                category.getId(), null, admin.getId());
        judgingService.assignRoundToPhysicalTable(round.getId(), pt.getId(), admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");
        var view = _get(JudgingAdminView.class);
        view.openDeletePhysicalTableDialog(pt);

        _click(_get(Button.class, spec -> spec.withText("Delete")));

        var notification = _get(Notification.class);
        assertThat(notification.getElement().getProperty("text")).contains("in use by");
        assertThat(judgingService.findPhysicalTableById(pt.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldWarnWhenEntriesHaveNoJudgingCategory() {
        advanceDivisionToJudging();
        var regCategory = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.REGISTRATION));
        var entrant = userRepository.save(new User(
                "ja-unassigned-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-1", "Mead",
                regCategory.getId(), Sweetness.DRY, BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Honey", null, false, null, null);
        entry.submit();
        entryRepository.save(entry);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var warning = _get(Span.class, spec -> spec.withId("judging-admin-unassigned-warning"));
        assertThat(warning.getText()).contains("1");
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
        var table = judgingService.createRound(judging.getId(), "Table 1",
                category.getId(), null, admin.getId());

        view.openAssignJudgesDialog(table);

        @SuppressWarnings("unchecked")
        var judgesGrid = (Grid<User>) _get(Grid.class, spec -> spec.withId("assign-judges-grid"));
        var allJudges = judgesGrid.getGenericDataView().getItems().toList();
        judgesGrid.asMultiSelect().select(allJudges.toArray(new User[0]));

        _click(_get(Button.class, spec -> spec.withText("Save")));

        assertThat(judgingRoundRepository.countAssignmentsByTableId(table.getId())).isEqualTo(2);
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
        var pt = judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());
        var table = judgingService.createRound(judging.getId(), "Round 1",
                category.getId(), null, admin.getId());
        judgingService.assignRoundToPhysicalTable(table.getId(), pt.getId(), admin.getId());
        judgingService.assignJudge(table.getId(), judge1.getId(), admin.getId());
        judgingService.assignJudge(table.getId(), judge2.getId(), admin.getId());
        // Scoring rounds require at least one explicit entry assignment before starting.
        var entrant = userRepository.save(new User(
                "start-table-entrant-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-1", "Mead",
                category.getId(), Sweetness.DRY, BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Honey", null, false, null, null);
        entry.submit();
        entry.markReceived();
        entryRepository.save(entry);
        judgingService.assignEntryToRound(table.getId(), entry.getId(), admin.getId());

        view.openStartTableDialog(table);

        _click(_get(Button.class, spec -> spec.withText("Start")));

        var refreshed = judgingService.findRoundsByJudgingId(judging.getId()).get(0);
        assertThat(refreshed.getStatus().name()).isEqualTo("ACTIVE");
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
        var table = judgingService.createRound(judging.getId(), "Empty Table",
                category.getId(), null, admin.getId());

        view.openStartTableDialog(table);
        _click(_get(Button.class, spec -> spec.withText("Start")));

        var refreshed = judgingService.findRoundsByJudgingId(judging.getId()).get(0);
        assertThat(refreshed.getStatus().name()).isEqualTo("PENDING");
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
        var table = judgingService.createRound(judging.getId(), "Doomed Table",
                category.getId(), null, admin.getId());

        view.openDeleteTableDialog(table);

        _click(_get(Button.class, spec -> spec.withText("Delete")));

        assertThat(judgingService.findRoundsByJudgingId(judging.getId())).isEmpty();
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
        var table = judgingService.createRound(judging.getId(), "Original Name",
                category.getId(), LocalDateTime.of(2026, 7, 1, 0, 0), admin.getId());

        view.openEditTableDialog(table);

        var nameField = _get(TextField.class, spec -> spec.withId("edit-table-name"));
        nameField.setValue("Renamed Table");
        var datePicker = _get(DateTimePicker.class, spec -> spec.withId("edit-table-scheduled"));
        datePicker.setValue(LocalDateTime.of(2026, 8, 15, 9, 30));

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var refreshed = judgingService.findRoundsByJudgingId(judging.getId()).get(0);
        assertThat(refreshed.getName()).isEqualTo("Renamed Table");
        assertThat(refreshed.getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 8, 15, 9, 30));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldReassignPhysicalTableViaEditDialog() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var tableOne = judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());
        var tableTwo = judgingService.createPhysicalTable(division.getId(), "Table 2", admin.getId());
        var round = judgingService.createRound(judging.getId(), "Round 1",
                category.getId(), LocalDateTime.of(2026, 7, 1, 0, 0), admin.getId());
        judgingService.assignRoundToPhysicalTable(round.getId(), tableOne.getId(), admin.getId());

        view.openEditTableDialog(round);

        var physicalTableSelect = _get(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("edit-table-physical-table"));
        physicalTableSelect.setValue(tableTwo);

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var refreshed = judgingService.findRoundsByJudgingId(judging.getId()).get(0);
        assertThat(refreshed.getPhysicalTableId()).isEqualTo(tableTwo.getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldPrefillCurrentPhysicalTableInEditDialog() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var tableOne = judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());
        judgingService.createPhysicalTable(division.getId(), "Table 2", admin.getId());
        var round = judgingService.createRound(judging.getId(), "Round 1",
                category.getId(), LocalDateTime.of(2026, 7, 1, 0, 0), admin.getId());
        judgingService.assignRoundToPhysicalTable(round.getId(), tableOne.getId(), admin.getId());

        // The grid passes a freshly-loaded round (refreshRoundsGrid re-queries),
        // so the dialog must pre-select from a current physicalTableId, not a stale one.
        var freshRound = judgingService.findRoundsByJudgingId(judging.getId()).get(0);
        view.openEditTableDialog(freshRound);

        var physicalTableSelect = _get(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("edit-table-physical-table"));
        assertThat(physicalTableSelect.getValue()).isNotNull();
        assertThat(((app.meads.judging.PhysicalTable) physicalTableSelect.getValue()).getId())
                .isEqualTo(tableOne.getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldCreateScoringRoundWhenAddRoundDialogSaved() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        // Pre-create a physical table so the Add Round dialog can pick one.
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var physicalTable = judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Rounds tab

        _click(_get(Button.class, spec -> spec.withId("add-round-button")));

        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        var nameField = _get(TextField.class, spec -> spec.withId("add-round-name"));
        nameField.setValue("Round 1");

        var categorySelect = _get(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("add-round-category"));
        categorySelect.setValue(category);

        var physicalTableSelect = _get(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("add-round-physical-table"));
        physicalTableSelect.setValue(physicalTable);

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var judging = judgingService.ensureJudgingExists(division.getId());
        var tables = judgingService.findRoundsByJudgingId(judging.getId());
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).getName()).isEqualTo("Round 1");
        assertThat(tables.get(0).getDivisionCategoryId()).isEqualTo(category.getId());
        assertThat(tables.get(0).getPhysicalTableId()).isEqualTo(physicalTable.getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldGiveMedalRowsTheSameInlineActionSetAsScoringRows() {
        // The headline of the unified grid: a medal round is "just another round",
        // so its row gets the full action set (edit / assign judges / assign
        // entries / start / revert / delete / open) — not the old delete+open pair.
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var scoringRound = judgingService.createRound(judging.getId(), "R1",
                category.getId(), null, admin.getId());
        var medalRound = judgingService.createMedalRound(judging.getId(),
                category.getId(), admin.getId());

        var scoringCell = view.createRoundsActionsCell(scoringRound);
        var medalCell = view.createRoundsActionsCell(medalRound);

        assertThat(medalCell.getComponentCount())
                .as("medal rows get the same inline action set as scoring rows")
                .isEqualTo(scoringCell.getComponentCount())
                .isEqualTo(7);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldToggleMedalModeSelectAndNameFieldByRoundTypeInAddRoundDialog() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        view.openAddRoundDialog();

        // SCORING (default): admin-entered name visible, medal-mode picker hidden.
        assertThat(_find(TextField.class, spec -> spec.withId("add-round-name"))).hasSize(1);
        assertThat(_find(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("add-round-medal-mode"))).isEmpty();

        _get(com.vaadin.flow.component.select.Select.class, spec -> spec.withId("add-round-type"))
                .setValue(RoundType.MEDAL);

        // MEDAL: name derived from category (hidden), medal-mode picker shown.
        assertThat(_find(TextField.class, spec -> spec.withId("add-round-name"))).isEmpty();
        assertThat(_find(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("add-round-medal-mode"))).hasSize(1);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldCreateScoreBasedMedalRoundWithChosenModeViaAddRoundDialog() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var physicalTable = judgingService.createPhysicalTable(division.getId(), "Table 1", admin.getId());

        var view = _get(JudgingAdminView.class);
        view.openAddRoundDialog();

        _get(com.vaadin.flow.component.select.Select.class, spec -> spec.withId("add-round-type"))
                .setValue(RoundType.MEDAL);
        _get(com.vaadin.flow.component.select.Select.class, spec -> spec.withId("add-round-medal-mode"))
                .setValue(MedalRoundMode.SCORE_BASED);
        _get(com.vaadin.flow.component.select.Select.class, spec -> spec.withId("add-round-category"))
                .setValue(category);
        _get(com.vaadin.flow.component.select.Select.class, spec -> spec.withId("add-round-physical-table"))
                .setValue(physicalTable);

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var judging = judgingService.ensureJudgingExists(division.getId());
        var rounds = judgingService.findRoundsByJudgingId(judging.getId());
        assertThat(rounds).hasSize(1);
        assertThat(rounds.get(0).getType()).isEqualTo(RoundType.MEDAL);
        assertThat(rounds.get(0).getMedalMode())
                .as("medal mode chosen at create time should stick")
                .isEqualTo(MedalRoundMode.SCORE_BASED);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderStatusFilterWithAllStatusesSelectedOnRoundsTab() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Rounds tab

        var statusFilter = (com.vaadin.flow.component.checkbox.CheckboxGroup<JudgingRoundStatus>)
                _get(com.vaadin.flow.component.checkbox.CheckboxGroup.class,
                        spec -> spec.withId("rounds-status-filter"));
        assertThat(statusFilter.getSelectedItems())
                .as("status filter starts with every status selected (no rows hidden)")
                .containsExactlyInAnyOrder(JudgingRoundStatus.values());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRevertMedalRoundToReadyViaRevertDialog() {
        // The unified grid's Revert routes a medal round through the medal-aware
        // service path (ACTIVE -> READY, clearing awards). If it wrongly called
        // the scoring revert, the service would reject it and the round would
        // stay ACTIVE — so the READY transition proves the medal branch.
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category",
                null, 1, CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var view = _get(JudgingAdminView.class);
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var medalRound = judgingService.createMedalRound(judging.getId(), category.getId(), admin.getId());
        // Force ACTIVE directly — this test targets the Revert wiring, not the start
        // flow. resetMedalRoundById also requires the judging phase to be ACTIVE
        // (normally set by startRound), so flip that too.
        var loaded = judgingRoundRepository.findById(medalRound.getId()).orElseThrow();
        loaded.markReady();
        loaded.start();
        judgingRoundRepository.save(loaded);
        var judgingEntity = judgingRepository.findById(judging.getId()).orElseThrow();
        judgingEntity.markActive();
        judgingRepository.save(judgingEntity);

        view.openRevertRoundDialog(judgingService.findRoundById(medalRound.getId()).orElseThrow());
        _click(_get(Button.class, spec -> spec.withText("Revert")));

        var reverted = judgingService.findRoundById(medalRound.getId()).orElseThrow();
        assertThat(reverted.getStatus()).isEqualTo(JudgingRoundStatus.READY);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderDisabledMessageOnBosTabWhenJudgingPhaseNotStarted() {
        advanceDivisionToJudging();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/judging-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(3); // BOS tab (index shifted)

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
        tabSheet.setSelectedIndex(3); // BOS tab (index shifted)

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
        tabSheet.setSelectedIndex(3); // BOS tab (index shifted by Physical Tables tab)

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
        tabSheet.setSelectedIndex(3); // BOS tab (index shifted)

        var expectedHref = "competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/bos";
        var managePlacements = _find(Anchor.class).stream()
                .filter(a -> expectedHref.equals(a.getHref()))
                .findFirst();
        assertThat(managePlacements).as("Manage placements deep link").isPresent();
    }
}
