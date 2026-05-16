package app.meads.identity.internal;

import app.meads.identity.EmailService;
import app.meads.identity.UserService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.util.StringUtils;

@Slf4j
@Route("mfa")
@AnonymousAllowed
public class MfaVerifyView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final EmailService emailService;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    private String pendingEmail;
    private TextField codeField;

    public MfaVerifyView(UserService userService, UserDetailsService userDetailsService,
                         EmailService emailService) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.emailService = emailService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var request = VaadinServletRequest.getCurrent().getHttpServletRequest();
        pendingEmail = (String) request.getSession().getAttribute(MfaAuthenticationSuccessHandler.MFA_PENDING_EMAIL);
        if (pendingEmail == null) {
            event.forwardTo("");
            return;
        }
        buildUi();
    }

    private void buildUi() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        var card = new VerticalLayout();
        card.setWidth("360px");
        card.setPadding(true);
        card.setSpacing(true);

        card.add(new H2(getTranslation("mfa.verify.title")));
        card.add(new Paragraph(getTranslation("mfa.verify.instructions")));

        codeField = new TextField(getTranslation("mfa.verify.code-label"));
        codeField.setMaxLength(6);
        codeField.setWidth("100%");
        codeField.setPlaceholder("000000");

        var submitButton = new Button(getTranslation("mfa.verify.submit"), e -> verifyCode());
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.setWidth("100%");

        var lostDeviceButton = new Button(getTranslation("mfa.verify.lost-device"), e -> sendResetLink());
        lostDeviceButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        card.add(codeField, submitButton, lostDeviceButton);
        add(card);

        Shortcuts.addShortcutListener(this, this::verifyCode, Key.ENTER);
    }

    private void sendResetLink() {
        try {
            var user = userService.findByEmail(pendingEmail);
            emailService.sendMfaReset(pendingEmail, user.getPreferredLanguage() != null
                    ? Locale.forLanguageTag(user.getPreferredLanguage())
                    : getLocale());
            log.info("MFA reset link requested for: {}", pendingEmail);
        } catch (Exception ex) {
            // Swallow — do not disclose whether the user/MFA exists. The generic notification covers both cases.
            log.warn("MFA reset request handling failed for {}: {}", pendingEmail, ex.getMessage());
        }
        Notification.show(getTranslation("mfa.verify.reset-link-sent"), 6000,
                        Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void verifyCode() {
        var code = codeField.getValue().trim();
        if (!StringUtils.hasText(code)) {
            showError(getTranslation("mfa.verify.code-required"));
            return;
        }
        try {
            var user = userService.findByEmail(pendingEmail);
            if (!userService.verifyMfaCode(user.getId(), code)) {
                showError(getTranslation("mfa.verify.invalid-code"));
                return;
            }

            var userDetails = userDetailsService.loadUserByUsername(pendingEmail);
            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            var securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            var request = VaadinServletRequest.getCurrent().getHttpServletRequest();
            var response = VaadinServletResponse.getCurrent().getHttpServletResponse();
            securityContextRepository.saveContext(securityContext, request, response);
            request.getSession().removeAttribute(MfaAuthenticationSuccessHandler.MFA_PENDING_EMAIL);

            log.info("MFA verification successful for: {}", pendingEmail);
            UI.getCurrent().getPage().setLocation("/");
        } catch (Exception e) {
            log.warn("MFA verification error for {}: {}", pendingEmail, e.getMessage());
            showError(getTranslation("mfa.verify.error"));
        }
    }

    private void showError(String message) {
        var notification = Notification.show(message, 4000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
