package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.*;
import app.meads.identity.User;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
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
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Route(value = "competitions/:competitionId", layout = MainLayout.class)
@PermitAll
public class CompetitionDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    private UUID competitionId;
    private Competition competition;
    private MeadEvent event;
    private Grid<CompetitionParticipant> participantsGrid;
    private Grid<CompetitionCategory> categoriesGrid;
    private Map<UUID, EventParticipant> eventParticipantMap;
    private Map<UUID, User> userMap;

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

        if (!competitionService.isAuthorizedForCompetition(competitionId, getCurrentUserId())) {
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
        var eventLink = new RouterLink(event.getName(), CompetitionListView.class,
                new RouteParameters("eventId", event.getId().toString()));
        nav.add(eventLink);
        nav.add(new Span(" / "));
        nav.add(new Span(competition.getName()));
        return nav;
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
        actions.add(addButton);
        tab.add(actions);

        participantsGrid = new Grid<>();
        participantsGrid.addColumn(p -> {
            var ep = eventParticipantMap.get(p.getEventParticipantId());
            var user = ep != null ? userMap.get(ep.getUserId()) : null;
            return user != null ? user.getName() : "Unknown";
        }).setHeader("Name");
        participantsGrid.addColumn(p -> {
            var ep = eventParticipantMap.get(p.getEventParticipantId());
            var user = ep != null ? userMap.get(ep.getUserId()) : null;
            return user != null ? user.getEmail() : "—";
        }).setHeader("Email");
        participantsGrid.addColumn(p -> p.getRole().name()).setHeader("Role");

        refreshParticipantsGrid();
        tab.add(participantsGrid);
        return tab;
    }

    private VerticalLayout createCategoriesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        boolean allowModification = competition.getStatus().allowsCategoryModification();

        var actions = new HorizontalLayout();
        var addCategoryButton = new Button("Add Category",
                e -> openAddCategoryDialog());
        addCategoryButton.setEnabled(allowModification);
        actions.add(addCategoryButton);
        tab.add(actions);

        categoriesGrid = new Grid<>(CompetitionCategory.class, false);
        categoriesGrid.setId("categories-grid");
        categoriesGrid.addColumn(CompetitionCategory::getCode).setHeader("Code").setSortable(true);
        categoriesGrid.addColumn(CompetitionCategory::getName).setHeader("Name");
        categoriesGrid.addColumn(CompetitionCategory::getDescription).setHeader("Description");

        categoriesGrid.addComponentColumn(cc -> {
            var removeButton = new Button("Remove", e -> {
                try {
                    competitionService.removeCompetitionCategory(
                            competitionId, cc.getId(), getCurrentUserId());
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
        categoriesGrid.setItems(
                competitionService.findCompetitionCategories(competitionId));
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
                competitionService.findAvailableCatalogCategories(competitionId));

        var catalogAddButton = new Button("Add", e -> {
            if (catalogSelect.getValue() == null) {
                return;
            }
            try {
                competitionService.addCatalogCategory(
                        competitionId, catalogSelect.getValue().getId(), getCurrentUserId());
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

        var parentSelect = new Select<CompetitionCategory>();
        parentSelect.setLabel("Parent Category (optional)");
        parentSelect.setEmptySelectionAllowed(true);
        parentSelect.setEmptySelectionCaption("None");
        parentSelect.setItemLabelGenerator(cc ->
                cc != null ? cc.getCode() + " — " + cc.getName() : "");
        var topLevel = competitionService.findCompetitionCategories(competitionId).stream()
                .filter(cc -> cc.getParentId() == null)
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
                        competitionId, codeField.getValue().trim(),
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
        statusField.setValue(competition.getStatus().getDisplayName());
        statusField.setReadOnly(true);

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            try {
                competition = competitionService.updateCompetition(
                        competitionId, nameField.getValue(),
                        scoringSelect.getValue(), getCurrentUserId());
                var notification = Notification.show("Settings saved successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalStateException | IllegalArgumentException ex) {
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
                refreshParticipantsGrid();
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

    private void advanceStatus() {
        var nextStatusName = competition.getStatus().next()
                .map(CompetitionStatus::getDisplayName).orElse("—");

        var dialog = new Dialog();
        dialog.setHeaderTitle("Advance Status");
        dialog.add("Advance from " + competition.getStatus().getDisplayName()
                + " to " + nextStatusName + "?");

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

    private void refreshParticipantsGrid() {
        var participants = competitionService.findParticipantsByCompetition(competitionId);

        var eventParticipants = competitionService.findEventParticipantsByEvent(event.getId());
        eventParticipantMap = eventParticipants.stream()
                .collect(Collectors.toMap(EventParticipant::getId, Function.identity()));

        var userIds = eventParticipants.stream()
                .map(EventParticipant::getUserId)
                .distinct()
                .toList();
        userMap = userService.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        participantsGrid.setItems(participants);
    }

    private Span createStatusBadge(CompetitionStatus status) {
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
