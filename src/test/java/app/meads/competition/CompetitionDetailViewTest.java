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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
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

    private static final String ADMIN_EMAIL = "compdetail-admin@example.com";

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
    UserRepository userRepository;

    private Competition testCompetition;

    @BeforeEach
    void setup(TestInfo testInfo) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            userRepository.save(new User(ADMIN_EMAIL,
                    "Comp Detail Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
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
    void shouldDisplayCompetitionHeaderAndDivisionsTab() {
        divisionRepository.save(new Division(testCompetition.getId(), "Home", ScoringSystem.MJP));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var heading = _get(H2.class, spec -> spec.withText("Test Competition"));
        assertThat(heading).isNotNull();

        var tabSheet = _get(TabSheet.class);
        assertThat(tabSheet).isNotNull();

        // Divisions tab is selected by default (index 0)
        @SuppressWarnings("unchecked")
        var grid = (Grid<Division>) _get(Grid.class);
        var headers = grid.getColumns().stream()
                .map(c -> c.getHeaderText())
                .toList();
        assertThat(headers).contains("Name", "Status", "Scoring");
        assertThat(grid.getGenericDataView().getItems().count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayParticipantsTab() {
        var judge = userRepository.findByEmail("judge-detail@test.com")
                .orElseGet(() -> userRepository.save(new User("judge-detail@test.com",
                        "Judge Person", UserStatus.ACTIVE, Role.USER)));
        var participant = participantRepository.save(
                new Participant(testCompetition.getId(), judge.getId()));
        participantRoleRepository.save(
                new ParticipantRole(participant.getId(), CompetitionRole.JUDGE));

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Participants tab

        @SuppressWarnings("unchecked")
        var grid = (Grid<ParticipantRole>) _find(Grid.class).stream()
                .filter(g -> !(g instanceof com.vaadin.flow.component.treegrid.TreeGrid))
                .reduce((first, second) -> second) // get the last non-TreeGrid
                .orElseThrow();
        var headers = grid.getColumns().stream()
                .map(c -> c.getHeaderText())
                .toList();
        assertThat(headers).contains("Name", "Email", "Role", "Access Code");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplaySettingsTab() {
        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(2); // Settings tab

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        assertThat(nameField.getValue()).isEqualTo("Test Competition");

        var startDate = _get(DatePicker.class, spec -> spec.withLabel("Start Date"));
        assertThat(startDate.getValue()).isEqualTo(LocalDate.of(2026, 6, 15));

        var endDate = _get(DatePicker.class, spec -> spec.withLabel("End Date"));
        assertThat(endDate.getValue()).isEqualTo(LocalDate.of(2026, 6, 17));

        var locationField = _get(TextField.class, spec -> spec.withLabel("Location"));
        assertThat(locationField.getValue()).isEqualTo("Porto");

        var uploads = _find(Upload.class);
        assertThat(uploads).hasSize(1);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldLogPasswordSetupLinkWhenAddingCompetitionAdminWithoutPassword() {
        // Create a user without a password who will become competition admin
        var newAdmin = userRepository.findByEmail("newcompadmin@example.com")
                .orElseGet(() -> userRepository.save(new User("newcompadmin@example.com",
                        "New Comp Admin", UserStatus.ACTIVE, Role.USER)));
        assertThat(newAdmin.getPasswordHash()).isNull();

        UI.getCurrent().navigate("competitions/" + testCompetition.getId());

        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1); // Participants tab

        _click(_get(Button.class, spec -> spec.withText("Add Participant")));

        _get(TextField.class, spec -> spec.withLabel("Email")).setValue("newcompadmin@example.com");
        _get(Select.class, spec -> spec.withLabel("Role")).setValue(CompetitionRole.ADMIN);
        _click(_get(Button.class, spec -> spec.withText("Add")));

        var notifications = _find(Notification.class);
        var notificationTexts = notifications.stream()
                .map(n -> n.getElement().getProperty("text"))
                .toList();
        assertThat(notificationTexts).anyMatch(t -> t != null && t.contains("Password setup link"));
    }
}
