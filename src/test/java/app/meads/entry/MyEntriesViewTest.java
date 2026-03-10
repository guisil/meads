package app.meads.entry;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.*;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.competition.internal.ParticipantRepository;
import app.meads.competition.internal.ParticipantRoleRepository;
import app.meads.entry.internal.EntryCreditRepository;
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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
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
class MyEntriesViewTest {

    private static final String ADMIN_EMAIL = "myentries-admin@example.com";
    private static final String ENTRANT_EMAIL = "myentries-entrant@example.com";

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
    EntryCreditRepository creditRepository;

    @Autowired
    CompetitionService competitionService;

    @Autowired
    EntryService entryService;

    private User admin;
    private User entrant;
    private Competition competition;
    private Division division;

    @BeforeEach
    void setup(TestInfo testInfo) {
        admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ADMIN_EMAIL, "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN)));

        entrant = userRepository.findByEmail(ENTRANT_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ENTRANT_EMAIL, "Entrant", UserStatus.ACTIVE, Role.USER)));
        // Reset meadery name to ensure test isolation
        entrant.updateMeaderyName(null);
        entrant = userRepository.save(entrant);

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "My Entries Test Competition", "my-entries-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        division = divisionRepository.save(new Division(
                competition.getId(), "Test Division", "test-div-" + suffix, ScoringSystem.MJP));

        // Add entrant as ENTRANT participant
        var participant = participantRepository.save(
                new Participant(competition.getId(), entrant.getId()));
        participantRoleRepository.save(
                new ParticipantRole(participant.getId(), CompetitionRole.ENTRANT));

        // Advance to REGISTRATION_OPEN
        division.advanceStatus();
        division = divisionRepository.save(division);

        // Add credits
        creditRepository.save(new EntryCredit(
                division.getId(), entrant.getId(), 3, "ADMIN", "test"));

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
    @WithMockUser(username = "noparticipant@example.com", roles = "USER")
    void shouldRedirectUnauthorizedEntrant() {
        userRepository.findByEmail("noparticipant@example.com")
                .orElseGet(() -> userRepository.save(
                        new User("noparticipant@example.com", "No Participant",
                                UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("competitions/" + competition.getShortName() + "/divisions/" + division.getShortName() + "/my-entries");

        // Should have been forwarded away — no H2 with division name
        var headings = _find(H2.class);
        assertThat(headings).noneMatch(h -> h.getText().contains("Test Division"));
    }

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldShowWarningWhenMeaderyNameRequiredButMissing() {
        // Set meaderyNameRequired on division (need to revert to DRAFT first)
        division.revertStatus(); // REGISTRATION_OPEN → DRAFT
        division.updateMeaderyNameRequired(true);
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        divisionRepository.save(division);

        // Entrant has no meadery name (default)

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-entries");

        // Warning banner should be present
        var warnings = _find(Div.class).stream()
                .filter(d -> {
                    var spans = _find(d, Span.class);
                    return spans.stream().anyMatch(s ->
                            s.getText() != null && s.getText().contains("meadery name"));
                })
                .toList();
        assertThat(warnings).isNotEmpty();

        // Submit All button should be disabled
        var submitButton = _get(Button.class, spec -> spec.withText("Submit All"));
        assertThat(submitButton.isEnabled()).isFalse();
    }

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldNotShowWarningWhenMeaderyNameIsSet() {
        // Set meaderyNameRequired on division
        division.revertStatus();
        division.updateMeaderyNameRequired(true);
        division.advanceStatus();
        divisionRepository.save(division);

        // Set meadery name on entrant
        entrant.updateMeaderyName("Golden Meadery");
        userRepository.save(entrant);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-entries");

        // No warning banner
        var warnings = _find(Div.class).stream()
                .filter(d -> {
                    var spans = _find(d, Span.class);
                    return spans.stream().anyMatch(s ->
                            s.getText() != null && s.getText().contains("meadery name"));
                })
                .toList();
        assertThat(warnings).isEmpty();
    }

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldDisplayCreditsAndEntryGrid() {
        UI.getCurrent().navigate("competitions/" + competition.getShortName() + "/divisions/" + division.getShortName() + "/my-entries");

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("Test Division");

        // Credit info
        var creditSpan = _find(Span.class).stream()
                .filter(s -> s.getText() != null && s.getText().contains("Credits"))
                .findFirst();
        assertThat(creditSpan).isPresent();

        // Grid
        @SuppressWarnings("unchecked")
        var grid = (Grid<Entry>) _get(Grid.class);
        assertThat(grid).isNotNull();
        var headers = grid.getColumns().stream()
                .map(c -> c.getHeaderText())
                .toList();
        assertThat(headers).contains("Entry #", "Mead Name", "Status");

        // Add Entry button
        var addButton = _get(Button.class, spec -> spec.withText("Add Entry"));
        assertThat(addButton.isEnabled()).isTrue();
    }
}
