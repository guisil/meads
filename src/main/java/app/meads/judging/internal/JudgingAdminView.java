package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import app.meads.judging.Judging;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingTable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
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

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "competitions/:compShortName/divisions/:divShortName/judging-admin", layout = MainLayout.class)
@PermitAll
public class JudgingAdminView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final transient AuthenticationContext authenticationContext;

    private Competition competition;
    private Division division;
    private Judging judging;
    private String compShortName;
    private String divShortName;
    private UUID currentUserId;

    private Grid<JudgingTable> tablesGrid;

    public JudgingAdminView(CompetitionService competitionService,
                            UserService userService,
                            JudgingService judgingService,
                            AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        divShortName = event.getRouteParameters().get("divShortName").orElse(null);

        if (compShortName == null || divShortName == null) {
            event.forwardTo("");
            return;
        }

        try {
            competition = competitionService.findCompetitionByShortName(compShortName);
            division = competitionService.findDivisionByShortName(competition.getId(), divShortName);
        } catch (BusinessRuleException e) {
            event.forwardTo("");
            return;
        }

        currentUserId = getCurrentUserId();
        var user = userService.findById(currentUserId);

        if (user.getRole() != Role.SYSTEM_ADMIN
                && !competitionService.isAuthorizedForDivision(division.getId(), currentUserId)) {
            event.forwardTo("");
            return;
        }

        if (user.getRole() != Role.SYSTEM_ADMIN && !userService.hasPassword(currentUserId)) {
            event.forwardTo("");
            return;
        }

        if (division.getStatus().ordinal() < DivisionStatus.JUDGING.ordinal()) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName);
            return;
        }

        judging = judgingService.ensureJudgingExists(division.getId());

        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        add(createTabSheet());
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        var user = userService.findById(currentUserId);
        boolean isSystemAdmin = user.getRole() == Role.SYSTEM_ADMIN;
        nav.add(new Anchor(
                isSystemAdmin ? "competitions" : "my-competitions",
                isSystemAdmin ? getTranslation("nav.competitions") : getTranslation("nav.my-competitions")));
        nav.add(new Span(" / "));
        nav.add(new Anchor("competitions/" + compShortName, competition.getName()));
        nav.add(new Span(" / "));
        nav.add(new Anchor(
                "competitions/" + compShortName + "/divisions/" + divShortName,
                division.getName()));
        nav.add(new Span(" / "));
        nav.add(new Span(getTranslation("judging-admin.nav.judging-admin")));
        return nav;
    }

    private HorizontalLayout createHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        if (competition.hasLogo()) {
            var dataUri = "data:" + competition.getLogoContentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(competition.getLogo());
            var logo = new Image(dataUri, competition.getName() + " logo");
            logo.setHeight("64px");
            header.add(logo);
        }

        header.add(new H2(competition.getName() + " — " + division.getName()
                + " — " + getTranslation("judging-admin.nav.judging-admin")));
        return header;
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();
        tabSheet.add(getTranslation("judging-admin.tab.tables"), createTablesTab());
        tabSheet.add(getTranslation("judging-admin.tab.medal-rounds"), createMedalRoundsTab());
        tabSheet.add(getTranslation("judging-admin.tab.bos"), createBosTab());
        return tabSheet;
    }

    private VerticalLayout createTablesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var addButton = new Button(getTranslation("judging-admin.tables.add"),
                e -> openAddTableDialog());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        tab.add(addButton);

        tablesGrid = new Grid<>(JudgingTable.class, false);
        tablesGrid.setId("tables-grid");
        tablesGrid.addColumn(JudgingTable::getName)
                .setHeader(getTranslation("judging-admin.tables.column.name"));
        tablesGrid.addColumn(t -> formatCategory(t.getDivisionCategoryId()))
                .setHeader(getTranslation("judging-admin.tables.column.category"));
        tablesGrid.addColumn(t -> t.getStatus().name())
                .setHeader(getTranslation("judging-admin.tables.column.status"));
        tablesGrid.addColumn(t -> t.getAssignments().size())
                .setHeader(getTranslation("judging-admin.tables.column.judges"));
        tablesGrid.addColumn(t -> t.getScheduledDate() == null ? ""
                        : t.getScheduledDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                                .withLocale(getLocale())))
                .setHeader(getTranslation("judging-admin.tables.column.scheduled"));
        tablesGrid.addColumn(t -> "—")
                .setHeader(getTranslation("judging-admin.tables.column.scoresheets"));
        tablesGrid.addColumn(t -> "")
                .setHeader(getTranslation("judging-admin.tables.column.actions"));

        refreshTablesGrid();
        tab.add(tablesGrid);
        return tab;
    }

    private void refreshTablesGrid() {
        tablesGrid.setItems(judgingService.findTablesByJudgingId(judging.getId()));
    }

    private String formatCategory(UUID divisionCategoryId) {
        if (divisionCategoryId == null) {
            return "";
        }
        return categoriesById().getOrDefault(divisionCategoryId, null) instanceof DivisionCategory dc
                ? dc.getCode() + " — " + dc.getName()
                : "";
    }

    private Map<UUID, DivisionCategory> categoriesById() {
        return competitionService.findJudgingCategories(division.getId()).stream()
                .collect(Collectors.toMap(DivisionCategory::getId, c -> c));
    }

    private void openAddTableDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.add"));

        var form = new VerticalLayout();
        form.setPadding(false);

        var nameField = new TextField(getTranslation("judging-admin.tables.dialog.name"));
        nameField.setId("add-table-name");
        nameField.setWidthFull();
        nameField.setMaxLength(120);

        var categorySelect = new Select<DivisionCategory>();
        categorySelect.setId("add-table-category");
        categorySelect.setLabel(getTranslation("judging-admin.tables.dialog.category"));
        categorySelect.setWidthFull();
        var categories = competitionService.findJudgingCategories(division.getId());
        categorySelect.setItems(categories);
        categorySelect.setItemLabelGenerator(c -> c == null ? "" : c.getCode() + " — " + c.getName());

        var datePicker = new DatePicker(getTranslation("judging-admin.tables.dialog.scheduled"));
        datePicker.setWidthFull();

        form.add(nameField, categorySelect, datePicker);
        dialog.add(form);

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (nameField.getValue() == null || nameField.getValue().isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("judging-admin.tables.dialog.name.error"));
                return;
            }
            if (categorySelect.getValue() == null) {
                categorySelect.setInvalid(true);
                categorySelect.setErrorMessage(getTranslation("judging-admin.tables.dialog.category.error"));
                return;
            }
            try {
                judgingService.createTable(judging.getId(), nameField.getValue().trim(),
                        categorySelect.getValue().getId(), datePicker.getValue(), currentUserId);
                dialog.close();
                refreshTablesGrid();
                Notification.show(getTranslation("judging-admin.tables.added"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private VerticalLayout createMedalRoundsTab() {
        return new VerticalLayout();
    }

    private VerticalLayout createBosTab() {
        return new VerticalLayout();
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
