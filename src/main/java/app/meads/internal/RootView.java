package app.meads.internal;

import app.meads.MainLayout;
import app.meads.identity.UserService;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.core.userdetails.UserDetails;

@Route(value = "", layout = MainLayout.class)
@AnonymousAllowed
public class RootView extends VerticalLayout implements BeforeEnterObserver {

    private final transient AuthenticationContext authenticationContext;
    private final transient UserService userService;

    public RootView(AuthenticationContext authenticationContext, UserService userService) {
        this.authenticationContext = authenticationContext;
        this.userService = userService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        authenticationContext.getAuthenticatedUser(UserDetails.class).ifPresentOrElse(
                userDetails -> {
                    removeAll();
                    String displayName;
                    try {
                        displayName = userService.findByEmail(userDetails.getUsername()).getName();
                    } catch (IllegalArgumentException e) {
                        displayName = userDetails.getUsername();
                    }
                    add(new H1("Welcome " + displayName));
                },
                () -> event.forwardTo("login")
        );
    }
}
