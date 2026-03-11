package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static com.github.mvysny.kaributesting.v10.ShortcutsKt.fireShortcut;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SetPasswordViewTest {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserService userService;

    @Autowired
    JwtMagicLinkService jwtMagicLinkService;

    @BeforeEach
    void setup() {
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDisplayInfoMessageAboutLoginLinksBeingDisabled() {
        User user = userService.createUser("setpw-info@example.com", "Info User", UserStatus.PENDING, Role.USER);
        String link = jwtMagicLinkService.generatePasswordSetupLink(user.getEmail(), Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        UI.getCurrent().navigate("set-password", QueryParameters.of("token", token));

        var infoParagraph = _get(Paragraph.class);
        assertThat(infoParagraph.getText()).contains("login links will no longer work");
    }

    @Test
    void shouldRenderPasswordFieldsAndSubmitButton() {
        User user = userService.createUser("setpw@example.com", "Set Pw User", UserStatus.PENDING, Role.USER);
        String link = jwtMagicLinkService.generatePasswordSetupLink(user.getEmail(), Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        UI.getCurrent().navigate("set-password", QueryParameters.of("token", token));

        var passwordField = _get(PasswordField.class, spec -> spec.withLabel("Password"));
        var confirmField = _get(PasswordField.class, spec -> spec.withLabel("Confirm Password"));
        var submitButton = _get(Button.class, spec -> spec.withText("Set Password"));
        assertThat(passwordField).isNotNull();
        assertThat(confirmField).isNotNull();
        assertThat(submitButton).isNotNull();
    }

    @Test
    void shouldSetPasswordAndShowSuccessNotification() {
        User user = userService.createUser("setpw2@example.com", "Set Pw User 2", UserStatus.PENDING, Role.USER);
        String link = jwtMagicLinkService.generatePasswordSetupLink(user.getEmail(), Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        UI.getCurrent().navigate("set-password", QueryParameters.of("token", token));

        var passwordField = _get(PasswordField.class, spec -> spec.withLabel("Password"));
        var confirmField = _get(PasswordField.class, spec -> spec.withLabel("Confirm Password"));
        passwordField.setValue("newPassword123");
        confirmField.setValue("newPassword123");

        _click(_get(Button.class, spec -> spec.withText("Set Password")));

        var notification = _get(Notification.class);
        assertThat(notification.getElement().getProperty("text")).contains("Password set successfully");

        User updated = userService.findById(user.getId());
        assertThat(updated.getPasswordHash()).isNotNull();
    }

    @Test
    void shouldShowErrorWhenPasswordsDoNotMatch() {
        User user = userService.createUser("setpw3@example.com", "Set Pw User 3", UserStatus.PENDING, Role.USER);
        String link = jwtMagicLinkService.generatePasswordSetupLink(user.getEmail(), Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        UI.getCurrent().navigate("set-password", QueryParameters.of("token", token));

        var passwordField = _get(PasswordField.class, spec -> spec.withLabel("Password"));
        var confirmField = _get(PasswordField.class, spec -> spec.withLabel("Confirm Password"));
        passwordField.setValue("newPassword123");
        confirmField.setValue("differentPassword");

        _click(_get(Button.class, spec -> spec.withText("Set Password")));

        assertThat(confirmField.isInvalid()).isTrue();
        assertThat(confirmField.getErrorMessage()).contains("Passwords do not match");
    }

    @Test
    void shouldShowErrorWhenPasswordTooShort() {
        User user = userService.createUser("setpw4@example.com", "Set Pw User 4", UserStatus.PENDING, Role.USER);
        String link = jwtMagicLinkService.generatePasswordSetupLink(user.getEmail(), Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        UI.getCurrent().navigate("set-password", QueryParameters.of("token", token));

        var passwordField = _get(PasswordField.class, spec -> spec.withLabel("Password"));
        var confirmField = _get(PasswordField.class, spec -> spec.withLabel("Confirm Password"));
        passwordField.setValue("short");
        confirmField.setValue("short");

        _click(_get(Button.class, spec -> spec.withText("Set Password")));

        var notification = _get(Notification.class);
        assertThat(notification.getElement().getProperty("text")).contains("at least 8 characters");
    }

    @Test
    void shouldSubmitPasswordOnEnterKey() {
        User user = userService.createUser("setpw-enter@example.com", "Enter Key User", UserStatus.PENDING, Role.USER);
        String link = jwtMagicLinkService.generatePasswordSetupLink(user.getEmail(), Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        UI.getCurrent().navigate("set-password", QueryParameters.of("token", token));

        var passwordField = _get(PasswordField.class, spec -> spec.withLabel("Password"));
        var confirmField = _get(PasswordField.class, spec -> spec.withLabel("Confirm Password"));
        passwordField.setValue("validPassword123");
        confirmField.setValue("validPassword123");

        fireShortcut(Key.ENTER);

        var notification = _get(Notification.class);
        assertThat(notification.getElement().getProperty("text")).contains("Password set successfully");

        User updated = userService.findById(user.getId());
        assertThat(updated.getPasswordHash()).isNotNull();
    }
}
