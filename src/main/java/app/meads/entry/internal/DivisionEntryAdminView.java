package app.meads.entry.internal;

import app.meads.MainLayout;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.entry.*;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
    private String competitionName;
    private UUID currentUserId;

    private Grid<EntrantCreditSummary> creditsGrid;
    private Grid<Entry> entriesGrid;
    private Grid<ProductMapping> productsGrid;
    private Grid<JumpsellerOrder> ordersGrid;
    private List<DivisionCategory> divisionCategories;

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
            competitionName = competition.getName();
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

        divisionCategories = competitionService.findDivisionCategories(divisionId);

        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        add(createTabSheet());
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        var user = userService.findById(currentUserId);
        boolean isSystemAdmin = user.getRole() == Role.SYSTEM_ADMIN;
        var listLink = new Anchor(isSystemAdmin ? "competitions" : "my-competitions",
                isSystemAdmin ? "Competitions" : "My Competitions");
        nav.add(listLink);
        nav.add(new Span(" / "));
        var competitionLink = new Anchor(
                "competitions/" + compShortName, competitionName);
        nav.add(competitionLink);
        nav.add(new Span(" / "));
        var divisionLink = new Anchor(
                "competitions/" + compShortName + "/divisions/" + divShortName,
                division.getName());
        nav.add(divisionLink);
        nav.add(new Span(" / "));
        nav.add(new Span("Entry Admin"));
        return nav;
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

    // --- Credits Tab ---

    private VerticalLayout createCreditsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var filterField = new TextField();
        filterField.setPlaceholder("Filter by name or email...");
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        var addCreditsButton = new Button("Add Credits", e -> openAddCreditsDialog());

        var toolbar = new HorizontalLayout(filterField, addCreditsButton);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        tab.add(toolbar);

        creditsGrid = new Grid<>(EntrantCreditSummary.class, false);
        creditsGrid.setAllRowsVisible(true);
        creditsGrid.setId("credits-grid");
        creditsGrid.addColumn(EntrantCreditSummary::name).setHeader("Name").setSortable(true).setFlexGrow(2);
        creditsGrid.addColumn(EntrantCreditSummary::email).setHeader("Email").setSortable(true).setFlexGrow(3);
        creditsGrid.addColumn(EntrantCreditSummary::creditBalance).setHeader("Credits").setSortable(true).setAutoWidth(true);
        creditsGrid.addColumn(EntrantCreditSummary::entryCount).setHeader("Entries").setSortable(true).setAutoWidth(true);
        creditsGrid.addComponentColumn(summary -> {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setAriaLabel("Adjust Credits");
            editButton.setTooltipText("Adjust Credits");
            editButton.addClickListener(e -> openEditCreditsDialog(summary));
            return editButton;
        }).setHeader("Actions").setAutoWidth(true);

        refreshCreditsGrid();

        filterField.addValueChangeListener(e -> {
            var filterString = e.getValue().toLowerCase();
            if (filterString.isBlank()) {
                creditsGrid.getListDataView().removeFilters();
            } else {
                creditsGrid.getListDataView().setFilter(s ->
                        s.name().toLowerCase().contains(filterString)
                                || s.email().toLowerCase().contains(filterString));
            }
        });

        tab.add(creditsGrid);
        return tab;
    }

    private void refreshCreditsGrid() {
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
        creditsGrid.setItems(summaries);
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
                refreshCreditsGrid();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void openEditCreditsDialog(EntrantCreditSummary summary) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Adjust Credits — " + summary.name());

        var amountField = new IntegerField("Adjustment");
        amountField.setValue(1);
        amountField.setHelperText("Current balance: " + summary.creditBalance()
                + ". Use positive to add, negative to remove.");

        dialog.add(new VerticalLayout(amountField));

        var saveButton = new Button("Save", e -> {
            if (amountField.getValue() == null || amountField.getValue() == 0) return;
            try {
                var amount = amountField.getValue();
                if (amount > 0) {
                    entryService.addCredits(divisionId, summary.email(),
                            amount, currentUserId);
                } else {
                    entryService.removeCredits(divisionId, summary.userId(),
                            -amount, currentUserId);
                }
                var notification = Notification.show("Credits updated");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshCreditsGrid();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    // --- Entries Tab ---

    private VerticalLayout createEntriesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var filterField = new TextField();
        filterField.setPlaceholder("Filter by mead name, entrant, or entry code...");
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        var toolbar = new HorizontalLayout(filterField);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        tab.add(toolbar);

        entriesGrid = new Grid<>(Entry.class, false);
        entriesGrid.setAllRowsVisible(true);
        entriesGrid.setId("entries-grid");
        entriesGrid.addColumn(entry -> formatEntryNumber(entry.getEntryNumber()))
                .setHeader("Entry #").setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(Entry::getEntryCode).setHeader("Code").setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(Entry::getMeadName).setHeader("Mead Name").setSortable(true).setFlexGrow(2);
        entriesGrid.addColumn(entry -> getCategoryName(entry.getInitialCategoryId()))
                .setHeader("Category").setSortable(true);
        entriesGrid.addColumn(entry -> userService.findById(entry.getUserId()).getEmail())
                .setHeader("Entrant").setSortable(true).setFlexGrow(2);
        entriesGrid.addColumn(entry -> entry.getStatus().name())
                .setHeader("Status").setSortable(true).setAutoWidth(true);
        entriesGrid.addComponentColumn(entry -> {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setAriaLabel("Edit");
            editButton.setTooltipText("Edit");
            editButton.setEnabled(entry.getStatus() != EntryStatus.WITHDRAWN);
            editButton.addClickListener(e -> openEditEntryDialog(entry));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setAriaLabel("Delete");
            deleteButton.setTooltipText("Delete");
            deleteButton.setEnabled(entry.getStatus() == EntryStatus.DRAFT);
            deleteButton.addClickListener(e -> openDeleteEntryDialog(entry));

            var withdrawButton = new Button(new Icon(VaadinIcon.BAN));
            withdrawButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            withdrawButton.setAriaLabel("Withdraw");
            withdrawButton.setTooltipText("Withdraw");
            withdrawButton.setEnabled(entry.getStatus() != EntryStatus.WITHDRAWN);
            withdrawButton.addClickListener(e -> openWithdrawEntryDialog(entry));

            return new HorizontalLayout(editButton, deleteButton, withdrawButton);
        }).setHeader("Actions").setAutoWidth(true);

        refreshEntriesGrid();

        filterField.addValueChangeListener(e -> {
            var filterString = e.getValue().toLowerCase();
            if (filterString.isBlank()) {
                entriesGrid.getListDataView().removeFilters();
            } else {
                entriesGrid.getListDataView().setFilter(entry ->
                        entry.getMeadName().toLowerCase().contains(filterString)
                                || entry.getEntryCode().toLowerCase().contains(filterString)
                                || userService.findById(entry.getUserId()).getEmail()
                                        .toLowerCase().contains(filterString));
            }
        });

        tab.add(entriesGrid);
        return tab;
    }

    private String formatEntryNumber(int entryNumber) {
        var prefix = division.getEntryPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix + "-" + entryNumber;
        }
        return String.valueOf(entryNumber);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "";
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    private String getCategoryName(UUID categoryId) {
        return divisionCategories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .map(DivisionCategory::getName)
                .findFirst()
                .orElse("—");
    }

    private void refreshEntriesGrid() {
        var entries = entryService.findEntriesByDivision(divisionId).stream()
                .sorted(Comparator.comparingInt(Entry::getEntryNumber))
                .toList();
        entriesGrid.setItems(entries);
    }

    private void openEditEntryDialog(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Edit Entry — " + formatEntryNumber(entry.getEntryNumber()));

        var meadNameField = new TextField("Mead Name");
        meadNameField.setValue(entry.getMeadName());

        dialog.add(new VerticalLayout(meadNameField));

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(meadNameField.getValue())) {
                meadNameField.setInvalid(true);
                meadNameField.setErrorMessage("Mead name is required");
                return;
            }
            try {
                entryService.adminUpdateEntry(entry.getId(), meadNameField.getValue().trim(),
                        entry.getInitialCategoryId(), entry.getSweetness(), entry.getStrength(),
                        entry.getAbv(), entry.getCarbonation(), entry.getHoneyVarieties(),
                        entry.getOtherIngredients(), entry.isWoodAged(),
                        entry.getWoodAgeingDetails(), entry.getAdditionalInformation(),
                        currentUserId);
                var notification = Notification.show("Entry updated");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openDeleteEntryDialog(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Entry");

        dialog.add("Delete entry " + formatEntryNumber(entry.getEntryNumber())
                + " \"" + entry.getMeadName() + "\"? This action cannot be undone.");

        var confirmButton = new Button("Delete", e -> {
            try {
                entryService.deleteEntry(entry.getId(), entry.getUserId());
                var notification = Notification.show("Entry deleted");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openWithdrawEntryDialog(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Withdraw Entry");

        dialog.add("Withdraw entry " + formatEntryNumber(entry.getEntryNumber())
                + " \"" + entry.getMeadName() + "\"?");

        var confirmButton = new Button("Withdraw", e -> {
            try {
                entryService.withdrawEntry(entry.getId(), currentUserId);
                var notification = Notification.show("Entry withdrawn");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    // --- Products Tab ---

    private VerticalLayout createProductsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var addButton = new Button("Add Mapping", e -> openAddProductDialog());
        tab.add(addButton);

        productsGrid = new Grid<>(ProductMapping.class, false);
        productsGrid.setAllRowsVisible(true);
        productsGrid.setId("products-grid");
        productsGrid.addColumn(ProductMapping::getJumpsellerProductId)
                .setHeader("Product ID").setSortable(true);
        productsGrid.addColumn(ProductMapping::getJumpsellerSku)
                .setHeader("SKU").setSortable(true).setAutoWidth(true);
        productsGrid.addColumn(ProductMapping::getProductName)
                .setHeader("Product Name").setSortable(true).setFlexGrow(2);
        productsGrid.addColumn(ProductMapping::getCreditsPerUnit)
                .setHeader("Credits/Unit").setSortable(true).setAutoWidth(true);
        productsGrid.addComponentColumn(mapping -> {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setAriaLabel("Edit");
            editButton.setTooltipText("Edit");
            editButton.addClickListener(e -> openEditProductDialog(mapping));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setAriaLabel("Delete");
            deleteButton.setTooltipText("Delete");
            deleteButton.addClickListener(e -> openDeleteProductDialog(mapping));

            return new HorizontalLayout(editButton, deleteButton);
        }).setHeader("Actions").setAutoWidth(true);

        refreshProductsGrid();
        tab.add(productsGrid);
        return tab;
    }

    private void refreshProductsGrid() {
        productsGrid.setItems(entryService.findProductMappings(divisionId));
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
                refreshProductsGrid();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void openEditProductDialog(ProductMapping mapping) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Edit Product Mapping");

        var nameField = new TextField("Product Name");
        nameField.setValue(mapping.getProductName());
        var creditsField = new IntegerField("Credits Per Unit");
        creditsField.setMin(1);
        creditsField.setValue(mapping.getCreditsPerUnit());

        dialog.add(new VerticalLayout(nameField, creditsField));

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue()) || creditsField.getValue() == null) {
                return;
            }
            try {
                entryService.updateProductMapping(mapping.getId(),
                        nameField.getValue().trim(), creditsField.getValue(), currentUserId);
                var notification = Notification.show("Product mapping updated");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshProductsGrid();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openDeleteProductDialog(ProductMapping mapping) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Product Mapping");

        dialog.add("Delete mapping for \"" + mapping.getProductName() + "\"?");

        var confirmButton = new Button("Delete", e -> {
            try {
                entryService.removeProductMapping(mapping.getId(), currentUserId);
                var notification = Notification.show("Product mapping deleted");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshProductsGrid();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    // --- Orders Tab ---

    private VerticalLayout createOrdersTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var filterField = new TextField();
        filterField.setPlaceholder("Filter by order ID or customer email...");
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        var toolbar = new HorizontalLayout(filterField);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        tab.add(toolbar);

        ordersGrid = new Grid<>(JumpsellerOrder.class, false);
        ordersGrid.setAllRowsVisible(true);
        ordersGrid.setId("orders-grid");
        ordersGrid.addColumn(JumpsellerOrder::getJumpsellerOrderId)
                .setHeader("Order ID").setSortable(true);
        ordersGrid.addColumn(JumpsellerOrder::getCustomerEmail)
                .setHeader("Customer").setSortable(true).setFlexGrow(2);
        ordersGrid.addColumn(order -> order.getStatus().name())
                .setHeader("Status").setSortable(true).setAutoWidth(true);
        ordersGrid.addColumn(order -> formatInstant(order.getCreatedAt()))
                .setHeader("Date").setSortable(true).setAutoWidth(true);
        ordersGrid.addColumn(JumpsellerOrder::getAdminNote)
                .setHeader("Note").setSortable(true).setFlexGrow(2);
        ordersGrid.addComponentColumn(order -> {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setAriaLabel("Edit");
            editButton.setTooltipText("Edit");
            editButton.addClickListener(e -> openEditOrderDialog(order));
            return editButton;
        }).setHeader("Actions").setAutoWidth(true);

        refreshOrdersGrid();

        filterField.addValueChangeListener(e -> {
            var filterString = e.getValue().toLowerCase();
            if (filterString.isBlank()) {
                ordersGrid.getListDataView().removeFilters();
            } else {
                ordersGrid.getListDataView().setFilter(order ->
                        order.getJumpsellerOrderId().toLowerCase().contains(filterString)
                                || order.getCustomerEmail().toLowerCase().contains(filterString));
            }
        });

        tab.add(ordersGrid);
        return tab;
    }

    private void refreshOrdersGrid() {
        ordersGrid.setItems(entryService.findOrdersByDivision(divisionId));
    }

    private void openEditOrderDialog(JumpsellerOrder order) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Edit Order — " + order.getJumpsellerOrderId());

        var statusSelect = new Select<OrderStatus>();
        statusSelect.setLabel("Status");
        statusSelect.setItems(OrderStatus.values());
        statusSelect.setItemLabelGenerator(OrderStatus::name);
        statusSelect.setValue(order.getStatus());

        var noteField = new TextField("Admin Note");
        noteField.setWidthFull();
        noteField.setValue(order.getAdminNote() != null ? order.getAdminNote() : "");

        dialog.add(new VerticalLayout(statusSelect, noteField));

        var saveButton = new Button("Save", e -> {
            try {
                entryService.updateOrderAdminDetails(order.getId(),
                        statusSelect.getValue(),
                        StringUtils.hasText(noteField.getValue())
                                ? noteField.getValue().trim() : null,
                        currentUserId);
                var notification = Notification.show("Order updated");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshOrdersGrid();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
