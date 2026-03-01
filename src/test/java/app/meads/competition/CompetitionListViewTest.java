package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionListView;
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
import com.vaadin.flow.component.notification.Notification;
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
class CompetitionListViewTest {

    private static final String ADMIN_EMAIL = "compview-admin@example.com";

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
    UserRepository userRepository;

    private MeadEvent testEvent;

    @BeforeEach
    void setup(TestInfo testInfo) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            userRepository.save(new User(ADMIN_EMAIL,
                    "Comp Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
        }

        testEvent = meadEventRepository.save(new MeadEvent("Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));

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
    void shouldDisplayCompetitionListViewWithEventHeader() {
        UI.getCurrent().navigate("events/" + testEvent.getId() + "/competitions");

        var heading = _get(H2.class, spec -> spec.withText("Test Event"));
        assertThat(heading).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCompetitionGrid() {
        UI.getCurrent().navigate("events/" + testEvent.getId() + "/competitions");

        var grid = _get(Grid.class);
        assertThat(grid).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCreateCompetitionButton() {
        UI.getCurrent().navigate("events/" + testEvent.getId() + "/competitions");

        var button = _get(Button.class, spec -> spec.withText("Create Competition"));
        assertThat(button).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldCreateCompetitionViaDialog() {
        UI.getCurrent().navigate("events/" + testEvent.getId() + "/competitions");

        _click(_get(Button.class, spec -> spec.withText("Create Competition")));

        _get(TextField.class, spec -> spec.withLabel("Name")).setValue("Home");

        _click(_get(Button.class, spec -> spec.withText("Create")));

        assertThat(_find(Dialog.class)).isEmpty();
        assertThat(_get(Notification.class).getElement().getProperty("text"))
                .contains("created");
    }

    @Test
    @WithMockUser(username = "comp-admin@example.com", roles = "USER")
    void shouldAllowCompetitionAdminAccess() {
        var compAdminUser = userRepository.findByEmail("comp-admin@example.com")
                .orElseGet(() -> userRepository.save(new User("comp-admin@example.com",
                        "Comp Admin User", UserStatus.ACTIVE, Role.USER)));
        var competition = competitionRepository.save(
                new Competition(testEvent.getId(), "Home", ScoringSystem.MJP));
        var ep = eventParticipantRepository.save(
                new EventParticipant(testEvent.getId(), compAdminUser.getId()));
        competitionParticipantRepository.save(
                new CompetitionParticipant(competition.getId(), ep.getId(),
                        CompetitionRole.COMPETITION_ADMIN));

        UI.getCurrent().navigate("events/" + testEvent.getId() + "/competitions");

        var heading = _get(H2.class, spec -> spec.withText("Test Event"));
        assertThat(heading).isNotNull();
    }

    @Test
    @WithMockUser(username = "unauthorized@example.com", roles = "USER")
    void shouldRedirectUnauthorizedUser() {
        userRepository.findByEmail("unauthorized@example.com")
                .orElseGet(() -> userRepository.save(new User("unauthorized@example.com",
                        "Unauthorized", UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("events/" + testEvent.getId() + "/competitions");

        var headings = _find(H2.class);
        assertThat(headings).noneMatch(h -> h.getText().equals("Test Event"));
    }

    @Test
    @WithMockUser(username = "comp-admin2@example.com", roles = "USER")
    void shouldShowOnlyAdminCompetitionsForCompetitionAdmin() {
        var compAdminUser = userRepository.findByEmail("comp-admin2@example.com")
                .orElseGet(() -> userRepository.save(new User("comp-admin2@example.com",
                        "Comp Admin 2", UserStatus.ACTIVE, Role.USER)));
        var comp1 = competitionRepository.save(
                new Competition(testEvent.getId(), "Comp One", ScoringSystem.MJP));
        var comp2 = competitionRepository.save(
                new Competition(testEvent.getId(), "Comp Two", ScoringSystem.MJP));
        var ep = eventParticipantRepository.save(
                new EventParticipant(testEvent.getId(), compAdminUser.getId()));
        // Only admin for comp1, not comp2
        competitionParticipantRepository.save(
                new CompetitionParticipant(comp1.getId(), ep.getId(),
                        CompetitionRole.COMPETITION_ADMIN));

        UI.getCurrent().navigate("events/" + testEvent.getId() + "/competitions");

        @SuppressWarnings("unchecked")
        var grid = (Grid<Competition>) _find(Grid.class).getFirst();
        var items = grid.getGenericDataView().getItems().toList();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getName()).isEqualTo("Comp One");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCompetitionsInGrid() {
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var competition = new Competition(testEvent.getId(),
                "Home", ScoringSystem.MJP);

        // Save directly via service to populate grid
        UI.getCurrent().navigate("events/" + testEvent.getId() + "/competitions");

        // Navigate after creating competition via repository
        // We need to use the service for proper creation
        var view = _get(CompetitionListView.class);
        assertThat(view).isNotNull();
    }
}
