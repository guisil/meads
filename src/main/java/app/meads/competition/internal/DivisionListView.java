package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.competition.ScoringSystem;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Route(value = "competitions/:competitionId/divisions", layout = MainLayout.class)
@PermitAll
public class DivisionListView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<Division> grid;

    private UUID competitionId;
    private UUID currentUserId;
    private boolean isSystemAdmin;
    private Competition competition;

    public DivisionListView(CompetitionService competitionService,
                             UserService userService,
                             AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;

        grid = new Grid<>(Division.class, false);
        grid.addColumn(Division::getName).setHeader("Name").setSortable(true);
        grid.addComponentColumn(div -> createStatusBadge(div.getStatus()))
                .setHeader("Status");
        grid.addColumn(div -> div.getScoringSystem().name()).setHeader("Scoring");
        grid.addComponentColumn(div -> {
            var viewButton = new Button("View", e ->
                    e.getSource().getUI().ifPresent(ui ->
                            ui.navigate("divisions/" + div.getId())));
            var advanceButton = new Button("Advance", e -> advanceStatus(div));
            advanceButton.setEnabled(div.getStatus() != DivisionStatus.RESULTS_PUBLISHED);
            var deleteButton = new Button("Delete", e -> openDeleteDialog(div));
            return new HorizontalLayout(viewButton, advanceButton, deleteButton);
        }).setHeader("Actions");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        competitionId = beforeEnterEvent.getRouteParameters().get("competitionId")
                .map(UUID::fromString)
                .orElse(null);

        if (competitionId == null) {
            beforeEnterEvent.forwardTo("competitions");
            return;
        }

        try {
            competition = competitionService.findCompetitionById(competitionId);
        } catch (IllegalArgumentException e) {
            beforeEnterEvent.forwardTo("competitions");
            return;
        }

        currentUserId = getCurrentUserId();
        var currentUser = userService.findById(currentUserId);
        isSystemAdmin = currentUser.getRole() == Role.SYSTEM_ADMIN;
        if (!isSystemAdmin
                && !competitionService.isAuthorizedForCompetition(competitionId, currentUserId)) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        removeAll();
        add(createCompetitionHeader());
        add(createActionBar());
        refreshGrid();
        add(grid);
    }

    private HorizontalLayout createCompetitionHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setWidthFull();

        if (competition.hasLogo()) {
            var dataUri = "data:" + competition.getLogoContentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(competition.getLogo());
            var logo = new Image(dataUri, competition.getName() + " logo");
            logo.setHeight("64px");
            header.add(logo);
        }

        var textBlock = new VerticalLayout();
        textBlock.setPadding(false);
        textBlock.setSpacing(false);
        textBlock.add(new H2(competition.getName()));

        var details = formatDateRange() +
                (competition.getLocation() != null ? "  ·  " + competition.getLocation() : "");
        textBlock.add(new Span(details));

        header.add(textBlock);
        return header;
    }

    private String formatDateRange() {
        var start = competition.getStartDate();
        var end = competition.getEndDate();
        var formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

        if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
            return start.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                    + "–" + end.format(DateTimeFormatter.ofPattern("d, yyyy", Locale.ENGLISH));
        }
        return start.format(formatter) + " – " + end.format(formatter);
    }

    private HorizontalLayout createActionBar() {
        var bar = new HorizontalLayout();
        bar.setWidthFull();
        bar.setJustifyContentMode(JustifyContentMode.END);
        if (isSystemAdmin) {
            bar.add(new Button("Add Participant",
                    e -> openAddParticipantDialog()));
            bar.add(new Button("Create Division", e -> openCreateDialog()));
        }
        return bar;
    }

    private void openCreateDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Create Division");

        var nameField = new TextField("Name");
        nameField.setRequired(true);

        var scoringSelect = new Select<ScoringSystem>();
        scoringSelect.setLabel("Scoring System");
        scoringSelect.setItems(ScoringSystem.values());
        scoringSelect.setValue(ScoringSystem.MJP);

        var createButton = new Button("Create", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }

            try {
                competitionService.createDivision(
                        competitionId,
                        nameField.getValue(),
                        scoringSelect.getValue(),
                        getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Division created successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(nameField, scoringSelect);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, createButton);
        dialog.open();
    }

    private void openAddParticipantDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Participant");

        var emailField = new TextField("Email");

        var roleSelect = new Select<CompetitionRole>();
        roleSelect.setLabel("Role");
        roleSelect.setItems(CompetitionRole.values());
        roleSelect.setValue(CompetitionRole.JUDGE);

        var addButton = new Button("Add", e -> {
            if (!StringUtils.hasText(emailField.getValue())) {
                emailField.setInvalid(true);
                emailField.setErrorMessage("Email is required");
                return;
            }
            try {
                competitionService.addParticipantByEmail(
                        competitionId,
                        emailField.getValue().trim(),
                        roleSelect.getValue(),
                        getCurrentUserId());
                var notification = Notification.show("Participant added successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(emailField, roleSelect);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void advanceStatus(Division division) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Advance Status");
        var nextStatus = division.getStatus().next()
                .map(DivisionStatus::getDisplayName).orElse("—");
        dialog.add("Advance division '" + division.getName() + "' from "
                + division.getStatus().getDisplayName() + " to "
                + nextStatus + "?");

        var confirmButton = new Button("Advance", e -> {
            try {
                competitionService.advanceDivisionStatus(division.getId(), getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Status advanced successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
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

    private void openDeleteDialog(Division division) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Division");
        dialog.add("Are you sure you want to delete \"" + division.getName() + "\"? "
                + "This will also remove all categories.");

        var confirmButton = new Button("Delete", e -> {
            try {
                competitionService.deleteDivision(division.getId(), getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Division deleted successfully");
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

    private Span createStatusBadge(DivisionStatus status) {
        var badge = new Span(status.getDisplayName());
        badge.getElement().getThemeList().add("badge pill small");
        badge.addClassName(status.getBadgeCssClass());
        return badge;
    }

    private void refreshGrid() {
        grid.setItems(competitionService.findAuthorizedDivisions(competitionId, currentUserId));
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
