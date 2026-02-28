package app.meads.identity.internal;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

class AccessCodeAuthenticationToken extends AbstractAuthenticationToken {

    private final String email;
    private final String code;

    AccessCodeAuthenticationToken(String email, String code) {
        super(Collections.emptyList());
        this.email = email;
        this.code = code;
        setAuthenticated(false);
    }

    @Override
    public Object getPrincipal() {
        return email;
    }

    @Override
    public Object getCredentials() {
        return code;
    }
}
