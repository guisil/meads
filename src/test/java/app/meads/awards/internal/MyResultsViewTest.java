package app.meads.awards.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class MyResultsViewTest {

    private static final String ENTRANT_EMAIL = "my-results-entrant@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired UserRepository userRepository;

    private Competition competition;
    private Division division;

    @BeforeEach
    void setup(TestInfo testInfo) {
        userRepository.findByEmail(ENTRANT_EMAIL).orElseGet(() ->
                userRepository.save(new User(ENTRANT_EMAIL, "Entrant",
                        UserStatus.ACTIVE, Role.USER)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "My Results Test", "myres-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        var d = new Division(competition.getId(), "Amateur", "myres-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division = divisionRepository.save(d);

        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);

        var auth = resolveAuthentication(testInfo);
        if (auth != null) {
            SecurityContextHolder.getContext().setAuthentication(auth);
            propagateSecurityContext(auth);
        }
    }

    private Authentication resolveAuthentication(TestInfo testInfo) {
        var method = testInfo.getTestMethod().orElse(null);
        if (method == null) {
            return null;
        }
        var withMockUser = method.getAnnotation(WithMockUser.class);
        if (withMockUser == null) {
            return null;
        }
        var username = withMockUser.username().isEmpty() ? withMockUser.value() : withMockUser.username();
        var authorities = Arrays.stream(withMockUser.roles())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        var ud = org.springframework.security.core.userdetails.User.builder()
                .username(username).password("p").authorities(authorities).build();
        return new UsernamePasswordAuthenticationToken(ud, null, authorities);
    }

    private void propagateSecurityContext(Authentication authentication) {
        var fakeRequest = (FakeRequest) VaadinServletRequest.getCurrent().getRequest();
        fakeRequest.setUserPrincipalInt(authentication);
        fakeRequest.setUserInRole((p, r) -> authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + r)));
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldForwardAwayWhenStatusNotPublished() {
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-results");

        // Status is DRAFT — view should have forwarded away, no my-results-heading rendered.
        assertThat(_find(H2.class).stream()
                .anyMatch(h -> "my-results-heading".equals(h.getId().orElse(""))))
                .isFalse();
    }

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldRenderHeadingAndGridWhenPublished() {
        for (int i = 0; i < 5; i++) {
            division.advanceStatus();
        }
        division = divisionRepository.save(division);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-results");

        var heading = _get(H2.class, spec -> spec.withId("my-results-heading"));
        assertThat(heading.getText()).contains("My Results Test");
        assertThat(heading.getText()).contains("Amateur");
        _get(Grid.class, spec -> spec.withId("my-results-grid"));
    }
}
