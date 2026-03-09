package app.meads.identity.internal;

import app.meads.identity.JwtMagicLinkService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
class MagicLinkAuthenticationFilter extends OncePerRequestFilter {

    private final JwtMagicLinkService jwtMagicLinkService;
    private final UserDetailsService userDetailsService;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    MagicLinkAuthenticationFilter(JwtMagicLinkService jwtMagicLinkService,
                                  UserDetailsService userDetailsService,
                                  ApplicationEventPublisher eventPublisher) {
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.userDetailsService = userDetailsService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"/login/magic".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getParameter("token");
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtMagicLinkService.extractEmail(token);
            var userDetails = userDetailsService.loadUserByUsername(email);

            if (!userDetails.getPassword().isEmpty()) {
                log.debug("Magic link rejected for user with password: {}", email);
                response.sendRedirect("/login?error");
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            var securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            // Persist the security context to the session
            securityContextRepository.saveContext(securityContext, request, response);

            // Publish event for UserActivationListener
            eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));

            log.info("Magic link authentication successful for: {}", email);
            response.sendRedirect("/");
        } catch (Exception e) {
            log.debug("JWT magic link authentication failed: {}", e.getMessage());
            response.sendRedirect("/login?error");
        }
    }
}
