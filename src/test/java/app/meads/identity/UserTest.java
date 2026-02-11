package app.meads.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void shouldHaveRoleWhenCreated() {
        // Arrange & Act
        var user = new User(
                UUID.randomUUID(),
                "test@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );

        // Assert
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldReturnAuthoritiesWithRolePrefixForUser() {
        // Arrange
        var user = new User(
                UUID.randomUUID(),
                "user@example.com",
                "Regular User",
                UserStatus.ACTIVE,
                Role.USER
        );

        // Act
        var authorities = user.getAuthorities();

        // Assert
        assertThat(authorities)
                .hasSize(1)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldReturnAuthoritiesWithRolePrefixForSystemAdmin() {
        // Arrange
        var user = new User(
                UUID.randomUUID(),
                "admin@example.com",
                "System Admin",
                UserStatus.ACTIVE,
                Role.SYSTEM_ADMIN
        );

        // Act
        var authorities = user.getAuthorities();

        // Assert
        assertThat(authorities)
                .hasSize(1)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SYSTEM_ADMIN");
    }
}
