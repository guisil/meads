package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionListView;
import app.meads.competition.internal.CompetitionRepository;
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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
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
class CompetitionListViewTest {

    private static final String ADMIN_EMAIL = "complistview-admin@example.com";

    @Autowired
    ApplicationContext ctx;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setup(TestInfo testInfo) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            userRepository.save(new User(ADMIN_EMAIL,
                    "Comp List Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
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
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCompetitionListViewWithGrid() {
        UI.getCurrent().navigate("competitions");

        var grid = _get(Grid.class);
        assertThat(grid).isNotNull();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldRedirectToRootWhenRegularUserAccessesCompetitionsView() {
        UI.getCurrent().navigate("competitions");

        assertThat(UI.getCurrent().getInternals().getActiveViewLocation().getPath()).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCreateCompetitionButton() {
        UI.getCurrent().navigate("competitions");

        var createButton = _get(Button.class, spec -> spec.withText("Create Competition"));
        assertThat(createButton).isNotNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldOpenCreateCompetitionDialogWhenCreateButtonClicked() {
        UI.getCurrent().navigate("competitions");

        var createButton = _get(Button.class, spec -> spec.withText("Create Competition"));
        _click(createButton);

        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();
        assertThat(_find(TextField.class, spec -> spec.withLabel("Name"))).hasSize(1);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldCreateCompetitionWhenFormSubmitted() {
        UI.getCurrent().navigate("competitions");

        var createButton = _get(Button.class, spec -> spec.withText("Create Competition"));
        _click(createButton);

        _get(TextField.class, spec -> spec.withLabel("Name")).setValue("Regional 2026");
        _get(TextField.class, spec -> spec.withLabel("Short Name")).setValue("regional-2026");
        _get(DatePicker.class, spec -> spec.withLabel("Start Date"))
                .setValue(LocalDate.of(2026, 6, 15));
        _get(DatePicker.class, spec -> spec.withLabel("End Date"))
                .setValue(LocalDate.of(2026, 6, 17));
        _get(TextField.class, spec -> spec.withLabel("Location")).setValue("Porto");

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        assertThat(_find(Dialog.class)).isEmpty();
        assertThat(_get(Notification.class).getElement().getProperty("text"))
                .contains("created");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowLogoUploadInCompetitionDialog() {
        UI.getCurrent().navigate("competitions");

        var createButton = _get(Button.class, spec -> spec.withText("Create Competition"));
        _click(createButton);

        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        var uploads = _find(Upload.class);
        assertThat(uploads).hasSize(1);
        assertThat(uploads.getFirst().getMaxFileSize()).isEqualTo(512 * 1024);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDisplayCompetitionsInGrid() {
        competitionRepository.save(new Competition("Test Competition",
                "test-comp-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));

        UI.getCurrent().navigate("competitions");

        var grid = _get(Grid.class);
        assertThat(grid.getGenericDataView().getItems().count()).isGreaterThanOrEqualTo(1);
    }
}
