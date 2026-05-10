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
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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
}
