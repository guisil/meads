package app.meads.identity;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final EmailField emailField;
    private final PasswordField passwordField;
    private final Element credentialsForm;
    private final Element usernameInput;
    private final Element passwordInput;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final UserService userService;

    public LoginView(JwtMagicLinkService jwtMagicLinkService, UserService userService) {
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.userService = userService;

        addClassName("login-view");
        setWidth("auto");
        getStyle().set("margin", "0 auto");

        // --- Email + Get Login Link ---
        emailField = new EmailField("Email");
        emailField.setWidthFull();
        emailField.setValueChangeMode(ValueChangeMode.EAGER);
        var magicLinkButton = new Button("Get Login Link");
        magicLinkButton.addClickListener(e -> sendMagicLink());
        var emailRow = new HorizontalLayout(emailField, magicLinkButton);
        emailRow.setWidthFull();
        emailRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        emailRow.expand(emailField);

        // --- Password + Login (collapsible) ---
        passwordField = new PasswordField("Code / Password");
        passwordField.setWidthFull();
        passwordField.setValueChangeMode(ValueChangeMode.EAGER);
        var loginButton = new Button("Login");
        loginButton.addClickListener(e -> loginWithCredentials());
        var passwordRow = new HorizontalLayout(passwordField, loginButton);
        passwordRow.setWidthFull();
        passwordRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        passwordRow.expand(passwordField);

        // Hidden native form for credentials POST (Spring Security formLogin)
        usernameInput = createHiddenInput("username");
        passwordInput = createHiddenInput("password");
        credentialsForm = new Element("form");
        credentialsForm.setAttribute("method", "post");
        credentialsForm.setAttribute("action", "login");
        credentialsForm.getStyle().set("display", "none");
        credentialsForm.appendChild(usernameInput, passwordInput);

        var forgotPasswordButton = new Button("Forgot password?");
        forgotPasswordButton.addClickListener(e -> sendPasswordResetLink());

        var credentialsContent = new VerticalLayout(passwordRow, forgotPasswordButton);
        credentialsContent.getElement().appendChild(credentialsForm);
        var credentialsDetails = new Details("Login with credentials", credentialsContent);

        // Enter key: credential login if password is filled, otherwise magic link
        Shortcuts.addShortcutListener(this, () -> {
            if (StringUtils.hasText(passwordField.getValue())) {
                loginWithCredentials();
            } else {
                sendMagicLink();
            }
        }, Key.ENTER);

        add(emailRow, credentialsDetails);
    }

    private void sendMagicLink() {
        String emailValue = emailField.getValue();
        if (!StringUtils.hasText(emailValue) || emailField.isInvalid()) {
            emailField.setInvalid(true);
            emailField.setErrorMessage("Please enter a valid email address");
            return;
        }
        try {
            var user = userService.findByEmail(emailValue);
            if (user.getPasswordHash() != null) {
                log.info("\n\n\tUser {} has a password — please log in with your credentials\n", emailValue);
            } else {
                String link = jwtMagicLinkService.generateLink(emailValue, Duration.ofDays(7));
                log.info("\n\n\tMagic link for {}: {}\n", emailValue, link);
            }
        } catch (IllegalArgumentException ex) {
            log.info("Magic link requested for non-existent email: {}", emailValue);
        }
        Notification.show("If this email is registered, a login link has been sent.");
    }

    private void sendPasswordResetLink() {
        String emailValue = emailField.getValue();
        if (!StringUtils.hasText(emailValue) || emailField.isInvalid()) {
            emailField.setInvalid(true);
            emailField.setErrorMessage("Please enter a valid email address");
            return;
        }
        try {
            userService.findByEmail(emailValue);
            String link = jwtMagicLinkService.generatePasswordSetupLink(emailValue, Duration.ofDays(7));
            log.info("\n\n\tPassword reset link for {}: {}\n", emailValue, link);
        } catch (IllegalArgumentException ex) {
            log.info("Password reset requested for non-existent email: {}", emailValue);
        }
        Notification.show("If this email is registered, a password reset link has been sent.");
    }

    private void loginWithCredentials() {
        String email = emailField.getValue();
        String password = passwordField.getValue();
        if (!StringUtils.hasText(email) || emailField.isInvalid()) {
            emailField.setInvalid(true);
            emailField.setErrorMessage("Please enter a valid email address");
            return;
        }
        if (!StringUtils.hasText(password)) {
            passwordField.setInvalid(true);
            passwordField.setErrorMessage("Code or password is required");
            return;
        }
        usernameInput.setProperty("value", email);
        passwordInput.setProperty("value", password);
        credentialsForm.executeJs("this.submit()");
    }

    private static Element createHiddenInput(String name) {
        var input = new Element("input");
        input.setAttribute("type", "hidden");
        input.setAttribute("name", name);
        return input;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error")) {
            Notification.show("Invalid email or password. Please try again.")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
