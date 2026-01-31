package app.meads.order.internal;

import app.meads.order.api.PendingOrder;
import app.meads.order.api.PendingOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class DefaultPendingOrderService implements PendingOrderService {

    private final PendingOrderRepository repository;

    @Override
    public List<PendingOrder> findAllPendingOrders() {
        return repository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    public List<PendingOrder> findOrdersNeedingReview() {
        return repository.findByStatus(PendingOrderStatus.NEEDS_REVIEW).stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    public Optional<PendingOrder> findById(UUID id) {
        return repository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional
    public void resolveOrder(UUID id, String resolvedBy, String notes) {
        var entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Pending order not found: " + id));

        entity.setStatus(PendingOrderStatus.RESOLVED);
        entity.setResolvedBy(resolvedBy);
        entity.setResolutionNotes(notes);
        entity.setResolvedAt(Instant.now());

        repository.save(entity);
    }

    @Override
    @Transactional
    public void cancelOrder(UUID id, String resolvedBy, String notes) {
        var entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Pending order not found: " + id));

        entity.setStatus(PendingOrderStatus.CANCELLED);
        entity.setResolvedBy(resolvedBy);
        entity.setResolutionNotes(notes);
        entity.setResolvedAt(Instant.now());

        repository.save(entity);
    }

    private PendingOrder toDto(PendingOrderEntity entity) {
        return new PendingOrder(
            entity.getId(),
            entity.getExternalOrderId(),
            entity.getExternalSource(),
            entity.getCompetitionId(),
            entity.getEntrantId(),
            entity.getReason(),
            entity.getStatus().name(),
            entity.getResolvedBy(),
            entity.getResolutionNotes(),
            entity.getCreatedAt(),
            entity.getResolvedAt()
        );
    }
}
