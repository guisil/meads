package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CategoryScope;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.entry.Carbonation;
import app.meads.entry.Entry;
import app.meads.entry.Sweetness;
import app.meads.entry.internal.EntryRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.entry.EntryService;
import app.meads.judging.internal.JudgingTableRepository;
import app.meads.judging.internal.MjpScoringFieldDefinition;
import app.meads.judging.internal.ScoresheetRepository;
import app.meads.judging.internal.TableView;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.select.Select;
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
class TableViewTest {

    private static final String ADMIN_EMAIL = "table-view-admin-test@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired DivisionCategoryRepository divisionCategoryRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired EntryService entryService;
    @Autowired JudgingTableRepository judgingTableRepository;
    @Autowired ScoresheetRepository scoresheetRepository;
    @Autowired JudgingService judgingService;

    private Competition competition;
    private Division division;

    @BeforeEach
    void setup(TestInfo testInfo) {
        userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ADMIN_EMAIL, "Table View Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "Table View Test Competition", "table-view-comp-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        division = divisionRepository.save(new Division(
                competition.getId(), "Amadora", "table-view-div-" + suffix,
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
        division.advanceStatus();
        division.advanceStatus();
        division.advanceStatus();
        division = divisionRepository.save(division);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderHeaderForSystemAdminWhenTableExists() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table A",
                category.getId(), null, admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/tables/" + table.getId());

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("Amadora");
        assertThat(heading.getText()).contains("Table A");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderScoresheetsGridWithExpectedColumns() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table A",
                category.getId(), null, admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/tables/" + table.getId());

        var grids = _find(Grid.class);
        var scoresheetsGrid = grids.stream()
                .filter(g -> "scoresheets-grid".equals(g.getId().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Scoresheets grid not found"));

        var headers = scoresheetsGrid.getColumns().stream()
                .map(c -> ((Grid.Column<?>) c).getHeaderText())
                .toList();
        assertThat(headers).containsExactly("Entry", "Mead name", "Status",
                "Total", "Filled by", "Actions");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderFilterBarWithStatusSelectAndSearchField() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table A",
                category.getId(), null, admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/tables/" + table.getId());

        var statusFilter = (Select<String>) _get(Select.class, spec -> spec.withId("status-filter"));
        assertThat(statusFilter.getValue()).isEqualTo("All");
        var options = statusFilter.getListDataView().getItems().toList();
        assertThat(options).containsExactly("All", "Draft", "Submitted");

        var searchField = _get(TextField.class, spec -> spec.withId("search-field"));
        assertThat(searchField).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRevertSubmittedScoresheetToDraftWhenAdminConfirms() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var entrant = userRepository.save(new User(
                "entrant-revert-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table A",
                category.getId(), null, admin.getId());

        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-1",
                "Test Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Wildflower", null, false, null, null);
        entry = entryRepository.save(entry);

        var sheet = new app.meads.judging.Scoresheet(table.getId(), entry.getId());
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            sheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        sheet.submit();
        var savedSheet = scoresheetRepository.save(sheet);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/tables/" + table.getId());

        var view = _get(TableView.class);
        view.openRevertDialog(savedSheet);
        _click(_get(Button.class, spec -> spec.withText("Revert")));

        var reverted = scoresheetRepository.findById(savedSheet.getId()).orElseThrow();
        assertThat(reverted.getStatus()).isEqualTo(app.meads.judging.ScoresheetStatus.DRAFT);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldMoveDraftScoresheetToAnotherTableWhenAdminConfirms() {
        advanceDivisionToJudging();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var entrant = userRepository.save(new User(
                "entrant-move-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table1 = judgingService.createTable(judging.getId(), "Table A",
                category.getId(), null, admin.getId());
        var table2 = judgingService.createTable(judging.getId(), "Table B",
                category.getId(), null, admin.getId());

        // Both tables must be ROUND_1 for move-target to be valid.
        var t1 = judgingTableRepository.findById(table1.getId()).orElseThrow();
        t1.startRound1();
        judgingTableRepository.save(t1);
        var t2 = judgingTableRepository.findById(table2.getId()).orElseThrow();
        t2.startRound1();
        judgingTableRepository.save(t2);

        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-1",
                "Test Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Wildflower", null, false, null, null);
        entry = entryRepository.save(entry);
        entryService.assignFinalCategory(entry.getId(), category.getId(), admin.getId());

        var sheet = new app.meads.judging.Scoresheet(table1.getId(), entry.getId());
        var savedSheet = scoresheetRepository.save(sheet);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/tables/" + table1.getId());

        var view = _get(TableView.class);
        view.openMoveDialog(savedSheet);

        var targetSelect = (Select<app.meads.judging.JudgingTable>) _get(
                Select.class, spec -> spec.withId("move-target-select"));
        var t2Refreshed = judgingTableRepository.findById(table2.getId()).orElseThrow();
        targetSelect.setValue(t2Refreshed);

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var moved = scoresheetRepository.findById(savedSheet.getId()).orElseThrow();
        assertThat(moved.getTableId()).isEqualTo(table2.getId());
    }
}
