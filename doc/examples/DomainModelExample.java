// == DomainModelExample.java ==
// Patterns for entities, value objects, events, repositories, services.
// Consult before creating any domain class.

// --- Aggregate Root (module root package = public API) ---

package com.example.app.order;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @ElementCollection
    @CollectionTable(name = "order_line_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<LineItem> items = new ArrayList<>();

    protected Order() {} // JPA

    public Order(UUID id, OrderStatus status) {
        this.id = id;
        this.status = status;
    }

    public void confirm() {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("Only CREATED orders can be confirmed");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public UUID getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public List<LineItem> getItems() { return Collections.unmodifiableList(items); }
}


// --- Value Object (JPA Embeddable) ---

@Embeddable
public class LineItem {
    private String sku;
    private int quantity;
    private long priceInCents;

    protected LineItem() {} // JPA

    public LineItem(String sku, int quantity, long priceInCents) {
        this.sku = sku;
        this.quantity = quantity;
        this.priceInCents = priceInCents;
    }

    public long subtotal() { return priceInCents * quantity; }
}


// --- Value Object (record, non-JPA) ---

public record Money(long amountInCents) {
    public static final Money ZERO = new Money(0);
    public Money add(Money other) { return new Money(this.amountInCents + other.amountInCents); }
}


// --- Domain Event (record, in module root = public API) ---

public record OrderCreatedEvent(UUID orderId, List<String> skus) {}


// --- Enum ---

public enum OrderStatus { CREATED, CONFIRMED, SHIPPED, CANCELLED }


// --- Repository (in internal/ sub-package) ---

package com.example.app.order.internal;

import com.example.app.order.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatus(OrderStatus status);
}


// --- Application Service (in module root = public API) ---

package com.example.app.order;

import com.example.app.order.internal.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class OrderService {
    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    OrderService(OrderRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public Order createOrder(CreateOrderRequest request) {
        var order = new Order(UUID.randomUUID(), OrderStatus.CREATED);
        repository.save(order);
        events.publishEvent(new OrderCreatedEvent(order.getId(), request.skus()));
        return order;
    }
}


// --- Event Listener (in another module's internal/) ---

package com.example.app.inventory.internal;

import com.example.app.order.OrderCreatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;

class InventoryEventHandler {
    @ApplicationModuleListener
    void on(OrderCreatedEvent event) {
        // Reserve stock
    }
}
