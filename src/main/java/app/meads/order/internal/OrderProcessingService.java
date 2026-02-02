package app.meads.order.internal;

import app.meads.entrant.api.AddEntryCreditCommand;
import app.meads.entrant.api.Entrant;
import app.meads.entrant.api.EntrantService;
import app.meads.event.api.Competition;
import app.meads.event.api.MeadEventService;
import app.meads.order.api.OrderPendingReviewEvent;
import app.meads.order.api.OrderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProcessingService {

    private final EntrantService entrantService;
    private final MeadEventService meadEventService;
    private final PendingOrderRepository pendingOrderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse processOrder(OrderPayload payload) {
        // Check for duplicate order
        var existingCredit = entrantService.findCreditByExternalOrder(
            payload.externalOrderId(), payload.externalSource());
        if (existingCredit.isPresent()) {
            log.info("Order already processed: {}", payload.externalOrderId());
            var entrant = entrantService.findEntrantByEmail(payload.customer().email());
            return OrderResponse.alreadyProcessed(entrant.map(Entrant::id).orElse(null));
        }

        // Check for pending order
        var existingPending = pendingOrderRepository.findByExternalOrderIdAndExternalSource(
            payload.externalOrderId(), payload.externalSource());
        if (existingPending.isPresent()) {
            log.info("Order already pending review: {}", payload.externalOrderId());
            return OrderResponse.alreadyProcessed(existingPending.get().getEntrantId());
        }

        // Find or create entrant
        var entrant = findOrCreateEntrant(payload);

        // Check competition exclusivity
        var competition = meadEventService.findCompetitionById(payload.competitionId());
        if (competition.isPresent() && hasCompetitionExclusivityViolation(entrant.id(), competition.get())) {
            return createPendingOrder(payload, entrant.id(), "COMPETITION_EXCLUSIVITY");
        }

        // Add entry credits
        var credit = entrantService.addCredit(new AddEntryCreditCommand(
            entrant.id(),
            payload.competitionId(),
            payload.quantity(),
            payload.externalOrderId(),
            payload.externalSource(),
            payload.purchasedAt()
        ));

        log.info("Processed order {} - added {} credits for entrant {}",
            payload.externalOrderId(), credit.quantity(), entrant.id());

        return OrderResponse.processed(entrant.id(), credit.quantity());
    }

    private Entrant findOrCreateEntrant(OrderPayload payload) {
        var customer = payload.customer();
        return entrantService.findEntrantByEmail(customer.email())
            .orElseGet(() -> entrantService.createEntrant(buildNewEntrant(customer)));
    }

    private Entrant buildNewEntrant(OrderPayload.CustomerInfo customer) {
        var address = customer.address();
        if (address == null) {
            return new Entrant(null, customer.email(), customer.name(), customer.phone(),
                null, null, null, null, null, null);
        }
        return new Entrant(
            null,
            customer.email(),
            customer.name(),
            customer.phone(),
            address.line1(),
            address.line2(),
            address.city(),
            address.stateProvince(),
            address.postalCode(),
            address.country()
        );
    }

    private boolean hasCompetitionExclusivityViolation(UUID entrantId, Competition targetCompetition) {
        var existingCompetitionIds = entrantService.getCompetitionIdsWithCredits(entrantId);
        if (existingCompetitionIds.isEmpty()) {
            return false;
        }

        // Get event ID for target competition
        var targetEventId = targetCompetition.meadEventId();

        // Check if entrant has credits for a different competition type in the same event
        for (UUID existingCompetitionId : existingCompetitionIds) {
            if (existingCompetitionId.equals(targetCompetition.id())) {
                continue; // Same competition, no conflict
            }

            var existingCompetition = meadEventService.findCompetitionById(existingCompetitionId);
            if (existingCompetition.isPresent()) {
                var existing = existingCompetition.get();
                if (existing.meadEventId().equals(targetEventId) &&
                    existing.type() != targetCompetition.type()) {
                    log.info("Competition exclusivity violation: entrant {} has credits for {} but trying to buy {}",
                        entrantId, existing.type(), targetCompetition.type());
                    return true;
                }
            }
        }

        return false;
    }

    private OrderResponse createPendingOrder(OrderPayload payload, UUID entrantId, String reason) {
        try {
            var rawPayload = objectMapper.writeValueAsString(payload);
            var pendingOrder = PendingOrderEntity.builder()
                .externalOrderId(payload.externalOrderId())
                .externalSource(payload.externalSource())
                .competitionId(payload.competitionId())
                .entrantId(entrantId)
                .rawPayload(rawPayload)
                .reason(reason)
                .status(PendingOrderStatus.NEEDS_REVIEW)
                .build();

            var saved = pendingOrderRepository.save(pendingOrder);

            eventPublisher.publishEvent(new OrderPendingReviewEvent(
                saved.getId(),
                saved.getExternalOrderId(),
                saved.getExternalSource(),
                saved.getCompetitionId(),
                payload.customer().email(),
                payload.quantity(),
                reason
            ));

            log.info("Created pending order {} for reason: {}", saved.getId(), reason);
            return OrderResponse.pendingReview(entrantId, formatReasonMessage(reason));

        } catch (JsonProcessingException e) {
            log.error("Error serializing order payload", e);
            throw new RuntimeException("Failed to process order", e);
        }
    }

    private String formatReasonMessage(String reason) {
        return switch (reason) {
            case "COMPETITION_EXCLUSIVITY" ->
                "entrant already has credits for a different competition type";
            default -> reason;
        };
    }
}
