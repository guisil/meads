package app.meads.admin.internal;

import app.meads.entrant.api.Entrant;
import app.meads.entrant.api.EntrantService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParam;
import jakarta.annotation.security.PermitAll;

@Route(value = "admin/entrants", layout = AdminLayout.class)
@PageTitle("Entrants | MEADS Admin")
@PermitAll
public class EntrantListView extends VerticalLayout {

    private final EntrantService entrantService;
    private final Grid<Entrant> grid = new Grid<>(Entrant.class, false);
    private final TextField searchField = new TextField();

    public EntrantListView(EntrantService entrantService) {
        this.entrantService = entrantService;

        addClassName("entrant-list-view");
        setSizeFull();

        configureGrid();
        add(createHeader(), createToolbar(), grid);

        refreshGrid();
    }

    private H2 createHeader() {
        return new H2("Entrants");
    }

    private HorizontalLayout createToolbar() {
        searchField.setPlaceholder("Search by email or name...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> refreshGrid());
        searchField.setWidth("300px");

        var addButton = new Button("Add Entrant", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(EntrantFormView.class)));

        var toolbar = new HorizontalLayout(searchField, addButton);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        return toolbar;
    }

    private void configureGrid() {
        grid.addClassName("entrant-grid");
        grid.setSizeFull();

        grid.addColumn(Entrant::name).setHeader("Name").setSortable(true).setAutoWidth(true);
        grid.addColumn(Entrant::email).setHeader("Email").setSortable(true).setAutoWidth(true);
        grid.addColumn(Entrant::city).setHeader("City").setSortable(true).setAutoWidth(true);
        grid.addColumn(Entrant::country).setHeader("Country").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(entrant -> {
            var editButton = new Button("Edit", VaadinIcon.EDIT.create());
            editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            editButton.addClickListener(e ->
                getUI().ifPresent(ui -> ui.navigate(EntrantFormView.class,
                    new RouteParam("id", entrant.id().toString()))));

            var creditsButton = new Button("Credits", VaadinIcon.TICKET.create());
            creditsButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            creditsButton.addClickListener(e ->
                getUI().ifPresent(ui -> ui.navigate(EntryCreditListView.class,
                    new RouteParam("id", entrant.id().toString()))));

            return new HorizontalLayout(editButton, creditsButton);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void refreshGrid() {
        var searchTerm = searchField.getValue().toLowerCase();
        var entrants = entrantService.findAllEntrants().stream()
            .filter(e -> searchTerm.isEmpty() ||
                e.email().toLowerCase().contains(searchTerm) ||
                e.name().toLowerCase().contains(searchTerm))
            .toList();
        grid.setItems(entrants);
    }
}
