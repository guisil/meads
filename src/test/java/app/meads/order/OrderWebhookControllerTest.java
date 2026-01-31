package app.meads.order;

import app.meads.TestcontainersConfiguration;
import app.meads.event.api.Competition;
import app.meads.event.api.CompetitionType;
import app.meads.event.api.MeadEvent;
import app.meads.event.api.MeadEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OrderWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeadEventService meadEventService;

    @Value("${meads.webhook.hmac-secret}")
    private String hmacSecret;

    private UUID competitionId;

    @BeforeEach
    void setUp() {
        var event = meadEventService.createEvent(new MeadEvent(
            null, "webhook-test-" + UUID.randomUUID(), "Webhook Test", null,
            LocalDate.of(2024, 1, 1), null, true
        ));
        var competition = meadEventService.createCompetition(new Competition(
            null, event.id(), CompetitionType.HOME, "Home", null, 3, true, null
        ));
        competitionId = competition.id();
    }

    @Test
    void shouldProcessValidOrder() throws Exception {
        var payload = createPayload("VALID-ORDER-" + UUID.randomUUID(), "valid@example.com");
        var signature = calculateHmac(payload);

        mockMvc.perform(post("/api/webhooks/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Jumpseller-Hmac-Sha256", signature)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"))
            .andExpect(jsonPath("$.creditsAdded").value(2))
            .andExpect(jsonPath("$.entrantId").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        var payload = createPayload("INVALID-SIG-ORDER", "invalid@example.com");

        mockMvc.perform(post("/api/webhooks/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Jumpseller-Hmac-Sha256", "invalid-signature")
                .content(payload))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid signature"));
    }

    @Test
    void shouldRejectMissingSignature() throws Exception {
        var payload = createPayload("NO-SIG-ORDER", "nosig@example.com");

        mockMvc.perform(post("/api/webhooks/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldHandleDuplicateOrder() throws Exception {
        var orderId = "DUPLICATE-ORDER-" + UUID.randomUUID();
        var payload = createPayload(orderId, "dup@example.com");
        var signature = calculateHmac(payload);

        // First request
        mockMvc.perform(post("/api/webhooks/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Jumpseller-Hmac-Sha256", signature)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Second request with same payload
        mockMvc.perform(post("/api/webhooks/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Jumpseller-Hmac-Sha256", signature)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ALREADY_PROCESSED"))
            .andExpect(jsonPath("$.creditsAdded").value(0));
    }

    private String createPayload(String orderId, String email) {
        return """
            {
              "externalOrderId": "%s",
              "externalSource": "jumpseller",
              "competitionId": "%s",
              "customer": {
                "email": "%s",
                "name": "Test User",
                "phone": "+1-555-0000"
              },
              "quantity": 2,
              "purchasedAt": "2024-03-15T10:30:00Z"
            }
            """.formatted(orderId, competitionId, email);
    }

    private String calculateHmac(String payload) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        var secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        var hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
}
