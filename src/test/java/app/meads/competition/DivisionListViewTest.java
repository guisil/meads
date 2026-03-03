package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionRepository;
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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
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
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class DivisionListViewTest {

    private static final String ADMIN_EMAIL = "divlistview-admin@example.com";

    @Autowired
    ApplicationContext ctx;

    @Autowired
    CompetitionService competitionService;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    ParticipantRepository participantRepository;

    @Autowired
    ParticipantRoleRepository participantRoleRepository;

    @Autowired
    UserRepository userRepository;

    private Competition testCompetition;

    @BeforeEach
    void setup(TestInfo testInfo) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            userRepository.save(new User(ADMIN_EMAIL,
                    "Div List Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
        }

        testCompetition = competitionRepository.save(new Competition("Test Competition",
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
    void shouldDisplayDivisionListViewWithCompetitionHeader() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        var heading = _get(H2.class, spec -> spec.withText("Test Competition"));
        assertThat(heading).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayDivisionGrid() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        var grid = _get(Grid.class);
        assertThat(grid).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCreateDivisionButton() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        var button = _get(Button.class, spec -> spec.withText("Create Division"));
        assertThat(button).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldCreateDivisionViaDialog() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        _click(_get(Button.class, spec -> spec.withText("Create Division")));

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
        var participant = participantRepository.save(
                new Participant(testCompetition.getId(), compAdminUser.getId()));
        participantRoleRepository.save(
                new ParticipantRole(participant.getId(), CompetitionRole.ADMIN));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        var heading = _get(H2.class, spec -> spec.withText("Test Competition"));
        assertThat(heading).isNotNull();
    }

    @Test
    @WithMockUser(username = "unauthorized@example.com", roles = "USER")
    void shouldRedirectUnauthorizedUser() {
        userRepository.findByEmail("unauthorized@example.com")
                .orElseGet(() -> userRepository.save(new User("unauthorized@example.com",
                        "Unauthorized", UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        var headings = _find(H2.class);
        assertThat(headings).noneMatch(h -> h.getText().equals("Test Competition"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCompetitionLogoInHeaderWhenLogoExists() {
        testCompetition.updateLogo(new byte[]{1, 2, 3}, "image/png");
        competitionRepository.save(testCompetition);

        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        var images = _find(Image.class);
        assertThat(images).hasSize(1);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldNotDisplayCompetitionLogoInHeaderWhenNoLogo() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        var images = _find(Image.class);
        assertThat(images).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRemoveDivisionFromGridAfterDeletion() {
        var division = divisionRepository.save(
                new Division(testCompetition.getId(), "To Delete", ScoringSystem.MJP));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        @SuppressWarnings("unchecked")
        var grid = (Grid<Division>) _find(Grid.class).getFirst();
        assertThat(grid.getGenericDataView().getItems().count()).isEqualTo(1);

        competitionService.deleteDivision(division.getId(), getCurrentUserId());

        // Re-navigate to refresh
        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        @SuppressWarnings("unchecked")
        var refreshedGrid = (Grid<Division>) _find(Grid.class).getFirst();
        assertThat(refreshedGrid.getGenericDataView().getItems().count()).isZero();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayAddParticipantButton() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId() + "/divisions");

        var button = _get(Button.class, spec -> spec.withText("Add Participant"));
        assertThat(button).isNotNull();
    }

    private UUID getCurrentUserId() {
        return userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
    }
}
