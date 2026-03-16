package app.meads;

import app.meads.MainLayout;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNavItem;
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
    void shouldDisplayAppLogoInNavbar() {
        UI.getCurrent().navigate("");

        var logo = _get(Image.class);
        assertThat(logo.getSrc()).contains("meads-logo.svg");
        assertThat(logo.getAlt().orElse("")).isEqualTo("MEADS");
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void shouldDisplayUserMenuInNavbarWhenAuthenticated() {
        UI.getCurrent().navigate("");

        var menuBars = _find(MenuBar.class);
        assertThat(menuBars).hasSizeGreaterThanOrEqualTo(1);

        // Find the user menu (contains the email address)
        var userMenu = menuBars.stream()
                .filter(mb -> mb.getItems().stream()
                        .anyMatch(item -> item.getChildren()
                                .anyMatch(c -> c instanceof Text
                                        && ((Text) c).getText().contains("@"))))
                .findFirst().orElseThrow();
        var userItem = userMenu.getItems().getFirst();
        var emailText = userItem.getChildren()
                .filter(c -> c instanceof Text)
                .map(c -> ((Text) c).getText())
                .findFirst().orElse("");
        assertThat(emailText).contains("user@example.com");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldDisplayUsersLinkInDrawerForAdmin() {
        UI.getCurrent().navigate("");

        assertThat(_find(SideNavItem.class))
                .extracting(SideNavItem::getLabel)
                .contains("Users");
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldNotDisplayUsersLinkInDrawerForRegularUser() {
        UI.getCurrent().navigate("");

        assertThat(_find(SideNavItem.class))
                .extracting(SideNavItem::getLabel)
                .doesNotContain("Users");
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldNotDisplayMyCompetitionsLinkForRegularUserWithoutAdminCompetitions() {
        UI.getCurrent().navigate("");

        assertThat(_find(SideNavItem.class))
                .extracting(SideNavItem::getLabel)
                .doesNotContain("My Competitions");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldNotDisplayMyCompetitionsLinkForSystemAdmin() {
        UI.getCurrent().navigate("");

        assertThat(_find(SideNavItem.class))
                .extracting(SideNavItem::getLabel)
                .doesNotContain("My Competitions");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldRenderUserListViewInsideAppLayout() {
        UI.getCurrent().navigate("users");

        assertThat(_find(AppLayout.class)).isNotEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDisplayMyEntriesLinkInDrawerForRegularUser() {
        UI.getCurrent().navigate("");

        assertThat(_find(SideNavItem.class))
                .extracting(SideNavItem::getLabel)
                .contains("My Entries");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldNotDisplayMyEntriesLinkInDrawerForSystemAdmin() {
        UI.getCurrent().navigate("");

        assertThat(_find(SideNavItem.class))
                .extracting(SideNavItem::getLabel)
                .doesNotContain("My Entries");
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
