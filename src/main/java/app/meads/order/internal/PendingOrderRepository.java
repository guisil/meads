package app.meads.order.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PendingOrderRepository extends JpaRepository<PendingOrderEntity, UUID> {

    Optional<PendingOrderEntity> findByExternalOrderIdAndExternalSource(
        String externalOrderId, String externalSource);

    List<PendingOrderEntity> findByStatus(PendingOrderStatus status);
}
