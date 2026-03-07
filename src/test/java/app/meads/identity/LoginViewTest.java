package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.getNotifications;
import static com.github.mvysny.kaributesting.v10.ShortcutsKt.fireShortcut;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class LoginViewTest {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserService userService;

    @BeforeEach
    void setup() {
        userService.createUser("user@example.com", "Dev User", UserStatus.ACTIVE, Role.USER);
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);
        UI.getCurrent().navigate("login");
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
    }

    @Test
    void shouldDisplaySingleScreenLayout() {
        assertThat(_find(EmailField.class, spec -> spec.withLabel("Email"))).isNotEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Get Login Link"))).isNotEmpty();

        var credentialsDetails = _get(Details.class);
        assertThat(credentialsDetails.isOpened()).isFalse();
        assertThat(credentialsDetails.getSummaryText()).isEqualTo("Login with credentials");
    }

    @Test
    void shouldNotSendMagicLinkWhenEmailIsInvalid() {
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("not-an-email");

        _click(_get(Button.class, spec -> spec.withText("Get Login Link")));

        assertThat(emailField.isInvalid()).isTrue();
        assertThat(getNotifications()).isEmpty();
    }

    @Test
    void shouldShowValidationErrorWhenMagicLinkEmailIsBlank() {
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("");

        _click(_get(Button.class, spec -> spec.withText("Get Login Link")));

        assertThat(emailField.isInvalid()).isTrue();
    }

    @Test
    void shouldSendMagicLinkWhenEmailIsValid() {
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("user@example.com");

        _click(_get(Button.class, spec -> spec.withText("Get Login Link")));

        assertThat(getNotifications()).hasSize(1);
        assertThat(getNotifications().getFirst().getElement().getProperty("text"))
                .isEqualTo("If this email is registered, a login link has been sent.");
    }

    @Test
    void shouldShowSameMessageWhenMagicLinkEmailDoesNotExist() {
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("nonexistent@example.com");

        _click(_get(Button.class, spec -> spec.withText("Get Login Link")));

        assertThat(getNotifications()).hasSize(1);
        assertThat(getNotifications().getFirst().getElement().getProperty("text"))
                .isEqualTo("If this email is registered, a login link has been sent.");
    }

    @Test
    void shouldShowValidationErrorWhenLoginWithBlankEmail() {
        _get(Details.class).setOpened(true);
        var passwordField = _get(PasswordField.class, spec -> spec.withLabel("Code / Password"));
        passwordField.setValue("somepassword");

        _click(_get(Button.class, spec -> spec.withText("Login")));

        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        assertThat(emailField.isInvalid()).isTrue();
    }

    @Test
    void shouldShowValidationErrorWhenLoginWithBlankPassword() {
        _get(Details.class).setOpened(true);
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("admin@example.com");

        _click(_get(Button.class, spec -> spec.withText("Login")));

        var passwordField = _get(PasswordField.class, spec -> spec.withLabel("Code / Password"));
        assertThat(passwordField.isInvalid()).isTrue();
    }

    @Test
    void shouldTriggerMagicLinkOnEnterWhenOnlyEmailFilled() {
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("user@example.com");

        fireShortcut(Key.ENTER);

        assertThat(getNotifications()).hasSize(1);
        assertThat(getNotifications().getFirst().getElement().getProperty("text"))
                .isEqualTo("If this email is registered, a login link has been sent.");
    }

    @Test
    void shouldTriggerCredentialLoginOnEnterWhenBothFieldsFilled() {
        _get(Details.class).setOpened(true);
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("admin@example.com");
        var passwordField = _get(PasswordField.class, spec -> spec.withLabel("Code / Password"));
        passwordField.setValue("somepassword");

        fireShortcut(Key.ENTER);

        // Credential login triggers executeJs (form POST) — no notification, no validation error
        assertThat(getNotifications()).isEmpty();
        assertThat(emailField.isInvalid()).isFalse();
        assertThat(passwordField.isInvalid()).isFalse();
    }

    @Test
    void shouldShowForgotPasswordButton() {
        _get(Details.class).setOpened(true);
        assertThat(_find(Button.class, spec -> spec.withText("Forgot password?"))).isNotEmpty();
    }

    @Test
    void shouldSendPasswordResetLinkWhenForgotPasswordClicked() {
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("user@example.com");

        _get(Details.class).setOpened(true);
        _click(_get(Button.class, spec -> spec.withText("Forgot password?")));

        assertThat(getNotifications()).hasSize(1);
        assertThat(getNotifications().getFirst().getElement().getProperty("text"))
                .isEqualTo("If this email is registered, a password reset link has been sent.");
    }

    @Test
    void shouldShowSameMessageWhenForgotPasswordEmailDoesNotExist() {
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("nonexistent@example.com");

        _get(Details.class).setOpened(true);
        _click(_get(Button.class, spec -> spec.withText("Forgot password?")));

        assertThat(getNotifications()).hasSize(1);
        assertThat(getNotifications().getFirst().getElement().getProperty("text"))
                .isEqualTo("If this email is registered, a password reset link has been sent.");
    }

    @Test
    void shouldShowValidationErrorWhenForgotPasswordEmailIsBlank() {
        _get(Details.class).setOpened(true);
        _click(_get(Button.class, spec -> spec.withText("Forgot password?")));

        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        assertThat(emailField.isInvalid()).isTrue();
    }
}
