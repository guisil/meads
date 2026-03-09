package app.meads.entry.internal;

import app.meads.MainLayout;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.entry.*;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName/my-entries", layout = MainLayout.class)
@PermitAll
public class MyEntriesView extends VerticalLayout implements BeforeEnterObserver {

    private final EntryService entryService;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    private UUID divisionId;
    private Division division;
    private String compShortName;
    private String divShortName;
    private UUID currentUserId;
    private Grid<Entry> entriesGrid;

    public MyEntriesView(EntryService entryService,
                          CompetitionService competitionService,
                          UserService userService,
                          AuthenticationContext authenticationContext) {
        this.entryService = entryService;
        this.competitionService = competitionService;
        this.userService = userService;
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
            var competition = competitionService.findCompetitionByShortName(compShortName);
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

        removeAll();
        add(createHeader());
        add(createCreditInfo());
        add(createActionButtons());
        add(createEntriesGrid());
    }

    private H2 createHeader() {
        return new H2(division.getName() + " — My Entries");
    }

    private Span createCreditInfo() {
        var creditBalance = entryService.getCreditBalance(divisionId, currentUserId);
        var activeEntries = entryService.countActiveEntries(divisionId, currentUserId);
        var remaining = creditBalance - activeEntries;
        return new Span("Credits: " + remaining + " remaining (" + creditBalance
                + " total, " + activeEntries + " used)");
    }

    private HorizontalLayout createActionButtons() {
        var actions = new HorizontalLayout();

        var creditBalance = entryService.getCreditBalance(divisionId, currentUserId);
        var activeEntries = entryService.countActiveEntries(divisionId, currentUserId);
        var isOpen = division.getStatus() == DivisionStatus.REGISTRATION_OPEN;

        var addButton = new Button("Add Entry", e -> openEntryDialog(null));
        addButton.setEnabled(isOpen && creditBalance > activeEntries);
        actions.add(addButton);

        var entries = entryService.findEntriesByDivisionAndUser(divisionId, currentUserId);
        var hasDrafts = entries.stream().anyMatch(en -> en.getStatus() == EntryStatus.DRAFT);

        var submitButton = new Button("Submit All", e -> submitAll());
        submitButton.setEnabled(hasDrafts);
        actions.add(submitButton);

        return actions;
    }

    private Grid<Entry> createEntriesGrid() {
        entriesGrid = new Grid<>(Entry.class, false);
        entriesGrid.setAllRowsVisible(true);
        entriesGrid.setId("entries-grid");
        entriesGrid.addColumn(Entry::getEntryNumber).setHeader("Entry #");
        entriesGrid.addColumn(Entry::getMeadName).setHeader("Mead Name");
        entriesGrid.addColumn(entry -> {
            var categories = competitionService.findDivisionCategories(divisionId);
            return categories.stream()
                    .filter(c -> c.getId().equals(entry.getInitialCategoryId()))
                    .map(DivisionCategory::getName)
                    .findFirst()
                    .orElse("—");
        }).setHeader("Category");
        entriesGrid.addColumn(entry -> entry.getStatus().name()).setHeader("Status");

        refreshGrid();
        return entriesGrid;
    }

    private void refreshGrid() {
        var entries = entryService.findEntriesByDivisionAndUser(divisionId, currentUserId);
        entriesGrid.setItems(entries);
    }

    private void openEntryDialog(Entry existing) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add Entry" : "Edit Entry");
        dialog.setWidth("600px");

        var layout = new VerticalLayout();
        layout.setPadding(false);

        var meadName = new TextField("Mead Name");
        var categorySelect = new Select<DivisionCategory>();
        categorySelect.setLabel("Category");
        categorySelect.setItemLabelGenerator(dc ->
                dc.getCode() + " — " + dc.getName());
        categorySelect.setItems(competitionService.findDivisionCategories(divisionId));

        var sweetness = new Select<Sweetness>();
        sweetness.setLabel("Sweetness");
        sweetness.setItems(Sweetness.values());
        sweetness.setItemLabelGenerator(Sweetness::getDisplayName);

        var strength = new Select<Strength>();
        strength.setLabel("Strength");
        strength.setItems(Strength.values());
        strength.setItemLabelGenerator(Strength::getDisplayName);

        var abv = new NumberField("ABV (%)");
        abv.setStep(0.1);
        abv.setMin(0);
        abv.setMax(30);

        var carbonation = new Select<Carbonation>();
        carbonation.setLabel("Carbonation");
        carbonation.setItems(Carbonation.values());
        carbonation.setItemLabelGenerator(Carbonation::getDisplayName);

        var honeyVarieties = new TextArea("Honey Varieties");
        var otherIngredients = new TextArea("Other Ingredients");

        var woodAged = new Checkbox("Wood Aged");
        var woodAgeingDetails = new TextArea("Wood Ageing Details");
        woodAgeingDetails.setVisible(false);
        woodAged.addValueChangeListener(e -> woodAgeingDetails.setVisible(e.getValue()));

        var additionalInfo = new TextArea("Additional Information");

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
                refreshGrid();
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
                refreshGrid();
                var notification = Notification.show(draftCount + " entries submitted");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                // Refresh the whole page to update buttons
                getUI().ifPresent(ui -> ui.navigate(
                        "competitions/" + compShortName
                                + "/divisions/" + divShortName + "/my-entries"));
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
