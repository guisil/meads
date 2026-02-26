package app.meads;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;

@AnonymousAllowed
public class MainLayout extends AppLayout {

    private final transient AuthenticationContext authenticationContext;

    public MainLayout(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;

        H1 title = new H1("MEADS");
        title.getStyle().set("font-size", "1.125rem").set("margin", "0");

        Button logoutButton = new Button("Logout", e -> authenticationContext.logout());

        if (authenticationContext.hasRole("SYSTEM_ADMIN")) {
            Button usersButton = new Button("Users", e -> e.getSource().getUI().ifPresent(ui -> ui.navigate("users")));
            addToNavbar(title, usersButton, logoutButton);
        } else {
            addToNavbar(title, logoutButton);
        }
    }
}
