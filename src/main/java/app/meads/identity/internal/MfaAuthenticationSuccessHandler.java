package app.meads.identity.internal;

import app.meads.identity.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import java.io.IOException;

@Slf4j
class MfaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    static final String MFA_PENDING_EMAIL = "MFA_PENDING_EMAIL";

    private final UserService userService;
    private final SavedRequestAwareAuthenticationSuccessHandler delegate;
    private final HttpSessionSecurityContextRepository securityContextRepository;

    MfaAuthenticationSuccessHandler(UserService userService) {
        this.userService = userService;
        this.delegate = new SavedRequestAwareAuthenticationSuccessHandler();
        this.delegate.setDefaultTargetUrl("/");
        this.delegate.setAlwaysUseDefaultTargetUrl(true);
        this.securityContextRepository = new HttpSessionSecurityContextRepository();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String email = authentication.getName();
        try {
            var user = userService.findByEmail(email);
            if (user.isMfaEnabled()) {
                SecurityContextHolder.clearContext();
                var emptyContext = SecurityContextHolder.createEmptyContext();
                securityContextRepository.saveContext(emptyContext, request, response);
                request.getSession().setAttribute(MFA_PENDING_EMAIL, email);
                log.info("MFA required for user: {} — redirecting to /mfa", email);
                response.sendRedirect("/mfa");
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check MFA status for {}: {}", email, e.getMessage());
        }
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
