package app.meads;

import app.meads.MainLayout;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.VaadinServletRequest;
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

import java.util.Arrays;
import java.util.List;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MainLayoutTest {

    @Autowired
    ApplicationContext ctx;

    @BeforeEach
    void setup(TestInfo testInfo) {
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);

        var authentication = resolveAuthentication(testInfo);
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        propagateSecurityContext(authentication);
    }

    private Authentication resolveAuthentication(TestInfo testInfo) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth;
        }
        var method = testInfo.getTestMethod().orElse(null);
        if (method == null) {
            return null;
        }
        var withMockUser = method.getAnnotation(WithMockUser.class);
        if (withMockUser == null) {
            return null;
        }
        var username = withMockUser.username().isEmpty() ? withMockUser.value() : withMockUser.username();
        if (username.isEmpty()) {
            username = "user";
        }
        var authorities = Arrays.stream(withMockUser.roles())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password("password")
                .authorities(authorities)
                .build();
        return new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    }

    private void propagateSecurityContext(Authentication authentication) {
        if (authentication != null) {
            var fakeRequest = (FakeRequest) VaadinServletRequest.getCurrent().getRequest();
            fakeRequest.setUserPrincipalInt(authentication);
            fakeRequest.setUserInRole((principal, role) ->
                    authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role)));
        }
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldRenderRootViewInsideAppLayout() {
        UI.getCurrent().navigate("");

        assertThat(_find(AppLayout.class)).isNotEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDisplayAppTitleInNavbar() {
        UI.getCurrent().navigate("");

        var title = _get(H1.class, spec -> spec.withText("MEADS"));
        assertThat(title).isNotNull();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDisplayLogoutButtonInNavbarWhenAuthenticated() {
        UI.getCurrent().navigate("");

        assertThat(_find(Button.class, spec -> spec.withText("Logout"))).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldDisplayUsersLinkInNavbarForAdmin() {
        UI.getCurrent().navigate("");

        assertThat(_find(Button.class, spec -> spec.withText("Users"))).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldNotDisplayUsersLinkInNavbarForRegularUser() {
        UI.getCurrent().navigate("");

        assertThat(_find(Button.class, spec -> spec.withText("Users"))).isEmpty();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldRenderUserListViewInsideAppLayout() {
        UI.getCurrent().navigate("users");

        assertThat(_find(AppLayout.class)).isNotEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldUseHorizontalLayoutInNavbar() {
        UI.getCurrent().navigate("");

        var layout = _get(MainLayout.class);
        var navbarLayouts = layout.getChildren()
                .filter(c -> c instanceof HorizontalLayout)
                .toList();
        assertThat(navbarLayouts).hasSize(1);
    }
}
