package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class JwtMagicLinkAuthenticationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JwtMagicLinkService jwtMagicLinkService;

    @Autowired
    UserRepository userRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldAuthenticateUserWhenValidJwtMagicLinkClicked() throws Exception {
        // Given — an active user exists and a JWT magic link is generated
        String email = "jwt-test@example.com";
        var user = new User(UUID.randomUUID(), email, "JWT Test User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        String link = jwtMagicLinkService.generateLink(email, Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        // When — the magic link is clicked (GET request)
        mockMvc.perform(get("/login/magic").param("token", token))
                // Then — user should be authenticated and redirected
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(email));
    }

    @Test
    void shouldRejectAuthenticationWhenTokenIsInvalid() throws Exception {
        // When — an invalid token is used
        mockMvc.perform(get("/login/magic").param("token", "invalid-jwt-token"))
                // Then — user should not be authenticated
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldRejectAuthenticationWhenTokenIsExpired() throws Exception {
        // Given — a user exists and an expired token is generated
        String email = "expired-jwt@example.com";
        var user = new User(UUID.randomUUID(), email, "Expired JWT User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        String link = jwtMagicLinkService.generateLink(email, Duration.ofSeconds(-1));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        // When — the expired token is used
        mockMvc.perform(get("/login/magic").param("token", token))
                // Then — user should not be authenticated
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldActivatePendingUserWhenAuthenticatedViaJwtMagicLink() throws Exception {
        // Given — a pending user exists
        String email = "pending-jwt@example.com";
        var user = new User(UUID.randomUUID(), email, "Pending JWT User", UserStatus.PENDING, Role.USER);
        userRepository.save(user);

        String link = jwtMagicLinkService.generateLink(email, Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        // When — the magic link is clicked
        mockMvc.perform(get("/login/magic").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(email));

        // Then — user should now be ACTIVE
        var activatedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(activatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
