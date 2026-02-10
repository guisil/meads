package app.meads;

import app.meads.internal.UserRepository;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
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
    void shouldDisplayEmailFieldAndContinueButton() {
        assertThat(_get(TextField.class).getLabel()).isEqualTo("Email");
        assertThat(_get(Button.class).getText()).isEqualTo("Continue");
    }

    @Test
    void shouldHaveUsernameAttributeOnEmailField() {
        assertThat(_get(TextField.class).getElement().getAttribute("name")).isEqualTo("username");
    }

    @Test
    void shouldRedirectToTokenSentPageWhenContinueClicked() {
        // Given - a user exists
        var user = new User(UUID.randomUUID(), "login.test@example.com", "Test User", UserStatus.ACTIVE);
        userRepository.save(user);

        var emailField = _get(TextField.class);
        emailField.setValue("login.test@example.com");

        var button = _get(Button.class);
        button.click();

        // After clicking Continue, should redirect to login?tokenSent
        // The UI location should contain "tokenSent" parameter
        var currentLocation = UI.getCurrent().getInternals().getActiveViewLocation();
        assertThat(currentLocation.getPath()).isEqualTo("login");
        assertThat(currentLocation.getQueryParameters().getParameters())
                .containsKey("tokenSent");
    }

    @Test
    void shouldShowValidationErrorWhenEmailIsInvalid() {
        var emailField = _get(TextField.class);
        emailField.setValue("notanemail");

        var button = _get(Button.class);
        button.click();

        assertThat(emailField.isInvalid()).isTrue();
        assertThat(emailField.getErrorMessage()).isNotEmpty();

        // Should not redirect when validation fails
        var currentLocation = UI.getCurrent().getInternals().getActiveViewLocation();
        assertThat(currentLocation.getQueryParameters().getParameters())
                .doesNotContainKey("tokenSent");
    }

    @Test
    void shouldShowValidationErrorWhenEmailIsBlank() {
        var emailField = _get(TextField.class);
        emailField.setValue("");

        var button = _get(Button.class);
        button.click();

        assertThat(emailField.isInvalid()).isTrue();
        assertThat(emailField.getErrorMessage()).isNotEmpty();

        // Should not redirect when validation fails
        var currentLocation = UI.getCurrent().getInternals().getActiveViewLocation();
        assertThat(currentLocation.getQueryParameters().getParameters())
                .doesNotContainKey("tokenSent");
    }
}
