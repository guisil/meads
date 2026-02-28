package app.meads.identity.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.Role;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "INITIAL_ADMIN_EMAIL=admin@test.local",
        "INITIAL_ADMIN_PASSWORD=testPassword123"
})
class AdminInitializerIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Test
    void shouldCreateActiveAdminWithPasswordOnApplicationStartup() {
        var admins = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.SYSTEM_ADMIN)
                .toList();

        assertThat(admins).hasSize(1);

        var admin = admins.get(0);
        assertThat(admin.getEmail()).isEqualTo("admin@test.local");
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(admin.getPasswordHash()).startsWith("$2a$");
    }
}
