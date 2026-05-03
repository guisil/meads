package app.meads.competition.internal;

import app.meads.BusinessRuleException;
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
    private TreeGrid<DivisionCategory> judgingCategoriesGrid;

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
        } catch (BusinessRuleException e) {
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
                isSystemAdmin ? getTranslation("nav.competitions") : getTranslation("nav.my-competitions"));
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

        if (competition.hasLogo()) {
            var dataUri = "data:" + competition.getLogoContentType() + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(competition.getLogo());
            var logo = new com.vaadin.flow.component.html.Image(dataUri, competition.getName() + " logo");
            logo.setHeight("64px");
            header.add(logo);
        }

        var textBlock = new VerticalLayout();
        textBlock.setPadding(false);
        textBlock.setSpacing(false);
        textBlock.add(new H2(competition.getName() + " — " + division.getName()));

        var details = new HorizontalLayout();
        details.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        details.add(new Span(division.getScoringSystem().name()));
        details.add(createStatusBadge(division.getStatus()));
        textBlock.add(details);

        header.add(textBlock);

        var manageEntriesButton = new Button(getTranslation("division-detail.manage-entries"), e ->
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + competition.getShortName()
                                + "/divisions/" + division.getShortName() + "/entry-admin")));
        header.add(manageEntriesButton);

        if (division.getStatus() != DivisionStatus.DRAFT) {
            var revertButton = new Button(getTranslation("division-detail.revert-status"), e -> revertStatus());
            header.add(revertButton);
        }

        if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
            var advanceButton = new Button(getTranslation("division-detail.advance-status"), e -> advanceStatus());
            header.add(advanceButton);
        }

        return header;
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();

        tabSheet.add(getTranslation("division-detail.tab.categories"), createCategoriesTab());
        if (division.getStatus().allowsJudgingCategoryManagement()) {
            tabSheet.add(getTranslation("division-detail.tab.judging-categories"), createJudgingCategoriesSection());
            tabSheet.setSelectedIndex(1);
        }
        tabSheet.add(getTranslation("division-detail.tab.settings"), createSettingsTab());

        return tabSheet;
    }

    private VerticalLayout createCategoriesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        boolean allowModification = division.getStatus().allowsCategoryModification();

        var actions = new HorizontalLayout();
        var addCategoryButton = new Button(getTranslation("division-detail.categories.add"),
                e -> openAddCategoryDialog());
        addCategoryButton.setEnabled(allowModification);
        actions.add(addCategoryButton);
        tab.add(actions);

        categoriesGrid = new TreeGrid<>(DivisionCategory.class, false);
        categoriesGrid.setAllRowsVisible(true);
        categoriesGrid.setId("categories-grid");
        categoriesGrid.addHierarchyColumn(DivisionCategory::getCode).setHeader(getTranslation("division-detail.categories.column.code"))
                .setWidth("150px").setFlexGrow(0).setSortable(true);
        categoriesGrid.addColumn(DivisionCategory::getName).setHeader(getTranslation("division-detail.categories.column.name"));
        categoriesGrid.addColumn(DivisionCategory::getDescription).setHeader(getTranslation("division-detail.categories.column.description"))
                .setFlexGrow(2)
                .setTooltipGenerator(DivisionCategory::getDescription);

        categoriesGrid.addComponentColumn(dc -> {
            var removeButton = new Button(new Icon(VaadinIcon.CLOSE));
            removeButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            removeButton.setAriaLabel(getTranslation("division-detail.categories.action.remove"));
            removeButton.setTooltipText(getTranslation("division-detail.categories.action.remove"));
            removeButton.addClickListener(e -> openRemoveCategoryDialog(dc));
            removeButton.setEnabled(allowModification);
            return removeButton;
        }).setHeader(getTranslation("division-detail.categories.column.actions")).setAutoWidth(true);

        refreshCategoriesGrid();
        tab.add(categoriesGrid);

        return tab;
    }

    private VerticalLayout createJudgingCategoriesSection() {
        var section = new VerticalLayout();
        section.setPadding(false);

        var judgingCategories = competitionService.findJudgingCategories(divisionId);

        if (judgingCategories.isEmpty()) {
            var initButton = new Button(getTranslation("division-detail.judging.initialize"), e -> {
                try {
                    competitionService.initializeJudgingCategories(divisionId, getCurrentUserId());
                    getUI().ifPresent(ui -> ui.navigate(
                            "competitions/" + competition.getShortName()
                                    + "/divisions/" + division.getShortName()));
                } catch (BusinessRuleException ex) {
                    Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                    e.getSource().setEnabled(true);
                }
            });
            initButton.setDisableOnClick(true);
            section.add(initButton);
        } else {
            var judgingActions = new HorizontalLayout();
            var addJudgingButton = new Button(getTranslation("division-detail.judging.add-button"),
                    e -> openAddJudgingCategoryDialog());
            judgingActions.add(addJudgingButton);
            section.add(judgingActions);

            judgingCategoriesGrid = new TreeGrid<>(DivisionCategory.class, false);
            judgingCategoriesGrid.setAllRowsVisible(true);
            judgingCategoriesGrid.setId("judging-categories-grid");
            judgingCategoriesGrid.addHierarchyColumn(DivisionCategory::getCode)
                    .setHeader(getTranslation("division-detail.judging.column.code"))
                    .setWidth("150px").setFlexGrow(0).setSortable(true);
            judgingCategoriesGrid.addColumn(DivisionCategory::getName)
                    .setHeader(getTranslation("division-detail.judging.column.name"));
            judgingCategoriesGrid.addColumn(DivisionCategory::getDescription)
                    .setHeader(getTranslation("division-detail.judging.column.description"))
                    .setFlexGrow(2);

            judgingCategoriesGrid.addComponentColumn(dc -> {
                var removeButton = new Button(new Icon(VaadinIcon.CLOSE));
                removeButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                removeButton.addClickListener(ev -> openRemoveJudgingCategoryDialog(dc));
                return removeButton;
            }).setHeader(getTranslation("division-detail.judging.column.actions")).setAutoWidth(true);

            refreshJudgingCategoriesGrid();
            section.add(judgingCategoriesGrid);
        }

        return section;
    }

    private void refreshJudgingCategoriesGrid() {
        if (judgingCategoriesGrid == null) return;
        var allJudging = competitionService.findJudgingCategories(divisionId);
        var rootJudging = allJudging.stream()
                .filter(dc -> dc.getParentId() == null)
                .toList();
        judgingCategoriesGrid.setItems(rootJudging,
                parent -> allJudging.stream()
                        .filter(dc -> parent.getId().equals(dc.getParentId()))
                        .toList());
        rootJudging.forEach(root -> judgingCategoriesGrid.expand(root));
    }

    private void openAddJudgingCategoryDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("division-detail.judging.add.title"));

        var codeField = new TextField(getTranslation("division-detail.judging.add.code"));
        codeField.setMaxLength(50);
        var nameField = new TextField(getTranslation("division-detail.judging.add.name"));
        nameField.setMaxLength(255);
        var descriptionField = new TextField(getTranslation("division-detail.judging.add.description"));
        descriptionField.setMaxLength(255);

        var parentSelect = new Select<DivisionCategory>();
        parentSelect.setLabel(getTranslation("division-detail.judging.add.parent"));
        parentSelect.setEmptySelectionAllowed(true);
        parentSelect.setEmptySelectionCaption(getTranslation("division-detail.judging.add.parent.none"));
        parentSelect.setItemLabelGenerator(dc -> dc != null ? dc.getCode() + " — " + dc.getName() : "");
        var topLevelJudging = competitionService.findJudgingCategories(divisionId).stream()
                .filter(dc -> dc.getParentId() == null)
                .toList();
        parentSelect.setItems(topLevelJudging);

        var content = new VerticalLayout(codeField, nameField, descriptionField, parentSelect);
        content.setPadding(false);

        var addButton = new Button(getTranslation("division-detail.judging.add.button"), e -> {
            if (!StringUtils.hasText(codeField.getValue())
                    || !StringUtils.hasText(nameField.getValue())
                    || !StringUtils.hasText(descriptionField.getValue())) {
                if (!StringUtils.hasText(codeField.getValue())) {
                    codeField.setInvalid(true);
                    codeField.setErrorMessage(getTranslation("division-detail.judging.add.code.error"));
                }
                if (!StringUtils.hasText(nameField.getValue())) {
                    nameField.setInvalid(true);
                    nameField.setErrorMessage(getTranslation("division-detail.judging.add.name.error"));
                }
                if (!StringUtils.hasText(descriptionField.getValue())) {
                    descriptionField.setInvalid(true);
                    descriptionField.setErrorMessage(getTranslation("division-detail.judging.add.description.error"));
                }
                e.getSource().setEnabled(true);
                return;
            }
            try {
                UUID parentId = parentSelect.getValue() != null
                        ? parentSelect.getValue().getId() : null;
                competitionService.addJudgingCategory(divisionId,
                        codeField.getValue().trim(),
                        nameField.getValue().trim(),
                        descriptionField.getValue().trim(),
                        parentId, getCurrentUserId());
                refreshJudgingCategoriesGrid();
                var notification = Notification.show(getTranslation("division-detail.judging.added"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        addButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.add(content);
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void openRemoveJudgingCategoryDialog(DivisionCategory category) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("division-detail.judging.remove.title"));
        dialog.add(getTranslation("division-detail.judging.remove.confirm",
                category.getCode(), category.getName()));

        var confirmButton = new Button(getTranslation("division-detail.judging.remove.button"), e -> {
            try {
                competitionService.removeJudgingCategory(divisionId, category.getId(), getCurrentUserId());
                refreshJudgingCategoriesGrid();
                var notification = Notification.show(getTranslation("division-detail.judging.removed"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
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
        dialog.setHeaderTitle(getTranslation("division-detail.categories.remove.title"));
        dialog.add(getTranslation("division-detail.categories.remove.confirm", category.getCode(), category.getName()));

        var confirmButton = new Button(getTranslation("division-detail.categories.remove.button"), e -> {
            try {
                competitionService.removeDivisionCategory(
                        divisionId, category.getId(), getCurrentUserId());
                refreshCategoriesGrid();
                var notification = Notification.show(getTranslation("division-detail.categories.removed"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            } catch (DataIntegrityViolationException ex) {
                Notification.show(getTranslation("division-detail.categories.remove.error"));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openAddCategoryDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("division-detail.categories.add.title"));

        var dialogTabs = new TabSheet();
        dialogTabs.setWidthFull();

        // --- From Catalog tab ---
        var catalogLayout = new VerticalLayout();
        catalogLayout.setPadding(false);

        var catalogSelect = new Select<Category>();
        catalogSelect.setLabel(getTranslation("division-detail.categories.add.catalog.select"));
        catalogSelect.setItemLabelGenerator(cat ->
                cat.getCode() + " — " + cat.getName());
        catalogSelect.setItems(
                competitionService.findAvailableCatalogCategories(divisionId));

        var catalogAddButton = new Button(getTranslation("division-detail.categories.add.catalog.button"), e -> {
            if (catalogSelect.getValue() == null) {
                e.getSource().setEnabled(true);
                return;
            }
            try {
                competitionService.addCatalogCategory(
                        divisionId, catalogSelect.getValue().getId(), getCurrentUserId());
                refreshCategoriesGrid();
                var notification = Notification.show(getTranslation("division-detail.categories.add.catalog.added"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        catalogAddButton.setDisableOnClick(true);

        catalogLayout.add(catalogSelect);
        var catalogTabLabel = getTranslation("division-detail.categories.add.tab.catalog");
        dialogTabs.add(catalogTabLabel, catalogLayout);

        // --- Custom tab ---
        var customLayout = new VerticalLayout();
        customLayout.setPadding(false);

        var codeField = new TextField(getTranslation("division-detail.categories.add.custom.code"));
        codeField.setMaxLength(50);
        var nameField = new TextField(getTranslation("division-detail.categories.add.custom.name"));
        nameField.setMaxLength(255);
        var descriptionField = new TextField(getTranslation("division-detail.categories.add.custom.description"));
        descriptionField.setMaxLength(255);

        var parentSelect = new Select<DivisionCategory>();
        parentSelect.setLabel(getTranslation("division-detail.categories.add.custom.parent"));
        parentSelect.setEmptySelectionAllowed(true);
        parentSelect.setEmptySelectionCaption(getTranslation("division-detail.categories.add.custom.parent.none"));
        parentSelect.setItemLabelGenerator(dc ->
                dc != null ? dc.getCode() + " — " + dc.getName() : "");
        var topLevel = competitionService.findDivisionCategories(divisionId).stream()
                .filter(dc -> dc.getParentId() == null)
                .toList();
        parentSelect.setItems(topLevel);

        var customAddButton = new Button(getTranslation("division-detail.categories.add.custom.button"), e -> {
            if (!StringUtils.hasText(codeField.getValue())
                    || !StringUtils.hasText(nameField.getValue())
                    || !StringUtils.hasText(descriptionField.getValue())) {
                if (!StringUtils.hasText(codeField.getValue())) {
                    codeField.setInvalid(true);
                    codeField.setErrorMessage(getTranslation("division-detail.categories.add.custom.code.error"));
                }
                if (!StringUtils.hasText(nameField.getValue())) {
                    nameField.setInvalid(true);
                    nameField.setErrorMessage(getTranslation("division-detail.categories.add.custom.name.error"));
                }
                if (!StringUtils.hasText(descriptionField.getValue())) {
                    descriptionField.setInvalid(true);
                    descriptionField.setErrorMessage(getTranslation("division-detail.categories.add.custom.description.error"));
                }
                e.getSource().setEnabled(true);
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
                var notification = Notification.show(getTranslation("division-detail.categories.add.custom.added"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        customAddButton.setDisableOnClick(true);

        customLayout.add(codeField, nameField, descriptionField, parentSelect);
        var customTabLabel = getTranslation("division-detail.categories.add.tab.custom");
        dialogTabs.add(customTabLabel, customLayout);

        // Show the correct Add button based on active tab
        customAddButton.setVisible(false);
        dialogTabs.addSelectedChangeListener(e -> {
            catalogAddButton.setVisible(e.getSelectedTab().getLabel().equals(catalogTabLabel));
            customAddButton.setVisible(e.getSelectedTab().getLabel().equals(customTabLabel));
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

        var nameField = new TextField(getTranslation("division-detail.settings.name"));
        nameField.setMaxLength(255);
        nameField.setValue(division.getName());
        nameField.setWidth("400px");

        var shortNameField = new TextField(getTranslation("division-detail.settings.short-name"));
        shortNameField.setMaxLength(100);
        shortNameField.setValue(division.getShortName());
        shortNameField.setHelperText(getTranslation("division-detail.settings.short-name.helper"));

        var scoringSelect = new Select<ScoringSystem>();
        scoringSelect.setLabel(getTranslation("division-detail.settings.scoring"));
        scoringSelect.setItems(ScoringSystem.values());
        scoringSelect.setValue(division.getScoringSystem());
        scoringSelect.setEnabled(isDraft);

        var entryPrefixField = new TextField(getTranslation("division-detail.settings.prefix"));
        entryPrefixField.setMaxLength(5);
        entryPrefixField.setHelperText(getTranslation("division-detail.settings.prefix.helper"));
        entryPrefixField.setValue(division.getEntryPrefix() != null ? division.getEntryPrefix() : "");
        entryPrefixField.setEnabled(isDraft);

        var maxPerSubcategoryField = new com.vaadin.flow.component.textfield.IntegerField(getTranslation("division-detail.settings.max-per-subcategory"));
        maxPerSubcategoryField.setMin(1);
        maxPerSubcategoryField.setStepButtonsVisible(true);
        maxPerSubcategoryField.setClearButtonVisible(true);
        maxPerSubcategoryField.setHelperText(getTranslation("division-detail.settings.max-per-subcategory.helper"));
        maxPerSubcategoryField.setEnabled(isDraft);
        if (division.getMaxEntriesPerSubcategory() != null) {
            maxPerSubcategoryField.setValue(division.getMaxEntriesPerSubcategory());
        }

        var maxPerMainCategoryField = new com.vaadin.flow.component.textfield.IntegerField(getTranslation("division-detail.settings.max-per-main-category"));
        maxPerMainCategoryField.setMin(1);
        maxPerMainCategoryField.setStepButtonsVisible(true);
        maxPerMainCategoryField.setClearButtonVisible(true);
        maxPerMainCategoryField.setHelperText(getTranslation("division-detail.settings.max-per-main-category.helper"));
        maxPerMainCategoryField.setEnabled(isDraft);
        if (division.getMaxEntriesPerMainCategory() != null) {
            maxPerMainCategoryField.setValue(division.getMaxEntriesPerMainCategory());
        }

        var maxTotalField = new com.vaadin.flow.component.textfield.IntegerField(getTranslation("division-detail.settings.max-total"));
        maxTotalField.setMin(1);
        maxTotalField.setStepButtonsVisible(true);
        maxTotalField.setClearButtonVisible(true);
        maxTotalField.setHelperText(getTranslation("division-detail.settings.max-total.helper"));
        maxTotalField.setEnabled(isDraft);
        if (division.getMaxEntriesTotal() != null) {
            maxTotalField.setValue(division.getMaxEntriesTotal());
        }

        var meaderyRequiredCheckbox = new Checkbox(getTranslation("division-detail.settings.meadery-required"));
        meaderyRequiredCheckbox.setValue(division.isMeaderyNameRequired());
        meaderyRequiredCheckbox.setEnabled(isDraft);
        meaderyRequiredCheckbox.setTooltipText(
                getTranslation("division-detail.settings.meadery-required.helper"));

        boolean canEditDeadline = isDraft || division.getStatus() == DivisionStatus.REGISTRATION_OPEN;

        var deadlinePicker = new DateTimePicker(getTranslation("division-detail.settings.deadline"));
        deadlinePicker.setValue(division.getRegistrationDeadline());
        deadlinePicker.setEnabled(canEditDeadline);

        var timezoneCombo = new ComboBox<String>(getTranslation("division-detail.settings.timezone"));
        timezoneCombo.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezoneCombo.setValue(division.getRegistrationDeadlineTimezone());
        timezoneCombo.setEnabled(canEditDeadline);
        timezoneCombo.setAllowCustomValue(false);

        var statusField = new TextField(getTranslation("division-detail.settings.status"));
        statusField.setValue(division.getStatus().getDisplayName());
        statusField.setReadOnly(true);

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("division-detail.settings.name.error"));
                e.getSource().setEnabled(true);
                return;
            }
            if (!StringUtils.hasText(shortNameField.getValue())) {
                shortNameField.setInvalid(true);
                shortNameField.setErrorMessage(getTranslation("division-detail.settings.short-name.error"));
                e.getSource().setEnabled(true);
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
                var notification = Notification.show(getTranslation("division-detail.settings.saved"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                e.getSource().setEnabled(true);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            } catch (IllegalStateException ex) {
                Notification.show(getTranslation("division-detail.settings.status-error"));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

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
        dialog.setHeaderTitle(getTranslation("division-detail.advance.title"));
        dialog.add(getTranslation("division-detail.advance.confirm",
                division.getStatus().getDisplayName(), nextStatusName));

        var confirmButton = new Button(getTranslation("division-detail.advance.button"), e -> {
            try {
                division = competitionService.advanceDivisionStatus(divisionId, getCurrentUserId());
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + competition.getShortName()
                                + "/divisions/" + division.getShortName()));
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void revertStatus() {
        var prevStatusName = division.getStatus().previous()
                .map(DivisionStatus::getDisplayName).orElse("—");

        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("division-detail.revert.title"));
        dialog.add(getTranslation("division-detail.revert.confirm",
                division.getStatus().getDisplayName(), prevStatusName));

        var confirmButton = new Button(getTranslation("division-detail.revert.button"), e -> {
            try {
                division = competitionService.revertDivisionStatus(divisionId, getCurrentUserId());
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + competition.getShortName()
                                + "/divisions/" + division.getShortName()));
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

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
