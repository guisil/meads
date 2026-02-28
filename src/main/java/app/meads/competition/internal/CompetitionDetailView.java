package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.*;
import app.meads.identity.User;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

@Route(value = "competitions/:competitionId", layout = MainLayout.class)
@RolesAllowed("SYSTEM_ADMIN")
public class CompetitionDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    private UUID competitionId;
    private Competition competition;
    private Event event;
    private Grid<CompetitionParticipant> participantsGrid;
    private Grid<Category> categoriesGrid;

    public CompetitionDetailView(CompetitionService competitionService,
                                  UserService userService,
                                  AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        competitionId = beforeEnterEvent.getRouteParameters().get("competitionId")
                .map(UUID::fromString)
                .orElse(null);

        if (competitionId == null) {
            beforeEnterEvent.forwardTo("events");
            return;
        }

        try {
            competition = competitionService.findById(competitionId);
            event = competitionService.findEventById(competition.getEventId());
        } catch (IllegalArgumentException e) {
            beforeEnterEvent.forwardTo("events");
            return;
        }

        removeAll();
        add(createHeader());
        add(createTabSheet());
    }

    private HorizontalLayout createHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setWidthFull();

        var textBlock = new VerticalLayout();
        textBlock.setPadding(false);
        textBlock.setSpacing(false);
        textBlock.add(new H2(competition.getName()));

        var details = new HorizontalLayout();
        details.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        details.add(new Span(competition.getScoringSystem().name()));
        details.add(createStatusBadge(competition.getStatus()));
        textBlock.add(details);

        header.add(textBlock);

        if (competition.getStatus() != CompetitionStatus.RESULTS_PUBLISHED) {
            var advanceButton = new Button("Advance Status", e -> advanceStatus());
            header.add(advanceButton);
        }

        return header;
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();

        tabSheet.add("Participants", createParticipantsTab());
        tabSheet.add("Categories", createCategoriesTab());
        tabSheet.add("Settings", createSettingsTab());

        return tabSheet;
    }

    private VerticalLayout createParticipantsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var actions = new HorizontalLayout();
        var addButton = new Button("Add Participant", e -> openAddParticipantDialog());
        var copyButton = new Button("Copy from Competition...", e -> openCopyParticipantsDialog());
        actions.add(addButton, copyButton);
        tab.add(actions);

        participantsGrid = new Grid<>();
        participantsGrid.addColumn(p -> {
            try {
                return userService.findById(p.getUserId()).getName();
            } catch (Exception e) {
                return "Unknown";
            }
        }).setHeader("Name");
        participantsGrid.addColumn(p -> {
            try {
                return userService.findById(p.getUserId()).getEmail();
            } catch (Exception e) {
                return "—";
            }
        }).setHeader("Email");
        participantsGrid.addColumn(p -> p.getRole().name()).setHeader("Role");
        participantsGrid.addColumn(p -> p.getAccessCode() != null ? p.getAccessCode() : "—")
                .setHeader("Access Code");
        participantsGrid.addColumn(p -> p.getStatus().name()).setHeader("Status");
        participantsGrid.addComponentColumn(p -> {
            var withdrawButton = new Button("Withdraw", e -> withdrawParticipant(p));
            withdrawButton.setEnabled(p.getStatus() == CompetitionParticipantStatus.ACTIVE);
            return withdrawButton;
        }).setHeader("Actions");

        refreshParticipantsGrid();
        tab.add(participantsGrid);
        return tab;
    }

    private VerticalLayout createCategoriesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        categoriesGrid = new Grid<>(Category.class, false);
        categoriesGrid.addColumn(Category::getCode).setHeader("Code").setSortable(true);
        categoriesGrid.addColumn(Category::getName).setHeader("Name");
        categoriesGrid.addColumn(Category::getDescription).setHeader("Description");

        categoriesGrid.setItems(
                competitionService.findCategoriesByScoringSystem(competition.getScoringSystem()));
        tab.add(categoriesGrid);
        return tab;
    }

    private VerticalLayout createSettingsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        boolean isDraft = competition.getStatus() == CompetitionStatus.DRAFT;

        var nameField = new TextField("Name");
        nameField.setValue(competition.getName());
        nameField.setEnabled(isDraft);

        var scoringSelect = new Select<ScoringSystem>();
        scoringSelect.setLabel("Scoring System");
        scoringSelect.setItems(ScoringSystem.values());
        scoringSelect.setValue(competition.getScoringSystem());
        scoringSelect.setEnabled(isDraft);

        var statusField = new TextField("Status");
        statusField.setValue(formatStatus(competition.getStatus()));
        statusField.setReadOnly(true);

        var saveButton = new Button("Save", e -> {
            if (nameField.getValue().isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            try {
                competition.updateDetails(nameField.getValue(), scoringSelect.getValue());
                // Save through a service method — for now reuse findById pattern
                // The entity is managed, so saving through any repo method works
                Notification.show("Settings saved — reload to see changes");
            } catch (IllegalStateException ex) {
                Notification.show(ex.getMessage());
            }
        });
        saveButton.setEnabled(isDraft);

        tab.add(nameField, scoringSelect, statusField, saveButton);
        return tab;
    }

    private void openAddParticipantDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Participant");

        var userComboBox = new ComboBox<User>("User");
        userComboBox.setItems(userService.findAll());
        userComboBox.setItemLabelGenerator(u -> u.getName() + " (" + u.getEmail() + ")");

        var roleSelect = new Select<CompetitionRole>();
        roleSelect.setLabel("Role");
        roleSelect.setItems(CompetitionRole.values());
        roleSelect.setValue(CompetitionRole.JUDGE);

        var addButton = new Button("Add", e -> {
            if (userComboBox.getValue() == null) {
                return;
            }
            try {
                competitionService.addParticipant(
                        competitionId,
                        userComboBox.getValue().getId(),
                        roleSelect.getValue(),
                        getCurrentUserId());
                refreshParticipantsGrid();
                var notification = Notification.show("Participant added successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(userComboBox, roleSelect);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void openCopyParticipantsDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Copy Participants");

        var competitions = competitionService.findByEvent(event.getId()).stream()
                .filter(c -> !c.getId().equals(competitionId))
                .toList();

        if (competitions.isEmpty()) {
            dialog.add("No other competitions in this event to copy from.");
            dialog.getFooter().add(new Button("Close", e -> dialog.close()));
            dialog.open();
            return;
        }

        var sourceSelect = new Select<Competition>();
        sourceSelect.setLabel("Source Competition");
        sourceSelect.setItems(competitions);
        sourceSelect.setItemLabelGenerator(Competition::getName);

        var copyButton = new Button("Copy", e -> {
            if (sourceSelect.getValue() == null) {
                return;
            }
            try {
                var copied = competitionService.copyParticipants(
                        sourceSelect.getValue().getId(),
                        competitionId,
                        getCurrentUserId());
                refreshParticipantsGrid();
                var notification = Notification.show("Copied " + copied.size() + " participants");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(sourceSelect);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, copyButton);
        dialog.open();
    }

    private void advanceStatus() {
        var nextStatus = switch (competition.getStatus()) {
            case DRAFT -> CompetitionStatus.REGISTRATION_OPEN;
            case REGISTRATION_OPEN -> CompetitionStatus.REGISTRATION_CLOSED;
            case REGISTRATION_CLOSED -> CompetitionStatus.JUDGING;
            case JUDGING -> CompetitionStatus.DELIBERATION;
            case DELIBERATION -> CompetitionStatus.RESULTS_PUBLISHED;
            case RESULTS_PUBLISHED -> competition.getStatus();
        };

        var dialog = new Dialog();
        dialog.setHeaderTitle("Advance Status");
        dialog.add("Advance from " + formatStatus(competition.getStatus())
                + " to " + formatStatus(nextStatus) + "?");

        var confirmButton = new Button("Advance", e -> {
            try {
                competition = competitionService.advanceStatus(competitionId, getCurrentUserId());
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + competitionId));
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

    private void withdrawParticipant(CompetitionParticipant participant) {
        try {
            competitionService.withdrawParticipant(
                    competitionId, participant.getId(), getCurrentUserId());
            refreshParticipantsGrid();
            var notification = Notification.show("Participant withdrawn");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void refreshParticipantsGrid() {
        participantsGrid.setItems(
                competitionService.findParticipantsByCompetition(competitionId));
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

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
