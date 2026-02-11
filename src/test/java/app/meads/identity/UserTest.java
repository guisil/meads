package app.meads;

import org.junit.jupiter.api.Test;

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
}
