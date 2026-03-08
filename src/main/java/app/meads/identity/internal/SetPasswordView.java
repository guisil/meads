package app.meads.identity.internal;

import app.meads.identity.UserService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
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
import org.springframework.util.StringUtils;

@Route("set-password")
@AnonymousAllowed
public class SetPasswordView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private String token;
    private PasswordField passwordField;
    private PasswordField confirmField;

    public SetPasswordView(UserService userService) {
        this.userService = userService;
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
        buildForm();
    }

    private void buildForm() {
        removeAll();

        add(new H2("Set Password"));

        passwordField = new PasswordField("Password");
        passwordField.setValueChangeMode(ValueChangeMode.EAGER);

        confirmField = new PasswordField("Confirm Password");
        confirmField.setValueChangeMode(ValueChangeMode.EAGER);

        var submitButton = new Button("Set Password");
        submitButton.addClickListener(e -> handleSubmit());

        Shortcuts.addShortcutListener(this, this::handleSubmit, Key.ENTER);

        add(passwordField, confirmField, submitButton);
    }

    private void handleSubmit() {
        String password = passwordField.getValue();
        String confirm = confirmField.getValue();

        if (!StringUtils.hasText(password)) {
            passwordField.setInvalid(true);
            passwordField.setErrorMessage("Password is required");
            return;
        }

        if (!password.equals(confirm)) {
            confirmField.setInvalid(true);
            confirmField.setErrorMessage("Passwords do not match");
            return;
        }

        try {
            userService.setPasswordByToken(token, password);
            Notification.show("Password set successfully")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            getUI().ifPresent(ui -> ui.navigate("login"));
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (JwtException ex) {
            Notification.show("Invalid or expired token. Please request a new link.")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
