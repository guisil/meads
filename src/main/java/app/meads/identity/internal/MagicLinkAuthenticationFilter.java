package app.meads.identity.internal;

import app.meads.identity.JwtMagicLinkService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

class MagicLinkAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkAuthenticationFilter.class);

    private final JwtMagicLinkService jwtMagicLinkService;
    private final UserDetailsService userDetailsService;
    private final ApplicationEventPublisher eventPublisher;

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
            String email = jwtMagicLinkService.validateToken(token);
            var userDetails = userDetailsService.loadUserByUsername(email);
            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            var securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            // Persist the security context to the session
            new HttpSessionSecurityContextRepository().saveContext(securityContext, request, response);

            // Publish event for UserActivationListener
            eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));

            response.sendRedirect("/");
        } catch (Exception e) {
            log.debug("JWT magic link authentication failed: {}", e.getMessage());
            response.sendRedirect("/login?error");
        }
    }
}
