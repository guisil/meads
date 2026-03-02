package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionStatus;
import app.meads.competition.MeadEvent;
import app.meads.competition.ScoringSystem;
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
import app.meads.identity.Role;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Route(value = "events/:eventId/competitions", layout = MainLayout.class)
@PermitAll
public class CompetitionListView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<Competition> grid;

    private UUID eventId;
    private UUID currentUserId;
    private boolean isSystemAdmin;
    private MeadEvent meadEvent;

    public CompetitionListView(CompetitionService competitionService,
                                UserService userService,
                                AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;

        grid = new Grid<>(Competition.class, false);
        grid.addColumn(Competition::getName).setHeader("Name").setSortable(true);
        grid.addComponentColumn(comp -> createStatusBadge(comp.getStatus()))
                .setHeader("Status");
        grid.addColumn(comp -> comp.getScoringSystem().name()).setHeader("Scoring");
        grid.addComponentColumn(comp -> {
            var viewButton = new Button("View", e ->
                    e.getSource().getUI().ifPresent(ui ->
                            ui.navigate("competitions/" + comp.getId())));
            var advanceButton = new Button("Advance", e -> advanceStatus(comp));
            advanceButton.setEnabled(comp.getStatus() != CompetitionStatus.RESULTS_PUBLISHED);
            var deleteButton = new Button("Delete", e -> openDeleteDialog(comp));
            return new HorizontalLayout(viewButton, advanceButton, deleteButton);
        }).setHeader("Actions");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        eventId = beforeEnterEvent.getRouteParameters().get("eventId")
                .map(UUID::fromString)
                .orElse(null);

        if (eventId == null) {
            beforeEnterEvent.forwardTo("events");
            return;
        }

        try {
            meadEvent = competitionService.findMeadEventById(eventId);
        } catch (IllegalArgumentException e) {
            beforeEnterEvent.forwardTo("events");
            return;
        }

        currentUserId = getCurrentUserId();
        var currentUser = userService.findById(currentUserId);
        isSystemAdmin = currentUser.getRole() == Role.SYSTEM_ADMIN;
        if (!isSystemAdmin
                && competitionService.findAuthorizedCompetitions(eventId, currentUserId).isEmpty()) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        removeAll();
        add(createMeadEventHeader());
        add(createActionBar());
        refreshGrid();
        add(grid);
    }

    private HorizontalLayout createMeadEventHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setWidthFull();

        if (meadEvent.hasLogo()) {
            var logo = new Image(meadEvent.getLogo(),
                    meadEvent.getName() + " logo");
            logo.setHeight("64px");
            header.add(logo);
        }

        var textBlock = new VerticalLayout();
        textBlock.setPadding(false);
        textBlock.setSpacing(false);
        textBlock.add(new H2(meadEvent.getName()));

        var details = formatDateRange() +
                (meadEvent.getLocation() != null ? "  ·  " + meadEvent.getLocation() : "");
        textBlock.add(new Span(details));

        header.add(textBlock);
        return header;
    }

    private String formatDateRange() {
        var start = meadEvent.getStartDate();
        var end = meadEvent.getEndDate();
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
            bar.add(new Button("Add Participant to All",
                    e -> openAddParticipantToAllDialog()));
            bar.add(new Button("Create Competition", e -> openCreateDialog()));
        }
        return bar;
    }

    private void openCreateDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Create Competition");

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
                competitionService.createCompetition(
                        eventId,
                        nameField.getValue(),
                        scoringSelect.getValue(),
                        getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Competition created successfully");
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

    private void openAddParticipantToAllDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Participant to All Competitions");

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
                var user = userService.findOrCreateByEmail(emailField.getValue().trim());
                var added = competitionService.addParticipantToAllCompetitions(
                        eventId, user.getId(), roleSelect.getValue(), getCurrentUserId());
                var notification = Notification.show(
                        "Participant added to " + added.size() + " competition(s)");
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

    private void advanceStatus(Competition competition) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Advance Status");
        var nextStatus = competition.getStatus().next()
                .map(CompetitionStatus::getDisplayName).orElse("—");
        dialog.add("Advance competition '" + competition.getName() + "' from "
                + competition.getStatus().getDisplayName() + " to "
                + nextStatus + "?");

        var confirmButton = new Button("Advance", e -> {
            try {
                competitionService.advanceStatus(competition.getId(), getCurrentUserId());
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

    private void openDeleteDialog(Competition competition) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Competition");
        dialog.add("Are you sure you want to delete \"" + competition.getName() + "\"? "
                + "This will also remove all participants and categories.");

        var confirmButton = new Button("Delete", e -> {
            try {
                competitionService.deleteCompetition(competition.getId(), getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Competition deleted successfully");
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

    private Span createStatusBadge(CompetitionStatus status) {
        var badge = new Span(status.getDisplayName());
        badge.getElement().getThemeList().add("badge pill small");
        badge.addClassName(status.getBadgeCssClass());
        return badge;
    }

    private void refreshGrid() {
        grid.setItems(competitionService.findAuthorizedCompetitions(eventId, currentUserId));
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
