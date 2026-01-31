package app.meads.admin.internal;

import app.meads.entrant.api.AddEntryCreditCommand;
import app.meads.entrant.api.Entrant;
import app.meads.entrant.api.EntrantService;
import app.meads.entrant.api.EntryCredit;
import app.meads.event.api.Competition;
import app.meads.event.api.MeadEventService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Route(value = "admin/entrants/:id/credits", layout = AdminLayout.class)
@PageTitle("Entry Credits | MEADS Admin")
@PermitAll
public class EntryCreditListView extends VerticalLayout implements BeforeEnterObserver {

    private final EntrantService entrantService;
    private final MeadEventService meadEventService;

    private UUID entrantId;
    private Entrant entrant;
    private final H2 title = new H2("Entry Credits");
    private final Grid<EntryCredit> grid = new Grid<>(EntryCredit.class, false);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    public EntryCreditListView(EntrantService entrantService, MeadEventService meadEventService) {
        this.entrantService = entrantService;
        this.meadEventService = meadEventService;

        addClassName("entry-credit-list-view");
        setSizeFull();

        configureGrid();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var idParam = event.getRouteParameters().get("id");
        if (idParam.isPresent()) {
            entrantId = UUID.fromString(idParam.get());
            entrantService.findEntrantById(entrantId).ifPresent(e -> {
                this.entrant = e;
                title.setText("Entry Credits for " + e.name());
                refreshView();
            });
        }
    }

    private void refreshView() {
        removeAll();
        add(createHeader(), createToolbar(), grid);
        refreshGrid();
    }

    private HorizontalLayout createHeader() {
        var backButton = new Button("Back to Entrants", VaadinIcon.ARROW_LEFT.create());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backButton.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(EntrantListView.class)));

        var header = new HorizontalLayout(backButton, title);
        header.setAlignItems(Alignment.CENTER);
        return header;
    }

    private HorizontalLayout createToolbar() {
        var addButton = new Button("Add Credit", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openAddCreditDialog());

        var toolbar = new HorizontalLayout(addButton);
        return toolbar;
    }

    private void configureGrid() {
        grid.addClassName("credit-grid");
        grid.setSizeFull();

        grid.addColumn(credit -> {
            var comp = meadEventService.findCompetitionById(credit.competitionId());
            return comp.map(Competition::name).orElse(credit.competitionId().toString());
        }).setHeader("Competition").setSortable(true).setAutoWidth(true);

        grid.addColumn(EntryCredit::quantity).setHeader("Quantity").setSortable(true).setAutoWidth(true);
        grid.addColumn(EntryCredit::usedCount).setHeader("Used").setSortable(true).setAutoWidth(true);
        grid.addColumn(EntryCredit::availableCredits).setHeader("Available").setSortable(true).setAutoWidth(true);
        grid.addColumn(EntryCredit::status).setHeader("Status").setSortable(true).setAutoWidth(true);
        grid.addColumn(EntryCredit::externalSource).setHeader("Source").setSortable(true).setAutoWidth(true);
        grid.addColumn(credit -> credit.purchasedAt() != null ? formatter.format(credit.purchasedAt()) : "")
            .setHeader("Purchased").setSortable(true).setAutoWidth(true);
    }

    private void refreshGrid() {
        var credits = entrantService.findCreditsByEntrantId(entrantId);
        grid.setItems(credits);
    }

    private void openAddCreditDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Entry Credit");

        var competitions = meadEventService.findAllEvents().stream()
            .flatMap(event -> meadEventService.findCompetitionsByEventId(event.id()).stream())
            .toList();

        var competitionSelect = new ComboBox<Competition>("Competition");
        competitionSelect.setItems(competitions);
        competitionSelect.setItemLabelGenerator(Competition::name);
        competitionSelect.setRequired(true);
        competitionSelect.setWidthFull();

        var quantityField = new IntegerField("Quantity");
        quantityField.setValue(1);
        quantityField.setMin(1);
        quantityField.setMax(10);
        quantityField.setStepButtonsVisible(true);
        quantityField.setRequired(true);

        var orderIdField = new TextField("External Order ID");
        orderIdField.setRequired(true);
        orderIdField.setPlaceholder("e.g., MANUAL-001");

        var sourceField = new TextField("Source");
        sourceField.setValue("manual");
        sourceField.setRequired(true);

        var form = new VerticalLayout(competitionSelect, quantityField, orderIdField, sourceField);
        form.setPadding(false);
        form.setSpacing(true);

        var saveButton = new Button("Add Credit", e -> {
            if (competitionSelect.isEmpty() || orderIdField.isEmpty()) {
                Notification.show("Please fill all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                var command = new AddEntryCreditCommand(
                    entrantId,
                    competitionSelect.getValue().id(),
                    quantityField.getValue(),
                    orderIdField.getValue(),
                    sourceField.getValue(),
                    Instant.now()
                );
                entrantService.addCredit(command);
                Notification.show("Credit added successfully", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error adding credit: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);

        dialog.open();
    }
}
