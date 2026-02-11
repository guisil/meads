package app.meads.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class OttLoginController {

    private final OneTimeTokenService tokenService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    OttLoginController(OneTimeTokenService tokenService, SecurityContextRepository securityContextRepository) {
        this.tokenService = tokenService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/login/ott")
    String loginWithToken(@RequestParam String token, HttpServletRequest request, HttpServletResponse response) {
        try {
            // Create authentication token and consume it
            var authToken = new OneTimeTokenAuthenticationToken(token);
            var oneTimeToken = tokenService.consume(authToken);

            if (oneTimeToken != null) {
                // Create authenticated user
                var user = User.withUsername(oneTimeToken.getUsername())
                        .password("{noop}unused")
                        .authorities("ROLE_USER")
                        .build();

                // Create authentication and set in security context
                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

                SecurityContext context = securityContextHolderStrategy.createEmptyContext();
                context.setAuthentication(authentication);
                securityContextHolderStrategy.setContext(context);
                securityContextRepository.saveContext(context, request, response);

                // Redirect to home page
                return "redirect:/";
            }
        } catch (Exception e) {
            // Token invalid or expired
        }

        // Redirect back to login on failure
        return "redirect:/login";
    }
}
