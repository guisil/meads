package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserListView;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserListViewTest {

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
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldDisplayUserListViewWithGrid() {
        UI.getCurrent().navigate("users");

        var grid = _get(Grid.class);
        assertThat(grid).isNotNull();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldHaveRolesAllowedAnnotationForSecurity() {
        // Note: @RolesAllowed("SYSTEM_ADMIN") on UserListView is enforced by Spring Security
        // at runtime, but Karibu tests don't go through the full security filter chain.
        // This test documents that the security annotation exists.
        // In production, regular users will get an access denied error.

        var annotation = UserListView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("SYSTEM_ADMIN");
    }
}
