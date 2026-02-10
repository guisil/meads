package app.meads;

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

import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class MagicLinkAuthenticationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    OneTimeTokenService tokenService;

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
        // Given - a magic link token is generated for a user
        String username = "test@example.com";
        var request = new GenerateOneTimeTokenRequest(username);
        var oneTimeToken = tokenService.generate(request);
        String token = oneTimeToken.getTokenValue();

        // When - the user clicks the magic link (navigates to /login/ott?token=xxx)
        mockMvc.perform(get("/login/ott")
                .param("token", token))
                // Then - the user should be authenticated
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(username));
    }

    @Test
    void shouldRejectAuthenticationWhenInvalidTokenProvided() throws Exception {
        // When - user tries to access with an invalid token
        mockMvc.perform(get("/login/ott")
                .param("token", "invalid-token"))
                // Then - authentication should fail
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }
}
