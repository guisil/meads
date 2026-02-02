package app.meads.admin.internal;

import app.meads.entrant.api.EntrantService;
import app.meads.event.api.Competition;
import app.meads.event.api.MeadEventService;
import app.meads.order.api.PendingOrder;
import app.meads.order.api.PendingOrderService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Route(value = "admin/pending-orders", layout = AdminLayout.class)
@PageTitle("Pending Orders | MEADS Admin")
@PermitAll
public class PendingOrderListView extends VerticalLayout {

    private final PendingOrderService pendingOrderService;
    private final EntrantService entrantService;
    private final MeadEventService meadEventService;

    private final Grid<PendingOrder> grid = new Grid<>(PendingOrder.class, false);
    private final Tab needsReviewTab = new Tab("Needs Review");
    private final Tab allOrdersTab = new Tab("All Orders");
    private final Tabs tabs = new Tabs(needsReviewTab, allOrdersTab);

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    public PendingOrderListView(PendingOrderService pendingOrderService,
                                EntrantService entrantService,
                                MeadEventService meadEventService) {
        this.pendingOrderService = pendingOrderService;
        this.entrantService = entrantService;
        this.meadEventService = meadEventService;

        addClassName("pending-order-list-view");
        setSizeFull();

        configureGrid();

        tabs.addSelectedChangeListener(e -> refreshGrid());

        add(new H2("Pending Orders"), tabs, grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.addClassName("pending-order-grid");
        grid.setSizeFull();

        grid.addColumn(PendingOrder::externalOrderId).setHeader("Order ID").setSortable(true).setAutoWidth(true);
        grid.addColumn(PendingOrder::externalSource).setHeader("Source").setSortable(true).setAutoWidth(true);
        grid.addColumn(order -> meadEventService.findCompetitionById(order.competitionId())
            .map(Competition::name)
            .orElse(order.competitionId().toString())
        ).setHeader("Competition").setSortable(true).setAutoWidth(true);
        grid.addColumn(this::formatEntrant).setHeader("Entrant").setSortable(true).setAutoWidth(true);
        grid.addColumn(PendingOrder::reason).setHeader("Reason").setSortable(true).setAutoWidth(true);
        grid.addColumn(order -> formatStatus(order.status())).setHeader("Status").setSortable(true).setAutoWidth(true);
        grid.addColumn(order -> formatTimestamp(order.createdAt()))
            .setHeader("Created").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(order -> {
            if (!"NEEDS_REVIEW".equals(order.status())) {
                return new Span(order.resolvedBy() != null ? "By: " + order.resolvedBy() : "");
            }

            var resolveButton = new Button("Resolve", VaadinIcon.CHECK.create());
            resolveButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
            resolveButton.addClickListener(e -> openResolveDialog(order.id(), false));

            var cancelButton = new Button("Cancel", VaadinIcon.CLOSE.create());
            cancelButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            cancelButton.addClickListener(e -> openResolveDialog(order.id(), true));

            return new HorizontalLayout(resolveButton, cancelButton);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void refreshGrid() {
        var orders = tabs.getSelectedTab() == needsReviewTab
            ? pendingOrderService.findOrdersNeedingReview()
            : pendingOrderService.findAllPendingOrders();
        grid.setItems(orders);
    }

    private void openResolveDialog(UUID orderId, boolean isCancel) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(isCancel ? "Cancel Order" : "Resolve Order");

        var notesField = new TextArea("Notes");
        notesField.setWidthFull();
        notesField.setPlaceholder("Enter resolution notes...");

        var form = new VerticalLayout(notesField);
        form.setPadding(false);

        var actionButton = new Button(isCancel ? "Cancel Order" : "Resolve", e -> {
            try {
                if (isCancel) {
                    pendingOrderService.cancelOrder(orderId, "admin", notesField.getValue());
                } else {
                    pendingOrderService.resolveOrder(orderId, "admin", notesField.getValue());
                }
                Notification.show("Order " + (isCancel ? "cancelled" : "resolved"), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        actionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (isCancel) {
            actionButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        }

        var cancelButton = new Button("Close", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, actionButton);

        dialog.open();
    }

    private String formatEntrant(PendingOrder order) {
        if (order.entrantId() == null) {
            return "N/A";
        }
        return entrantService.findEntrantById(order.entrantId())
            .map(e -> e.name() + " (" + e.email() + ")")
            .orElse(order.entrantId().toString());
    }

    private String formatStatus(String status) {
        return switch (status) {
            case "NEEDS_REVIEW" -> "Needs Review";
            case "RESOLVED" -> "Resolved";
            case "CANCELLED" -> "Cancelled";
            default -> status;
        };
    }

    private String formatTimestamp(java.time.Instant timestamp) {
        if (timestamp == null) {
            return "";
        }
        return formatter.format(timestamp);
    }
}
