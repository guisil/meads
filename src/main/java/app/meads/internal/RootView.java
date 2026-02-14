package app.meads.internal;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.core.context.SecurityContextHolder;

@Route("")
@AnonymousAllowed
public class RootView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticationContext authenticationContext;

    public RootView(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            removeAll();
            String username = authentication.getName();
            add(new H1("Welcome " + username));

            // Show Users link for system admin users
            if (authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN"))) {
                add(new Button("Users", e -> e.getSource().getUI().ifPresent(ui -> ui.navigate("users"))));
            }

            add(new Button("Logout", e -> authenticationContext.logout()));
        } else {
            event.forwardTo("login");
        }
    }
}
