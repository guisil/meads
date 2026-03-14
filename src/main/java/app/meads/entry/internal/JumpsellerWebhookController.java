package app.meads.entry.internal;

import app.meads.entry.WebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/jumpseller")
class JumpsellerWebhookController {

    private final WebhookService webhookService;

    JumpsellerWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @RequestMapping(value = "/order-paid", method = {RequestMethod.GET, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<Void> rejectNonPost() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @PostMapping("/order-paid")
    public ResponseEntity<Void> handleOrderPaid(
            @RequestHeader(value = "Jumpseller-Hmac-Sha256", required = false) String signature,
            @RequestBody String rawPayload) {

        log.debug("Received webhook: order-paid");
        if (signature == null || !webhookService.verifySignature(rawPayload, signature)) {
            log.warn("Webhook rejected: {} HMAC signature", signature == null ? "missing" : "invalid");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        webhookService.processOrderPaid(rawPayload);
        return ResponseEntity.ok().build();
    }
}
