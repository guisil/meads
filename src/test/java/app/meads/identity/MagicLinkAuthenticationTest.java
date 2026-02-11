package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.ServletException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class MagicLinkAuthenticationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    OneTimeTokenService tokenService;

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
    void shouldAuthenticateUserWhenValidMagicLinkClicked() throws Exception {
        // Given - an active user exists and a token is generated
        String email = "test@example.com";
        var user = new User(UUID.randomUUID(), email, "Test User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        var oneTimeToken = tokenService.generate(new GenerateOneTimeTokenRequest(email));
        String token = oneTimeToken.getTokenValue();

        // When - the token is posted to /login/ott
        mockMvc.perform(post("/login/ott")
                .param("token", token))
                // Then - the user should be authenticated
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(email));
    }

    @Test
    void shouldRejectAuthenticationWhenInvalidTokenProvided() throws Exception {
        // When - user posts an invalid token
        mockMvc.perform(post("/login/ott")
                .param("token", "invalid-token"))
                // Then - authentication should fail
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldNotAuthenticateViaGetRequestToLoginOtt() throws Exception {
        // Given - an active user exists and a token is generated
        String email = "get-test@example.com";
        var user = new User(UUID.randomUUID(), email, "Get Test User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        var oneTimeToken = tokenService.generate(new GenerateOneTimeTokenRequest(email));
        String token = oneTimeToken.getTokenValue();

        // When - the token is sent via GET to /login/ott
        // Then - the OTT filter does not process GET requests;
        // the request falls through to Vaadin's servlet (not available in MockMvc)
        assertThatThrownBy(() -> mockMvc.perform(get("/login/ott").param("token", token)))
                .isInstanceOf(ServletException.class);
    }

    @Test
    void shouldActivatePendingUserWhenAuthenticatedViaMagicLink() throws Exception {
        // Given - a pending user exists and a token is generated
        String email = "pending-activate@example.com";
        var user = new User(UUID.randomUUID(), email, "Pending User", UserStatus.PENDING, Role.USER);
        userRepository.save(user);

        var oneTimeToken = tokenService.generate(new GenerateOneTimeTokenRequest(email));
        String token = oneTimeToken.getTokenValue();

        // When - the token is posted to /login/ott
        mockMvc.perform(post("/login/ott")
                .param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(email));

        // Then - user should now be ACTIVE
        var activatedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(activatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
