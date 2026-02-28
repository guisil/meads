package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.CompetitionStatus;
import app.meads.competition.Event;
import app.meads.competition.ScoringSystem;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Route(value = "events/:eventId/competitions", layout = MainLayout.class)
@RolesAllowed("SYSTEM_ADMIN")
public class CompetitionListView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<Competition> grid;

    private UUID eventId;
    private Event event;

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
            return new HorizontalLayout(viewButton, advanceButton);
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
            event = competitionService.findEventById(eventId);
        } catch (IllegalArgumentException e) {
            beforeEnterEvent.forwardTo("events");
            return;
        }

        removeAll();
        add(createEventHeader());
        add(createActionBar());
        refreshGrid();
        add(grid);
    }

    private HorizontalLayout createEventHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setWidthFull();

        var textBlock = new VerticalLayout();
        textBlock.setPadding(false);
        textBlock.setSpacing(false);
        textBlock.add(new H2(event.getName()));

        var details = formatDateRange() +
                (event.getLocation() != null ? "  ·  " + event.getLocation() : "");
        textBlock.add(new Span(details));

        header.add(textBlock);
        return header;
    }

    private String formatDateRange() {
        var start = event.getStartDate();
        var end = event.getEndDate();
        var formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

        if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
            return start.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                    + "–" + end.format(DateTimeFormatter.ofPattern("d, yyyy", Locale.ENGLISH));
        }
        return start.format(formatter) + " – " + end.format(formatter);
    }

    private HorizontalLayout createActionBar() {
        var createButton = new Button("Create Competition", e -> openCreateDialog());
        var bar = new HorizontalLayout(createButton);
        bar.setWidthFull();
        bar.setJustifyContentMode(JustifyContentMode.END);
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
            if (nameField.getValue().isBlank()) {
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

    private void advanceStatus(Competition competition) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Advance Status");
        dialog.add("Advance competition '" + competition.getName() + "' from "
                + formatStatus(competition.getStatus()) + " to "
                + formatStatus(nextStatus(competition.getStatus())) + "?");

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

    private Span createStatusBadge(CompetitionStatus status) {
        var badge = new Span(formatStatus(status));
        badge.getElement().getThemeList().add("badge pill small");
        badge.addClassName(switch (status) {
            case DRAFT -> "badge-draft";
            case REGISTRATION_OPEN -> "badge-registration-open";
            case REGISTRATION_CLOSED -> "badge-registration-closed";
            case JUDGING -> "badge-judging";
            case DELIBERATION -> "badge-deliberation";
            case RESULTS_PUBLISHED -> "badge-results-published";
        });
        return badge;
    }

    private String formatStatus(CompetitionStatus status) {
        return switch (status) {
            case DRAFT -> "Draft";
            case REGISTRATION_OPEN -> "Registration Open";
            case REGISTRATION_CLOSED -> "Registration Closed";
            case JUDGING -> "Judging";
            case DELIBERATION -> "Deliberation";
            case RESULTS_PUBLISHED -> "Results Published";
        };
    }

    private CompetitionStatus nextStatus(CompetitionStatus status) {
        return switch (status) {
            case DRAFT -> CompetitionStatus.REGISTRATION_OPEN;
            case REGISTRATION_OPEN -> CompetitionStatus.REGISTRATION_CLOSED;
            case REGISTRATION_CLOSED -> CompetitionStatus.JUDGING;
            case JUDGING -> CompetitionStatus.DELIBERATION;
            case DELIBERATION -> CompetitionStatus.RESULTS_PUBLISHED;
            case RESULTS_PUBLISHED -> status;
        };
    }

    private void refreshGrid() {
        grid.setItems(competitionService.findByEvent(eventId));
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
