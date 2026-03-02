package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CategoryRepository;
import app.meads.competition.internal.CompetitionCategoryRepository;
import app.meads.competition.internal.CompetitionDetailView;
import app.meads.competition.internal.CompetitionParticipantRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.EventParticipantRepository;
import app.meads.competition.internal.MeadEventRepository;
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
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.component.tabs.TabSheet;
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

import java.time.LocalDate;
import java.util.Arrays;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class CompetitionDetailViewTest {

    private static final String ADMIN_EMAIL = "detailview-admin@example.com";

    @Autowired
    ApplicationContext ctx;

    @Autowired
    MeadEventRepository meadEventRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    EventParticipantRepository eventParticipantRepository;

    @Autowired
    CompetitionParticipantRepository competitionParticipantRepository;

    @Autowired
    CompetitionCategoryRepository competitionCategoryRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    private Competition testCompetition;
    private MeadEvent testEvent;

    @BeforeEach
    void setup(TestInfo testInfo) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            userRepository.save(new User(ADMIN_EMAIL,
                    "Detail Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
        }

        testEvent = meadEventRepository.save(new MeadEvent("Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        testCompetition = competitionRepository.save(new Competition(
                testEvent.getId(), "Home", ScoringSystem.MJP));

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
    void shouldDisplayCompetitionHeader() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var heading = _get(H2.class, spec -> spec.withText("Home"));
        assertThat(heading).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayStatusBadge() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var badges = _find(Span.class);
        assertThat(badges).anyMatch(span ->
                span.getElement().getThemeList().contains("badge")
                        && span.getText().equals("Draft"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayTabSheet() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var tabSheet = _get(TabSheet.class);
        assertThat(tabSheet).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayParticipantsGrid() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var grids = _find(Grid.class);
        assertThat(grids).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(username = "comp-admin@example.com", roles = "USER")
    void shouldAllowCompetitionAdminAccess() {
        var compAdminUser = userRepository.findByEmail("comp-admin@example.com")
                .orElseGet(() -> userRepository.save(new User("comp-admin@example.com",
                        "Comp Admin", UserStatus.ACTIVE, Role.USER)));
        var ep = eventParticipantRepository.save(
                new EventParticipant(testEvent.getId(), compAdminUser.getId()));
        competitionParticipantRepository.save(
                new CompetitionParticipant(testCompetition.getId(), ep.getId(),
                        CompetitionRole.COMPETITION_ADMIN));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var heading = _get(H2.class, spec -> spec.withText("Home"));
        assertThat(heading).isNotNull();
    }

    @Test
    @WithMockUser(username = "unauthorized@example.com", roles = "USER")
    void shouldRedirectUnauthorizedUser() {
        userRepository.findByEmail("unauthorized@example.com")
                .orElseGet(() -> userRepository.save(new User("unauthorized@example.com",
                        "Unauthorized", UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        // Should have been forwarded away — no H2 heading should be present
        var headings = _find(H2.class);
        assertThat(headings).noneMatch(h -> h.getText().equals("Home"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowEmailFieldInAddDialog() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var addButton = _get(Button.class, spec -> spec.withText("Add Participant"));
        _click(addButton);

        var dialog = _get(Dialog.class);
        assertThat(dialog).isNotNull();
        var emailField = _get(dialog, TextField.class, spec -> spec.withCaption("Email"));
        assertThat(emailField).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayBreadcrumb() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var nav = _get(Nav.class);
        assertThat(nav).isNotNull();
        var routerLink = _get(nav, RouterLink.class);
        assertThat(routerLink.getText()).isEqualTo(testEvent.getName());
        var competitionSpan = _find(nav, Span.class);
        assertThat(competitionSpan).anyMatch(s -> s.getText().equals(testCompetition.getName()));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCompetitionCategoriesInGrid() {
        competitionCategoryRepository.save(new CompetitionCategory(
                testCompetition.getId(), null,
                "M1A", "Traditional Mead", "A traditional mead", null, 0));
        competitionCategoryRepository.save(new CompetitionCategory(
                testCompetition.getId(), null,
                "M1B", "Semi-Sweet Mead", "A semi-sweet mead", null, 1));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        // Select the Categories tab to make its content visible
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        @SuppressWarnings("unchecked")
        var grid = (Grid<CompetitionCategory>) _get(Grid.class,
                spec -> spec.withId("categories-grid"));
        var headerNames = grid.getColumns().stream()
                .map(c -> c.getHeaderText())
                .toList();
        assertThat(headerNames).contains("Code", "Name", "Description");

        var items = grid.getGenericDataView().getItems().toList();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getCode()).isEqualTo("M1A");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowRemoveColumnInCategoriesGrid() {
        competitionCategoryRepository.save(new CompetitionCategory(
                testCompetition.getId(), null,
                "M1A", "Traditional Mead", "A traditional mead", null, 0));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        @SuppressWarnings("unchecked")
        var grid = (Grid<CompetitionCategory>) _get(Grid.class,
                spec -> spec.withId("categories-grid"));
        // Grid should have 4 columns: Code, Name, Description, and the component column (Remove)
        assertThat(grid.getColumns()).hasSize(4);
        var headerNames = grid.getColumns().stream()
                .map(c -> c.getHeaderText())
                .toList();
        assertThat(headerNames).containsExactly("Code", "Name", "Description", "");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowAddCategoryButton() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var addButton = _get(Button.class, spec -> spec.withText("Add Category"));
        assertThat(addButton).isNotNull();
        assertThat(addButton.isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAddCatalogCategoryViaDialog() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var addButton = _get(Button.class, spec -> spec.withText("Add Category"));
        _click(addButton);

        var dialog = _get(Dialog.class);
        assertThat(dialog.getHeaderTitle()).isEqualTo("Add Category");

        // Select a catalog category from the "From Catalog" tab
        @SuppressWarnings("unchecked")
        var catalogSelect = (Select<Category>) _get(dialog, Select.class,
                spec -> spec.withCaption("Catalog Category"));
        var availableCategories = categoryRepository.findByScoringSystem(ScoringSystem.MJP);
        catalogSelect.setValue(availableCategories.getFirst());

        var submitButton = _get(dialog, Button.class, spec -> spec.withText("Add"));
        _click(submitButton);

        // Verify category was added
        var categories = competitionCategoryRepository
                .findByCompetitionIdOrderByCode(testCompetition.getId());
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().getCatalogCategoryId())
                .isEqualTo(availableCategories.getFirst().getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAddCustomCategoryViaDialog() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var addButton = _get(Button.class, spec -> spec.withText("Add Category"));
        _click(addButton);

        var dialog = _get(Dialog.class);
        // Switch to Custom tab inside the dialog
        var dialogTabSheet = _get(dialog, TabSheet.class);
        dialogTabSheet.setSelectedIndex(1);

        var codeField = _get(dialog, TextField.class, spec -> spec.withCaption("Code"));
        codeField.setValue("CUSTOM1");
        var nameField = _get(dialog, TextField.class, spec -> spec.withCaption("Name"));
        nameField.setValue("Best Local Honey");
        var descField = _get(dialog, TextField.class, spec -> spec.withCaption("Description"));
        descField.setValue("Mead made with local honey");

        // The visible Add button is the Custom tab's (From Catalog tab is hidden)
        var customAddButton = _get(dialog, Button.class, spec -> spec.withText("Add"));
        _click(customAddButton);

        var categories = competitionCategoryRepository
                .findByCompetitionIdOrderByCode(testCompetition.getId());
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().getCode()).isEqualTo("CUSTOM1");
        assertThat(categories.getFirst().getName()).isEqualTo("Best Local Honey");
        assertThat(categories.getFirst().getCatalogCategoryId()).isNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowRemoveColumnInParticipantsGrid() {
        var judge = userRepository.save(new User("judge-col@test.com",
                "Judge Col", UserStatus.ACTIVE, Role.USER));
        var ep = eventParticipantRepository.save(
                new EventParticipant(testEvent.getId(), judge.getId()));
        competitionParticipantRepository.save(
                new CompetitionParticipant(testCompetition.getId(), ep.getId(),
                        CompetitionRole.JUDGE));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        @SuppressWarnings("unchecked")
        var grid = (Grid<CompetitionParticipant>) _find(Grid.class).getFirst();
        // Should have 5 columns: Name, Email, Role, Access Code, and Remove (component column)
        assertThat(grid.getColumns()).hasSize(5);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayAccessCodeColumnInParticipantsGrid() {
        var judge = userRepository.save(new User("judge-code@test.com",
                "Judge Code", UserStatus.ACTIVE, Role.USER));
        var ep = eventParticipantRepository.save(
                new EventParticipant(testEvent.getId(), judge.getId()));
        ep.assignAccessCode("ABCD1234");
        eventParticipantRepository.save(ep);
        competitionParticipantRepository.save(
                new CompetitionParticipant(testCompetition.getId(), ep.getId(),
                        CompetitionRole.JUDGE));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        @SuppressWarnings("unchecked")
        var grid = (Grid<CompetitionParticipant>) _find(Grid.class).getFirst();
        var headerNames = grid.getColumns().stream()
                .map(c -> c.getHeaderText())
                .toList();
        assertThat(headerNames).contains("Access Code");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayParticipantWithNameAndEmail() {
        var judge = userRepository.save(new User("judge@test.com",
                "Judge Person", UserStatus.ACTIVE, Role.USER));
        var ep = eventParticipantRepository.save(
                new EventParticipant(testEvent.getId(), judge.getId()));
        competitionParticipantRepository.save(
                new CompetitionParticipant(testCompetition.getId(), ep.getId(),
                        CompetitionRole.JUDGE));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        @SuppressWarnings("unchecked")
        var grid = (Grid<CompetitionParticipant>) _find(Grid.class).getFirst();
        var columns = grid.getColumns();
        var headerNames = columns.stream()
                .map(c -> c.getHeaderText())
                .toList();

        assertThat(headerNames).contains("Name", "Email", "Role");
    }
}
