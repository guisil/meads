package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
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

import java.util.Arrays;
import java.util.List;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class ProfileViewTest {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setup(TestInfo testInfo) {
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
    @WithMockUser(username = "entrant@test.com", roles = "USER")
    void shouldDisplayProfileFields(TestInfo testInfo) {
        userRepository.findByEmail("entrant@test.com")
                .orElseGet(() -> userRepository.save(
                        new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("profile");

        var emailField = _get(TextField.class, spec -> spec.withLabel("Email"));
        assertThat(emailField.isReadOnly()).isTrue();
        assertThat(emailField.getValue()).isEqualTo("entrant@test.com");

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        assertThat(nameField.getValue()).isEqualTo("Test Entrant");
    }

    @Test
    @WithMockUser(username = "save-profile@test.com", roles = "USER")
    void shouldSaveProfileChanges(TestInfo testInfo) {
        userRepository.findByEmail("save-profile@test.com")
                .orElseGet(() -> userRepository.save(
                        new User("save-profile@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("profile");

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        nameField.setValue("Updated Name");

        var meaderyField = _get(TextField.class, spec -> spec.withLabel("Meadery Name"));
        meaderyField.setValue("My Meadery");

        _click(_get(Button.class, spec -> spec.withText("Save")));

        var updated = userRepository.findByEmail("save-profile@test.com").orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getMeaderyName()).isEqualTo("My Meadery");
    }
}
