package app.meads;

import app.meads.internal.UserRepository;
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

    @Autowired
    UserRepository userRepository;

    @Test
    void shouldSaveAndRetrieveUserByEmail() {
        var user = new User(UUID.randomUUID(), "user@example.com", "John Doe", UserStatus.ACTIVE);

        userRepository.save(user);
        var found = userRepository.findByEmail("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("user@example.com");
        assertThat(found.get().getName()).isEqualTo("John Doe");
        assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNull();
    }
}
