package app.meads.identity.internal;

import app.meads.MainLayout;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Locale;

@Slf4j
@Route(value = "profile", layout = MainLayout.class)
@PermitAll
@PageTitle("My Profile")
public class ProfileView extends VerticalLayout {

    ProfileView(UserService userService) {
        var email = SecurityContextHolder.getContext().getAuthentication().getName();
        var user = userService.findByEmail(email);

        var emailField = new TextField("Email");
        emailField.setValue(user.getEmail());
        emailField.setReadOnly(true);
        emailField.setWidthFull();

        var nameField = new TextField("Name");
        nameField.setValue(user.getName());
        nameField.setRequired(true);
        nameField.setWidthFull();

        var meaderyField = new TextField("Meadery Name");
        meaderyField.setValue(user.getMeaderyName() != null ? user.getMeaderyName() : "");
        meaderyField.setWidthFull();

        var countryCombo = new ComboBox<String>("Country");
        var countries = Arrays.stream(Locale.getISOCountries())
                .sorted((a, b) -> new Locale("", a).getDisplayCountry(Locale.ENGLISH)
                        .compareTo(new Locale("", b).getDisplayCountry(Locale.ENGLISH)))
                .toList();
        countryCombo.setItems(countries);
        countryCombo.setItemLabelGenerator(code ->
                new Locale("", code).getDisplayCountry(Locale.ENGLISH));
        countryCombo.setClearButtonVisible(true);
        countryCombo.setWidthFull();
        if (user.getCountry() != null) {
            countryCombo.setValue(user.getCountry());
        }

        var saveButton = new Button("Save", e -> {
            var name = nameField.getValue();
            if (name == null || name.isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            try {
                var meadery = meaderyField.getValue();
                userService.updateProfile(user.getId(), name.trim(),
                        meadery != null && !meadery.isBlank() ? meadery.trim() : null,
                        countryCombo.getValue());
                Notification.show("Profile updated")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show(ex.getMessage());
            }
        });

        add(emailField, nameField, meaderyField, countryCombo, saveButton);
        setMaxWidth("600px");
    }
}
