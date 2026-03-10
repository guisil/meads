package app.meads;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
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

@AnonymousAllowed
public class MainLayout extends AppLayout {

    private final transient AuthenticationContext authenticationContext;
    private final CompetitionAdminChecker competitionAdminChecker;

    public MainLayout(AuthenticationContext authenticationContext,
                       CompetitionAdminChecker competitionAdminChecker) {
        this.authenticationContext = authenticationContext;
        this.competitionAdminChecker = competitionAdminChecker;

        var toggle = new DrawerToggle();
        var title = new H1("MEADS");
        title.addClassName("app-title");

        var navbar = new HorizontalLayout(toggle, title);
        navbar.setFlexGrow(1, title);
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

        if (authenticationContext.isAuthenticated()) {
            nav.addItem(new SideNavItem("My Profile", "profile", VaadinIcon.USER.create()));
        }

        var scroller = new Scroller(nav);
        scroller.getStyle().set("padding", "var(--lumo-space-s)");
        addToDrawer(scroller);

        setPrimarySection(Section.DRAWER);
        setDrawerOpened(false);
    }
}
