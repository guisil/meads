// == DomainModelExample.java ==
// Patterns for entities, value objects, events, repositories, services.
// Consult before creating any domain class.
// REFERENCE: Entry.java, Competition.java, User.java, EntryService.java

// --- Aggregate Root (module root package = public API) ---

package app.meads.order;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
public class Order {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Order() {} // JPA

    public Order(String description) {
        this.id = UUID.randomUUID();   // self-generated, NOT passed as parameter
        this.description = description;
        this.status = OrderStatus.CREATED;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("Only CREATED orders can be confirmed");
        }
        this.status = OrderStatus.CONFIRMED;
    }
}

// KEY CONVENTIONS:
// - @Getter (Lombok) — no manual getters
// - No @Data, @Builder, or @Setter — state changes via domain methods only
// - UUID self-generated in constructor (UUID.randomUUID()), not passed as parameter
// - Instant for timestamps (not LocalDateTime), TIMESTAMP WITH TIME ZONE in DB
// - @PrePersist / @PreUpdate for automatic timestamps
// - Protected no-arg constructor for JPA
// - Public constructor with required business fields (not including id)
// - Domain methods for state changes (throw IllegalStateException for invalid transitions)


// --- Enum with display helpers ---

package app.meads.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    CREATED("Created", "badge"),
    CONFIRMED("Confirmed", "badge success"),
    SHIPPED("Shipped", "badge contrast"),
    CANCELLED("Cancelled", "badge error");

    private final String displayName;
    private final String badgeCssClass;

    public Optional<OrderStatus> next() {
        return switch (this) {
            case CREATED -> Optional.of(CONFIRMED);
            case CONFIRMED -> Optional.of(SHIPPED);
            default -> Optional.empty();
        };
    }
}

// KEY: @Getter + @RequiredArgsConstructor for enums with fields.
// State machine helpers (next()) for display; enforcement via entity domain methods.


// --- Domain Event (record, in module root = public API) ---

package app.meads.order;

import java.util.UUID;

public record OrderCreatedEvent(UUID orderId, String description) {}


// --- Repository (in internal/ sub-package) ---

package app.meads.order.internal;

import app.meads.order.Order;
import app.meads.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatus(OrderStatus status);
}

// KEY: Package-private interface in internal/. Spring Data derived queries.


// --- Application Service (in module root = public API) ---

package app.meads.order;

import app.meads.order.internal.OrderRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Transactional
@Validated
public class OrderService {

    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    OrderService(OrderRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public Order createOrder(@NotBlank String description) {
        var order = new Order(description);
        repository.save(order);
        events.publishEvent(new OrderCreatedEvent(order.getId(), description));
        return order;
    }
}

// KEY:
// - @Service + @Transactional + @Validated at class level
// - Package-private constructor (convention)
// - Constructor injection (no @Autowired)
// - @NotBlank / @NotNull for format validation; IllegalArgumentException for business rules
// - Throws ConstraintViolationException for bean validation, IllegalArgumentException for business rules


// --- Event Listener (in another module's internal/) ---

package app.meads.inventory.internal;

import app.meads.order.OrderCreatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;

class InventoryEventHandler {
    @ApplicationModuleListener
    void on(OrderCreatedEvent event) {
        // React to event — reserve stock, update read model, etc.
    }
}
