package app.meads.entry.internal;

import app.meads.entry.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JumpsellerWebhookControllerTest {

    MockMvc mockMvc;

    @InjectMocks
    JumpsellerWebhookController controller;

    @Mock
    WebhookService webhookService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturn200WhenSignatureIsValid() throws Exception {
        var payload = "{\"id\":\"12345\",\"customer\":{\"email\":\"test@example.com\"}}";
        var signature = "valid-signature";

        given(webhookService.verifySignature(payload, signature)).willReturn(true);

        mockMvc.perform(post("/api/webhooks/jumpseller/order-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Jumpseller-Hmac-Sha256", signature))
                .andExpect(status().isOk());

        then(webhookService).should().processOrderPaid(payload);
    }

    @Test
    void shouldReturn401WhenSignatureIsInvalid() throws Exception {
        var payload = "{\"id\":\"12345\"}";
        var signature = "invalid-signature";

        given(webhookService.verifySignature(payload, signature)).willReturn(false);

        mockMvc.perform(post("/api/webhooks/jumpseller/order-paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Jumpseller-Hmac-Sha256", signature))
                .andExpect(status().isUnauthorized());

        then(webhookService).should(never()).processOrderPaid(payload);
    }
}
