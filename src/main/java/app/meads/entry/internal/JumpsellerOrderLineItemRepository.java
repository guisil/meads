package app.meads.entry.internal;

import app.meads.entry.JumpsellerOrderLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JumpsellerOrderLineItemRepository extends JpaRepository<JumpsellerOrderLineItem, UUID> {
    List<JumpsellerOrderLineItem> findByOrderId(UUID orderId);
}
