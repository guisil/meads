package app.meads.internal;

import app.meads.MainLayout;
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

    public RootView(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        authenticationContext.getAuthenticatedUser(UserDetails.class).ifPresentOrElse(
                user -> {
                    removeAll();
                    add(new H1("Welcome " + user.getUsername()));
                },
                () -> event.forwardTo("login")
        );
    }
}
