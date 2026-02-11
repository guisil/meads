package app.meads.identity;

import app.meads.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class MagicLinkLandingControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldReturnAutoSubmitFormWithTokenWhenMagicLinkAccessed() throws Exception {
        mockMvc.perform(get("/login/magic")
                .param("token", "test-token-123"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/login/ott\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("method=\"post\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"test-token-123\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("document.forms[0].submit()")));
    }

    @Test
    void shouldEscapeTokenToPreventXss() throws Exception {
        mockMvc.perform(get("/login/magic")
                .param("token", "\"><script>alert('xss')</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>alert('xss')</script>"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
    }
}
