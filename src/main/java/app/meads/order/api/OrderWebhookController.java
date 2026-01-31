package app.meads.order.api;

import app.meads.order.internal.OrderPayload;
import app.meads.order.internal.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class OrderWebhookController {

    private final OrderProcessingService orderProcessingService;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> processOrder(@RequestBody OrderPayload payload) {
        log.info("Received order webhook: orderId={}, source={}",
            payload.externalOrderId(), payload.externalSource());

        var response = orderProcessingService.processOrder(payload);

        return ResponseEntity.ok(response);
    }
}
