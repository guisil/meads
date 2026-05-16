package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CategoryRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.competition.internal.ParticipantRepository;
import app.meads.competition.internal.ParticipantRoleRepository;
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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class DivisionDetailViewTest {

    private static final String ADMIN_EMAIL = "detailview-admin@example.com";

    @Autowired
    ApplicationContext ctx;

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
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    private Division testDivision;
    private Competition testCompetition;

    @BeforeEach
    void setup(TestInfo testInfo) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            userRepository.save(new User(ADMIN_EMAIL,
                    "Detail Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
        }

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        testCompetition = competitionRepository.save(new Competition("Test Competition",
                "test-comp-" + suffix,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        testDivision = divisionRepository.save(new Division(
                testCompetition.getId(), "Home", "home-" + suffix, ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));

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
    void shouldDisplayDivisionHeader() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains(testCompetition.getName()).contains(testDivision.getName());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayStatusBadge() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        var badges = _find(Span.class);
        assertThat(badges).anyMatch(span ->
                span.getElement().getThemeList().contains("badge")
                        && span.getText().equals("Draft"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayTabSheet() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        var tabSheet = _get(TabSheet.class);
        assertThat(tabSheet).isNotNull();
    }

    @Test
    @WithMockUser(username = "comp-admin@example.com", roles = "USER")
    void shouldAllowCompetitionAdminAccess() {
        var compAdminUser = userRepository.findByEmail("comp-admin@example.com")
                .orElseGet(() -> {
                    var u = new User("comp-admin@example.com",
                            "Comp Admin", UserStatus.ACTIVE, Role.USER);
                    u.assignPasswordHash("$2a$10$dummyhash");
                    return userRepository.save(u);
                });
        var participant = participantRepository.save(
                new Participant(testCompetition.getId(), compAdminUser.getId()));
        participantRoleRepository.save(
                new ParticipantRole(participant.getId(), CompetitionRole.ADMIN));

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains(testCompetition.getName()).contains(testDivision.getName());
    }

    @Test
    @WithMockUser(username = "unauthorized@example.com", roles = "USER")
    void shouldRedirectUnauthorizedUser() {
        userRepository.findByEmail("unauthorized@example.com")
                .orElseGet(() -> userRepository.save(new User("unauthorized@example.com",
                        "Unauthorized", UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        // Should have been forwarded away — no H2 heading should be present
        var headings = _find(H2.class);
        assertThat(headings).noneMatch(h -> h.getText().contains(testDivision.getName()));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayBreadcrumb() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        var nav = _get(Nav.class);
        assertThat(nav).isNotNull();
        var anchors = _find(nav, Anchor.class);
        assertThat(anchors).anyMatch(a -> a.getText().equals("Competitions"));
        assertThat(anchors).anyMatch(a -> a.getText().equals(testCompetition.getName()));
        var spans = _find(nav, Span.class);
        assertThat(spans).anyMatch(s -> s.getText().equals(testDivision.getName()));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayDivisionCategoriesInGrid() {
        divisionCategoryRepository.save(new DivisionCategory(
                testDivision.getId(), null,
                "M1A", "Traditional Mead", "A traditional mead", null, 0));
        divisionCategoryRepository.save(new DivisionCategory(
                testDivision.getId(), null,
                "M1B", "Semi-Sweet Mead", "A semi-sweet mead", null, 1));

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        // Categories tab is now the first tab (index 0) — selected by default
        @SuppressWarnings("unchecked")
        var grid = (TreeGrid<DivisionCategory>) _get(TreeGrid.class,
                spec -> spec.withId("categories-grid"));
        var headerNames = grid.getColumns().stream()
                .map(c -> c.getHeaderText())
                .toList();
        assertThat(headerNames).contains("Code", "Name", "Description");

        var dataProvider = grid.getDataProvider();
        var rootItems = dataProvider.fetchChildren(
                new HierarchicalQuery<>(
                        null, null)).toList();
        assertThat(rootItems).hasSize(2);
        assertThat(rootItems.get(0).getCode()).isEqualTo("M1A");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCategoriesInTreeGrid() {
        var parent = divisionCategoryRepository.save(new DivisionCategory(
                testDivision.getId(), null,
                "M1", "Traditional Mead", "Traditional mead category", null, 0));
        divisionCategoryRepository.save(new DivisionCategory(
                testDivision.getId(), null,
                "M1A", "Traditional Mead (Dry)", "Dry traditional mead", parent.getId(), 0));

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        // Categories tab is the first tab (index 0) — selected by default
        var treeGrid = _get(TreeGrid.class, spec -> spec.withId("categories-grid"));
        assertThat(treeGrid).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowRemoveColumnInCategoriesGrid() {
        divisionCategoryRepository.save(new DivisionCategory(
                testDivision.getId(), null,
                "M1A", "Traditional Mead", "A traditional mead", null, 0));

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        // Categories tab is the first tab (index 0) — selected by default
        @SuppressWarnings("unchecked")
        var grid = (TreeGrid<DivisionCategory>) _get(TreeGrid.class,
                spec -> spec.withId("categories-grid"));
        // Grid should have 4 columns: Code, Name, Description, and the component column (Remove)
        assertThat(grid.getColumns()).hasSize(4);
        var headerNames = grid.getColumns().stream()
                .map(c -> c.getHeaderText())
                .toList();
        assertThat(headerNames).containsExactly("Code", "Name", "Description", "Actions");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowAddCategoryButton() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        // Categories tab is the first tab (index 0) — selected by default
        var addButton = _get(Button.class, spec -> spec.withText("Add Category"));
        assertThat(addButton).isNotNull();
        assertThat(addButton.isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAddCatalogCategoryViaDialog() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        // Categories tab is the first tab (index 0) — selected by default
        var addButton = _get(Button.class, spec -> spec.withText("Add Category"));
        _click(addButton);

        var dialog = _get(Dialog.class);
        assertThat(dialog.getHeaderTitle()).isEqualTo("Add Category");

        // Select a catalog category from the "From Catalog" tab
        @SuppressWarnings("unchecked")
        var catalogSelect = (Select<Category>) _get(dialog, Select.class,
                spec -> spec.withCaption("Catalog Category"));
        var availableCategories = categoryRepository.findByScoringSystemOrderByCode(ScoringSystem.MJP);
        catalogSelect.setValue(availableCategories.getFirst());

        var submitButton = _get(dialog, Button.class, spec -> spec.withText("Add"));
        _click(submitButton);

        // Verify category was added
        var categories = divisionCategoryRepository
                .findByDivisionIdOrderByCode(testDivision.getId());
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().getCatalogCategoryId())
                .isEqualTo(availableCategories.getFirst().getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAddCustomCategoryViaDialog() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        // Categories tab is the first tab (index 0) — selected by default
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

        var categories = divisionCategoryRepository
                .findByDivisionIdOrderByCode(testDivision.getId());
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().getCode()).isEqualTo("CUSTOM1");
        assertThat(categories.getFirst().getName()).isEqualTo("Best Local Honey");
        assertThat(categories.getFirst().getCatalogCategoryId()).isNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayManageEntriesLink() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        var buttons = _find(Button.class);
        var manageEntries = buttons.stream()
                .filter(b -> b.getText().equals("Manage Entries"))
                .findFirst();
        assertThat(manageEntries).isPresent();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayManageJudgingButtonWhenDivisionInJudgingStatus() {
        testDivision.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        testDivision.advanceStatus(); // → REGISTRATION_CLOSED
        testDivision.advanceStatus(); // → JUDGING
        divisionRepository.save(testDivision);

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        var buttons = _find(Button.class);
        var manageJudging = buttons.stream()
                .filter(b -> b.getText().equals("Manage Judging"))
                .findFirst();
        assertThat(manageJudging).isPresent();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldHideManageJudgingButtonWhenDivisionBelowJudgingStatus() {
        testDivision.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        divisionRepository.save(testDivision);

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        var buttons = _find(Button.class);
        var manageJudging = buttons.stream()
                .filter(b -> b.getText().equals("Manage Judging"))
                .findFirst();
        assertThat(manageJudging).isNotPresent();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayRevertStatusButtonWhenNotDraft() {
        testDivision.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        divisionRepository.save(testDivision);

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        var buttons = _find(Button.class);
        var revertButton = buttons.stream()
                .filter(b -> b.getText().equals("Revert Status"))
                .findFirst();
        assertThat(revertButton).isPresent();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayMeaderyNameRequiredCheckboxInSettings() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        // Switch to Settings tab (index 1)
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var checkbox = _get(Checkbox.class, spec -> spec.withLabel("Meadery Name Required"));
        assertThat(checkbox.getValue()).isFalse();
        assertThat(checkbox.isEnabled()).isTrue(); // DRAFT allows editing
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldNotDisplayRevertStatusButtonWhenDraft() {
        // testDivision starts in DRAFT
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName() + "/divisions/" + testDivision.getShortName());

        var buttons = _find(Button.class);
        var revertButton = buttons.stream()
                .filter(b -> b.getText().equals("Revert Status"))
                .findFirst();
        assertThat(revertButton).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisableAddCategoryButtonWhenRegistrationClosed() {
        testDivision.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        testDivision.advanceStatus(); // → REGISTRATION_CLOSED
        divisionRepository.save(testDivision);

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        // Default tab is Judging Categories (index 1) when past REGISTRATION_CLOSED;
        // switch to Categories tab (index 0) to find the Add Category button
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(0);

        var addButton = _get(Button.class, spec -> spec.withText("Add Category"));
        assertThat(addButton.isEnabled()).isFalse();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowInitializeJudgingCategoriesButtonWhenRegistrationClosed() {
        testDivision.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        testDivision.advanceStatus(); // → REGISTRATION_CLOSED
        divisionRepository.save(testDivision);

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        // Judging Categories tab is at index 1 when allowsJudgingCategoryManagement()
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var buttons = _find(Button.class);
        assertThat(buttons).anyMatch(b -> b.getText().equals("Initialize Judging Categories"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldNotShowInitializeJudgingCategoriesButtonWhenDraft() {
        // testDivision starts in DRAFT
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        var buttons = _find(Button.class);
        assertThat(buttons).noneMatch(b -> b.getText().equals("Initialize Judging Categories"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowJudgingCategoriesGridAfterInitialization() {
        testDivision.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        testDivision.advanceStatus(); // → REGISTRATION_CLOSED
        divisionRepository.save(testDivision);

        // Pre-seed a judging category
        divisionCategoryRepository.save(new DivisionCategory(
                testDivision.getId(), null, "CX1", "Combined Category", "desc", null, 0,
                CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        // Judging Categories tab is at index 1 when allowsJudgingCategoryManagement()
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        @SuppressWarnings("unchecked")
        var judgingGrid = (TreeGrid<DivisionCategory>) _get(TreeGrid.class,
                spec -> spec.withId("judging-categories-grid"));
        assertThat(judgingGrid).isNotNull();

        var rootItems = judgingGrid.getDataProvider().fetchChildren(
                new HierarchicalQuery<>(null, null))
                .toList();
        assertThat(rootItems).hasSize(1);
        assertThat(rootItems.getFirst().getCode()).isEqualTo("CX1");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayBosPlacesIntegerFieldInSettingsTab() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Settings

        var bosField = _get(com.vaadin.flow.component.textfield.IntegerField.class,
                spec -> spec.withId("bos-places-field"));
        assertThat(bosField.getValue()).isEqualTo(1); // default
        assertThat(bosField.isReadOnly()).isFalse(); // DRAFT allows editing
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldLockBosPlacesFieldWhenRegistrationClosed() {
        testDivision.advanceStatus(); // → REGISTRATION_OPEN
        testDivision.advanceStatus(); // → REGISTRATION_CLOSED
        divisionRepository.save(testDivision);

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(2); // Settings (index shifts when judging categories tab added)

        var bosField = _get(com.vaadin.flow.component.textfield.IntegerField.class,
                spec -> spec.withId("bos-places-field"));
        assertThat(bosField.isReadOnly()).isTrue();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldPersistBosPlacesWhenSettingsSaved() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var bosField = _get(com.vaadin.flow.component.textfield.IntegerField.class,
                spec -> spec.withId("bos-places-field"));
        bosField.setValue(5);

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var refreshed = divisionRepository.findById(testDivision.getId()).orElseThrow();
        assertThat(refreshed.getBosPlaces()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayMinJudgesPerTableIntegerFieldInSettingsTab() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var minJudgesField = _get(com.vaadin.flow.component.textfield.IntegerField.class,
                spec -> spec.withId("min-judges-field"));
        assertThat(minJudgesField.getValue()).isEqualTo(2); // default
        assertThat(minJudgesField.isReadOnly()).isFalse();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldPersistMinJudgesPerTableWhenSettingsSaved() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var minJudgesField = _get(com.vaadin.flow.component.textfield.IntegerField.class,
                spec -> spec.withId("min-judges-field"));
        minJudgesField.setValue(3);

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var refreshed = divisionRepository.findById(testDivision.getId()).orElseThrow();
        assertThat(refreshed.getMinJudgesPerTable()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowAddJudgingCategoryButtonAfterInitialization() {
        testDivision.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        testDivision.advanceStatus(); // → REGISTRATION_CLOSED
        divisionRepository.save(testDivision);

        divisionCategoryRepository.save(new DivisionCategory(
                testDivision.getId(), null, "CX1", "Combined Category", "desc", null, 0,
                CategoryScope.JUDGING));

        UI.getCurrent().navigate("competitions/" + testCompetition.getShortName()
                + "/divisions/" + testDivision.getShortName());

        // Judging Categories tab is at index 1 when allowsJudgingCategoryManagement()
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var buttons = _find(Button.class);
        assertThat(buttons).anyMatch(b -> b.getText().equals("Add Judging Category"));
    }
}
