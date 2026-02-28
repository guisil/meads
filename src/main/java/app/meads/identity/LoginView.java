package app.meads.identity;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(LoginView.class);

    private final LoginForm loginForm;

    public LoginView(JwtMagicLinkService jwtMagicLinkService) {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();

        // --- Magic Link tab ---
        var magicLinkLayout = new VerticalLayout();
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
        magicLinkLayout.add(magicLinkEmail, magicLinkButton);
        tabSheet.add("Magic Link", magicLinkLayout);

        // --- Credentials tab (code / password) ---
        loginForm = new LoginForm();
        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        var i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("");
        i18n.getForm().setUsername("Email");
        i18n.getForm().setPassword("Code / Password");
        loginForm.setI18n(i18n);

        loginForm.getElement().getStyle()
                .set("--vaadin-login-form-background", "transparent")
                .set("--vaadin-login-form-padding", "0");

        tabSheet.add("Login with Credentials", loginForm);

        add(tabSheet);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
