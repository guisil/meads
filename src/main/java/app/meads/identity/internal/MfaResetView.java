package app.meads.identity.internal;

import app.meads.BusinessRuleException;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

@Slf4j
@Route("mfa-reset")
@AnonymousAllowed
public class MfaResetView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;

    public MfaResetView(UserService userService) {
        this.userService = userService;
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var tokenValues = event.getLocation().getQueryParameters().getParameters().get("token");
        if (tokenValues == null || tokenValues.isEmpty() || !StringUtils.hasText(tokenValues.getFirst())) {
            event.forwardTo("login");
            return;
        }
        var token = tokenValues.getFirst();
        try {
            var email = userService.completeMfaReset(token);
            invalidateSession();
            log.info("MFA reset completed via email link for: {}", email);
            renderOutcome(getTranslation("mfa.reset.success"), false);
        } catch (BusinessRuleException ex) {
            renderOutcome(getTranslation(ex.getMessageKey(), ex.getParams()), true);
        }
    }

    private void renderOutcome(String message, boolean error) {
        removeAll();
        var card = new VerticalLayout();
        card.setWidth("420px");
        card.setPadding(true);
        card.setSpacing(true);
        card.add(new H2(getTranslation(error ? "mfa.reset.title.error" : "mfa.reset.title.success")));
        var paragraph = new Paragraph(message);
        if (error) {
            paragraph.getStyle().set("color", "var(--lumo-error-text-color)");
        }
        card.add(paragraph);
        var loginButton = new Button(getTranslation("mfa.reset.continue-login"),
                e -> getUI().ifPresent(ui -> ui.navigate("login")));
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidth("100%");
        card.add(loginButton);
        add(card);
    }

    private void invalidateSession() {
        var request = VaadinServletRequest.getCurrent();
        if (request != null) {
            var session = request.getHttpServletRequest().getSession(false);
            if (session != null) {
                session.removeAttribute(MfaAuthenticationSuccessHandler.MFA_PENDING_EMAIL);
            }
        }
        SecurityContextHolder.clearContext();
    }
}
