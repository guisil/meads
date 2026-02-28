package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.EmailField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LoginViewTest {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setup() {
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
    void shouldDisplayEmailFieldAndSendMagicLinkButton() {
        assertThat(_get(EmailField.class).getLabel()).isEqualTo("Email");
        assertThat(_get(Button.class).getText()).isEqualTo("Send Magic Link");
    }

    @Test
    void shouldShowNotificationWhenMagicLinkSent() {
        // Given - a user exists
        var user = new User(UUID.randomUUID(), "login.test@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        var emailField = _get(EmailField.class);
        emailField.setValue("login.test@example.com");

        // When - click Send Magic Link
        _click(_get(Button.class));

        // Then - notification should appear
        var notification = _get(Notification.class);
        assertThat(notification.isOpened()).isTrue();
    }

    @Test
    void shouldShowValidationErrorWhenEmailIsBlank() {
        var emailField = _get(EmailField.class);
        emailField.setValue("");

        _click(_get(Button.class));

        assertThat(emailField.isInvalid()).isTrue();
        assertThat(emailField.getErrorMessage()).isNotEmpty();
    }

    @Test
    void shouldUseEmailFieldForEmailInput() {
        assertThat(_find(EmailField.class)).isNotEmpty();
    }
}
