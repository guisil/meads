package app.meads.identity;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(LoginView.class);

    private final JwtMagicLinkService jwtMagicLinkService;

    public LoginView(JwtMagicLinkService jwtMagicLinkService) {
        this.jwtMagicLinkService = jwtMagicLinkService;

        var email = new EmailField("Email");

        var button = new Button("Send Magic Link");
        button.addClickListener(e -> {
            String emailValue = email.getValue();
            if (emailValue == null || emailValue.isBlank()) {
                email.setInvalid(true);
                email.setErrorMessage("Please enter a valid email address");
                return;
            }

            email.setInvalid(false);
            String link = jwtMagicLinkService.generateLink(emailValue, Duration.ofDays(7));
            log.info("\n\n\tMagic link for {}: {}\n", emailValue, link);
            Notification.show("Magic link sent! Check the server logs.");
        });

        add(email, button);
    }
}
