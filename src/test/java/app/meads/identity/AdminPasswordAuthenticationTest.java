package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class AdminPasswordAuthenticationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldAuthenticateAdminWithCorrectPassword() throws Exception {
        // Given — an active admin with a password
        String email = "admin-auth@example.com";
        var admin = new User(UUID.randomUUID(), email, "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        admin.setPasswordHash(passwordEncoder.encode("correctPassword"));
        userRepository.save(admin);

        // When — POST /login with correct credentials
        mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", "correctPassword")
                        .with(csrf()))
                // Then — should be authenticated
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(email));
    }

    @Test
    void shouldRejectAdminWithWrongPassword() throws Exception {
        // Given — an active admin with a password
        String email = "admin-wrong@example.com";
        var admin = new User(UUID.randomUUID(), email, "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        admin.setPasswordHash(passwordEncoder.encode("correctPassword"));
        userRepository.save(admin);

        // When — POST /login with wrong password
        mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", "wrongPassword")
                        .with(csrf()))
                // Then — should not be authenticated
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldRejectUserWithoutPassword() throws Exception {
        // Given — a user with no password hash
        String email = "nopass@example.com";
        var user = new User(UUID.randomUUID(), email, "No Password User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        // When — POST /login with any password
        mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", "anyPassword")
                        .with(csrf()))
                // Then — should not be authenticated
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }
}
