package app.meads.entry.internal;

import app.meads.MainLayout;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.entry.*;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName/entry-admin", layout = MainLayout.class)
@PermitAll
public class DivisionEntryAdminView extends VerticalLayout implements BeforeEnterObserver {

    private final EntryService entryService;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    private UUID divisionId;
    private Division division;
    private String compShortName;
    private String divShortName;
    private UUID currentUserId;

    public DivisionEntryAdminView(EntryService entryService,
                                   CompetitionService competitionService,
                                   UserService userService,
                                   AuthenticationContext authenticationContext) {
        this.entryService = entryService;
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        compShortName = beforeEnterEvent.getRouteParameters().get("compShortName")
                .orElse(null);
        divShortName = beforeEnterEvent.getRouteParameters().get("divShortName")
                .orElse(null);

        if (compShortName == null || divShortName == null) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        try {
            var competition = competitionService.findCompetitionByShortName(compShortName);
            division = competitionService.findDivisionByShortName(
                    competition.getId(), divShortName);
            divisionId = division.getId();
        } catch (IllegalArgumentException e) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        currentUserId = getCurrentUserId();

        // Authorization: SYSTEM_ADMIN or ADMIN for this competition
        var user = userService.findById(currentUserId);
        if (user.getRole() != Role.SYSTEM_ADMIN
                && !competitionService.isAuthorizedForDivision(divisionId, currentUserId)) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        removeAll();
        add(createHeader());
        add(createTabSheet());
    }

    private H2 createHeader() {
        return new H2(division.getName() + " — Entry Admin");
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();

        tabSheet.add("Credits", createCreditsTab());
        tabSheet.add("Entries", createEntriesTab());
        tabSheet.add("Products", createProductsTab());
        tabSheet.add("Orders", createOrdersTab());

        return tabSheet;
    }

    private VerticalLayout createCreditsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var addCreditsButton = new Button("Add Credits", e -> openAddCreditsDialog());
        tab.add(addCreditsButton);

        var grid = new Grid<EntrantCreditSummary>(EntrantCreditSummary.class, false);
        grid.setAllRowsVisible(true);
        grid.setId("credits-grid");
        grid.addColumn(EntrantCreditSummary::email).setHeader("Email");
        grid.addColumn(EntrantCreditSummary::name).setHeader("Name");
        grid.addColumn(EntrantCreditSummary::creditBalance).setHeader("Credits");
        grid.addColumn(EntrantCreditSummary::entryCount).setHeader("Entries");

        // Populate: list all entrants with credits for this division
        refreshCreditsGrid(grid);
        tab.add(grid);
        return tab;
    }

    private void refreshCreditsGrid(Grid<EntrantCreditSummary> grid) {
        // Get all participants with credits
        var participants = competitionService.findParticipantsByCompetition(
                division.getCompetitionId());
        var summaries = participants.stream()
                .map(p -> {
                    var user = userService.findById(p.getUserId());
                    var credits = entryService.getCreditBalance(divisionId, p.getUserId());
                    var entries = entryService.countActiveEntries(divisionId, p.getUserId());
                    return new EntrantCreditSummary(
                            p.getUserId(), user.getEmail(), user.getName(), credits, entries);
                })
                .filter(s -> s.creditBalance() > 0 || s.entryCount() > 0)
                .toList();
        grid.setItems(summaries);
    }

    private void openAddCreditsDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Credits");

        var emailField = new TextField("Entrant Email");
        var amountField = new IntegerField("Amount");
        amountField.setMin(1);
        amountField.setValue(1);

        dialog.add(new VerticalLayout(emailField, amountField));

        var addButton = new Button("Add", e -> {
            if (!StringUtils.hasText(emailField.getValue()) || amountField.getValue() == null) {
                return;
            }
            try {
                entryService.addCredits(divisionId, emailField.getValue().trim(),
                        amountField.getValue(), currentUserId);
                var notification = Notification.show("Credits added");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                // Refresh view
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + compShortName
                                + "/divisions/" + divShortName + "/entry-admin"));
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private VerticalLayout createEntriesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var grid = new Grid<Entry>(Entry.class, false);
        grid.setAllRowsVisible(true);
        grid.setId("entries-grid");
        grid.addColumn(Entry::getEntryNumber).setHeader("Entry #");
        grid.addColumn(Entry::getEntryCode).setHeader("Entry Code");
        grid.addColumn(Entry::getMeadName).setHeader("Mead Name");
        grid.addColumn(entry -> {
            var categories = competitionService.findDivisionCategories(divisionId);
            return categories.stream()
                    .filter(c -> c.getId().equals(entry.getInitialCategoryId()))
                    .map(DivisionCategory::getName)
                    .findFirst()
                    .orElse("—");
        }).setHeader("Category");
        grid.addColumn(entry -> {
            var user = userService.findById(entry.getUserId());
            return user.getEmail();
        }).setHeader("Entrant");
        grid.addColumn(entry -> entry.getStatus().name()).setHeader("Status");

        var entries = entryService.findEntriesByDivision(divisionId);
        grid.setItems(entries);
        tab.add(grid);
        return tab;
    }

    private VerticalLayout createProductsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var addButton = new Button("Add Mapping", e -> openAddProductDialog());
        tab.add(addButton);

        var grid = new Grid<ProductMapping>(ProductMapping.class, false);
        grid.setAllRowsVisible(true);
        grid.setId("products-grid");
        grid.addColumn(ProductMapping::getJumpsellerProductId).setHeader("Product ID");
        grid.addColumn(ProductMapping::getJumpsellerSku).setHeader("SKU");
        grid.addColumn(ProductMapping::getProductName).setHeader("Product Name");
        grid.addColumn(ProductMapping::getCreditsPerUnit).setHeader("Credits/Unit");

        var mappings = entryService.findProductMappings(divisionId);
        grid.setItems(mappings);
        tab.add(grid);
        return tab;
    }

    private void openAddProductDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Product Mapping");

        var productIdField = new TextField("Jumpseller Product ID");
        var skuField = new TextField("SKU (optional)");
        var nameField = new TextField("Product Name");
        var creditsField = new IntegerField("Credits Per Unit");
        creditsField.setMin(1);
        creditsField.setValue(1);

        dialog.add(new VerticalLayout(productIdField, skuField, nameField, creditsField));

        var addButton = new Button("Add", e -> {
            if (!StringUtils.hasText(productIdField.getValue())
                    || !StringUtils.hasText(nameField.getValue())
                    || creditsField.getValue() == null) {
                return;
            }
            try {
                entryService.createProductMapping(divisionId,
                        productIdField.getValue().trim(),
                        StringUtils.hasText(skuField.getValue())
                                ? skuField.getValue().trim() : null,
                        nameField.getValue().trim(),
                        creditsField.getValue(),
                        currentUserId);
                var notification = Notification.show("Product mapping added");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + compShortName
                                + "/divisions/" + divShortName + "/entry-admin"));
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private VerticalLayout createOrdersTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var grid = new Grid<JumpsellerOrder>(JumpsellerOrder.class, false);
        grid.setAllRowsVisible(true);
        grid.setId("orders-grid");
        grid.addColumn(JumpsellerOrder::getJumpsellerOrderId).setHeader("Order ID");
        grid.addColumn(JumpsellerOrder::getCustomerEmail).setHeader("Customer");
        grid.addColumn(order -> order.getStatus().name()).setHeader("Status");
        grid.addColumn(JumpsellerOrder::getCreatedAt).setHeader("Date");

        tab.add(grid);
        return tab;
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
