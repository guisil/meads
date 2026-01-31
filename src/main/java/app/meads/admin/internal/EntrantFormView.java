package app.meads.admin.internal;

import app.meads.entrant.api.Entrant;
import app.meads.entrant.api.EntrantService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.UUID;

@Route(value = "admin/entrants/:id?", layout = AdminLayout.class)
@PageTitle("Entrant | MEADS Admin")
@PermitAll
public class EntrantFormView extends VerticalLayout implements BeforeEnterObserver {

    private final EntrantService entrantService;

    private UUID entrantId;
    private final H2 title = new H2("New Entrant");

    private final EmailField email = new EmailField("Email");
    private final TextField name = new TextField("Name");
    private final TextField phone = new TextField("Phone");
    private final TextField addressLine1 = new TextField("Address Line 1");
    private final TextField addressLine2 = new TextField("Address Line 2");
    private final TextField city = new TextField("City");
    private final TextField stateProvince = new TextField("State/Province");
    private final TextField postalCode = new TextField("Postal Code");
    private final TextField country = new TextField("Country");

    private final Button saveButton = new Button("Save");
    private final Button cancelButton = new Button("Cancel");

    public EntrantFormView(EntrantService entrantService) {
        this.entrantService = entrantService;

        addClassName("entrant-form-view");
        setMaxWidth("800px");

        email.setRequiredIndicatorVisible(true);
        name.setRequiredIndicatorVisible(true);

        var formLayout = new FormLayout();
        formLayout.add(email, name, phone, addressLine1, addressLine2, city, stateProvince, postalCode, country);
        formLayout.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> save());

        cancelButton.addClickListener(e -> navigateToList());

        var buttons = new HorizontalLayout(saveButton, cancelButton);

        add(title, formLayout, buttons);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var idParam = event.getRouteParameters().get("id");
        if (idParam.isPresent() && !idParam.get().equals("new")) {
            entrantId = UUID.fromString(idParam.get());
            title.setText("Edit Entrant");
            loadEntrant();
        }
    }

    private void loadEntrant() {
        entrantService.findEntrantById(entrantId).ifPresent(entrant -> {
            email.setValue(entrant.email() != null ? entrant.email() : "");
            name.setValue(entrant.name() != null ? entrant.name() : "");
            phone.setValue(entrant.phone() != null ? entrant.phone() : "");
            addressLine1.setValue(entrant.addressLine1() != null ? entrant.addressLine1() : "");
            addressLine2.setValue(entrant.addressLine2() != null ? entrant.addressLine2() : "");
            city.setValue(entrant.city() != null ? entrant.city() : "");
            stateProvince.setValue(entrant.stateProvince() != null ? entrant.stateProvince() : "");
            postalCode.setValue(entrant.postalCode() != null ? entrant.postalCode() : "");
            country.setValue(entrant.country() != null ? entrant.country() : "");
        });
    }

    private void save() {
        if (email.getValue().isBlank() || name.getValue().isBlank()) {
            Notification.show("Email and Name are required", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        var entrant = new Entrant(
            entrantId,
            email.getValue(),
            name.getValue(),
            phone.getValue().isBlank() ? null : phone.getValue(),
            addressLine1.getValue().isBlank() ? null : addressLine1.getValue(),
            addressLine2.getValue().isBlank() ? null : addressLine2.getValue(),
            city.getValue().isBlank() ? null : city.getValue(),
            stateProvince.getValue().isBlank() ? null : stateProvince.getValue(),
            postalCode.getValue().isBlank() ? null : postalCode.getValue(),
            country.getValue().isBlank() ? null : country.getValue()
        );

        try {
            if (entrantId == null) {
                entrantService.createEntrant(entrant);
                Notification.show("Entrant created", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                entrantService.updateEntrant(entrant);
                Notification.show("Entrant updated", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }
            navigateToList();
        } catch (Exception e) {
            Notification.show("Error saving entrant: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void navigateToList() {
        getUI().ifPresent(ui -> ui.navigate(EntrantListView.class));
    }
}
