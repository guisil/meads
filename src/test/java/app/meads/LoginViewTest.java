package app.meads;

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

import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LoginViewTest {

    @Autowired
    ApplicationContext ctx;

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
        var emailField = _get(TextField.class);
        emailField.setValue("user@example.com");

        var button = _get(Button.class);
        button.click();

        // After clicking Continue, should redirect to login?tokenSent
        // The UI location should contain "tokenSent" parameter
        var currentLocation = UI.getCurrent().getInternals().getActiveViewLocation();
        assertThat(currentLocation.getPath()).isEqualTo("login");
        assertThat(currentLocation.getQueryParameters().getParameters())
                .containsKey("tokenSent");
    }
}
