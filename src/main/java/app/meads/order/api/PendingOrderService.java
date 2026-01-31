package app.meads.order.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingOrderService {

    List<PendingOrder> findAllPendingOrders();

    List<PendingOrder> findOrdersNeedingReview();

    Optional<PendingOrder> findById(UUID id);

    void resolveOrder(UUID id, String resolvedBy, String notes);

    void cancelOrder(UUID id, String resolvedBy, String notes);
}
