package app.meads.identity.internal;

import app.meads.identity.AccessCodeValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AccessCodeAuthenticationProviderTest {

    @InjectMocks AccessCodeAuthenticationProvider provider;
    @Mock AccessCodeValidator accessCodeValidator;
    @Mock UserDetailsService userDetailsService;

    @Test
    void shouldReturnNullWhenTokenTypeIsNotAccessCode() {
        // Arrange
        var token = new UsernamePasswordAuthenticationToken("user", "pass");

        // Act
        var result = provider.authenticate(token);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void shouldAuthenticateWhenAccessCodeIsValid() {
        // Arrange
        var token = new AccessCodeAuthenticationToken("user@example.com", "ABC123");
        given(accessCodeValidator.validate("user@example.com", "ABC123")).willReturn(true);
        var userDetails = new User("user@example.com", "", List.of());
        given(userDetailsService.loadUserByUsername("user@example.com")).willReturn(userDetails);

        // Act
        var result = provider.authenticate(token);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("user@example.com");
    }

    @Test
    void shouldThrowBadCredentialsWhenAccessCodeIsInvalid() {
        // Arrange
        var token = new AccessCodeAuthenticationToken("user@example.com", "WRONG");
        given(accessCodeValidator.validate("user@example.com", "WRONG")).willReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> provider.authenticate(token))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldSupportAccessCodeAuthenticationTokenOnly() {
        assertThat(provider.supports(AccessCodeAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }
}
