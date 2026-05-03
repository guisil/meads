package app.meads.entry.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
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
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "competitions/:compShortName/divisions/:divShortName/entry-admin", layout = MainLayout.class)
@PermitAll
public class DivisionEntryAdminView extends VerticalLayout implements BeforeEnterObserver {

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

    private Grid<EntrantCreditSummary> creditsGrid;
    private Grid<Entry> entriesGrid;
    private Grid<ProductMapping> productsGrid;
    private Grid<JumpsellerOrder> ordersGrid;
    private Map<UUID, List<JumpsellerOrderLineItem>> lineItemsByOrderId;
    private List<DivisionCategory> divisionCategories;

    // Entries tab filter state
    private String entriesNameFilter = "";
    private EntryStatus entriesStatusFilter;

    // Entries tab summary
    private Span totalCreditsLabel;
    private Span entriesSummaryLabel;

    public DivisionEntryAdminView(EntryService entryService,
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

        // Authorization: SYSTEM_ADMIN or ADMIN for this competition
        var user = userService.findById(currentUserId);
        if (user.getRole() != Role.SYSTEM_ADMIN
                && !competitionService.isAuthorizedForDivision(divisionId, currentUserId)) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        if (user.getRole() != Role.SYSTEM_ADMIN && !userService.hasPassword(currentUserId)) {
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
                isSystemAdmin ? getTranslation("nav.competitions") : getTranslation("nav.my-competitions"));
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
        nav.add(new Span(getTranslation("entry-admin.nav.entry-admin")));
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

        header.add(new H2(competition.getName() + " — " + division.getName() + " — " + getTranslation("entry-admin.nav.entry-admin")));
        return header;
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();

        tabSheet.add(getTranslation("entry-admin.tab.credits"), createCreditsTab());
        tabSheet.add(getTranslation("entry-admin.tab.entries"), createEntriesTab());
        tabSheet.add(getTranslation("entry-admin.tab.products"), createProductsTab());
        tabSheet.add(getTranslation("entry-admin.tab.orders"), createOrdersTab());

        return tabSheet;
    }

    // --- Credits Tab ---

    private VerticalLayout createCreditsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var filterField = new TextField();
        filterField.setPlaceholder(getTranslation("entry-admin.credits.filter.placeholder"));
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        boolean registrationOpen = division.getStatus().allowsRegistrationActions();
        var addCreditsButton = new Button(getTranslation("entry-admin.credits.add"), e -> openAddCreditsDialog());
        addCreditsButton.setEnabled(registrationOpen);
        com.vaadin.flow.component.Component addCreditsComponent;
        if (!registrationOpen) {
            var wrapper = new Span(addCreditsButton);
            wrapper.getStyle().set("display", "inline-block");
            Tooltip.forComponent(wrapper).setText(getTranslation("entry-admin.registration-closed.tooltip"));
            addCreditsComponent = wrapper;
        } else {
            addCreditsComponent = addCreditsButton;
        }

        var toolbar = new HorizontalLayout(filterField, addCreditsComponent);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        tab.add(toolbar);

        creditsGrid = new Grid<>(EntrantCreditSummary.class, false);
        creditsGrid.setAllRowsVisible(true);
        creditsGrid.setId("credits-grid");
        creditsGrid.addColumn(EntrantCreditSummary::name).setHeader(getTranslation("entry-admin.credits.column.name")).setSortable(true).setFlexGrow(2);
        creditsGrid.addColumn(EntrantCreditSummary::email).setHeader(getTranslation("entry-admin.credits.column.email")).setSortable(true).setFlexGrow(3);
        creditsGrid.addColumn(EntrantCreditSummary::creditBalance).setHeader(getTranslation("entry-admin.credits.column.credits")).setSortable(true).setAutoWidth(true);
        creditsGrid.addColumn(EntrantCreditSummary::entryCount).setHeader(getTranslation("entry-admin.credits.column.entries")).setSortable(true).setAutoWidth(true);
        creditsGrid.addComponentColumn(summary -> {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setEnabled(registrationOpen);
            editButton.setAriaLabel(registrationOpen
                    ? getTranslation("entry-admin.credits.action.adjust.tooltip")
                    : getTranslation("entry-admin.registration-closed.tooltip"));
            if (registrationOpen) {
                editButton.setTooltipText(getTranslation("entry-admin.credits.action.adjust.tooltip"));
                editButton.addClickListener(e -> openEditCreditsDialog(summary));
                return editButton;
            }
            var wrapper = new Span(editButton);
            wrapper.getStyle().set("display", "inline-block");
            Tooltip.forComponent(wrapper).setText(getTranslation("entry-admin.registration-closed.tooltip"));
            return wrapper;
        }).setHeader(getTranslation("entry-admin.credits.column.actions")).setAutoWidth(true);

        creditsGrid.getColumns().forEach(col -> col.setResizable(true));

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
        dialog.setHeaderTitle(getTranslation("entry-admin.credits.add.title"));

        var emailField = new TextField(getTranslation("entry-admin.credits.add.email"));
        emailField.setMaxLength(255);
        var amountField = new IntegerField(getTranslation("entry-admin.credits.add.amount"));
        amountField.setMin(1);
        amountField.setValue(1);

        dialog.add(new VerticalLayout(emailField, amountField));

        var addButton = new Button(getTranslation("entry-admin.credits.add.button"), e -> {
            var valid = true;
            if (!StringUtils.hasText(emailField.getValue())) {
                emailField.setInvalid(true);
                emailField.setErrorMessage(getTranslation("entry-admin.credits.add.email.error"));
                valid = false;
            }
            if (amountField.getValue() == null) {
                amountField.setInvalid(true);
                amountField.setErrorMessage(getTranslation("entry-admin.credits.add.amount.error"));
                valid = false;
            }
            if (!valid) {
                e.getSource().setEnabled(true);
                return;
            }
            try {
                entryService.addCredits(divisionId, emailField.getValue().trim(),
                        amountField.getValue(), currentUserId);
                var notification = Notification.show(getTranslation("entry-admin.credits.added"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshCreditsGrid();
                refreshCreditsBalance();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            } catch (jakarta.validation.ConstraintViolationException ex) {
                emailField.setInvalid(true);
                emailField.setErrorMessage(getTranslation("entry-admin.credits.add.email.invalid"));
                e.getSource().setEnabled(true);
            }
        });
        addButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void openEditCreditsDialog(EntrantCreditSummary summary) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.credits.adjust.title", summary.name()));

        var amountField = new IntegerField(getTranslation("entry-admin.credits.adjust.amount"));
        amountField.setValue(1);
        amountField.setHelperText(getTranslation("entry-admin.credits.adjust.helper", summary.creditBalance()));

        dialog.add(new VerticalLayout(amountField));

        var saveButton = new Button("Save", e -> {
            if (amountField.getValue() == null || amountField.getValue() == 0) {
                e.getSource().setEnabled(true);
                return;
            }
            try {
                var amount = amountField.getValue();
                if (amount > 0) {
                    entryService.addCredits(divisionId, summary.email(),
                            amount, currentUserId);
                } else {
                    entryService.removeCredits(divisionId, summary.userId(),
                            -amount, currentUserId);
                }
                var notification = Notification.show(getTranslation("entry-admin.credits.adjusted"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshCreditsGrid();
                refreshCreditsBalance();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    // --- Entries Tab ---

    private VerticalLayout createEntriesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var filterField = new TextField();
        filterField.setPlaceholder(getTranslation("entry-admin.entries.filter.placeholder"));
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        var downloadAllBtn = new Button(getTranslation("entry-admin.entries.download-all"), new Icon(VaadinIcon.DOWNLOAD_ALT));
        downloadAllBtn.addClickListener(e -> {
            var allEntries = entryService.findEntriesByDivision(divisionId);
            var qualifyingEntries = allEntries.stream()
                    .filter(entry -> entry.getStatus() == EntryStatus.SUBMITTED
                            || entry.getStatus() == EntryStatus.RECEIVED)
                    .toList();
            if (qualifyingEntries.isEmpty()) {
                Notification.show(getTranslation("entry-admin.entries.download-all.empty"));
                return;
            }
            var dialog = new Dialog();
            dialog.setHeaderTitle(getTranslation("entry-admin.entries.download-all.confirm.title"));
            dialog.add(new Span(getTranslation("entry-admin.entries.download-all.confirm.body", qualifyingEntries.size())));

            var resource = new StreamResource("all-labels.pdf", () -> {
                java.util.function.Function<UUID, DivisionCategory> resolver = id ->
                        divisionCategories.stream()
                                .filter(c -> c.getId().equals(id)).findFirst().orElse(null);
                return new ByteArrayInputStream(
                        labelPdfService.generateLabels(qualifyingEntries, competition,
                                division, resolver));
            });
            resource.setContentType("application/pdf");
            var downloadAnchor = new Anchor(resource, getTranslation("entry-admin.entries.download-all.anchor"));
            downloadAnchor.getElement().setAttribute("download", true);
            downloadAnchor.getElement().addEventListener("click", ev -> dialog.close());

            var cancelBtn = new Button("Cancel", ev -> dialog.close());
            dialog.getFooter().add(cancelBtn, downloadAnchor);
            dialog.open();
        });

        var statusSelect = new Select<EntryStatus>();
        statusSelect.setPlaceholder(getTranslation("entry-admin.entries.status.all"));
        statusSelect.setItems(EntryStatus.values());
        statusSelect.setItemLabelGenerator(s -> s != null ? s.getDisplayName() : getTranslation("entry-admin.entries.status.all"));
        statusSelect.setEmptySelectionAllowed(true);
        statusSelect.addValueChangeListener(e -> {
            entriesStatusFilter = e.getValue();
            applyEntriesFilters();
        });

        var addEntryButton = new Button(getTranslation("entry-admin.entries.add"), e -> openAdminAddEntryConfirmDialog());

        var toolbar = new HorizontalLayout(filterField, statusSelect, addEntryButton, downloadAllBtn);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
        tab.add(toolbar);

        totalCreditsLabel = new Span();
        totalCreditsLabel.setId("credits-balance-label");
        entriesSummaryLabel = new Span();
        entriesSummaryLabel.setId("entries-summary-label");
        var separator = new Span("|");
        separator.getStyle().set("color", "var(--lumo-contrast-30pct)").set("padding", "0 var(--lumo-space-m)");
        var summary = new HorizontalLayout(totalCreditsLabel, separator, entriesSummaryLabel);
        summary.setSpacing(false);
        summary.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        tab.add(summary);

        entriesGrid = new Grid<>(Entry.class, false);
        entriesGrid.setAllRowsVisible(true);
        entriesGrid.setId("entries-grid");
        entriesGrid.addColumn(entry -> formatEntryNumber(entry.getEntryNumber()))
                .setHeader(getTranslation("entry-admin.entries.column.number")).setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(Entry::getEntryCode).setHeader(getTranslation("entry-admin.entries.column.code")).setSortable(true).setAutoWidth(true);
        entriesGrid.addComponentColumn(entry -> {
            var span = new Span(entry.getMeadName());
            span.setTitle(entry.getMeadName());
            return span;
        }).setHeader(getTranslation("entry-admin.entries.column.mead-name")).setSortable(true).setFlexGrow(2)
                .setComparator((a, b) -> a.getMeadName().compareToIgnoreCase(b.getMeadName()));
        entriesGrid.addComponentColumn(entry -> createCategorySpan(entry.getInitialCategoryId()))
                .setHeader(getTranslation("entry-admin.entries.column.category")).setSortable(true)
                .setComparator((a, b) -> resolveCategoryCode(a.getInitialCategoryId())
                        .compareTo(resolveCategoryCode(b.getInitialCategoryId())));
        entriesGrid.addComponentColumn(entry -> {
            if (entry.getFinalCategoryId() == null) {
                return new Span("—");
            }
            return createCategorySpan(entry.getFinalCategoryId());
        }).setHeader(getTranslation("entry-admin.entries.column.final-category")).setSortable(true);
        entriesGrid.addColumn(entry -> userService.findById(entry.getUserId()).getEmail())
                .setHeader(getTranslation("entry-admin.entries.column.entrant")).setSortable(true).setFlexGrow(2);
        entriesGrid.addColumn(entry -> {
            var user = userService.findById(entry.getUserId());
            return user.getMeaderyName() != null ? user.getMeaderyName() : "";
        }).setHeader(getTranslation("entry-admin.entries.column.meadery")).setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(entry -> {
            var user = userService.findById(entry.getUserId());
            return user.getCountry() != null
                    ? new Locale("", user.getCountry()).getDisplayCountry(Locale.ENGLISH)
                    : "";
        }).setHeader(getTranslation("entry-admin.entries.column.country")).setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(entry -> entry.getStatus().name())
                .setHeader(getTranslation("entry-admin.entries.column.status")).setSortable(true).setAutoWidth(true);
        entriesGrid.addComponentColumn(entry -> {
            var viewButton = new Button(new Icon(VaadinIcon.EYE));
            viewButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            viewButton.setAriaLabel(getTranslation("entry-admin.entries.action.view.tooltip"));
            viewButton.setTooltipText(getTranslation("entry-admin.entries.action.view.tooltip"));
            viewButton.addClickListener(e -> openViewEntryDialog(entry));

            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setAriaLabel(getTranslation("entry-admin.entries.action.edit.tooltip"));
            editButton.setTooltipText(getTranslation("entry-admin.entries.action.edit.tooltip"));
            editButton.setEnabled(entry.getStatus() != EntryStatus.WITHDRAWN);
            editButton.addClickListener(e -> openEditEntryDialog(entry));

            var revertButton = new Button(new Icon(VaadinIcon.ARROW_LEFT));
            revertButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            revertButton.setAriaLabel(getTranslation("entry-admin.entries.action.revert.tooltip"));
            revertButton.setEnabled(entry.getStatus() != EntryStatus.DRAFT);
            revertButton.setTooltipText(switch (entry.getStatus()) {
                case SUBMITTED, WITHDRAWN -> getTranslation("entry-admin.entries.revert.tooltip.to-draft");
                case RECEIVED -> getTranslation("entry-admin.entries.revert.tooltip.to-submitted");
                case DRAFT -> getTranslation("entry-admin.entries.revert.tooltip.default");
            });
            revertButton.addClickListener(e -> openRevertStatusDialog(entry));

            var advanceButton = new Button(new Icon(VaadinIcon.ARROW_RIGHT));
            advanceButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            advanceButton.setAriaLabel(getTranslation("entry-admin.entries.action.advance.tooltip"));
            advanceButton.setEnabled(entry.getStatus() == EntryStatus.DRAFT
                    || entry.getStatus() == EntryStatus.SUBMITTED);
            advanceButton.setTooltipText(switch (entry.getStatus()) {
                case DRAFT -> getTranslation("entry-admin.entries.advance.tooltip.submit");
                case SUBMITTED -> getTranslation("entry-admin.entries.advance.tooltip.received");
                case RECEIVED, WITHDRAWN -> getTranslation("entry-admin.entries.advance.tooltip.default");
            });
            advanceButton.addClickListener(e -> openAdvanceStatusDialog(entry));

            var withdrawButton = new Button(new Icon(VaadinIcon.BAN));
            withdrawButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            withdrawButton.setAriaLabel(getTranslation("entry-admin.entries.action.withdraw.tooltip"));
            withdrawButton.setTooltipText(getTranslation("entry-admin.entries.action.withdraw.tooltip"));
            withdrawButton.setEnabled(entry.getStatus() != EntryStatus.WITHDRAWN);
            withdrawButton.addClickListener(e -> openWithdrawEntryDialog(entry));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setAriaLabel(getTranslation("entry-admin.entries.action.delete.tooltip"));
            deleteButton.setTooltipText(getTranslation("entry-admin.entries.action.delete.tooltip"));
            deleteButton.setEnabled(entry.getStatus() == EntryStatus.DRAFT);
            deleteButton.addClickListener(e -> openDeleteEntryDialog(entry));

            var actions = new HorizontalLayout(viewButton, editButton, revertButton, advanceButton, withdrawButton, deleteButton);

            if (entry.getStatus() == EntryStatus.SUBMITTED || entry.getStatus() == EntryStatus.RECEIVED) {
                var category = getCategoryById(entry.getInitialCategoryId());
                var resource = new StreamResource(
                        "label-" + formatEntryNumber(entry.getEntryNumber()) + ".pdf",
                        () -> new ByteArrayInputStream(
                                labelPdfService.generateLabel(entry, competition, division, category)));
                resource.setContentType("application/pdf");
                var downloadAnchor = new Anchor(resource, "");
                downloadAnchor.getElement().setAttribute("download", true);
                var downloadBtn = new Button(new Icon(VaadinIcon.DOWNLOAD_ALT));
                downloadBtn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                downloadBtn.setAriaLabel(getTranslation("entry-admin.entries.action.download.tooltip"));
                downloadBtn.setTooltipText(getTranslation("entry-admin.entries.action.download.tooltip"));
                downloadAnchor.add(downloadBtn);
                actions.add(downloadAnchor);
            }

            return actions;
        }).setHeader(getTranslation("entry-admin.entries.column.actions")).setAutoWidth(true);

        entriesGrid.getColumns().forEach(col -> col.setResizable(true));

        refreshEntriesGrid();

        filterField.addValueChangeListener(e -> {
            entriesNameFilter = e.getValue();
            applyEntriesFilters();
        });

        tab.add(entriesGrid);
        return tab;
    }

    private void applyEntriesFilters() {
        entriesGrid.getListDataView().setFilter(entry -> {
            if (entriesStatusFilter != null && entry.getStatus() != entriesStatusFilter) {
                return false;
            }
            if (entriesNameFilter != null && !entriesNameFilter.isBlank()) {
                var filterString = entriesNameFilter.toLowerCase();
                return entry.getMeadName().toLowerCase().contains(filterString)
                        || entry.getEntryCode().toLowerCase().contains(filterString)
                        || userService.findById(entry.getUserId()).getEmail()
                                .toLowerCase().contains(filterString);
            }
            return true;
        });
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

    private String resolveCategoryCode(UUID categoryId) {
        return divisionCategories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .map(DivisionCategory::getCode)
                .findFirst()
                .orElse("—");
    }

    private Span createCategorySpan(UUID categoryId) {
        var cat = divisionCategories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElse(null);
        if (cat == null) return new Span("—");
        var span = new Span(cat.getCode());
        span.setTitle(cat.getName());
        return span;
    }

    private DivisionCategory getCategoryById(UUID categoryId) {
        return divisionCategories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElse(null);
    }

    private void refreshEntriesGrid() {
        var entries = entryService.findEntriesByDivision(divisionId).stream()
                .sorted(Comparator.comparingInt(Entry::getEntryNumber))
                .toList();
        entriesGrid.setItems(entries);
        updateEntriesSummary(entries);
    }

    private void updateEntriesSummary(List<Entry> entries) {
        int creditsBalance = entryService.getTotalCreditBalance(divisionId);
        var countByStatus = entries.stream().collect(Collectors.groupingBy(Entry::getStatus, Collectors.counting()));
        long draft = countByStatus.getOrDefault(EntryStatus.DRAFT, 0L);
        long submitted = countByStatus.getOrDefault(EntryStatus.SUBMITTED, 0L);
        long received = countByStatus.getOrDefault(EntryStatus.RECEIVED, 0L);
        long withdrawn = countByStatus.getOrDefault(EntryStatus.WITHDRAWN, 0L);
        long total = entries.size();
        totalCreditsLabel.setText(getTranslation("entry-admin.entries.summary.credits", creditsBalance));
        entriesSummaryLabel.setText(getTranslation("entry-admin.entries.summary.entries", total, draft, submitted, received, withdrawn));
    }

    private void refreshCreditsBalance() {
        int creditsBalance = entryService.getTotalCreditBalance(divisionId);
        totalCreditsLabel.setText(getTranslation("entry-admin.entries.summary.credits", creditsBalance));
    }

    private void openAdminAddEntryConfirmDialog() {
        var confirm = new Dialog();
        confirm.setHeaderTitle(getTranslation("entry-admin.entries.add.confirm.title"));
        confirm.add(new Span(getTranslation("entry-admin.entries.add.confirm.body")));
        var proceedButton = new Button(getTranslation("entry-admin.entries.add.confirm.button"), e -> {
            confirm.close();
            openAdminAddEntryDialog();
        });
        proceedButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        proceedButton.setDisableOnClick(true);
        var cancelButton = new Button(getTranslation("button.cancel"), e -> confirm.close());
        confirm.getFooter().add(cancelButton, proceedButton);
        confirm.open();
    }

    private void openAdminAddEntryDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.entries.add.title"));
        dialog.setWidth("600px");

        var warning = new Span(getTranslation("entry-admin.entries.add.warning"));
        warning.getStyle()
                .set("color", "var(--lumo-error-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-m)");

        var emailField = new TextField(getTranslation("entry-admin.entries.add.email"));
        emailField.setMaxLength(255);
        emailField.setWidthFull();

        var categorySelect = new com.vaadin.flow.component.select.Select<DivisionCategory>();
        categorySelect.setLabel(getTranslation("entry-admin.entries.edit.category"));
        categorySelect.setWidthFull();
        var subcategories = divisionCategories.stream()
                .filter(c -> c.getParentId() != null)
                .toList();
        categorySelect.setItems(subcategories);
        categorySelect.setItemLabelGenerator(c -> c.getCode() + " — " + c.getName());

        var meadNameField = new TextField(getTranslation("entry-admin.entries.edit.mead-name"));
        meadNameField.setMaxLength(255);
        meadNameField.setWidthFull();

        var sweetnessSelect = new com.vaadin.flow.component.select.Select<Sweetness>();
        sweetnessSelect.setLabel(getTranslation("entry-admin.entries.edit.sweetness"));
        sweetnessSelect.setItems(Sweetness.values());
        sweetnessSelect.setItemLabelGenerator(Sweetness::getDisplayName);
        sweetnessSelect.setWidthFull();

        var abvField = new NumberField(getTranslation("entry-admin.entries.edit.abv"));
        abvField.setMin(0);
        abvField.setMax(30);
        abvField.setWidthFull();

        var strengthField = new TextField(getTranslation("entry-admin.entries.edit.strength"));
        strengthField.setReadOnly(true);
        strengthField.setWidthFull();
        abvField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                strengthField.setValue(Strength.fromAbv(BigDecimal.valueOf(e.getValue())).getDisplayName());
            } else {
                strengthField.clear();
            }
        });

        var carbonationSelect = new com.vaadin.flow.component.select.Select<Carbonation>();
        carbonationSelect.setLabel(getTranslation("entry-admin.entries.edit.carbonation"));
        carbonationSelect.setItems(Carbonation.values());
        carbonationSelect.setItemLabelGenerator(Carbonation::getDisplayName);
        carbonationSelect.setWidthFull();

        var honeyField = new TextField(getTranslation("entry-admin.entries.edit.honey"));
        honeyField.setMaxLength(255);
        honeyField.setWidthFull();

        var otherField = new TextField(getTranslation("entry-admin.entries.edit.other-ingredients"));
        otherField.setMaxLength(255);
        otherField.setWidthFull();

        var woodAgedCheckbox = new com.vaadin.flow.component.checkbox.Checkbox(getTranslation("entry-admin.entries.edit.wood-aged"));
        var woodDetailsField = new TextField(getTranslation("entry-admin.entries.edit.wood-details"));
        woodDetailsField.setMaxLength(255);
        woodDetailsField.setWidthFull();
        woodDetailsField.setEnabled(false);
        woodAgedCheckbox.addValueChangeListener(e -> woodDetailsField.setEnabled(e.getValue()));

        var additionalField = new TextArea(getTranslation("entry-admin.entries.edit.additional-info"));
        additionalField.setMaxLength(1000);
        additionalField.setWidthFull();

        var layout = new VerticalLayout(warning, emailField, categorySelect, meadNameField,
                sweetnessSelect, abvField, strengthField, carbonationSelect, honeyField,
                otherField, woodAgedCheckbox, woodDetailsField, additionalField);
        layout.setPadding(false);
        dialog.add(layout);

        var addButton = new Button(getTranslation("entry-admin.entries.add.button"), e -> {
            var valid = true;
            if (!StringUtils.hasText(emailField.getValue())) {
                emailField.setInvalid(true);
                emailField.setErrorMessage(getTranslation("entry-admin.entries.add.email.error"));
                valid = false;
            }
            if (categorySelect.getValue() == null) {
                categorySelect.setInvalid(true);
                categorySelect.setErrorMessage(getTranslation("entry-admin.entries.edit.category.error"));
                valid = false;
            }
            if (!StringUtils.hasText(meadNameField.getValue())) {
                meadNameField.setInvalid(true);
                meadNameField.setErrorMessage(getTranslation("entry-admin.entries.edit.mead-name.error"));
                valid = false;
            }
            if (sweetnessSelect.getValue() == null) {
                sweetnessSelect.setInvalid(true);
                sweetnessSelect.setErrorMessage(getTranslation("entry-admin.entries.edit.sweetness.error"));
                valid = false;
            }
            if (abvField.getValue() == null) {
                abvField.setInvalid(true);
                abvField.setErrorMessage(getTranslation("entry-admin.entries.edit.abv.error"));
                valid = false;
            }
            if (carbonationSelect.getValue() == null) {
                carbonationSelect.setInvalid(true);
                carbonationSelect.setErrorMessage(getTranslation("entry-admin.entries.edit.carbonation.error"));
                valid = false;
            }
            if (!StringUtils.hasText(honeyField.getValue())) {
                honeyField.setInvalid(true);
                honeyField.setErrorMessage(getTranslation("entry-admin.entries.edit.honey.error"));
                valid = false;
            }
            if (!valid) {
                e.getSource().setEnabled(true);
                return;
            }
            try {
                entryService.adminCreateEntry(divisionId, emailField.getValue().trim(),
                        meadNameField.getValue().trim(), categorySelect.getValue().getId(),
                        sweetnessSelect.getValue(), BigDecimal.valueOf(abvField.getValue()),
                        carbonationSelect.getValue(),
                        honeyField.getValue().trim(),
                        StringUtils.hasText(otherField.getValue()) ? otherField.getValue().trim() : null,
                        woodAgedCheckbox.getValue(),
                        StringUtils.hasText(woodDetailsField.getValue()) ? woodDetailsField.getValue().trim() : null,
                        StringUtils.hasText(additionalField.getValue()) ? additionalField.getValue().trim() : null,
                        currentUserId);
                var notification = Notification.show(getTranslation("entry-admin.entries.added"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            } catch (jakarta.validation.ConstraintViolationException ex) {
                emailField.setInvalid(true);
                emailField.setErrorMessage(getTranslation("entry-admin.entries.add.email.error"));
                e.getSource().setEnabled(true);
            }
        });
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void openViewEntryDialog(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.entries.view.title",
                formatEntryNumber(entry.getEntryNumber()), entry.getMeadName()));
        dialog.setWidth("600px");

        var layout = new VerticalLayout();
        layout.setPadding(false);

        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.mead-name"), entry.getMeadName()));
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.category"),
                resolveCategoryCodeAndName(entry.getInitialCategoryId())));
        if (entry.getFinalCategoryId() != null) {
            layout.add(readOnlyField(getTranslation("entry-admin.entries.view.final-category"),
                    resolveCategoryCodeAndName(entry.getFinalCategoryId())));
        }
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.sweetness"), entry.getSweetness().getDisplayName()));
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.strength"), entry.getStrength().getDisplayName()));
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.abv"), entry.getAbv() + "%"));
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.carbonation"), entry.getCarbonation().getDisplayName()));
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.honey"), entry.getHoneyVarieties()));
        if (StringUtils.hasText(entry.getOtherIngredients())) {
            layout.add(readOnlyField(getTranslation("entry-admin.entries.view.other-ingredients"), entry.getOtherIngredients()));
        }
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.wood-aged"),
                entry.isWoodAged() ? getTranslation("entry-admin.entries.view.wood-aged.yes")
                        : getTranslation("entry-admin.entries.view.wood-aged.no")));
        if (entry.isWoodAged() && StringUtils.hasText(entry.getWoodAgeingDetails())) {
            layout.add(readOnlyField(getTranslation("entry-admin.entries.view.wood-details"), entry.getWoodAgeingDetails()));
        }
        if (StringUtils.hasText(entry.getAdditionalInformation())) {
            layout.add(readOnlyField(getTranslation("entry-admin.entries.view.additional-info"),
                    entry.getAdditionalInformation()));
        }
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.status"), entry.getStatus().name()));
        layout.add(readOnlyField(getTranslation("entry-admin.entries.view.entrant"), userService.findById(entry.getUserId()).getEmail()));

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

    private String resolveCategoryCodeAndName(UUID categoryId) {
        return divisionCategories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .map(c -> c.getCode() + " — " + c.getName())
                .orElse("—");
    }

    private void openEditEntryDialog(Entry entry) {
        var confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle(getTranslation("entry-admin.entries.edit.confirm.title",
                formatEntryNumber(entry.getEntryNumber())));
        confirmDialog.add(getTranslation("entry-admin.entries.edit.confirm.body"));
        var proceedButton = new Button(getTranslation("entry-admin.entries.edit.confirm.proceed"), e -> {
            confirmDialog.close();
            openEditEntryForm(entry);
        });
        var cancelButton = new Button("Cancel", e -> confirmDialog.close());
        confirmDialog.getFooter().add(cancelButton, proceedButton);
        confirmDialog.open();
    }

    private void openEditEntryForm(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.entries.edit.title",
                formatEntryNumber(entry.getEntryNumber())));
        dialog.setWidth("600px");

        var layout = new VerticalLayout();
        layout.setPadding(false);

        var meadNameField = new TextField(getTranslation("entry-admin.entries.edit.mead-name"));
        meadNameField.setWidthFull();
        meadNameField.setMaxLength(255);
        meadNameField.setValue(entry.getMeadName());

        var categorySelect = new Select<DivisionCategory>();
        categorySelect.setLabel(getTranslation("entry-admin.entries.edit.category"));
        categorySelect.setWidthFull();
        categorySelect.setItemLabelGenerator(dc ->
                dc.getCode() + " — " + dc.getName());
        categorySelect.setItems(divisionCategories.stream()
                .filter(dc -> dc.getParentId() != null)
                .toList());
        categorySelect.getListDataView().getItems()
                .filter(c -> c.getId().equals(entry.getInitialCategoryId()))
                .findFirst()
                .ifPresent(categorySelect::setValue);

        var sweetness = new Select<Sweetness>();
        sweetness.setLabel(getTranslation("entry-admin.entries.edit.sweetness"));
        sweetness.setWidthFull();
        sweetness.setItems(Sweetness.values());
        sweetness.setItemLabelGenerator(Sweetness::getDisplayName);
        sweetness.setValue(entry.getSweetness());

        var strengthField = new TextField(getTranslation("entry-admin.entries.edit.strength"));
        strengthField.setWidthFull();
        strengthField.setReadOnly(true);
        strengthField.setValue(entry.getStrength().getDisplayName());

        var abv = new NumberField(getTranslation("entry-admin.entries.edit.abv"));
        abv.setWidthFull();
        abv.setStep(0.1);
        abv.setMin(0);
        abv.setMax(30);
        abv.setValue(entry.getAbv().doubleValue());
        abv.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                strengthField.setValue(Strength.fromAbv(BigDecimal.valueOf(e.getValue())).getDisplayName());
            }
        });

        var carbonation = new Select<Carbonation>();
        carbonation.setLabel(getTranslation("entry-admin.entries.edit.carbonation"));
        carbonation.setWidthFull();
        carbonation.setItems(Carbonation.values());
        carbonation.setItemLabelGenerator(Carbonation::getDisplayName);
        carbonation.setValue(entry.getCarbonation());

        var honeyVarieties = new TextArea(getTranslation("entry-admin.entries.edit.honey"));
        honeyVarieties.setWidthFull();
        honeyVarieties.setMaxLength(500);
        honeyVarieties.setValue(entry.getHoneyVarieties());

        var otherIngredients = new TextArea(getTranslation("entry-admin.entries.edit.other-ingredients"));
        otherIngredients.setWidthFull();
        otherIngredients.setMaxLength(500);
        if (entry.getOtherIngredients() != null) {
            otherIngredients.setValue(entry.getOtherIngredients());
        }

        var woodAged = new Checkbox(getTranslation("entry-admin.entries.edit.wood-aged"));
        woodAged.setValue(entry.isWoodAged());
        var woodAgeingDetails = new TextArea(getTranslation("entry-admin.entries.edit.wood-details"));
        woodAgeingDetails.setWidthFull();
        woodAgeingDetails.setMaxLength(500);
        woodAgeingDetails.setVisible(entry.isWoodAged());
        if (entry.getWoodAgeingDetails() != null) {
            woodAgeingDetails.setValue(entry.getWoodAgeingDetails());
        }
        woodAged.addValueChangeListener(e -> woodAgeingDetails.setVisible(e.getValue()));

        var additionalInfo = new TextArea(getTranslation("entry-admin.entries.edit.additional-info"));
        additionalInfo.setWidthFull();
        additionalInfo.setMaxLength(1000);
        if (entry.getAdditionalInformation() != null) {
            additionalInfo.setValue(entry.getAdditionalInformation());
        }

        var judgingCategories = competitionService.findJudgingCategories(divisionId);
        var finalCategoryOptions = judgingCategories.isEmpty() ? divisionCategories : judgingCategories;
        var finalCategorySelect = new Select<DivisionCategory>();
        finalCategorySelect.setLabel("Final Category");
        finalCategorySelect.setWidthFull();
        finalCategorySelect.setEmptySelectionAllowed(true);
        finalCategorySelect.setEmptySelectionCaption("— Not assigned —");
        finalCategorySelect.setItemLabelGenerator(dc ->
                dc != null ? dc.getCode() + " — " + dc.getName() : "");
        finalCategorySelect.setItems(finalCategoryOptions);
        finalCategoryOptions.stream()
                .filter(c -> c.getId().equals(entry.getFinalCategoryId()))
                .findFirst()
                .ifPresent(finalCategorySelect::setValue);

        layout.add(meadNameField, categorySelect, sweetness, strengthField, abv, carbonation,
                honeyVarieties, otherIngredients, woodAged, woodAgeingDetails, additionalInfo,
                finalCategorySelect);
        dialog.add(layout);

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(meadNameField.getValue())) {
                meadNameField.setInvalid(true);
                meadNameField.setErrorMessage(getTranslation("entry-admin.entries.edit.mead-name.error"));
                e.getSource().setEnabled(true);
                return;
            }
            var valid = true;
            if (categorySelect.getValue() == null) {
                categorySelect.setInvalid(true);
                categorySelect.setErrorMessage(getTranslation("entry-admin.entries.edit.category.error"));
                valid = false;
            }
            if (sweetness.getValue() == null) {
                sweetness.setInvalid(true);
                sweetness.setErrorMessage(getTranslation("entry-admin.entries.edit.sweetness.error"));
                valid = false;
            }
            if (abv.getValue() == null) {
                abv.setInvalid(true);
                abv.setErrorMessage(getTranslation("entry-admin.entries.edit.abv.error"));
                valid = false;
            }
            if (carbonation.getValue() == null) {
                carbonation.setInvalid(true);
                carbonation.setErrorMessage(getTranslation("entry-admin.entries.edit.carbonation.error"));
                valid = false;
            }
            if (!StringUtils.hasText(honeyVarieties.getValue())) {
                honeyVarieties.setInvalid(true);
                honeyVarieties.setErrorMessage(getTranslation("entry-admin.entries.edit.honey.error"));
                valid = false;
            }
            if (!valid) {
                e.getSource().setEnabled(true);
                return;
            }
            try {
                entryService.adminUpdateEntry(entry.getId(), meadNameField.getValue().trim(),
                        categorySelect.getValue().getId(),
                        sweetness.getValue(),
                        BigDecimal.valueOf(abv.getValue()),
                        carbonation.getValue(),
                        honeyVarieties.getValue().trim(),
                        StringUtils.hasText(otherIngredients.getValue())
                                ? otherIngredients.getValue().trim() : null,
                        woodAged.getValue(),
                        woodAged.getValue() ? woodAgeingDetails.getValue().trim() : null,
                        StringUtils.hasText(additionalInfo.getValue())
                                ? additionalInfo.getValue().trim() : null,
                        currentUserId);
                var finalCategoryId = finalCategorySelect.getValue() != null
                        ? finalCategorySelect.getValue().getId() : null;
                entryService.assignFinalCategory(entry.getId(), finalCategoryId, currentUserId);
                var notification = Notification.show(getTranslation("entry-admin.entries.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openDeleteEntryDialog(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.entries.delete.title"));

        dialog.add(getTranslation("entry-admin.entries.delete.confirm",
                formatEntryNumber(entry.getEntryNumber()), entry.getMeadName()));

        var confirmButton = new Button(getTranslation("button.delete"), e -> {
            try {
                entryService.deleteEntry(entry.getId(), entry.getUserId());
                var notification = Notification.show(getTranslation("entry-admin.entries.deleted"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openAdvanceStatusDialog(Entry entry) {
        var dialog = new Dialog();
        var targetLabel = entry.getStatus() == EntryStatus.DRAFT
                ? getTranslation("entry-admin.entries.advance.submit.title")
                : getTranslation("entry-admin.entries.advance.received.title");
        dialog.setHeaderTitle(targetLabel);

        dialog.add(getTranslation("entry-admin.entries.advance.confirm",
                targetLabel, formatEntryNumber(entry.getEntryNumber()), entry.getMeadName()));

        var confirmButton = new Button(targetLabel, e -> {
            try {
                entryService.advanceEntryStatus(entry.getId(), currentUserId);
                var notification = Notification.show(getTranslation("entry-admin.entries.status-updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            } catch (IllegalStateException ex) {
                Notification.show(getTranslation("entry-admin.entries.status-changed"));
                dialog.close();
                refreshEntriesGrid();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openRevertStatusDialog(Entry entry) {
        var dialog = new Dialog();
        var targetLabel = entry.getStatus() == EntryStatus.RECEIVED
                ? getTranslation("entry-admin.entries.revert.submitted.title")
                : getTranslation("entry-admin.entries.revert.draft.title");
        dialog.setHeaderTitle(targetLabel);

        dialog.add(getTranslation("entry-admin.entries.revert.confirm",
                targetLabel, formatEntryNumber(entry.getEntryNumber()), entry.getMeadName()));

        var confirmButton = new Button(targetLabel, e -> {
            try {
                entryService.revertEntryStatus(entry.getId(), currentUserId);
                var notification = Notification.show(getTranslation("entry-admin.entries.status-updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            } catch (IllegalStateException ex) {
                Notification.show(getTranslation("entry-admin.entries.status-changed"));
                dialog.close();
                refreshEntriesGrid();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openWithdrawEntryDialog(Entry entry) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.entries.withdraw.title"));

        dialog.add(getTranslation("entry-admin.entries.withdraw.confirm",
                formatEntryNumber(entry.getEntryNumber()), entry.getMeadName()));

        var confirmButton = new Button(getTranslation("entry-admin.entries.withdraw.button"), e -> {
            try {
                entryService.withdrawEntry(entry.getId(), currentUserId);
                var notification = Notification.show(getTranslation("entry-admin.entries.withdrawn"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshEntriesGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    // --- Products Tab ---

    private VerticalLayout createProductsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        boolean registrationOpen = division.getStatus().allowsRegistrationActions();
        var addButton = new Button(getTranslation("entry-admin.products.add"), e -> openAddProductDialog());
        addButton.setEnabled(registrationOpen);
        if (!registrationOpen) {
            var wrapper = new Span(addButton);
            wrapper.getStyle().set("display", "inline-block");
            Tooltip.forComponent(wrapper).setText(getTranslation("entry-admin.registration-closed.tooltip"));
            tab.add(wrapper);
        } else {
            tab.add(addButton);
        }

        productsGrid = new Grid<>(ProductMapping.class, false);
        productsGrid.setAllRowsVisible(true);
        productsGrid.setId("products-grid");
        productsGrid.addColumn(ProductMapping::getJumpsellerProductId)
                .setHeader(getTranslation("entry-admin.products.column.product-id")).setSortable(true);
        productsGrid.addColumn(ProductMapping::getJumpsellerSku)
                .setHeader(getTranslation("entry-admin.products.column.sku")).setSortable(true).setAutoWidth(true);
        productsGrid.addColumn(ProductMapping::getProductName)
                .setHeader(getTranslation("entry-admin.products.column.name")).setSortable(true).setFlexGrow(2);
        productsGrid.addColumn(ProductMapping::getCreditsPerUnit)
                .setHeader(getTranslation("entry-admin.products.column.credits")).setSortable(true).setAutoWidth(true);
        productsGrid.addComponentColumn(mapping -> {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setEnabled(registrationOpen);
            editButton.setAriaLabel(registrationOpen
                    ? getTranslation("entry-admin.entries.action.edit.tooltip")
                    : getTranslation("entry-admin.registration-closed.tooltip"));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setEnabled(registrationOpen);
            deleteButton.setAriaLabel(registrationOpen
                    ? getTranslation("entry-admin.entries.action.delete.tooltip")
                    : getTranslation("entry-admin.registration-closed.tooltip"));

            if (registrationOpen) {
                editButton.setTooltipText(getTranslation("entry-admin.entries.action.edit.tooltip"));
                editButton.addClickListener(e -> openEditProductDialog(mapping));
                deleteButton.setTooltipText(getTranslation("entry-admin.entries.action.delete.tooltip"));
                deleteButton.addClickListener(e -> openDeleteProductDialog(mapping));
                return (com.vaadin.flow.component.Component) new HorizontalLayout(editButton, deleteButton);
            }
            var editWrapper = new Span(editButton);
            editWrapper.getStyle().set("display", "inline-block");
            Tooltip.forComponent(editWrapper).setText(getTranslation("entry-admin.registration-closed.tooltip"));
            var deleteWrapper = new Span(deleteButton);
            deleteWrapper.getStyle().set("display", "inline-block");
            Tooltip.forComponent(deleteWrapper).setText(getTranslation("entry-admin.registration-closed.tooltip"));
            return new HorizontalLayout(editWrapper, deleteWrapper);
        }).setHeader(getTranslation("entry-admin.products.column.actions")).setAutoWidth(true);

        productsGrid.getColumns().forEach(col -> col.setResizable(true));

        refreshProductsGrid();
        tab.add(productsGrid);
        return tab;
    }

    private void refreshProductsGrid() {
        productsGrid.setItems(entryService.findProductMappings(divisionId));
    }

    private void openAddProductDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.products.add.title"));

        var productIdField = new TextField(getTranslation("entry-admin.products.add.product-id"));
        productIdField.setMaxLength(255);
        var skuField = new TextField(getTranslation("entry-admin.products.add.sku"));
        skuField.setMaxLength(255);
        var nameField = new TextField(getTranslation("entry-admin.products.add.name"));
        nameField.setMaxLength(255);
        var creditsField = new IntegerField(getTranslation("entry-admin.products.add.credits"));
        creditsField.setMin(1);
        creditsField.setValue(1);

        dialog.add(new VerticalLayout(productIdField, skuField, nameField, creditsField));

        var addButton = new Button(getTranslation("entry-admin.products.add.button"), e -> {
            var valid = true;
            if (!StringUtils.hasText(productIdField.getValue())) {
                productIdField.setInvalid(true);
                productIdField.setErrorMessage(getTranslation("entry-admin.products.add.product-id.error"));
                valid = false;
            }
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("entry-admin.products.add.name.error"));
                valid = false;
            }
            if (creditsField.getValue() == null) {
                creditsField.setInvalid(true);
                creditsField.setErrorMessage(getTranslation("entry-admin.products.add.credits.error"));
                valid = false;
            }
            if (!valid) {
                e.getSource().setEnabled(true);
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
                var notification = Notification.show(getTranslation("entry-admin.products.added"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshProductsGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        addButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void openEditProductDialog(ProductMapping mapping) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.products.edit.title"));

        var nameField = new TextField(getTranslation("entry-admin.products.edit.name"));
        nameField.setMaxLength(255);
        nameField.setValue(mapping.getProductName());
        var creditsField = new IntegerField(getTranslation("entry-admin.products.edit.credits"));
        creditsField.setMin(1);
        creditsField.setValue(mapping.getCreditsPerUnit());

        dialog.add(new VerticalLayout(nameField, creditsField));

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue()) || creditsField.getValue() == null) {
                e.getSource().setEnabled(true);
                return;
            }
            try {
                entryService.updateProductMapping(mapping.getId(),
                        nameField.getValue().trim(), creditsField.getValue(), currentUserId);
                var notification = Notification.show(getTranslation("entry-admin.products.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshProductsGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openDeleteProductDialog(ProductMapping mapping) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.products.delete.title"));

        dialog.add(getTranslation("entry-admin.products.delete.confirm", mapping.getProductName()));

        var confirmButton = new Button(getTranslation("button.delete"), e -> {
            try {
                entryService.removeProductMapping(mapping.getId(), currentUserId);
                var notification = Notification.show(getTranslation("entry-admin.products.deleted"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshProductsGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    // --- Orders Tab ---

    private VerticalLayout createOrdersTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var filterField = new TextField();
        filterField.setPlaceholder(getTranslation("entry-admin.orders.filter.placeholder"));
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
        ordersGrid.setColumnReorderingAllowed(true);
        ordersGrid.addColumn(JumpsellerOrder::getJumpsellerOrderId)
                .setHeader(getTranslation("entry-admin.orders.column.order-id")).setSortable(true);
        ordersGrid.addColumn(JumpsellerOrder::getCustomerEmail)
                .setHeader(getTranslation("entry-admin.orders.column.customer")).setSortable(true).setFlexGrow(2)
                .setTooltipGenerator(JumpsellerOrder::getCustomerEmail);
        ordersGrid.addColumn(order -> order.getStatus().name())
                .setHeader(getTranslation("entry-admin.orders.column.status")).setSortable(true).setAutoWidth(true);
        ordersGrid.addColumn(order -> {
            var items = lineItemsByOrderId.getOrDefault(order.getId(), List.of());
            return items.stream()
                    .filter(i -> i.getStatus() == LineItemStatus.PROCESSED)
                    .mapToInt(JumpsellerOrderLineItem::getCreditsAwarded)
                    .sum();
        }).setHeader(getTranslation("entry-admin.orders.column.awarded")).setSortable(true).setAutoWidth(true);
        ordersGrid.addColumn(order -> {
            var items = lineItemsByOrderId.getOrDefault(order.getId(), List.of());
            return items.stream()
                    .filter(i -> i.getStatus() == LineItemStatus.NEEDS_REVIEW)
                    .mapToInt(JumpsellerOrderLineItem::getCreditsAwarded)
                    .sum();
        }).setHeader(getTranslation("entry-admin.orders.column.pending")).setSortable(true).setAutoWidth(true);
        ordersGrid.addColumn(order -> {
            var items = lineItemsByOrderId.getOrDefault(order.getId(), List.of());
            return items.stream()
                    .filter(i -> i.getStatus() == LineItemStatus.NEEDS_REVIEW && i.getReviewReason() != null)
                    .map(JumpsellerOrderLineItem::getReviewReason)
                    .distinct()
                    .collect(Collectors.joining("; "));
        }).setHeader(getTranslation("entry-admin.orders.column.review")).setSortable(true).setFlexGrow(2)
                .setTooltipGenerator(order -> {
                    var items = lineItemsByOrderId.getOrDefault(order.getId(), List.of());
                    var reason = items.stream()
                            .filter(i -> i.getStatus() == LineItemStatus.NEEDS_REVIEW && i.getReviewReason() != null)
                            .map(JumpsellerOrderLineItem::getReviewReason)
                            .distinct()
                            .collect(Collectors.joining("; "));
                    return reason.isEmpty() ? null : reason;
                });
        ordersGrid.addColumn(order -> formatInstant(order.getCreatedAt()))
                .setHeader(getTranslation("entry-admin.orders.column.date")).setSortable(true).setAutoWidth(true);
        ordersGrid.addColumn(JumpsellerOrder::getAdminNote)
                .setHeader(getTranslation("entry-admin.orders.column.note")).setSortable(true).setFlexGrow(2)
                .setTooltipGenerator(order -> order.getAdminNote());
        ordersGrid.addComponentColumn(order -> {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setAriaLabel(getTranslation("entry-admin.entries.action.edit.tooltip"));
            editButton.setTooltipText(getTranslation("entry-admin.entries.action.edit.tooltip"));
            editButton.addClickListener(e -> openEditOrderDialog(order));
            return editButton;
        }).setHeader(getTranslation("entry-admin.orders.column.actions")).setAutoWidth(true);

        ordersGrid.getColumns().forEach(col -> col.setResizable(true));

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
        lineItemsByOrderId = entryService.findLineItemsByDivision(divisionId).stream()
                .collect(Collectors.groupingBy(JumpsellerOrderLineItem::getOrderId));
        ordersGrid.setItems(entryService.findOrdersByDivision(divisionId));
    }

    private void openEditOrderDialog(JumpsellerOrder order) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("entry-admin.orders.edit.title", order.getJumpsellerOrderId()));

        var statusSelect = new Select<OrderStatus>();
        statusSelect.setLabel(getTranslation("entry-admin.orders.edit.status"));
        statusSelect.setItems(OrderStatus.values());
        statusSelect.setItemLabelGenerator(OrderStatus::name);
        statusSelect.setValue(order.getStatus());

        var noteField = new TextField(getTranslation("entry-admin.orders.edit.note"));
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
                var notification = Notification.show(getTranslation("entry-admin.orders.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshOrdersGrid();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

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
