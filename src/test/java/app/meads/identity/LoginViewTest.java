package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.EmailField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
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
    void shouldDisplayTabSheet() {
        var tabSheet = _get(TabSheet.class);
        assertThat(tabSheet).isNotNull();
    }

    @Test
    void shouldDisplayMagicLinkTab() {
        assertThat(_find(EmailField.class, spec -> spec.withLabel("Email"))).isNotEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Send Magic Link"))).isNotEmpty();
    }

    @Test
    void shouldDisplayCredentialsTab() {
        var tabSheet = _get(TabSheet.class);
        tabSheet.setSelectedIndex(1);

        var loginForm = _get(LoginForm.class);
        assertThat(loginForm).isNotNull();
        assertThat(loginForm.getAction()).isEqualTo("login");
    }

    @Test
    void shouldShowValidationErrorWhenMagicLinkEmailIsBlank() {
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        emailField.setValue("");

        _click(_get(Button.class, spec -> spec.withText("Send Magic Link")));

        assertThat(emailField.isInvalid()).isTrue();
    }
}
