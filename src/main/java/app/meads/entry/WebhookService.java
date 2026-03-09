package app.meads.entry;

import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.internal.EntryCreditRepository;
import app.meads.entry.internal.JumpsellerOrderLineItemRepository;
import app.meads.entry.internal.JumpsellerOrderRepository;
import app.meads.entry.internal.ProductMappingRepository;
import app.meads.identity.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@Validated
public class WebhookService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JumpsellerOrderRepository orderRepository;
    private final JumpsellerOrderLineItemRepository lineItemRepository;
    private final ProductMappingRepository productMappingRepository;
    private final EntryCreditRepository creditRepository;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final String hooksToken;

    WebhookService(JumpsellerOrderRepository orderRepository,
                   JumpsellerOrderLineItemRepository lineItemRepository,
                   ProductMappingRepository productMappingRepository,
                   EntryCreditRepository creditRepository,
                   CompetitionService competitionService,
                   UserService userService,
                   ApplicationEventPublisher eventPublisher,
                   @Value("${app.jumpseller.hooks-token}") String hooksToken) {
        this.orderRepository = orderRepository;
        this.lineItemRepository = lineItemRepository;
        this.productMappingRepository = productMappingRepository;
        this.creditRepository = creditRepository;
        this.competitionService = competitionService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.hooksToken = hooksToken;
    }

    public boolean verifySignature(String payload, String signature) {
        if (signature == null || payload == null) {
            return false;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hooksToken.getBytes(), "HmacSHA256"));
            var expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
            return expected.equalsIgnoreCase(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    public void processOrderPaid(String rawPayload) {
        try {
            var root = MAPPER.readTree(rawPayload);
            var orderId = root.get("id").asText();
            var customerEmail = root.get("customer").get("email").asText();
            var customerName = root.get("customer").get("full_name").asText();
            var products = root.get("products");

            // Idempotency check
            if (orderRepository.existsByJumpsellerOrderId(orderId)) {
                log.info("Order {} already processed, skipping", orderId);
                return;
            }

            // Find or create user
            var user = userService.findOrCreateByEmail(customerEmail);

            // Save order first (line items have FK to this)
            var order = new JumpsellerOrder(orderId, customerEmail, customerName, rawPayload);
            orderRepository.save(order);

            int processedCount = 0;
            int needsReviewCount = 0;
            int ignoredCount = 0;
            int totalItems = 0;

            for (JsonNode product : products) {
                totalItems++;
                var productId = product.get("product_id").asText();
                var sku = product.has("sku") && !product.get("sku").asText().isEmpty()
                        ? product.get("sku").asText() : null;
                var productName = product.get("name").asText();
                var quantity = product.get("qty").asInt();

                var lineItem = new JumpsellerOrderLineItem(
                        order.getId(), productId, sku, productName, quantity);

                var mappings = productMappingRepository.findByJumpsellerProductId(productId);
                if (mappings.isEmpty()) {
                    lineItem.markIgnored();
                    lineItemRepository.save(lineItem);
                    ignoredCount++;
                    continue;
                }

                var mapping = mappings.getFirst();
                var divisionId = mapping.getDivisionId();
                var division = competitionService.findDivisionById(divisionId);

                // Mutual exclusivity check
                if (hasCreditConflict(user.getId(), divisionId, division.getCompetitionId())) {
                    lineItem.markNeedsReview("Mutual exclusivity conflict: user already has credits in another division of the same competition");
                    lineItemRepository.save(lineItem);
                    needsReviewCount++;
                    continue;
                }

                // Create credits
                var credits = quantity * mapping.getCreditsPerUnit();
                lineItem.markProcessed(divisionId, credits);
                lineItemRepository.save(lineItem);

                var credit = new EntryCredit(divisionId, user.getId(), credits,
                        "WEBHOOK", lineItem.getId().toString());
                creditRepository.save(credit);
                processedCount++;
            }

            // Determine order status
            if (processedCount > 0 && needsReviewCount == 0) {
                order.markProcessed();
            } else if (processedCount > 0 && needsReviewCount > 0) {
                order.markPartiallyProcessed();
            } else if (needsReviewCount > 0) {
                order.markNeedsReview();
            } else {
                // All ignored (non-mapped products only)
                order.markProcessed();
            }

            orderRepository.save(order);

        } catch (Exception e) {
            log.error("Error processing order paid webhook", e);
            throw new IllegalArgumentException("Failed to process webhook payload", e);
        }
    }

    private boolean hasCreditConflict(UUID userId, UUID divisionId, UUID competitionId) {
        var existingDivisionIds = creditRepository.findDistinctDivisionIdsByUserId(userId);
        if (existingDivisionIds.isEmpty()) {
            return false;
        }
        if (existingDivisionIds.contains(divisionId)) {
            return false; // Same division — no conflict
        }
        // Check if any existing divisions are in the same competition
        var competitionDivisions = competitionService.findDivisionsByCompetition(competitionId);
        var competitionDivisionIds = competitionDivisions.stream()
                .map(Division::getId)
                .toList();
        return existingDivisionIds.stream()
                .anyMatch(id -> competitionDivisionIds.contains(id) && !id.equals(divisionId));
    }
}
