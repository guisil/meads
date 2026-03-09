package app.meads.identity.internal;

import app.meads.identity.AccessCodeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;

@Slf4j
class AccessCodeAwareAuthenticationProvider implements AuthenticationProvider {

    private final AccessCodeValidator accessCodeValidator;
    private final UserDetailsService userDetailsService;

    AccessCodeAwareAuthenticationProvider(AccessCodeValidator accessCodeValidator, UserDetailsService userDetailsService) {
        this.accessCodeValidator = accessCodeValidator;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = (String) authentication.getPrincipal();
        String code = (String) authentication.getCredentials();

        if (!accessCodeValidator.validate(email, code)) {
            if (authentication instanceof AccessCodeAuthenticationToken) {
                log.debug("Access code authentication failed for: {}", email);
                throw new BadCredentialsException("Invalid access code");
            }
            return null;
        }
        log.info("Access code authentication successful for: {}", email);
        var userDetails = userDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return AccessCodeAuthenticationToken.class.isAssignableFrom(authentication)
                || UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
