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
}
