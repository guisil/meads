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
        if (!(authentication instanceof AccessCodeAuthenticationToken token)) {
            return null;
        }
        String email = (String) token.getPrincipal();
        String code = (String) token.getCredentials();
        if (!accessCodeValidator.validate(email, code)) {
            throw new BadCredentialsException("Invalid access code");
        }
        var userDetails = userDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return AccessCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
