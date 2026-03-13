package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.*;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName", layout = MainLayout.class)
@PermitAll
public class DivisionDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    private UUID divisionId;
    private Division division;
    private Competition competition;
    private Nav breadcrumb;
    private HorizontalLayout header;
    private TreeGrid<DivisionCategory> categoriesGrid;

    public DivisionDetailView(CompetitionService competitionService,
                               UserService userService,
                               AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        var compShortName = beforeEnterEvent.getRouteParameters().get("compShortName")
                .orElse(null);
        var divShortName = beforeEnterEvent.getRouteParameters().get("divShortName")
                .orElse(null);

        if (compShortName == null || divShortName == null) {
            beforeEnterEvent.forwardTo("competitions");
            return;
        }

        try {
            competition = competitionService.findCompetitionByShortName(compShortName);
            division = competitionService.findDivisionByShortName(
                    competition.getId(), divShortName);
            divisionId = division.getId();
        } catch (IllegalArgumentException e) {
            beforeEnterEvent.forwardTo("competitions");
            return;
        }

        var currentUserId = getCurrentUserId();
        if (!competitionService.isAuthorizedForDivision(divisionId, currentUserId)) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        var currentUser = userService.findById(currentUserId);
        if (currentUser.getRole() != Role.SYSTEM_ADMIN && !userService.hasPassword(currentUserId)) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        removeAll();
        breadcrumb = createBreadcrumb();
        header = createHeader();
        add(breadcrumb);
        add(header);
        add(createTabSheet());
    }

    private void refreshBreadcrumbAndHeader() {
        var newBreadcrumb = createBreadcrumb();
        replace(breadcrumb, newBreadcrumb);
        breadcrumb = newBreadcrumb;

        var newHeader = createHeader();
        replace(header, newHeader);
        header = newHeader;
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        boolean isSystemAdmin = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(user -> user.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN")))
                .orElse(false);
        var listLink = new Anchor(isSystemAdmin ? "competitions" : "my-competitions",
                isSystemAdmin ? "Competitions" : "My Competitions");
        nav.add(listLink);
        nav.add(new Span(" / "));
        var competitionLink = new Anchor(
                "competitions/" + competition.getShortName(), competition.getName());
        nav.add(competitionLink);
        nav.add(new Span(" / "));
        nav.add(new Span(division.getName()));
        return nav;
    }

    private HorizontalLayout createHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setWidthFull();

        var textBlock = new VerticalLayout();
        textBlock.setPadding(false);
        textBlock.setSpacing(false);
        textBlock.add(new H2(division.getName()));

        var details = new HorizontalLayout();
        details.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        details.add(new Span(division.getScoringSystem().name()));
        details.add(createStatusBadge(division.getStatus()));
        textBlock.add(details);

        header.add(textBlock);

        var manageEntriesButton = new Button("Manage Entries", e ->
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + competition.getShortName()
                                + "/divisions/" + division.getShortName() + "/entry-admin")));
        header.add(manageEntriesButton);

        if (division.getStatus() != DivisionStatus.DRAFT) {
            var revertButton = new Button("Revert Status", e -> revertStatus());
            header.add(revertButton);
        }

        if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
            var advanceButton = new Button("Advance Status", e -> advanceStatus());
            header.add(advanceButton);
        }

        return header;
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();

        tabSheet.add("Categories", createCategoriesTab());
        tabSheet.add("Settings", createSettingsTab());

        return tabSheet;
    }

    private VerticalLayout createCategoriesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        boolean allowModification = division.getStatus().allowsCategoryModification();

        var actions = new HorizontalLayout();
        var addCategoryButton = new Button("Add Category",
                e -> openAddCategoryDialog());
        addCategoryButton.setEnabled(allowModification);
        actions.add(addCategoryButton);
        tab.add(actions);

        categoriesGrid = new TreeGrid<>(DivisionCategory.class, false);
        categoriesGrid.setAllRowsVisible(true);
        categoriesGrid.setId("categories-grid");
        categoriesGrid.addHierarchyColumn(DivisionCategory::getCode).setHeader("Code")
                .setWidth("150px").setFlexGrow(0).setSortable(true);
        categoriesGrid.addColumn(DivisionCategory::getName).setHeader("Name");
        categoriesGrid.addColumn(DivisionCategory::getDescription).setHeader("Description")
                .setFlexGrow(2)
                .setTooltipGenerator(DivisionCategory::getDescription);

        categoriesGrid.addComponentColumn(dc -> {
            var removeButton = new Button(new Icon(VaadinIcon.CLOSE));
            removeButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            removeButton.setAriaLabel("Remove");
            removeButton.setTooltipText("Remove");
            removeButton.addClickListener(e -> openRemoveCategoryDialog(dc));
            removeButton.setEnabled(allowModification);
            return removeButton;
        }).setHeader("Actions").setAutoWidth(true);

        refreshCategoriesGrid();
        tab.add(categoriesGrid);
        return tab;
    }

    private void refreshCategoriesGrid() {
        var allCategories = competitionService.findDivisionCategories(divisionId);
        var rootCategories = allCategories.stream()
                .filter(dc -> dc.getParentId() == null)
                .toList();
        categoriesGrid.setItems(rootCategories,
                parent -> allCategories.stream()
                        .filter(dc -> parent.getId().equals(dc.getParentId()))
                        .toList());
        rootCategories.forEach(root -> categoriesGrid.expand(root));
    }

    private void openRemoveCategoryDialog(DivisionCategory category) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Remove Category");
        dialog.add("Remove \"" + category.getCode() + " — " + category.getName()
                + "\" from this division?");

        var confirmButton = new Button("Remove", e -> {
            try {
                competitionService.removeDivisionCategory(
                        divisionId, category.getId(), getCurrentUserId());
                refreshCategoriesGrid();
                var notification = Notification.show("Category removed");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            } catch (DataIntegrityViolationException ex) {
                Notification.show("Cannot remove category: it is used by one or more entries");
                dialog.close();
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openAddCategoryDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Category");

        var dialogTabs = new TabSheet();
        dialogTabs.setWidthFull();

        // --- From Catalog tab ---
        var catalogLayout = new VerticalLayout();
        catalogLayout.setPadding(false);

        var catalogSelect = new Select<Category>();
        catalogSelect.setLabel("Catalog Category");
        catalogSelect.setItemLabelGenerator(cat ->
                cat.getCode() + " — " + cat.getName());
        catalogSelect.setItems(
                competitionService.findAvailableCatalogCategories(divisionId));

        var catalogAddButton = new Button("Add", e -> {
            if (catalogSelect.getValue() == null) {
                return;
            }
            try {
                competitionService.addCatalogCategory(
                        divisionId, catalogSelect.getValue().getId(), getCurrentUserId());
                refreshCategoriesGrid();
                var notification = Notification.show("Category added");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        catalogLayout.add(catalogSelect);
        dialogTabs.add("From Catalog", catalogLayout);

        // --- Custom tab ---
        var customLayout = new VerticalLayout();
        customLayout.setPadding(false);

        var codeField = new TextField("Code");
        codeField.setMaxLength(50);
        var nameField = new TextField("Name");
        nameField.setMaxLength(255);
        var descriptionField = new TextField("Description");
        descriptionField.setMaxLength(255);

        var parentSelect = new Select<DivisionCategory>();
        parentSelect.setLabel("Parent Category (optional)");
        parentSelect.setEmptySelectionAllowed(true);
        parentSelect.setEmptySelectionCaption("None");
        parentSelect.setItemLabelGenerator(dc ->
                dc != null ? dc.getCode() + " — " + dc.getName() : "");
        var topLevel = competitionService.findDivisionCategories(divisionId).stream()
                .filter(dc -> dc.getParentId() == null)
                .toList();
        parentSelect.setItems(topLevel);

        var customAddButton = new Button("Add", e -> {
            if (!StringUtils.hasText(codeField.getValue())
                    || !StringUtils.hasText(nameField.getValue())
                    || !StringUtils.hasText(descriptionField.getValue())) {
                if (!StringUtils.hasText(codeField.getValue())) {
                    codeField.setInvalid(true);
                    codeField.setErrorMessage("Code is required");
                }
                if (!StringUtils.hasText(nameField.getValue())) {
                    nameField.setInvalid(true);
                    nameField.setErrorMessage("Name is required");
                }
                if (!StringUtils.hasText(descriptionField.getValue())) {
                    descriptionField.setInvalid(true);
                    descriptionField.setErrorMessage("Description is required");
                }
                return;
            }
            try {
                UUID parentId = parentSelect.getValue() != null
                        ? parentSelect.getValue().getId() : null;
                competitionService.addCustomCategory(
                        divisionId, codeField.getValue().trim(),
                        nameField.getValue().trim(),
                        descriptionField.getValue().trim(),
                        parentId, getCurrentUserId());
                refreshCategoriesGrid();
                var notification = Notification.show("Custom category added");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        customLayout.add(codeField, nameField, descriptionField, parentSelect);
        dialogTabs.add("Custom", customLayout);

        // Show the correct Add button based on active tab
        customAddButton.setVisible(false);
        dialogTabs.addSelectedChangeListener(e -> {
            catalogAddButton.setVisible(e.getSelectedTab().getLabel().equals("From Catalog"));
            customAddButton.setVisible(e.getSelectedTab().getLabel().equals("Custom"));
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.add(dialogTabs);
        dialog.getFooter().add(cancelButton, catalogAddButton, customAddButton);
        dialog.open();
    }

    private VerticalLayout createSettingsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        boolean isDraft = division.getStatus() == DivisionStatus.DRAFT;

        var nameField = new TextField("Name");
        nameField.setMaxLength(255);
        nameField.setValue(division.getName());

        var shortNameField = new TextField("Short Name");
        shortNameField.setMaxLength(100);
        shortNameField.setValue(division.getShortName());
        shortNameField.setHelperText("URL-friendly identifier (e.g. amadora)");

        var scoringSelect = new Select<ScoringSystem>();
        scoringSelect.setLabel("Scoring System");
        scoringSelect.setItems(ScoringSystem.values());
        scoringSelect.setValue(division.getScoringSystem());
        scoringSelect.setEnabled(isDraft);

        var entryPrefixField = new TextField("Entry Prefix");
        entryPrefixField.setMaxLength(5);
        entryPrefixField.setHelperText("Short prefix for entry numbers (e.g. AMA), up to 5 characters");
        entryPrefixField.setValue(division.getEntryPrefix() != null ? division.getEntryPrefix() : "");
        entryPrefixField.setEnabled(isDraft);

        var maxPerSubcategoryField = new com.vaadin.flow.component.textfield.IntegerField("Max Entries per Subcategory");
        maxPerSubcategoryField.setMin(1);
        maxPerSubcategoryField.setStepButtonsVisible(true);
        maxPerSubcategoryField.setClearButtonVisible(true);
        maxPerSubcategoryField.setHelperText("Per entrant per subcategory (empty = unlimited)");
        maxPerSubcategoryField.setEnabled(isDraft);
        if (division.getMaxEntriesPerSubcategory() != null) {
            maxPerSubcategoryField.setValue(division.getMaxEntriesPerSubcategory());
        }

        var maxPerMainCategoryField = new com.vaadin.flow.component.textfield.IntegerField("Max Entries per Main Category");
        maxPerMainCategoryField.setMin(1);
        maxPerMainCategoryField.setStepButtonsVisible(true);
        maxPerMainCategoryField.setClearButtonVisible(true);
        maxPerMainCategoryField.setHelperText("Per entrant per main category (empty = unlimited)");
        maxPerMainCategoryField.setEnabled(isDraft);
        if (division.getMaxEntriesPerMainCategory() != null) {
            maxPerMainCategoryField.setValue(division.getMaxEntriesPerMainCategory());
        }

        var maxTotalField = new com.vaadin.flow.component.textfield.IntegerField("Max Total Entries");
        maxTotalField.setMin(1);
        maxTotalField.setStepButtonsVisible(true);
        maxTotalField.setClearButtonVisible(true);
        maxTotalField.setHelperText("Per entrant total in this division (empty = unlimited)");
        maxTotalField.setEnabled(isDraft);
        if (division.getMaxEntriesTotal() != null) {
            maxTotalField.setValue(division.getMaxEntriesTotal());
        }

        var meaderyRequiredCheckbox = new Checkbox("Meadery Name Required");
        meaderyRequiredCheckbox.setValue(division.isMeaderyNameRequired());
        meaderyRequiredCheckbox.setEnabled(isDraft);
        meaderyRequiredCheckbox.setTooltipText(
                "When enabled, entrants must have a meadery name in their profile to submit entries");

        boolean canEditDeadline = isDraft || division.getStatus() == DivisionStatus.REGISTRATION_OPEN;

        var deadlinePicker = new DateTimePicker("Registration Deadline");
        deadlinePicker.setValue(division.getRegistrationDeadline());
        deadlinePicker.setEnabled(canEditDeadline);

        var timezoneCombo = new ComboBox<String>("Timezone");
        timezoneCombo.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezoneCombo.setValue(division.getRegistrationDeadlineTimezone());
        timezoneCombo.setEnabled(canEditDeadline);
        timezoneCombo.setAllowCustomValue(false);

        var statusField = new TextField("Status");
        statusField.setValue(division.getStatus().getDisplayName());
        statusField.setReadOnly(true);

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            if (!StringUtils.hasText(shortNameField.getValue())) {
                shortNameField.setInvalid(true);
                shortNameField.setErrorMessage("Short name is required");
                return;
            }
            try {
                var prefix = StringUtils.hasText(entryPrefixField.getValue())
                        ? entryPrefixField.getValue().trim() : null;
                division = competitionService.updateDivision(
                        divisionId, nameField.getValue(),
                        shortNameField.getValue(),
                        scoringSelect.getValue(), prefix, getCurrentUserId());
                if (isDraft) {
                    competitionService.updateDivisionEntryLimits(
                            divisionId,
                            maxPerSubcategoryField.getValue(),
                            maxPerMainCategoryField.getValue(),
                            maxTotalField.getValue(),
                            getCurrentUserId());
                }
                if (isDraft) {
                    competitionService.updateDivisionMeaderyNameRequired(
                            divisionId, meaderyRequiredCheckbox.getValue(), getCurrentUserId());
                }
                if (deadlinePicker.getValue() != null && timezoneCombo.getValue() != null) {
                    competitionService.updateDivisionDeadline(
                            divisionId, deadlinePicker.getValue(),
                            timezoneCombo.getValue(), getCurrentUserId());
                }
                refreshBreadcrumbAndHeader();
                var notification = Notification.show("Settings saved successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        tab.add(nameField, shortNameField, entryPrefixField, scoringSelect,
                maxPerSubcategoryField, maxPerMainCategoryField, maxTotalField,
                meaderyRequiredCheckbox, deadlinePicker, timezoneCombo,
                statusField, saveButton);
        return tab;
    }

    private void advanceStatus() {
        var nextStatusName = division.getStatus().next()
                .map(DivisionStatus::getDisplayName).orElse("—");

        var dialog = new Dialog();
        dialog.setHeaderTitle("Advance Status");
        dialog.add("Advance from " + division.getStatus().getDisplayName()
                + " to " + nextStatusName + "?");

        var confirmButton = new Button("Advance", e -> {
            try {
                division = competitionService.advanceDivisionStatus(divisionId, getCurrentUserId());
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + competition.getShortName()
                                + "/divisions/" + division.getShortName()));
                dialog.close();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void revertStatus() {
        var prevStatusName = division.getStatus().previous()
                .map(DivisionStatus::getDisplayName).orElse("—");

        var dialog = new Dialog();
        dialog.setHeaderTitle("Revert Status");
        dialog.add("Revert from " + division.getStatus().getDisplayName()
                + " to " + prevStatusName + "?");

        var confirmButton = new Button("Revert", e -> {
            try {
                division = competitionService.revertDivisionStatus(divisionId, getCurrentUserId());
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + competition.getShortName()
                                + "/divisions/" + division.getShortName()));
                dialog.close();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private Span createStatusBadge(DivisionStatus status) {
        var badge = new Span(status.getDisplayName());
        badge.getElement().getThemeList().add("badge pill small");
        badge.addClassName(status.getBadgeCssClass());
        return badge;
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
