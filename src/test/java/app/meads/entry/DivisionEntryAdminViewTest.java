package app.meads.entry;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.*;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.entry.internal.EntryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.entry.internal.EntryCreditRepository;
import app.meads.competition.internal.ParticipantRepository;
import app.meads.competition.internal.ParticipantRoleRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingService;
import app.meads.judging.Scoresheet;
import app.meads.judging.internal.MjpScoringFieldDefinition;
import app.meads.judging.internal.ScoresheetRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.data.provider.SortDirection;
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

import static com.github.mvysny.kaributesting.v10.GridKt._getCellComponent;
import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;
import com.vaadin.flow.component.html.Span;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class DivisionEntryAdminViewTest {

    private static final String ADMIN_EMAIL = "entryadmin-admin@example.com";

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    ParticipantRepository participantRepository;

    @Autowired
    ParticipantRoleRepository participantRoleRepository;

    @Autowired
    DivisionCategoryRepository divisionCategoryRepository;

    @Autowired
    EntryService entryService;

    @Autowired
    EntryCreditRepository creditRepository;

    @Autowired
    EntryRepository entryRepository;

    @Autowired
    JudgingService judgingService;

    @Autowired
    ScoresheetRepository scoresheetRepository;

    private Competition competition;
    private Division division;

    @BeforeEach
    void setup(TestInfo testInfo) {
        var admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ADMIN_EMAIL, "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "Entry Admin Test Competition", "entry-admin-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        division = divisionRepository.save(new Division(
                competition.getId(), "Admin Division", "admin-div-" + suffix, ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));

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

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayTabsForEntryAdmin() {
        UI.getCurrent().navigate("competitions/" + competition.getShortName() + "/divisions/" + division.getShortName() + "/entry-admin");

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("Admin Division");

        var tabSheet = _get(TabSheet.class);
        assertThat(tabSheet).isNotNull();
        assertThat(tabSheet.getTabCount()).isEqualTo(4);
    }

    @SuppressWarnings("unchecked")
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowMeaderyAndCountryColumnsInEntriesTab() {
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");

        // Select the Entries tab
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Entries is the second tab

        // Find the entries grid (it has id "entries-grid")
        var grids = _find(Grid.class);
        var entriesGrid = grids.stream()
                .filter(g -> "entries-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entries grid not found"));

        var headers = entriesGrid.getColumns().stream()
                .map(c -> ((Grid.Column<?>) c).getHeaderText())
                .toList();
        assertThat(headers).contains("Meadery", "Country");
    }

    @SuppressWarnings("unchecked")
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowEntriesSummaryWithCorrectTotals() {
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division = divisionRepository.save(division);

        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        creditRepository.save(new EntryCredit(division.getId(), admin.getId(), 3, "ADMIN", "test"));

        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category", null, 1));
        var entry = entryService.createEntry(division.getId(), admin.getId(),
                "Test Mead", category.getId(),
                Sweetness.DRY, new BigDecimal("12.0"),
                Carbonation.STILL, "Honey", null, false, null, null);
        entryService.submitEntry(entry.getId(), admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Entries tab

        var creditsLabel = _get(Span.class, spec -> spec.withId("credits-balance-label"));
        var entriesLabel = _get(Span.class, spec -> spec.withId("entries-summary-label"));

        assertThat(creditsLabel.getText()).isEqualTo("Credits balance: 3");
        assertThat(entriesLabel.getText()).isEqualTo("Total entries: 1 (Draft: 0, Submitted: 1, Received: 0, Withdrawn: 0)");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowDownloadAllLabelsButtonInEntriesTab() {
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");

        // Select the Entries tab
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        // The "Download all labels" button should exist in the toolbar
        var downloadBtn = _get(Button.class, spec -> spec.withText("Download all labels"));
        assertThat(downloadBtn).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisableAddEntryButtonWhenDivisionPastJudging() {
        for (int i = 0; i < 4; i++) {
            division.advanceStatus(); // DRAFT -> ... -> DELIBERATION
        }
        division = divisionRepository.save(division);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Entries tab

        var addBtn = _get(Button.class, spec -> spec.withId("add-entry-button"));
        assertThat(addBtn.isEnabled())
                .as("Add Entry must be disabled once the division is past judging (P21)")
                .isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderEntriesGridWithCorrectColumnsForSubmittedEntry() {
        // Advance division and add credits so entry can be created
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division = divisionRepository.save(division);

        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        creditRepository.save(new EntryCredit(division.getId(), admin.getId(), 1, "ADMIN", "test"));

        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead category", null, 1));
        var entry = entryService.createEntry(division.getId(), admin.getId(),
                "Test Mead", category.getId(),
                Sweetness.DRY, new BigDecimal("12.0"),
                Carbonation.STILL, "Honey", null, false, null, null);
        entryService.submitEntry(entry.getId(), admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        // Find the entries grid
        var grids = _find(Grid.class);
        var entriesGrid = grids.stream()
                .filter(g -> "entries-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entries grid not found"));

        // Grid has 11 columns: Entry #, Code, Mead Name, Category, Final Category,
        // Entrant, Meadery, Country, Status, Scoresheet, Actions (view/edit/←/→/withdraw/delete)
        // Note: buttons inside addComponentColumn are not reachable via _find (Karibu limitation).
        // Advance/revert service behavior is tested in EntryServiceTest.
        assertThat(entriesGrid.getColumns()).hasSize(11);

        @SuppressWarnings("rawtypes")
        var headers = entriesGrid.getColumns().stream()
                .map(c -> ((Grid.Column) c).getHeaderText())
                .filter(h -> h instanceof String s && !s.isBlank())
                .map(Object::toString)
                .toList();
        assertThat(headers).containsExactlyInAnyOrder(
                "Entry #", "Code", "Mead Name", "Category", "Final Category",
                "Entrant", "Meadery", "Country", "Status", "Scoresheet", "Actions");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderEntriesGridWhenEntryHasFinalCategoryAssigned() {
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division.advanceStatus(); // → REGISTRATION_CLOSED
        division = divisionRepository.save(division);

        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        var registrationCategory = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead", null, 1));
        var judgingCategory = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "CX1", "Combined", "Combined judging", null, 1,
                CategoryScope.JUDGING));

        // Create entry directly via repository (bypasses credit check — admin scenario)
        var entry = new Entry(division.getId(), admin.getId(), 1, "ABCDEF",
                "Test Mead", registrationCategory.getId(), Sweetness.DRY,
                new BigDecimal("12.0"), Carbonation.STILL, "Honey", null, false, null, null);
        entry = entryRepository.save(entry);
        entry.assignFinalCategory(judgingCategory.getId());
        entryRepository.save(entry);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var grids = _find(Grid.class);
        var entriesGrid = grids.stream()
                .filter(g -> "entries-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entries grid not found"));

        // Grid renders without error and has the correct column count
        assertThat(entriesGrid.getColumns()).hasSize(11);

        // Entry with final category is present in the grid
        @SuppressWarnings("unchecked")
        var items = entriesGrid.getGenericDataView().getItems().toList();
        assertThat(items).hasSize(1);
        assertThat(((Entry) items.getFirst()).getFinalCategoryId()).isEqualTo(judgingCategory.getId());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldSortEntriesByFinalCategoryCode() {
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division.advanceStatus(); // → REGISTRATION_CLOSED
        division = divisionRepository.save(division);

        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        var registrationCategory = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead", null, 1));
        var aaaCategory = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "AAA", "Alpha", "Alpha judging", null, 1,
                CategoryScope.JUDGING));
        var zzzCategory = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "ZZZ", "Omega", "Omega judging", null, 2,
                CategoryScope.JUDGING));

        var aaaEntry = entryRepository.save(new Entry(division.getId(), admin.getId(), 1, "ENTAAA",
                "Alpha Mead", registrationCategory.getId(), Sweetness.DRY,
                new BigDecimal("12.0"), Carbonation.STILL, "Honey", null, false, null, null));
        aaaEntry.assignFinalCategory(aaaCategory.getId());
        aaaEntry = entryRepository.save(aaaEntry);

        var zzzEntry = entryRepository.save(new Entry(division.getId(), admin.getId(), 2, "ENTZZZ",
                "Omega Mead", registrationCategory.getId(), Sweetness.DRY,
                new BigDecimal("12.0"), Carbonation.STILL, "Honey", null, false, null, null));
        zzzEntry.assignFinalCategory(zzzCategory.getId());
        zzzEntry = entryRepository.save(zzzEntry);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var grids = _find(Grid.class);
        var entriesGrid = grids.stream()
                .filter(g -> "entries-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entries grid not found"));

        Grid<Entry> typedGrid = entriesGrid;
        Grid.Column<Entry> finalCategoryColumn = typedGrid.getColumns().stream()
                .filter(c -> "Final Category".equals(c.getHeaderText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Final Category column not found"));

        var comparator = finalCategoryColumn.getComparator(SortDirection.ASCENDING);
        assertThat(comparator).as("Final Category column must have a comparator").isNotNull();
        assertThat(comparator.compare(aaaEntry, zzzEntry))
                .as("AAA must sort before ZZZ ascending").isNegative();
        assertThat(comparator.compare(zzzEntry, aaaEntry))
                .as("ZZZ must sort after AAA ascending").isPositive();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldLinkToScoresheetWhenEntryHasSubmittedScoresheet() {
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var category = judgingCategoryInJudgingDivision();
        var round = createScoringRound(category, admin.getId());

        var judgedEntry = receivedEntry(1, "JUDGED", category.getId(), admin.getId());
        receivedEntry(2, "UNJUDG", category.getId(), admin.getId());
        var sheet = submittedScoresheet(round, judgedEntry);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");
        var entriesGrid = entriesGrid();

        var judgedCell = _getCellComponent(entriesGrid, 0, "scoresheet");
        assertThat(judgedCell).isInstanceOf(Anchor.class);
        assertThat(((Anchor) judgedCell).getHref()).isEqualTo("competitions/"
                + competition.getShortName() + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());
        assertThat(((Anchor) judgedCell).getText()).isEqualTo("Scored");

        var unjudgedCell = _getCellComponent(entriesGrid, 1, "scoresheet");
        assertThat(unjudgedCell).isInstanceOf(Span.class);
        assertThat(((Span) unjudgedCell).getText()).isEqualTo("\u2014");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowInProgressLinkWhenEntryScoresheetIsNotSubmitted() {
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var category = judgingCategoryInJudgingDivision();
        var round = createScoringRound(category, admin.getId());
        var entry = receivedEntry(1, "DRAFTS", category.getId(), admin.getId());
        var sheet = new Scoresheet(round.getId(), entry.getId());
        sheet.updateOverallComments("Started tasting");
        sheet = scoresheetRepository.save(sheet);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");
        var entriesGrid = entriesGrid();

        var cell = _getCellComponent(entriesGrid, 0, "scoresheet");
        assertThat(cell).isInstanceOf(Anchor.class);
        assertThat(((Anchor) cell).getHref()).endsWith("/scoresheets/" + sheet.getId());
        assertThat(((Anchor) cell).getText()).isEqualTo("In progress");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldCountScoredOutOfReceivedEntriesWhenDivisionIsJudging() {
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var category = judgingCategoryInJudgingDivision();
        var round = createScoringRound(category, admin.getId());
        var scored = receivedEntry(1, "SCORED", category.getId(), admin.getId());
        receivedEntry(2, "UNJUDG", category.getId(), admin.getId());
        var notReceived = new Entry(division.getId(), admin.getId(), 3, "NOTREC", "Mead 3",
                category.getId(), Sweetness.DRY, new BigDecimal("12.0"), Carbonation.STILL,
                "Honey", null, false, null, null);
        notReceived.submit();
        entryRepository.save(notReceived);
        submittedScoresheet(round, scored);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");
        entriesGrid();

        var label = _get(Span.class, spec -> spec.withId("scoresheets-summary-label"));
        assertThat(label.getText()).isEqualTo("Scored: 1/2");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldHideScoredCountWhenDivisionIsBeforeJudging() {
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division = divisionRepository.save(division);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");
        entriesGrid();

        assertThat(_find(Span.class, spec -> spec.withId("scoresheets-summary-label"))).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldOfferJudgingLabelsForReceivedEntriesOnlyWhenDownloadClicked() {
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var category = judgingCategoryInJudgingDivision();
        receivedEntry(1, "RECEI1", category.getId(), admin.getId());
        receivedEntry(2, "RECEI2", category.getId(), admin.getId());
        var submittedOnly = new Entry(division.getId(), admin.getId(), 3, "SUBMIT", "Mead 3",
                category.getId(), Sweetness.DRY, new BigDecimal("12.0"), Carbonation.STILL,
                "Honey", null, false, null, null);
        submittedOnly.submit();
        entryRepository.save(submittedOnly);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/entry-admin");
        entriesGrid();
        _click(_get(Button.class, spec -> spec.withId("download-judging-labels-button")));

        var dialog = _get(Dialog.class, spec -> spec.withId("judging-labels-dialog"));
        assertThat(_get(dialog, Span.class).getText())
                .isEqualTo("This will generate judging labels for 2 received entries. Continue?");
    }

    private DivisionCategory judgingCategoryInJudgingDivision() {
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division.advanceStatus(); // → REGISTRATION_CLOSED
        division.advanceStatus(); // → JUDGING
        division = divisionRepository.save(division);
        return divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Dry mead", null, 1,
                CategoryScope.JUDGING));
    }

    private JudgingRound createScoringRound(DivisionCategory category, UUID adminId) {
        var judging = judgingService.ensureJudgingExists(division.getId());
        return judgingService.createRound(judging.getId(), "M1A Panel",
                category.getId(), null, adminId);
    }

    @SuppressWarnings("unchecked")
    private Grid<Entry> entriesGrid() {
        _get(TabSheet.class).setSelectedIndex(1);
        return _find(Grid.class).stream()
                .filter(g -> "entries-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entries grid not found"));
    }

    private Scoresheet submittedScoresheet(JudgingRound round, Entry entry) {
        var sheet = new Scoresheet(round.getId(), entry.getId());
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            sheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        sheet.markFilled();
        sheet.submit();
        return scoresheetRepository.save(sheet);
    }

    private Entry receivedEntry(int number, String code, UUID categoryId, UUID userId) {
        var entry = new Entry(division.getId(), userId, number, code, "Mead " + number,
                categoryId, Sweetness.DRY, new BigDecimal("12.0"), Carbonation.STILL,
                "Honey", null, false, null, null);
        entry.submit();
        entry.markReceived();
        entry.assignFinalCategory(categoryId);
        return entryRepository.save(entry);
    }
}
