package app.meads;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
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

    public MainLayout(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;

        var toggle = new DrawerToggle();
        var title = new H1("MEADS");
        title.getStyle().set("font-size", "1.125rem").set("margin", "0");

        var logoutButton = new Button("Logout", e -> authenticationContext.logout());
        logoutButton.getElement().getThemeList().add("tertiary small");

        var navbar = new HorizontalLayout(toggle, title);
        navbar.setFlexGrow(1, title);
        navbar.setWidthFull();
        navbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.add(logoutButton);

        addToNavbar(navbar);

        var nav = new SideNav();
        nav.addItem(new SideNavItem("Home", "", VaadinIcon.HOME.create()));

        if (authenticationContext.hasRole("SYSTEM_ADMIN")) {
            nav.addItem(new SideNavItem("Events", "events", VaadinIcon.CALENDAR.create()));
            nav.addItem(new SideNavItem("Users", "users", VaadinIcon.USERS.create()));
        }

        var scroller = new Scroller(nav);
        scroller.getStyle().set("padding", "var(--lumo-space-s)");
        addToDrawer(scroller);

        setPrimarySection(Section.DRAWER);
    }
}
