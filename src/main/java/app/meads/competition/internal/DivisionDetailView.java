package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.*;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

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

        if (!competitionService.isAuthorizedForDivision(divisionId, getCurrentUserId())) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        add(createTabSheet());
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
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

        var manageEntriesLink = new Anchor(
                "competitions/" + competition.getShortName()
                        + "/divisions/" + division.getShortName() + "/entry-admin",
                "Manage Entries");
        header.add(manageEntriesLink);

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
        categoriesGrid.setId("categories-grid");
        categoriesGrid.addHierarchyColumn(DivisionCategory::getCode).setHeader("Code")
                .setWidth("100px").setFlexGrow(0).setSortable(true);
        categoriesGrid.addColumn(DivisionCategory::getName).setHeader("Name");
        categoriesGrid.addColumn(DivisionCategory::getDescription).setHeader("Description");

        categoriesGrid.addComponentColumn(dc -> {
            var removeButton = new Button("Remove", e -> {
                try {
                    competitionService.removeDivisionCategory(
                            divisionId, dc.getId(), getCurrentUserId());
                    refreshCategoriesGrid();
                    var notification = Notification.show("Category removed");
                    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } catch (IllegalArgumentException ex) {
                    Notification.show(ex.getMessage());
                }
            });
            removeButton.setEnabled(allowModification);
            return removeButton;
        }).setHeader("");

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

        catalogLayout.add(catalogSelect, catalogAddButton);
        dialogTabs.add("From Catalog", catalogLayout);

        // --- Custom tab ---
        var customLayout = new VerticalLayout();
        customLayout.setPadding(false);

        var codeField = new TextField("Code");
        var nameField = new TextField("Name");
        var descriptionField = new TextField("Description");

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

        customLayout.add(codeField, nameField, descriptionField, parentSelect, customAddButton);
        dialogTabs.add("Custom", customLayout);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.add(dialogTabs);
        dialog.getFooter().add(cancelButton);
        dialog.open();
    }

    private VerticalLayout createSettingsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        boolean isDraft = division.getStatus() == DivisionStatus.DRAFT;

        var nameField = new TextField("Name");
        nameField.setValue(division.getName());
        nameField.setEnabled(isDraft);

        var shortNameField = new TextField("Short Name");
        shortNameField.setValue(division.getShortName());
        shortNameField.setHelperText("URL-friendly identifier (e.g. amadora)");
        shortNameField.setEnabled(isDraft);

        var scoringSelect = new Select<ScoringSystem>();
        scoringSelect.setLabel("Scoring System");
        scoringSelect.setItems(ScoringSystem.values());
        scoringSelect.setValue(division.getScoringSystem());
        scoringSelect.setEnabled(isDraft);

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
                division = competitionService.updateDivision(
                        divisionId, nameField.getValue(),
                        shortNameField.getValue(),
                        scoringSelect.getValue(), getCurrentUserId());
                var notification = Notification.show("Settings saved successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });
        saveButton.setEnabled(isDraft);

        tab.add(nameField, shortNameField, scoringSelect, statusField, saveButton);
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
