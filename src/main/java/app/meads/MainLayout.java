package app.meads;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.boot.info.BuildProperties;
import org.springframework.lang.Nullable;

@AnonymousAllowed
public class MainLayout extends AppLayout {

    private final transient AuthenticationContext authenticationContext;
    private final CompetitionAdminChecker competitionAdminChecker;
    private final String appVersion;

    public MainLayout(AuthenticationContext authenticationContext,
                       CompetitionAdminChecker competitionAdminChecker,
                       @Nullable BuildProperties buildProperties) {
        this.authenticationContext = authenticationContext;
        this.competitionAdminChecker = competitionAdminChecker;
        this.appVersion = buildProperties != null ? "v" + buildProperties.getVersion() : "";

        var toggle = new DrawerToggle();
        var logo = new Image("images/meads-logo.svg", "MEADS");
        logo.setHeight("44px");
        logo.addClassName("app-logo");

        var spacer = new HorizontalLayout();

        var navbar = new HorizontalLayout(toggle, logo, spacer);
        navbar.setFlexGrow(1, spacer);
        navbar.setWidthFull();
        navbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        if (authenticationContext.isAuthenticated()) {
            var email = authenticationContext.getPrincipalName().orElse("");

            var userIcon = new Icon(VaadinIcon.USER);
            userIcon.getStyle().setWidth("var(--lumo-icon-size-s)");
            userIcon.getStyle().setHeight("var(--lumo-icon-size-s)");
            userIcon.getStyle().setMarginRight("var(--lumo-space-xs)");

            var userMenu = new MenuBar();
            userMenu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
            var userItem = userMenu.addItem(userIcon);
            userItem.add(new Text(email));
            userItem.getSubMenu().addItem("My Profile", e ->
                    getUI().ifPresent(ui -> ui.navigate("profile")));
            userItem.getSubMenu().addItem("Logout", e -> authenticationContext.logout());

            navbar.add(userMenu);
        }

        addToNavbar(navbar);

        var nav = new SideNav();

        if (authenticationContext.hasRole("SYSTEM_ADMIN")) {
            nav.addItem(new SideNavItem("Competitions", "competitions", VaadinIcon.CALENDAR.create()));
            nav.addItem(new SideNavItem("Users", "users", VaadinIcon.USERS.create()));
        } else if (authenticationContext.isAuthenticated()) {
            var email = authenticationContext.getPrincipalName().orElse("");
            if (competitionAdminChecker.hasAdminCompetitions(email)) {
                nav.addItem(new SideNavItem("My Competitions", "my-competitions", VaadinIcon.CALENDAR.create()));
            }
        }

        if (authenticationContext.isAuthenticated() && !authenticationContext.hasRole("SYSTEM_ADMIN")) {
            nav.addItem(new SideNavItem("My Entries", "my-entries", VaadinIcon.LIST.create()));
        }

        var drawerContent = new Div();
        drawerContent.getStyle().set("display", "flex");
        drawerContent.getStyle().set("flex-direction", "column");
        drawerContent.getStyle().set("height", "100%");

        var scroller = new Scroller(nav);
        scroller.getStyle().set("padding", "var(--lumo-space-s)");
        scroller.getStyle().set("flex", "1");
        drawerContent.add(scroller);

        if (!appVersion.isEmpty()) {
            var versionLabel = new Span(appVersion);
            versionLabel.getStyle().set("padding", "var(--lumo-space-s)");
            versionLabel.getStyle().set("color", "var(--lumo-tertiary-text-color)");
            versionLabel.getStyle().set("font-size", "var(--lumo-font-size-xs)");
            versionLabel.getStyle().set("text-align", "center");
            drawerContent.add(versionLabel);
        }

        addToDrawer(drawerContent);

        setPrimarySection(Section.DRAWER);
        setDrawerOpened(false);
    }
}
