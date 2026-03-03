package app.meads.entry.internal;

import app.meads.entry.JumpsellerOrder;
import app.meads.entry.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JumpsellerOrderRepository extends JpaRepository<JumpsellerOrder, UUID> {
    Optional<JumpsellerOrder> findByJumpsellerOrderId(String jumpsellerOrderId);
    boolean existsByJumpsellerOrderId(String jumpsellerOrderId);
    List<JumpsellerOrder> findByStatus(OrderStatus status);
}
