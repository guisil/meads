package app.meads.identity;

import app.meads.BusinessRuleException;
import app.meads.LanguageMapping;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
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

@Slf4j
@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final EmailField emailField;
    private final PasswordField passwordField;
    private final Element credentialsForm;
    private final Element usernameInput;
    private final Element passwordInput;
    private final EmailService emailService;
    private final UserService userService;
    private final Button loginButton;

    public LoginView(EmailService emailService, UserService userService) {
        this.emailService = emailService;
        this.userService = userService;

        addClassName("login-view");
        setWidth("auto");
        getStyle().set("margin", "0 auto");

        // --- Email + Get Login Link ---
        emailField = new EmailField(getTranslation("login.email.label"));
        emailField.setWidthFull();
        emailField.setMaxLength(255);
        emailField.setValueChangeMode(ValueChangeMode.EAGER);
        emailField.setAutocomplete(Autocomplete.EMAIL);
        var magicLinkButton = new Button(getTranslation("login.magic-link.button"));
        magicLinkButton.addClickListener(e -> sendMagicLink());
        var emailRow = new HorizontalLayout(emailField, magicLinkButton);
        emailRow.setWidthFull();
        emailRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        emailRow.expand(emailField);

        // --- Password + Login (collapsible) ---
        passwordField = new PasswordField(getTranslation("login.password.label"));
        passwordField.setWidthFull();
        passwordField.setMaxLength(128);
        passwordField.setValueChangeMode(ValueChangeMode.EAGER);
        passwordField.setAutocomplete(Autocomplete.CURRENT_PASSWORD);
        loginButton = new Button(getTranslation("login.login.button"));
        loginButton.setDisableOnClick(true);
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

        var forgotPasswordButton = new Button(getTranslation("login.forgot-password"));
        forgotPasswordButton.addClickListener(e -> sendPasswordResetLink());

        var credentialsContent = new VerticalLayout(passwordRow, forgotPasswordButton);
        credentialsContent.getElement().appendChild(credentialsForm);
        var credentialsDetails = new Details(getTranslation("login.credentials.section"), credentialsContent);

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
            emailField.setErrorMessage(getTranslation("login.email.error"));
            return;
        }
        try {
            var user = userService.findByEmail(emailValue);
            var locale = LanguageMapping.resolveLocale(user.getPreferredLanguage(), user.getCountry());
            if (user.getPasswordHash() != null) {
                emailService.sendCredentialsReminder(emailValue, locale);
            } else {
                emailService.sendMagicLink(emailValue, locale);
            }
        } catch (BusinessRuleException ex) {
            log.info("Magic link requested for non-existent email: {}", emailValue);
        }
        Notification.show(getTranslation("login.magic-link.sent"));
    }

    private void sendPasswordResetLink() {
        String emailValue = emailField.getValue();
        if (!StringUtils.hasText(emailValue) || emailField.isInvalid()) {
            emailField.setInvalid(true);
            emailField.setErrorMessage(getTranslation("login.email.error"));
            return;
        }
        try {
            var user = userService.findByEmail(emailValue);
            var locale = LanguageMapping.resolveLocale(user.getPreferredLanguage(), user.getCountry());
            emailService.sendPasswordReset(emailValue, locale);
        } catch (BusinessRuleException ex) {
            log.info("Password reset requested for non-existent email: {}", emailValue);
        }
        Notification.show(getTranslation("login.password-reset.sent"));
    }

    private void loginWithCredentials() {
        String email = emailField.getValue();
        String password = passwordField.getValue();
        if (!StringUtils.hasText(email) || emailField.isInvalid()) {
            emailField.setInvalid(true);
            emailField.setErrorMessage(getTranslation("login.email.error"));
            loginButton.setEnabled(true);
            return;
        }
        if (!StringUtils.hasText(password)) {
            passwordField.setInvalid(true);
            passwordField.setErrorMessage(getTranslation("login.password.error"));
            loginButton.setEnabled(true);
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
            Notification.show(getTranslation("login.invalid-credentials"))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
