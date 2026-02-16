package app.meads.identity.internal;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.Map;

@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    private final MagicLinkService magicLinkService;

    public LoginView(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;

        var email = new TextField("Email");
        email.getElement().setAttribute("name", "username");

        var button = new Button("Continue");
        button.addClickListener(e -> {
            // Validate email format
            String emailValue = email.getValue();
            if (emailValue == null || !emailValue.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                email.setInvalid(true);
                email.setErrorMessage("Please enter a valid email address");
                return;
            }

            email.setInvalid(false);
            magicLinkService.requestMagicLink(emailValue);
            e.getSource().getUI().ifPresent(ui ->
                ui.navigate("login", QueryParameters.simple(Map.of("tokenSent", "")))
            );
        });

        add(email, button);
    }
}
