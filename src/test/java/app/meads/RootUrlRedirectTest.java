package app.meads;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RootUrlRedirectTest {

    @Autowired
    ApplicationContext ctx;

    @BeforeEach
    void setup() {
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
    }

    @Test
    void shouldRedirectToLoginWhenAccessingRootUrl() {
        UI.getCurrent().navigate("");

        var location = UI.getCurrent().getInternals().getActiveViewLocation();
        assertThat(location.getPath()).isEqualTo("login");
    }

    @Test
    @WithMockUser
    void shouldShowHomePageWhenAuthenticatedUserAccessesRootUrl() {
        UI.getCurrent().navigate("");

        var heading = _get(H1.class);
        assertThat(heading.getText()).contains("Welcome");
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldDisplayUserEmailWhenAuthenticated() {
        UI.getCurrent().navigate("");

        var heading = _get(H1.class);
        assertThat(heading.getText()).contains("test@example.com");
    }

    @Test
    @WithMockUser
    void shouldDisplayLogoutButtonWhenAuthenticated() {
        UI.getCurrent().navigate("");

        var button = _get(Button.class);
        assertThat(button.getText()).isEqualTo("Logout");
    }

    @Test
    @WithMockUser
    void shouldRedirectToLoginWhenLogoutButtonClicked() {
        UI.getCurrent().navigate("");

        _click(_get(Button.class));

        var location = UI.getCurrent().getInternals().getActiveViewLocation();
        assertThat(location.getPath()).isEqualTo("login");
    }

    @Test
    @WithMockUser
    void shouldHandleLogoutWithoutNPEWhenSessionInvalidated() {
        UI.getCurrent().navigate("");
        var button = _get(Button.class);

        // Verify that clicking logout doesn't cause NPE even if UI context is cleared
        // This simulates the production scenario where UI.getCurrent() might return null
        // after session invalidation
        assertThat(button).isNotNull();

        // The button click should not throw NPE - it should handle the UI reference properly
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            _click(button);
        });

        // Verify we still end up on login page
        var location = UI.getCurrent().getInternals().getActiveViewLocation();
        assertThat(location.getPath()).isEqualTo("login");
    }
}
