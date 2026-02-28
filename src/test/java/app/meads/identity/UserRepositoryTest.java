package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryTest {

    // Each test uses unique email to avoid conflicts

    @Autowired
    UserRepository userRepository;

    @Test
    void shouldSaveAndRetrieveUserByEmail() {
        var user = new User(UUID.randomUUID(), "test@repository.com", "John Doe", UserStatus.ACTIVE, Role.USER);

        userRepository.save(user);
        var found = userRepository.findByEmail("test@repository.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@repository.com");
        assertThat(found.get().getName()).isEqualTo("John Doe");
        assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNull();
    }

    @Test
    void shouldPersistUserWithPasswordHash() {
        var user = new User(UUID.randomUUID(), "hashed@repository.com", "Hashed User", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        user.setPasswordHash("$2a$10$someBcryptHashValue");

        userRepository.save(user);
        var found = userRepository.findByEmail("hashed@repository.com");

        assertThat(found).isPresent();
        assertThat(found.get().getPasswordHash()).isEqualTo("$2a$10$someBcryptHashValue");
    }

    @Test
    void shouldPersistUserWithNullPasswordHash() {
        var user = new User(UUID.randomUUID(), "nopassword@repository.com", "No Password", UserStatus.ACTIVE, Role.USER);

        userRepository.save(user);
        var found = userRepository.findByEmail("nopassword@repository.com");

        assertThat(found).isPresent();
        assertThat(found.get().getPasswordHash()).isNull();
    }
}
