package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionRepository;
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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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
class MyCompetitionsViewTest {

    private static final String COMP_ADMIN_EMAIL = "mycompview-admin@example.com";
    private static final String REGULAR_USER_EMAIL = "mycompview-user@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired ParticipantRepository participantRepository;
    @Autowired ParticipantRoleRepository participantRoleRepository;
    @Autowired CompetitionService competitionService;

    @BeforeEach
    void setup(TestInfo testInfo) {
        if (userRepository.findByEmail(COMP_ADMIN_EMAIL).isEmpty()) {
            userRepository.save(new User(COMP_ADMIN_EMAIL,
                    "Comp Admin", UserStatus.ACTIVE, Role.USER));
        }
        if (userRepository.findByEmail(REGULAR_USER_EMAIL).isEmpty()) {
            userRepository.save(new User(REGULAR_USER_EMAIL,
                    "Regular User", UserStatus.ACTIVE, Role.USER));
        }

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
    @WithMockUser(username = COMP_ADMIN_EMAIL, roles = "USER")
    void shouldDisplayMyCompetitionsViewWithGrid() {
        UI.getCurrent().navigate("my-competitions");

        var title = _get(H2.class, spec -> spec.withText("My Competitions"));
        assertThat(title).isNotNull();
        var grid = _get(Grid.class);
        assertThat(grid).isNotNull();
    }

    @Test
    @WithMockUser(username = COMP_ADMIN_EMAIL, roles = "USER")
    void shouldShowCompetitionsWhereUserIsAdmin() {
        // Create a competition and make the user an admin
        var sysAdmin = userRepository.save(new User("mycompview-sysadmin@example.com",
                "Sys Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
        var suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        var comp = competitionService.createCompetition("My Test Comp", "my-test-" + suffix,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30),
                "Lisbon", sysAdmin.getId());
        competitionService.addParticipantByEmail(
                comp.getId(), COMP_ADMIN_EMAIL, CompetitionRole.ADMIN, sysAdmin.getId());

        UI.getCurrent().navigate("my-competitions");

        var grid = _get(Grid.class);
        assertThat(grid.getGenericDataView().getItems().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(username = REGULAR_USER_EMAIL, roles = "USER")
    void shouldShowEmptyGridWhenUserIsNotAdminOfAnyCompetition() {
        UI.getCurrent().navigate("my-competitions");

        var grid = _get(Grid.class);
        assertThat(grid.getGenericDataView().getItems().count()).isZero();
    }
}
