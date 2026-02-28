package app.meads;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.sidenav.SideNavItem;
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

        var heading = _get(H1.class, spec -> spec.withText("Welcome user"));
        assertThat(heading.getText()).contains("Welcome");
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldDisplayUserEmailWhenAuthenticated() {
        UI.getCurrent().navigate("");

        var heading = _get(H1.class, spec -> spec.withText("Welcome test@example.com"));
        assertThat(heading.getText()).contains("test@example.com");
    }

    @Test
    @WithMockUser
    void shouldDisplayLogoutButtonWhenAuthenticated() {
        UI.getCurrent().navigate("");

        var button = _get(Button.class, spec -> spec.withText("Logout"));
        assertThat(button.getText()).isEqualTo("Logout");
    }

    @Test
    @WithMockUser
    void shouldHaveLogoutButtonThatNavigatesToLogoutEndpoint() {
        UI.getCurrent().navigate("");

        var button = _get(Button.class, spec -> spec.withText("Logout"));
        assertThat(button.getText()).isEqualTo("Logout");

        // The button should not throw NPE when clicked
        // In production, it will navigate to /logout endpoint via setLocation()
        // In Karibu tests, setLocation() doesn't actually navigate, but shouldn't error
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            _click(button);
        });
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldHaveUserListLinkForAdminUsers() {
        UI.getCurrent().navigate("");

        assertThat(com.github.mvysny.kaributesting.v10.LocatorJ._find(SideNavItem.class))
                .extracting(SideNavItem::getLabel)
                .contains("Users");
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldNotShowUserListLinkForRegularUsers() {
        UI.getCurrent().navigate("");

        assertThat(com.github.mvysny.kaributesting.v10.LocatorJ._find(SideNavItem.class))
                .extracting(SideNavItem::getLabel)
                .doesNotContain("Users");
    }
}
