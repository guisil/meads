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

    @Test
    void shouldTransitionFromPendingToActiveWhenActivated() {
        // Arrange
        var user = new User(
                UUID.randomUUID(),
                "pending@example.com",
                "Pending User",
                UserStatus.PENDING,
                Role.USER
        );

        // Act
        user.activate();

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldThrowWhenActivatingDisabledUser() {
        // Arrange
        var user = new User(
                UUID.randomUUID(),
                "disabled@example.com",
                "Disabled User",
                UserStatus.DISABLED,
                Role.USER
        );

        // Act & Assert
        assertThatThrownBy(user::activate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenActivatingLockedUser() {
        // Arrange
        var user = new User(
                UUID.randomUUID(),
                "locked@example.com",
                "Locked User",
                UserStatus.LOCKED,
                Role.USER
        );

        // Act & Assert
        assertThatThrownBy(user::activate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRemainActiveWhenActivatingActiveUser() {
        // Arrange
        var user = new User(
                UUID.randomUUID(),
                "active@example.com",
                "Active User",
                UserStatus.ACTIVE,
                Role.USER
        );

        // Act
        user.activate();

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldUpdateDetailsWhenValidDataProvided() {
        // Arrange
        var user = new User(
                UUID.randomUUID(),
                "user@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );

        // Act
        user.updateDetails("Updated Name", Role.SYSTEM_ADMIN, UserStatus.ACTIVE);

        // Assert
        assertThat(user.getName()).isEqualTo("Updated Name");
        assertThat(user.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
