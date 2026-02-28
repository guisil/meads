package app.meads.identity.internal;

import app.meads.identity.AccessCodeValidator;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;

class AccessCodeAuthenticationProvider implements AuthenticationProvider {

    private final AccessCodeValidator accessCodeValidator;
    private final UserDetailsService userDetailsService;

    AccessCodeAuthenticationProvider(AccessCodeValidator accessCodeValidator, UserDetailsService userDetailsService) {
        this.accessCodeValidator = accessCodeValidator;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email;
        String code;
        if (authentication instanceof AccessCodeAuthenticationToken token) {
            email = (String) token.getPrincipal();
            code = (String) token.getCredentials();
        } else if (authentication instanceof UsernamePasswordAuthenticationToken token) {
            email = (String) token.getPrincipal();
            code = (String) token.getCredentials();
        } else {
            return null;
        }
        if (!accessCodeValidator.validate(email, code)) {
            if (authentication instanceof AccessCodeAuthenticationToken) {
                throw new BadCredentialsException("Invalid access code");
            }
            return null;
        }
        var userDetails = userDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return AccessCodeAuthenticationToken.class.isAssignableFrom(authentication)
                || UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
