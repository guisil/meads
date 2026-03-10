package app.meads.entry.internal;

import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DocumentType;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.entry.*;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Route(value = "competitions/:compShortName/divisions/:divShortName/my-entries", layout = MainLayout.class)
@PermitAll
public class MyEntriesView extends VerticalLayout implements BeforeEnterObserver {

    private final EntryService entryService;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final LabelPdfService labelPdfService;
    private final transient AuthenticationContext authenticationContext;

    private UUID divisionId;
    private Division division;
    private Competition competition;
    private String compShortName;
    private String divShortName;
    private String competitionName;
    private UUID currentUserId;
    private Grid<Entry> entriesGrid;
    private Map<UUID, DivisionCategory> categoriesById;
    private List<Entry> entries;

    private boolean meaderyNameMissing;

    // Filter state
    private String nameFilter = "";
    private EntryStatus statusFilter;

    public MyEntriesView(EntryService entryService,
                          CompetitionService competitionService,
                          UserService userService,
                          LabelPdfService labelPdfService,
                          AuthenticationContext authenticationContext) {
        this.entryService = entryService;
        this.competitionService = competitionService;
        this.userService = userService;
        this.labelPdfService = labelPdfService;
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
            competition = competitionService.findCompetitionByShortName(compShortName);
            competitionName = competition.getName();
            division = competitionService.findDivisionByShortName(
                    competition.getId(), divShortName);
            divisionId = division.getId();
        } catch (IllegalArgumentException e) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        currentUserId = getCurrentUserId();

        // Authorization: SYSTEM_ADMIN, competition ADMIN, or has credits in this division
        var user = userService.findById(currentUserId);
        if (user.getRole() != Role.SYSTEM_ADMIN
                && !competitionService.isAuthorizedForDivision(divisionId, currentUserId)
                && entryService.getCreditBalance(divisionId, currentUserId) == 0) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        // Check meadery name requirement
        meaderyNameMissing = division.isMeaderyNameRequired()
                && (user.getMeaderyName() == null || user.getMeaderyName().isBlank());

        // Pre-load categories for the division
        categoriesById = competitionService.findDivisionCategories(divisionId).stream()
                .collect(Collectors.toMap(DivisionCategory::getId, Function.identity()));

        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        if (meaderyNameMissing) {
            add(createMeaderyWarning());
        }
        add(createDocumentsSection());
        add(createCreditInfo());
        var toolbar = createToolbar();
        toolbar.getStyle().set("margin-top", "var(--lumo-space-s)");
        add(toolbar);
        add(createEntriesGrid());
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        nav.add(new Anchor("my-entries", "My Entries"));
        nav.add(new Span(" / "));
        nav.add(new Span(competitionName));
        nav.add(new Span(" / "));
        nav.add(new Span(division.getName()));
        return nav;
    }

    private H2 createHeader() {
        return new H2(division.getName() + " — My Entries");
    }

    private VerticalLayout createDocumentsSection() {
        var docs = competitionService.getDocuments(competition.getId());
        if (docs.isEmpty()) {
            return new VerticalLayout();
        }

        var section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);

        var header = new Span("Competition Documents");
        header.getStyle().set("font-weight", "600");
        section.add(header);

        for (var doc : docs) {
            if (doc.getType() == DocumentType.LINK) {
                var anchor = new Anchor(doc.getUrl(), doc.getName());
                anchor.setTarget("_blank");
                section.add(anchor);
            } else {
                var streamResource = new StreamResource(doc.getName() + ".pdf",
                        () -> new ByteArrayInputStream(
                                competitionService.getDocument(doc.getId()).getData()));
                var anchor = new Anchor(streamResource, doc.getName());
                anchor.getElement().setAttribute("download", true);
                section.add(anchor);
            }
        }
        return section;
    }

    private Div createMeaderyWarning() {
        var warning = new Div();
        warning.getStyle()
                .set("background-color", "var(--lumo-warning-color-10pct)")
                .set("color", "var(--lumo-warning-text-color)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-bottom", "var(--lumo-space-m)");
        warning.add(new Span("This division requires a meadery name. Please update your profile (via the user menu on the top right corner of the page) before submitting entries."));
        return warning;
    }

    private HorizontalLayout createCreditInfo() {
        var layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        layout.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)");

        var creditBalance = entryService.getCreditBalance(divisionId, currentUserId);
        var activeEntries = entryService.countActiveEntries(divisionId, currentUserId);
        var remaining = creditBalance - activeEntries;

        var creditsLabel = new Span("Credits:");
        creditsLabel.getStyle().set("font-weight", "600");

        var remainingBadge = new Span(remaining + " remaining");
        remainingBadge.getElement().getThemeList().add("badge pill small");
        if (remaining > 0) {
            remainingBadge.getElement().getThemeList().add("success");
        }

        var detailSpan = new Span("(" + creditBalance + " total, " + activeEntries + " used)");
        detailSpan.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        layout.add(creditsLabel, remainingBadge, detailSpan);

        // Limits badges (right side with spacer)
        var hasLimits = division.getMaxEntriesPerSubcategory() != null
                || division.getMaxEntriesPerMainCategory() != null
                || division.getMaxEntriesTotal() != null;
        if (hasLimits) {
            var spacer = new Div();
            layout.add(spacer);
            layout.expand(spacer);

            var limitsLabel = new Span("Limits:");
            limitsLabel.getStyle().set("font-weight", "600");
            layout.add(limitsLabel);

            if (division.getMaxEntriesPerSubcategory() != null) {
                layout.add(createLimitBadge(division.getMaxEntriesPerSubcategory() + " per subcategory"));
            }
            if (division.getMaxEntriesPerMainCategory() != null) {
                layout.add(createLimitBadge(division.getMaxEntriesPerMainCategory() + " per main category"));
            }
            if (division.getMaxEntriesTotal() != null) {
                layout.add(createLimitBadge(division.getMaxEntriesTotal() + " total"));
            }
        }

        return layout;
    }

    private Span createLimitBadge(String text) {
        var badge = new Span(text);
        badge.getElement().getThemeList().add("badge pill small contrast");
        return badge;
    }

    private HorizontalLayout createToolbar() {
        var toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        // Filters (left)
        var nameField = new TextField();
        nameField.setPlaceholder("Filter by mead name...");
        nameField.setWidth("300px");
        nameField.setClearButtonVisible(true);
        nameField.setValueChangeMode(ValueChangeMode.EAGER);
        nameField.addValueChangeListener(e -> {
            nameFilter = e.getValue();
            applyFilters();
        });

        var statusSelect = new Select<EntryStatus>();
        statusSelect.setPlaceholder("All statuses");
        statusSelect.setItems(EntryStatus.values());
        statusSelect.setItemLabelGenerator(s -> s != null ? s.getDisplayName() : "All statuses");
        statusSelect.setEmptySelectionAllowed(true);
        statusSelect.addValueChangeListener(e -> {
            statusFilter = e.getValue();
            applyFilters();
        });

        // Action buttons (right)
        var creditBalance = entryService.getCreditBalance(divisionId, currentUserId);
        var activeEntries = entryService.countActiveEntries(divisionId, currentUserId);
        var isOpen = division.getStatus() == DivisionStatus.REGISTRATION_OPEN;

        var addButton = new Button("Add Entry", e -> openEntryDialog(null));
        addButton.setEnabled(isOpen && creditBalance > activeEntries);

        var entries = entryService.findEntriesByDivisionAndUser(divisionId, currentUserId);
        var hasDrafts = entries.stream().anyMatch(en -> en.getStatus() == EntryStatus.DRAFT);

        var submitButton = new Button("Submit All", e -> submitAll());
        submitButton.setEnabled(hasDrafts && !meaderyNameMissing);
        if (meaderyNameMissing) {
            submitButton.setTooltipText("Meadery name required — update your profile");
        }

        // Download all labels (SUBMITTED entries)
        var downloadAllResource = new StreamResource("all-labels.pdf", () -> {
            var submittedEntries = entries != null
                    ? entries.stream().filter(e2 -> e2.getStatus() == EntryStatus.SUBMITTED).toList()
                    : List.<Entry>of();
            if (submittedEntries.isEmpty()) {
                return new ByteArrayInputStream(new byte[0]);
            }
            return new ByteArrayInputStream(
                    labelPdfService.generateLabels(submittedEntries, competition, division, categoriesById::get));
        });
        downloadAllResource.setContentType("application/pdf");
        var downloadAllAnchor = new Anchor(downloadAllResource, "");
        downloadAllAnchor.getElement().setAttribute("download", true);
        var downloadAllBtn = new Button("Download all labels", new Icon(VaadinIcon.DOWNLOAD_ALT));
        downloadAllAnchor.add(downloadAllBtn);

        // Spacer pushes buttons to the right
        var spacer = new Div();
        toolbar.add(nameField, statusSelect, spacer, addButton, submitButton, downloadAllAnchor);
        toolbar.expand(spacer);

        return toolbar;
    }

    private void applyFilters() {
        var dataProvider = (ListDataProvider<Entry>) entriesGrid.getDataProvider();
        dataProvider.setFilter(entry -> {
            if (StringUtils.hasText(nameFilter)
                    && !entry.getMeadName().toLowerCase().contains(nameFilter.toLowerCase())) {
                return false;
            }
            if (statusFilter != null && entry.getStatus() != statusFilter) {
                return false;
            }
            return true;
        });
    }

    private Grid<Entry> createEntriesGrid() {
        entriesGrid = new Grid<>(Entry.class, false);
        entriesGrid.setAllRowsVisible(true);
        entriesGrid.setId("entries-grid");

        // 1. Entry # — smaller column
        var entryNumCol = entriesGrid.addColumn(entry -> formatEntryId(entry))
                .setHeader("Entry #")
                .setSortable(true)
                .setComparator((a, b) -> Integer.compare(a.getEntryNumber(), b.getEntryNumber()))
                .setWidth("110px")
                .setFlexGrow(0);

        // Mead Name
        entriesGrid.addColumn(Entry::getMeadName)
                .setHeader("Mead Name")
                .setSortable(true);

        // Category (initial)
        entriesGrid.addColumn(entry -> resolveCategoryName(entry.getInitialCategoryId()))
                .setHeader("Category")
                .setSortable(true);

        // 3. Final Category
        entriesGrid.addColumn(entry -> {
            if (entry.getFinalCategoryId() == null) {
                return "—";
            }
            return resolveCategoryName(entry.getFinalCategoryId());
        }).setHeader("Final Category").setSortable(true);

        // 2. Status badge — styled like DivisionStatus
        entriesGrid.addComponentColumn(this::createStatusBadge)
                .setHeader("Status")
                .setSortable(true)
                .setComparator((a, b) -> a.getStatus().compareTo(b.getStatus()));

        // 4. Actions column
        entriesGrid.addComponentColumn(this::createActions)
                .setHeader("Actions")
                .setWidth("140px")
                .setFlexGrow(0);

        // Make all columns resizable
        entriesGrid.getColumns().forEach(col -> col.setResizable(true));

        // Default sort by entry number
        entriesGrid.sort(List.of(new GridSortOrder<>(entryNumCol,
                com.vaadin.flow.data.provider.SortDirection.ASCENDING)));

        refreshGrid();
        return entriesGrid;
    }

    private String resolveCategoryName(UUID categoryId) {
        var cat = categoriesById.get(categoryId);
        return cat != null ? cat.getName() : "—";
    }

    private Span createStatusBadge(Entry entry) {
        var badge = new Span(entry.getStatus().getDisplayName());
        badge.getElement().getThemeList().add("badge pill small");
        badge.addClassName(entry.getStatus().getBadgeCssClass());
        return badge;
    }

    private HorizontalLayout createActions(Entry entry) {
        var actions = new HorizontalLayout();
        actions.setSpacing(false);
        actions.getStyle().set("gap", "var(--lumo-space-xs)");

        // View — always available
        var viewBtn = new Button(new Icon(VaadinIcon.EYE), e -> openViewDialog(entry));
        viewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        viewBtn.setTooltipText("View entry details");
        actions.add(viewBtn);

        var isOpen = division.getStatus() == DivisionStatus.REGISTRATION_OPEN;
        var isDraft = entry.getStatus() == EntryStatus.DRAFT;

        // Edit — only DRAFT entries when registration is open
        var editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openEntryDialog(entry));
        editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        editBtn.setTooltipText("Edit entry");
        editBtn.setEnabled(isDraft && isOpen);
        actions.add(editBtn);

        // Submit — only DRAFT entries when registration is open and meadery name not missing
        var submitBtn = new Button(new Icon(VaadinIcon.CHECK), e -> submitSingle(entry));
        submitBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        submitBtn.setEnabled(isDraft && isOpen && !meaderyNameMissing);
        submitBtn.setTooltipText(meaderyNameMissing
                ? "Meadery name required — update your profile"
                : "Submit entry");
        actions.add(submitBtn);

        // Download label — only SUBMITTED entries
        if (entry.getStatus() == EntryStatus.SUBMITTED) {
            var category = categoriesById.get(entry.getInitialCategoryId());
            var resource = new StreamResource(
                    "label-" + formatEntryId(entry) + ".pdf",
                    () -> new ByteArrayInputStream(
                            labelPdfService.generateLabel(entry, competition, division, category)));
            resource.setContentType("application/pdf");
            var downloadAnchor = new Anchor(resource, "");
            downloadAnchor.getElement().setAttribute("download", true);
            var downloadIcon = new Button(new Icon(VaadinIcon.DOWNLOAD_ALT));
            downloadIcon.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
            downloadIcon.setTooltipText("Download label");
            downloadAnchor.add(downloadIcon);
            actions.add(downloadAnchor);
        }

        return actions;
    }

    private void openViewDialog(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Entry #" + entry.getEntryNumber() + " — " + entry.getMeadName());
        dialog.setWidth("600px");

        var layout = new VerticalLayout();
        layout.setPadding(false);

        layout.add(readOnlyField("Mead Name", entry.getMeadName()));
        layout.add(readOnlyField("Category",
                resolveCategoryName(entry.getInitialCategoryId())));
        if (entry.getFinalCategoryId() != null) {
            layout.add(readOnlyField("Final Category",
                    resolveCategoryName(entry.getFinalCategoryId())));
        }
        layout.add(readOnlyField("Sweetness", entry.getSweetness().getDisplayName()));
        layout.add(readOnlyField("Strength", entry.getStrength().getDisplayName()));
        layout.add(readOnlyField("ABV", entry.getAbv() + "%"));
        layout.add(readOnlyField("Carbonation", entry.getCarbonation().getDisplayName()));
        layout.add(readOnlyField("Honey Varieties", entry.getHoneyVarieties()));
        if (StringUtils.hasText(entry.getOtherIngredients())) {
            layout.add(readOnlyField("Other Ingredients", entry.getOtherIngredients()));
        }
        layout.add(readOnlyField("Wood Aged", entry.isWoodAged() ? "Yes" : "No"));
        if (entry.isWoodAged() && StringUtils.hasText(entry.getWoodAgeingDetails())) {
            layout.add(readOnlyField("Wood Ageing Details", entry.getWoodAgeingDetails()));
        }
        if (StringUtils.hasText(entry.getAdditionalInformation())) {
            layout.add(readOnlyField("Additional Information",
                    entry.getAdditionalInformation()));
        }
        layout.add(readOnlyField("Status", entry.getStatus().getDisplayName()));

        dialog.add(layout);
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    private TextField readOnlyField(String label, String value) {
        var field = new TextField(label);
        field.setValue(value != null ? value : "");
        field.setReadOnly(true);
        field.setWidthFull();
        return field;
    }

    private void submitSingle(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Submit Entry");
        dialog.add("Submit entry #" + entry.getEntryNumber()
                + " (" + entry.getMeadName() + ")? This cannot be undone.");

        var confirmButton = new Button("Submit", e -> {
            try {
                entryService.submitEntry(entry.getId(), currentUserId);
                refreshPage();
                var notification = Notification.show("Entry submitted");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void refreshGrid() {
        entries = entryService.findEntriesByDivisionAndUser(divisionId, currentUserId);
        entriesGrid.setItems(entries);
    }

    private String formatEntryId(Entry entry) {
        var prefix = division.getEntryPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix + "-" + entry.getEntryNumber();
        }
        return String.valueOf(entry.getEntryNumber());
    }

    private void refreshPage() {
        getUI().ifPresent(ui -> ui.navigate(
                "competitions/" + compShortName
                        + "/divisions/" + divShortName + "/my-entries"));
    }

    private void openEntryDialog(Entry existing) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add Entry" : "Edit Entry");
        dialog.setWidth("600px");

        var layout = new VerticalLayout();
        layout.setPadding(false);

        var meadName = new TextField("Mead Name");
        meadName.setWidthFull();
        var categorySelect = new Select<DivisionCategory>();
        categorySelect.setLabel("Category");
        categorySelect.setWidthFull();
        categorySelect.setItemLabelGenerator(dc ->
                dc.getCode() + " — " + dc.getName());
        categorySelect.setItems(competitionService.findDivisionCategories(divisionId));

        var sweetness = new Select<Sweetness>();
        sweetness.setLabel("Sweetness");
        sweetness.setWidthFull();
        sweetness.setItems(Sweetness.values());
        sweetness.setItemLabelGenerator(Sweetness::getDisplayName);

        var strength = new Select<Strength>();
        strength.setLabel("Strength");
        strength.setWidthFull();
        strength.setItems(Strength.values());
        strength.setItemLabelGenerator(Strength::getDisplayName);

        var abv = new NumberField("ABV (%)");
        abv.setWidthFull();
        abv.setStep(0.1);
        abv.setMin(0);
        abv.setMax(30);

        var carbonation = new Select<Carbonation>();
        carbonation.setLabel("Carbonation");
        carbonation.setWidthFull();
        carbonation.setItems(Carbonation.values());
        carbonation.setItemLabelGenerator(Carbonation::getDisplayName);

        var honeyVarieties = new TextArea("Honey Varieties");
        honeyVarieties.setWidthFull();
        var otherIngredients = new TextArea("Other Ingredients");
        otherIngredients.setWidthFull();

        var woodAged = new Checkbox("Wood Aged");
        var woodAgeingDetails = new TextArea("Wood Ageing Details");
        woodAgeingDetails.setWidthFull();
        woodAgeingDetails.setVisible(false);
        woodAged.addValueChangeListener(e -> woodAgeingDetails.setVisible(e.getValue()));

        var additionalInfo = new TextArea("Additional Information");
        additionalInfo.setWidthFull();

        if (existing != null) {
            meadName.setValue(existing.getMeadName());
            var categories = competitionService.findDivisionCategories(divisionId);
            categories.stream()
                    .filter(c -> c.getId().equals(existing.getInitialCategoryId()))
                    .findFirst()
                    .ifPresent(categorySelect::setValue);
            sweetness.setValue(existing.getSweetness());
            strength.setValue(existing.getStrength());
            abv.setValue(existing.getAbv().doubleValue());
            carbonation.setValue(existing.getCarbonation());
            honeyVarieties.setValue(existing.getHoneyVarieties());
            if (existing.getOtherIngredients() != null) {
                otherIngredients.setValue(existing.getOtherIngredients());
            }
            woodAged.setValue(existing.isWoodAged());
            woodAgeingDetails.setVisible(existing.isWoodAged());
            if (existing.getWoodAgeingDetails() != null) {
                woodAgeingDetails.setValue(existing.getWoodAgeingDetails());
            }
            if (existing.getAdditionalInformation() != null) {
                additionalInfo.setValue(existing.getAdditionalInformation());
            }
        }

        layout.add(meadName, categorySelect, sweetness, strength, abv, carbonation,
                honeyVarieties, otherIngredients, woodAged, woodAgeingDetails, additionalInfo);
        dialog.add(layout);

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(meadName.getValue())) {
                meadName.setInvalid(true);
                meadName.setErrorMessage("Mead name is required");
                return;
            }
            if (categorySelect.getValue() == null || sweetness.getValue() == null
                    || strength.getValue() == null || abv.getValue() == null
                    || carbonation.getValue() == null
                    || !StringUtils.hasText(honeyVarieties.getValue())) {
                Notification.show("Please fill in all required fields");
                return;
            }
            try {
                if (existing == null) {
                    entryService.createEntry(divisionId, currentUserId,
                            meadName.getValue().trim(),
                            categorySelect.getValue().getId(),
                            sweetness.getValue(), strength.getValue(),
                            BigDecimal.valueOf(abv.getValue()),
                            carbonation.getValue(),
                            honeyVarieties.getValue().trim(),
                            StringUtils.hasText(otherIngredients.getValue())
                                    ? otherIngredients.getValue().trim() : null,
                            woodAged.getValue(),
                            woodAged.getValue() ? woodAgeingDetails.getValue().trim() : null,
                            StringUtils.hasText(additionalInfo.getValue())
                                    ? additionalInfo.getValue().trim() : null);
                } else {
                    entryService.updateEntry(existing.getId(), currentUserId,
                            meadName.getValue().trim(),
                            categorySelect.getValue().getId(),
                            sweetness.getValue(), strength.getValue(),
                            BigDecimal.valueOf(abv.getValue()),
                            carbonation.getValue(),
                            honeyVarieties.getValue().trim(),
                            StringUtils.hasText(otherIngredients.getValue())
                                    ? otherIngredients.getValue().trim() : null,
                            woodAged.getValue(),
                            woodAged.getValue() ? woodAgeingDetails.getValue().trim() : null,
                            StringUtils.hasText(additionalInfo.getValue())
                                    ? additionalInfo.getValue().trim() : null);
                }
                refreshPage();
                var notification = Notification.show(existing == null
                        ? "Entry created" : "Entry updated");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void submitAll() {
        var entries = entryService.findEntriesByDivisionAndUser(divisionId, currentUserId);
        var draftCount = entries.stream()
                .filter(en -> en.getStatus() == EntryStatus.DRAFT).count();

        var dialog = new Dialog();
        dialog.setHeaderTitle("Submit All Entries");
        dialog.add("Submit " + draftCount + " entries? This cannot be undone.");

        var confirmButton = new Button("Submit", e -> {
            try {
                entryService.submitAllDrafts(divisionId, currentUserId);
                refreshPage();
                var notification = Notification.show(draftCount + " entries submitted");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
