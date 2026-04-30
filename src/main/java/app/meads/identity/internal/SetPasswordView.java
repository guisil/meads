package app.meads.identity.internal;

import app.meads.BusinessRuleException;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.UserService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
@Route("set-password")
@AnonymousAllowed
public class SetPasswordView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private final JwtMagicLinkService jwtMagicLinkService;
    private String token;
    private PasswordField passwordField;
    private PasswordField confirmField;
    private Button submitButton;

    public SetPasswordView(UserService userService, JwtMagicLinkService jwtMagicLinkService) {
        this.userService = userService;
        this.jwtMagicLinkService = jwtMagicLinkService;
        setWidth("auto");
        getStyle().set("margin", "0 auto");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var params = event.getLocation().getQueryParameters().getParameters();
        var tokenValues = params.get("token");
        if (tokenValues == null || tokenValues.isEmpty()) {
            event.forwardTo("login");
            return;
        }
        this.token = tokenValues.getFirst();
        if (!StringUtils.hasText(this.token)) {
            event.forwardTo("login");
            return;
        }
        try {
            jwtMagicLinkService.extractEmail(this.token);
        } catch (JwtException ex) {
            log.warn("Invalid or expired set-password token");
            Notification.show(getTranslation("set-password.token-invalid"))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        buildForm();
    }

    private void buildForm() {
        removeAll();

        add(new H2(getTranslation("set-password.heading")));
        add(new Paragraph(getTranslation("set-password.body")));

        passwordField = new PasswordField(getTranslation("set-password.password.label"));
        passwordField.setMaxLength(128);
        passwordField.setValueChangeMode(ValueChangeMode.EAGER);

        confirmField = new PasswordField(getTranslation("set-password.confirm.label"));
        confirmField.setMaxLength(128);
        confirmField.setValueChangeMode(ValueChangeMode.EAGER);

        submitButton = new Button(getTranslation("set-password.submit"));
        submitButton.setDisableOnClick(true);
        submitButton.addClickListener(e -> handleSubmit());

        Shortcuts.addShortcutListener(this, this::handleSubmit, Key.ENTER);

        add(passwordField, confirmField, submitButton);
    }

    private void handleSubmit() {
        String password = passwordField.getValue();
        String confirm = confirmField.getValue();

        if (!StringUtils.hasText(password)) {
            passwordField.setInvalid(true);
            passwordField.setErrorMessage(getTranslation("set-password.password.error"));
            submitButton.setEnabled(true);
            return;
        }

        if (!password.equals(confirm)) {
            confirmField.setInvalid(true);
            confirmField.setErrorMessage(getTranslation("set-password.confirm.error"));
            submitButton.setEnabled(true);
            return;
        }

        try {
            userService.setPasswordByToken(token, password);
            Notification.show(getTranslation("set-password.success"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            getUI().ifPresent(ui -> ui.navigate("login"));
        } catch (BusinessRuleException ex) {
            Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            submitButton.setEnabled(true);
        } catch (JwtException ex) {
            Notification.show(getTranslation("set-password.token-invalid"))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            submitButton.setEnabled(true);
        }
    }
}
