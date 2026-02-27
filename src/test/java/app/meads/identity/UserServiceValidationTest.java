package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserServiceValidationTest {

    @Autowired
    UserService userService;

    @Test
    void shouldRejectCreateWhenEmailIsBlank() {
        assertThatThrownBy(() -> userService.createUser("", "Name", UserStatus.PENDING, Role.USER))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectCreateWhenEmailFormatIsInvalid() {
        assertThatThrownBy(() -> userService.createUser("not-an-email", "Name", UserStatus.PENDING, Role.USER))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectCreateWhenNameIsBlank() {
        assertThatThrownBy(() -> userService.createUser("valid@example.com", "", UserStatus.PENDING, Role.USER))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectUpdateWhenNameIsBlank() {
        User user = userService.createUser("update-test@example.com", "Original", UserStatus.PENDING, Role.USER);

        assertThatThrownBy(() -> userService.updateUser(user.getId(), "", Role.USER, UserStatus.PENDING, "admin@example.com"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectCreateWhenStatusIsNull() {
        assertThatThrownBy(() -> userService.createUser("valid@example.com", "Name", null, Role.USER))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectCreateWhenRoleIsNull() {
        assertThatThrownBy(() -> userService.createUser("valid@example.com", "Name", UserStatus.PENDING, null))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
