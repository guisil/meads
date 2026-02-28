package app.meads.identity;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(LoginView.class);

    public LoginView(JwtMagicLinkService jwtMagicLinkService) {
        // --- Magic link section ---
        var magicLinkEmail = new EmailField("Email");
        var magicLinkButton = new Button("Send Magic Link");
        magicLinkButton.addClickListener(e -> {
            String emailValue = magicLinkEmail.getValue();
            if (emailValue == null || emailValue.isBlank()) {
                magicLinkEmail.setInvalid(true);
                magicLinkEmail.setErrorMessage("Please enter a valid email address");
                return;
            }
            magicLinkEmail.setInvalid(false);
            String link = jwtMagicLinkService.generateLink(emailValue, Duration.ofDays(7));
            log.info("\n\n\tMagic link for {}: {}\n", emailValue, link);
            Notification.show("Magic link sent! Check the server logs.");
        });
        add(magicLinkEmail, magicLinkButton);

        add(new Hr());

        // --- Access code section ---
        var accessCodeEmail = new EmailField("Access Code Email");
        var accessCodeField = new TextField("Access Code");
        var accessCodeButton = new Button("Login with Code");
        accessCodeButton.addClickListener(e ->
                Notification.show("Access code login is not yet available."));
        add(accessCodeEmail, accessCodeField, accessCodeButton);

        add(new Hr());

        // --- Admin password section ---
        var adminEmail = new EmailField("Admin Email");
        adminEmail.getElement().setAttribute("name", "username");
        var adminPassword = new PasswordField("Password");
        adminPassword.getElement().setAttribute("name", "password");
        var adminButton = new Button("Admin Login");
        adminButton.addClickListener(e -> {
            // Submit handled by Spring Security formLogin via native form POST
            getElement().executeJs(
                    "const form = document.createElement('form');" +
                    "form.method = 'POST';" +
                    "form.action = '/login';" +
                    "const emailInput = document.createElement('input');" +
                    "emailInput.name = 'username';" +
                    "emailInput.value = $0;" +
                    "form.appendChild(emailInput);" +
                    "const passInput = document.createElement('input');" +
                    "passInput.name = 'password';" +
                    "passInput.value = $1;" +
                    "form.appendChild(passInput);" +
                    "const csrf = document.querySelector('meta[name=\"_csrf\"]');" +
                    "if (csrf) {" +
                    "  const csrfInput = document.createElement('input');" +
                    "  csrfInput.name = csrf.getAttribute('content');" +
                    "  const csrfToken = document.querySelector('meta[name=\"_csrf_token\"]');" +
                    "  if (csrfToken) csrfInput.value = csrfToken.getAttribute('content');" +
                    "  form.appendChild(csrfInput);" +
                    "}" +
                    "document.body.appendChild(form);" +
                    "form.submit();",
                    adminEmail.getValue(), adminPassword.getValue());
        });
        add(adminEmail, adminPassword, adminButton);
    }
}
