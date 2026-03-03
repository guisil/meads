package app.meads.entry.internal;

import app.meads.entry.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/jumpseller")
class JumpsellerWebhookController {

    private final WebhookService webhookService;

    JumpsellerWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/order-paid")
    public ResponseEntity<Void> handleOrderPaid(
            @RequestHeader("Jumpseller-Hmac-Sha256") String signature,
            @RequestBody String rawPayload) {

        if (!webhookService.verifySignature(rawPayload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        webhookService.processOrderPaid(rawPayload);
        return ResponseEntity.ok().build();
    }
}
