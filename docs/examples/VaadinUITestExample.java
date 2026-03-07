// == VaadinUITestExample.java ==
// Server-side Vaadin UI tests using Karibu Testing. No browser needed.
// USE WHEN: Testing view rendering, form validation, button actions, grids.
// REFERENCE: MyEntriesViewTest.java, UserListViewTest.java, DivisionEntryAdminViewTest.java

package app.meads.order;

import app.meads.TestcontainersConfiguration;
import app.meads.order.internal.OrderView;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.UI;
import jakarta.servlet.http.HttpServletRequest;
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

import java.security.Principal;
import java.util.List;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class OrderViewUITest {

    @Autowired ApplicationContext ctx;

    @BeforeEach
    void setup(TestInfo testInfo) {
        // 1. Auto-discover all Vaadin views
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);

        // 2. Resolve @WithMockUser and propagate to Vaadin security context
        var auth = resolveAuthentication(testInfo);
        if (auth != null) {
            propagateSecurityContext(auth);
        }
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    void shouldDisplayOrderViewWithGrid() {
        UI.getCurrent().navigate(OrderView.class);
        assertThat(_get(Grid.class)).isNotNull();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    void shouldShowSuccessNotificationWhenOrderCreated() {
        UI.getCurrent().navigate(OrderView.class);

        _get(TextField.class, spec -> spec.withCaption("Product")).setValue("Widget");
        _click(_get(Button.class, spec -> spec.withText("Place Order")));

        // Notification text is stored under element property "text"
        assertThat(_get(Notification.class).getElement().getProperty("text"))
                .contains("Order created");
    }

    // --- Security context helpers ---
    // @WithMockUser context can be lost when VaadinAwareSecurityContextHolderStrategy
    // is active. These helpers resolve and propagate the authentication manually.

    private Authentication resolveAuthentication(TestInfo testInfo) {
        var method = testInfo.getTestMethod().orElse(null);
        if (method == null) return null;

        var annotation = method.getAnnotation(WithMockUser.class);
        if (annotation == null) return null;

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + annotation.roles()[0]));
        return new UsernamePasswordAuthenticationToken(annotation.username(), "password", authorities);
    }

    private void propagateSecurityContext(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Also set principal on MockVaadin's FakeRequest for Vaadin security integration
        var request = com.vaadin.flow.server.VaadinServletRequest.getCurrent();
        if (request != null) {
            var httpRequest = request.getRequest();
            if (httpRequest instanceof com.github.mvysny.kaributesting.v10.mock.FakeRequest fakeRequest) {
                fakeRequest.setUserPrincipalInt((Principal) auth);
                fakeRequest.setUserInRole(role ->
                        auth.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role)));
            }
        }
    }
}

// KEY:
// - @SpringBootTest + @DirtiesContext (modifies security context strategy)
// - Routes().autoDiscoverViews("app.meads") — discovers all Vaadin views
// - MockSpringServlet(routes, ctx, UI::new) — creates mock servlet with Spring context
// - MockVaadin.setup(UI::new, servlet) in @BeforeEach
// - MockVaadin.tearDown() + SecurityContextHolder.clearContext() in @AfterEach — ALWAYS
// - resolveAuthentication() + propagateSecurityContext() — handles @WithMockUser context loss
// - _get(Class, spec) finds ONE component. Throws if 0 or 2+.
// - _find(Class, spec) returns all matches.
// - _click() simulates user click.
// - Notification text: getElement().getProperty("text"), NOT getText()
// - Tests run in 5-60ms — no browser.
//
// KARIBU QUIRKS:
// - TabSheet lazy-loads content. Must call tabSheet.setSelectedIndex(N) before finding
//   components inside non-default tabs.
// - Grid component columns render lazily. Buttons inside addComponentColumn are NOT
//   found by _find(Button.class). Verify column count/headers instead.
// - Grid ID for lookup: grid.setId("my-grid"), then _get(Grid.class, spec -> spec.withId("my-grid"))
