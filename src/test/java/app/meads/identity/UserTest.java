package app.meads.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void shouldHaveRoleWhenCreated() {
        // Arrange & Act
        var user = new User(
                "test@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );

        // Assert
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldGenerateIdWhenCreated() {
        // Arrange & Act
        var user = new User(
                "test@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );

        // Assert
        assertThat(user.getId()).isNotNull();
    }

    @Test
    void shouldTransitionToActiveWhenActivated() {
        // Arrange
        var user = new User(
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
    void shouldRemainActiveWhenActivatingActiveUser() {
        // Arrange
        var user = new User(
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
    void shouldTransitionToInactiveWhenDeactivated() {
        // Arrange
        var user = new User(
                "active@example.com",
                "Active User",
                UserStatus.ACTIVE,
                Role.USER
        );

        // Act
        user.deactivate();

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void shouldUpdateMeaderyName() {
        var user = new User("user@example.com", "Test User", UserStatus.ACTIVE, Role.USER);

        user.updateMeaderyName("Golden Meadery");

        assertThat(user.getMeaderyName()).isEqualTo("Golden Meadery");
    }

    @Test
    void shouldHaveNullMeaderyNameByDefault() {
        var user = new User("user@example.com", "Test User", UserStatus.ACTIVE, Role.USER);

        assertThat(user.getMeaderyName()).isNull();
    }

    @Test
    void shouldUpdateCountry() {
        var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
        user.updateCountry("PT");
        assertThat(user.getCountry()).isEqualTo("PT");
    }

    @Test
    void shouldAllowNullCountry() {
        var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
        user.updateCountry("PT");
        user.updateCountry(null);
        assertThat(user.getCountry()).isNull();
    }

    @Test
    void shouldUpdatePreferredLanguage() {
        var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
        user.updatePreferredLanguage("pt");
        assertThat(user.getPreferredLanguage()).isEqualTo("pt");
    }

    @Test
    void shouldAllowNullPreferredLanguage() {
        var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
        user.updatePreferredLanguage("pt");
        user.updatePreferredLanguage(null);
        assertThat(user.getPreferredLanguage()).isNull();
    }

    @Test
    void shouldHaveNullPreferredLanguageByDefault() {
        var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
        assertThat(user.getPreferredLanguage()).isNull();
    }

    @Test
    void shouldUpdateDetailsWhenValidDataProvided() {
        // Arrange
        var user = new User(
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
