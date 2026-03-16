package app.meads.entry.internal;

import app.meads.BusinessRuleException;
import app.meads.LanguageMapping;
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
import com.vaadin.flow.component.Component;
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
    private java.util.Locale userLocale;

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
        } catch (BusinessRuleException e) {
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

        // Resolve user locale for date/timezone formatting
        userLocale = LanguageMapping.resolveLocale(user.getPreferredLanguage(), user.getCountry());

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
        add(createProcessInfo());
        var hasDeadline = division.getRegistrationDeadline() != null;
        var hasContact = StringUtils.hasText(competition.getContactEmail());
        if (hasDeadline || hasContact) {
            var infoRow = new HorizontalLayout();
            infoRow.setWidthFull();
            infoRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
            if (hasDeadline) {
                infoRow.add(createDeadlineInfo());
            }
            var spacer = new Div();
            infoRow.add(spacer);
            infoRow.expand(spacer);
            if (hasContact) {
                infoRow.add(createContactInfo());
            }
            add(infoRow);
        }
        var toolbar = createToolbar();
        toolbar.getStyle().set("margin-top", "var(--lumo-space-s)");
        add(toolbar);
        add(createEntriesGrid());
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        nav.add(new Anchor("my-entries", getTranslation("nav.my-entries")));
        nav.add(new Span(" / "));
        nav.add(new Span(competitionName));
        nav.add(new Span(" / "));
        nav.add(new Span(division.getName()));
        return nav;
    }

    private HorizontalLayout createHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        if (competition.hasLogo()) {
            var dataUri = "data:" + competition.getLogoContentType() + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(competition.getLogo());
            var logo = new com.vaadin.flow.component.html.Image(dataUri, competition.getName() + " logo");
            logo.setHeight("64px");
            header.add(logo);
        }

        header.add(new H2(competition.getName() + " — " + division.getName() + " — " + getTranslation("nav.my-entries")));
        return header;
    }

    private VerticalLayout createDocumentsSection() {
        var docs = competitionService.getDocuments(competition.getId());
        if (docs.isEmpty()) {
            return new VerticalLayout();
        }

        var section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);

        var header = new Span(getTranslation("entries.documents"));
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
        var userIcon = VaadinIcon.USER.create();
        userIcon.getStyle().set("width", "1em");
        userIcon.getStyle().set("height", "1em");
        userIcon.getStyle().set("vertical-align", "middle");
        warning.add(new Span(getTranslation("entries.meadery-warning.part1") + " "));
        warning.add(userIcon);
        warning.add(new Span(getTranslation("entries.meadery-warning.part2")));
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

        var creditsLabel = new Span(getTranslation("entries.credits.label"));
        creditsLabel.getStyle().set("font-weight", "600");

        var remainingBadge = new Span(getTranslation("entries.credits.remaining", remaining));
        remainingBadge.getElement().getThemeList().add("badge pill small");
        if (remaining > 0) {
            remainingBadge.getElement().getThemeList().add("success");
        }

        var detailSpan = new Span(getTranslation("entries.credits.detail", creditBalance, activeEntries));
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

            var limitsLabel = new Span(getTranslation("entries.limits.label"));
            limitsLabel.getStyle().set("font-weight", "600");
            layout.add(limitsLabel);

            if (division.getMaxEntriesPerSubcategory() != null) {
                layout.add(createLimitBadge(getTranslation("entries.limits.per-subcategory", division.getMaxEntriesPerSubcategory())));
            }
            if (division.getMaxEntriesPerMainCategory() != null) {
                layout.add(createLimitBadge(getTranslation("entries.limits.per-main-category", division.getMaxEntriesPerMainCategory())));
            }
            if (division.getMaxEntriesTotal() != null) {
                layout.add(createLimitBadge(getTranslation("entries.limits.total", division.getMaxEntriesTotal())));
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
        nameField.setPlaceholder(getTranslation("entries.filter.placeholder"));
        nameField.setWidth("300px");
        nameField.setClearButtonVisible(true);
        nameField.setValueChangeMode(ValueChangeMode.EAGER);
        nameField.addValueChangeListener(e -> {
            nameFilter = e.getValue();
            applyFilters();
        });

        var statusSelect = new Select<EntryStatus>();
        statusSelect.setPlaceholder(getTranslation("entries.filter.all-statuses"));
        statusSelect.setItems(EntryStatus.values());
        statusSelect.setItemLabelGenerator(s -> s != null
                ? getTranslation("entry.status." + s.name())
                : getTranslation("entries.filter.all-statuses"));
        statusSelect.setEmptySelectionAllowed(true);
        statusSelect.addValueChangeListener(e -> {
            statusFilter = e.getValue();
            applyFilters();
        });

        // Action buttons (right)
        var creditBalance = entryService.getCreditBalance(divisionId, currentUserId);
        var activeEntries = entryService.countActiveEntries(divisionId, currentUserId);
        var isOpen = division.getStatus() == DivisionStatus.REGISTRATION_OPEN;

        var addButton = new Button(getTranslation("entries.add"), e -> openEntryDialog(null));
        addButton.setEnabled(isOpen && creditBalance > activeEntries);

        var entries = entryService.findEntriesByDivisionAndUser(divisionId, currentUserId);
        var hasDrafts = entries.stream().anyMatch(en -> en.getStatus() == EntryStatus.DRAFT);

        var submitButton = new Button(getTranslation("entries.submit-all"), e -> submitAll());
        submitButton.setEnabled(hasDrafts && !meaderyNameMissing);
        if (meaderyNameMissing) {
            submitButton.setTooltipText(getTranslation("entries.meadery-required.tooltip"));
        }

        // Download all labels (SUBMITTED entries) — only enabled when all entries are submitted
        var downloadAllBtn = new Button(getTranslation("entries.download-all"), new Icon(VaadinIcon.DOWNLOAD_ALT));
        Component downloadAllComponent;
        var hasSubmitted = entries.stream().anyMatch(en -> en.getStatus() == EntryStatus.SUBMITTED);
        if (hasDrafts || !hasSubmitted) {
            downloadAllBtn.setEnabled(false);
            if (hasDrafts) {
                downloadAllBtn.setTooltipText(getTranslation("entries.download-all.disabled"));
            }
            downloadAllComponent = downloadAllBtn;
        } else {
            var downloadAllResource = new StreamResource("all-labels.pdf", () -> {
                var submittedEntries = entries != null
                        ? entries.stream().filter(e2 -> e2.getStatus() == EntryStatus.SUBMITTED).toList()
                        : List.<Entry>of();
                if (submittedEntries.isEmpty()) {
                    return new ByteArrayInputStream(new byte[0]);
                }
                return new ByteArrayInputStream(
                        labelPdfService.generateLabels(submittedEntries, competition, division, categoriesById::get, userLocale));
            });
            downloadAllResource.setContentType("application/pdf");
            var downloadAllAnchor = new Anchor(downloadAllResource, "");
            downloadAllAnchor.getElement().setAttribute("download", true);
            downloadAllAnchor.add(downloadAllBtn);
            downloadAllComponent = downloadAllAnchor;
        }

        // Spacer pushes buttons to the right
        var spacer = new Div();
        toolbar.add(nameField, statusSelect, spacer, addButton, submitButton, downloadAllComponent);
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
                .setHeader(getTranslation("entries.column.entry-number"))
                .setSortable(true)
                .setComparator((a, b) -> Integer.compare(a.getEntryNumber(), b.getEntryNumber()))
                .setWidth("130px")
                .setFlexGrow(0);

        // Mead Name
        entriesGrid.addComponentColumn(entry -> {
            var span = new Span(entry.getMeadName());
            span.setTitle(entry.getMeadName());
            return span;
        }).setHeader(getTranslation("entries.column.mead-name")).setSortable(true)
                .setComparator((a, b) -> a.getMeadName().compareToIgnoreCase(b.getMeadName()));

        // Category (initial) — show code with tooltip
        entriesGrid.addComponentColumn(entry -> createCategorySpan(entry.getInitialCategoryId()))
                .setHeader(getTranslation("entries.column.category"))
                .setSortable(true)
                .setComparator((a, b) -> resolveCategoryCode(a.getInitialCategoryId())
                        .compareTo(resolveCategoryCode(b.getInitialCategoryId())));

        // Final Category — show code with tooltip
        entriesGrid.addComponentColumn(entry -> {
            if (entry.getFinalCategoryId() == null) {
                return new Span(getTranslation("entries.column.final-category.none"));
            }
            return createCategorySpan(entry.getFinalCategoryId());
        }).setHeader(getTranslation("entries.column.final-category")).setSortable(true);

        // 2. Status badge — styled like DivisionStatus
        entriesGrid.addComponentColumn(this::createStatusBadge)
                .setHeader(getTranslation("entries.column.status"))
                .setSortable(true)
                .setComparator((a, b) -> a.getStatus().compareTo(b.getStatus()));

        // 4. Actions column
        entriesGrid.addComponentColumn(this::createActions)
                .setHeader(getTranslation("entries.column.actions"))
                .setWidth("130px")
                .setFlexGrow(0);

        // Make all columns resizable
        entriesGrid.getColumns().forEach(col -> col.setResizable(true));

        // Default sort by entry number
        entriesGrid.sort(List.of(new GridSortOrder<>(entryNumCol,
                com.vaadin.flow.data.provider.SortDirection.ASCENDING)));

        refreshGrid();
        return entriesGrid;
    }

    private String resolveCategoryCode(UUID categoryId) {
        var cat = categoriesById.get(categoryId);
        return cat != null ? cat.getCode() : "—";
    }

    private String translateCategoryName(DivisionCategory cat) {
        var key = "category." + cat.getCode() + ".name";
        var translated = getTranslation(key);
        return translated.equals(key) ? cat.getName() : translated;
    }

    private String resolveCategoryCodeAndName(UUID categoryId) {
        var cat = categoriesById.get(categoryId);
        return cat != null ? cat.getCode() + " — " + translateCategoryName(cat) : "—";
    }

    private Span createCategorySpan(UUID categoryId) {
        var cat = categoriesById.get(categoryId);
        if (cat == null) return new Span("—");
        var span = new Span(cat.getCode());
        span.setTitle(translateCategoryName(cat));
        return span;
    }

    private Span createStatusBadge(Entry entry) {
        var badge = new Span(getTranslation("entry.status." + entry.getStatus().name()));
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
        viewBtn.setTooltipText(getTranslation("entries.action.view"));
        actions.add(viewBtn);

        var isOpen = division.getStatus() == DivisionStatus.REGISTRATION_OPEN;
        var isDraft = entry.getStatus() == EntryStatus.DRAFT;

        // Edit — only DRAFT entries when registration is open
        var editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openEntryDialog(entry));
        editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        editBtn.setTooltipText(getTranslation("entries.action.edit"));
        editBtn.setEnabled(isDraft && isOpen);
        actions.add(editBtn);

        // Submit — only DRAFT entries when registration is open and meadery name not missing
        var submitBtn = new Button(new Icon(VaadinIcon.CHECK), e -> submitSingle(entry));
        submitBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        submitBtn.setEnabled(isDraft && isOpen && !meaderyNameMissing);
        submitBtn.setTooltipText(meaderyNameMissing
                ? getTranslation("entries.meadery-required.tooltip")
                : getTranslation("entries.action.submit"));
        actions.add(submitBtn);

        // Download label — only SUBMITTED entries
        if (entry.getStatus() == EntryStatus.SUBMITTED) {
            var category = categoriesById.get(entry.getInitialCategoryId());
            var resource = new StreamResource(
                    "label-" + formatEntryId(entry) + ".pdf",
                    () -> new ByteArrayInputStream(
                            labelPdfService.generateLabel(entry, competition, division, category, userLocale)));
            resource.setContentType("application/pdf");
            var downloadAnchor = new Anchor(resource, "");
            downloadAnchor.getElement().setAttribute("download", true);
            var downloadIcon = new Button(new Icon(VaadinIcon.DOWNLOAD_ALT));
            downloadIcon.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
            downloadIcon.setTooltipText(getTranslation("entries.action.download"));
            downloadAnchor.add(downloadIcon);
            actions.add(downloadAnchor);
        }

        return actions;
    }

    private void openViewDialog(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entries.view.title", formatEntryId(entry), entry.getMeadName()));
        dialog.setWidth("600px");

        var layout = new VerticalLayout();
        layout.setPadding(false);

        layout.add(readOnlyField(getTranslation("entries.view.mead-name"), entry.getMeadName()));
        layout.add(readOnlyField(getTranslation("entries.view.category"),
                resolveCategoryCodeAndName(entry.getInitialCategoryId())));
        if (entry.getFinalCategoryId() != null) {
            layout.add(readOnlyField(getTranslation("entries.view.final-category"),
                    resolveCategoryCodeAndName(entry.getFinalCategoryId())));
        }
        layout.add(readOnlyField(getTranslation("entries.view.sweetness"),
                getTranslation("entry.sweetness." + entry.getSweetness().name())));
        layout.add(readOnlyField(getTranslation("entries.view.strength"),
                getTranslation("entry.strength." + entry.getStrength().name())));
        layout.add(readOnlyField(getTranslation("entries.view.abv"), entry.getAbv() + "%"));
        layout.add(readOnlyField(getTranslation("entries.view.carbonation"),
                getTranslation("entry.carbonation." + entry.getCarbonation().name())));
        layout.add(readOnlyField(getTranslation("entries.view.honey"), entry.getHoneyVarieties()));
        if (StringUtils.hasText(entry.getOtherIngredients())) {
            layout.add(readOnlyField(getTranslation("entries.view.other-ingredients"), entry.getOtherIngredients()));
        }
        layout.add(readOnlyField(getTranslation("entries.view.wood-aged"),
                entry.isWoodAged() ? getTranslation("entries.view.wood-aged.yes") : getTranslation("entries.view.wood-aged.no")));
        if (entry.isWoodAged() && StringUtils.hasText(entry.getWoodAgeingDetails())) {
            layout.add(readOnlyField(getTranslation("entries.view.wood-details"), entry.getWoodAgeingDetails()));
        }
        if (StringUtils.hasText(entry.getAdditionalInformation())) {
            layout.add(readOnlyField(getTranslation("entries.view.additional-info"),
                    entry.getAdditionalInformation()));
        }
        layout.add(readOnlyField(getTranslation("entries.view.status"),
                getTranslation("entry.status." + entry.getStatus().name())));

        dialog.add(layout);
        dialog.getFooter().add(new Button(getTranslation("entries.view.close"), e -> dialog.close()));
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
        dialog.setHeaderTitle(getTranslation("entries.submit.title"));
        dialog.add(getTranslation("entries.submit.confirm", formatEntryId(entry), entry.getMeadName()));

        var confirmButton = new Button(getTranslation("entries.submit.button"), e -> {
            e.getSource().setEnabled(false);
            try {
                entryService.submitEntry(entry.getId(), currentUserId);
                refreshPage();
                var notification = Notification.show(getTranslation("entries.submit.success"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage());
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });

        var cancelButton = new Button(getTranslation("entries.submit.cancel"), e -> dialog.close());
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
        dialog.setHeaderTitle(existing == null
                ? getTranslation("entries.dialog.title.add")
                : getTranslation("entries.dialog.title.edit"));
        dialog.setWidth("600px");

        var layout = new VerticalLayout();
        layout.setPadding(false);

        var meadName = new TextField(getTranslation("entries.dialog.mead-name"));
        meadName.setWidthFull();
        meadName.setMaxLength(255);
        var categorySelect = new Select<DivisionCategory>();
        categorySelect.setLabel(getTranslation("entries.dialog.category"));
        categorySelect.setWidthFull();
        categorySelect.setItemLabelGenerator(dc ->
                dc.getCode() + " — " + translateCategoryName(dc));
        categorySelect.setItems(competitionService.findDivisionCategories(divisionId).stream()
                .filter(dc -> dc.getParentId() != null)
                .toList());

        var categoryHint = new Span();
        categoryHint.setId("category-hint");
        categoryHint.setVisible(false);
        categoryHint.setWidthFull();
        categoryHint.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        categorySelect.addValueChangeListener(e -> {
            var selected = e.getValue();
            if (selected != null) {
                var hintKey = "category.hint." + selected.getCode();
                var hint = getTranslation(hintKey);
                if (!hint.equals(hintKey)) {
                    categoryHint.setText(hint);
                    categoryHint.setVisible(true);
                } else {
                    categoryHint.setVisible(false);
                }
            } else {
                categoryHint.setVisible(false);
            }
        });

        var sweetness = new Select<Sweetness>();
        sweetness.setLabel(getTranslation("entries.dialog.sweetness"));
        sweetness.setWidthFull();
        sweetness.setItems(Sweetness.values());
        sweetness.setItemLabelGenerator(s -> getTranslation("entry.sweetness." + s.name()));

        var strength = new Select<Strength>();
        strength.setLabel(getTranslation("entries.dialog.strength"));
        strength.setWidthFull();
        strength.setItems(Strength.values());
        strength.setItemLabelGenerator(s -> getTranslation("entry.strength." + s.name()));

        var abv = new NumberField(getTranslation("entries.dialog.abv"));
        abv.setWidthFull();
        abv.setStep(0.1);
        abv.setMin(0);
        abv.setMax(30);

        var carbonation = new Select<Carbonation>();
        carbonation.setLabel(getTranslation("entries.dialog.carbonation"));
        carbonation.setWidthFull();
        carbonation.setItems(Carbonation.values());
        carbonation.setItemLabelGenerator(c -> getTranslation("entry.carbonation." + c.name()));

        var honeyVarieties = new TextArea(getTranslation("entries.dialog.honey"));
        honeyVarieties.setWidthFull();
        honeyVarieties.setMaxLength(500);
        var otherIngredients = new TextArea(getTranslation("entries.dialog.other-ingredients"));
        otherIngredients.setWidthFull();
        otherIngredients.setMaxLength(500);

        var woodAged = new Checkbox(getTranslation("entries.dialog.wood-aged"));
        var woodAgeingDetails = new TextArea(getTranslation("entries.dialog.wood-details"));
        woodAgeingDetails.setWidthFull();
        woodAgeingDetails.setMaxLength(500);
        woodAgeingDetails.setVisible(false);
        woodAged.addValueChangeListener(e -> woodAgeingDetails.setVisible(e.getValue()));

        var additionalInfo = new TextArea(getTranslation("entries.dialog.additional-info"));
        additionalInfo.setWidthFull();
        additionalInfo.setMaxLength(1000);

        if (existing != null) {
            meadName.setValue(existing.getMeadName());
            categorySelect.getListDataView().getItems()
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

        layout.add(meadName, categorySelect, categoryHint, sweetness, strength, abv, carbonation,
                honeyVarieties, otherIngredients, woodAged, woodAgeingDetails, additionalInfo);
        dialog.add(layout);

        var saveButton = new Button(getTranslation("entries.dialog.save"), e -> {
            e.getSource().setEnabled(false);
            if (!StringUtils.hasText(meadName.getValue())) {
                meadName.setInvalid(true);
                meadName.setErrorMessage(getTranslation("entries.validation.mead-name-required"));
                e.getSource().setEnabled(true);
                return;
            }
            var valid = true;
            if (categorySelect.getValue() == null) {
                categorySelect.setInvalid(true);
                categorySelect.setErrorMessage(getTranslation("entries.validation.category-required"));
                valid = false;
            }
            if (sweetness.getValue() == null) {
                sweetness.setInvalid(true);
                sweetness.setErrorMessage(getTranslation("entries.validation.sweetness-required"));
                valid = false;
            }
            if (strength.getValue() == null) {
                strength.setInvalid(true);
                strength.setErrorMessage(getTranslation("entries.validation.strength-required"));
                valid = false;
            }
            if (abv.getValue() == null) {
                abv.setInvalid(true);
                abv.setErrorMessage(getTranslation("entries.validation.abv-required"));
                valid = false;
            }
            if (carbonation.getValue() == null) {
                carbonation.setInvalid(true);
                carbonation.setErrorMessage(getTranslation("entries.validation.carbonation-required"));
                valid = false;
            }
            if (!StringUtils.hasText(honeyVarieties.getValue())) {
                honeyVarieties.setInvalid(true);
                honeyVarieties.setErrorMessage(getTranslation("entries.validation.honey-required"));
                valid = false;
            }
            if (!valid) {
                e.getSource().setEnabled(true);
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
                        ? getTranslation("entries.dialog.created") : getTranslation("entries.dialog.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage());
                e.getSource().setEnabled(true);
            }
        });

        var cancelButton = new Button(getTranslation("entries.dialog.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void submitAll() {
        var entries = entryService.findEntriesByDivisionAndUser(divisionId, currentUserId);
        var draftCount = entries.stream()
                .filter(en -> en.getStatus() == EntryStatus.DRAFT).count();

        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entries.submit-all.title"));
        dialog.add(getTranslation("entries.submit-all.confirm", draftCount));

        var confirmButton = new Button(getTranslation("entries.submit-all.button"), e -> {
            e.getSource().setEnabled(false);
            try {
                entryService.submitAllDrafts(divisionId, currentUserId);
                refreshPage();
                var notification = Notification.show(getTranslation("entries.submit-all.success", draftCount));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage());
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });

        var cancelButton = new Button(getTranslation("entries.submit-all.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private Span createContactInfo() {
        var label = new Span(getTranslation("entries.contact") + " ");
        var emailLink = new Anchor("mailto:" + competition.getContactEmail(),
                competition.getContactEmail());
        var contactSpan = new Span(label, emailLink);
        contactSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        return contactSpan;
    }

    private Span createDeadlineInfo() {
        var formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", userLocale);
        var formatted = division.getRegistrationDeadline().format(formatter);
        var zoneId = java.time.ZoneId.of(division.getRegistrationDeadlineTimezone());
        var zoneName = zoneId.getDisplayName(java.time.format.TextStyle.FULL, userLocale);
        var deadlineSpan = new Span(getTranslation("entries.deadline") + " " + formatted
                + " " + zoneName);
        deadlineSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        return deadlineSpan;
    }

    private Div createProcessInfo() {
        var info = new Div();
        info.getStyle()
                .set("background-color", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-body-text-color)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("line-height", "var(--lumo-line-height-m)");
        info.add(new Span(getTranslation("entries.process-info")));
        return info;
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
