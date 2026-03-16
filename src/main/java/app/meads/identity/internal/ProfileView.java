package app.meads.identity.internal;

import app.meads.LanguageMapping;
import app.meads.MainLayout;
import app.meads.identity.UserService;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Route(value = "profile", layout = MainLayout.class)
@PermitAll
@PageTitle("My Profile")
public class ProfileView extends VerticalLayout {

    private static final Map<String, String> LANGUAGE_LABELS = Map.of(
            "en", "English",
            "pt", "Portugu\u00eas"
    );

    private final transient AuthenticationContext authenticationContext;

    ProfileView(UserService userService, AuthenticationContext authenticationContext,
                I18NProvider i18nProvider) {
        this.authenticationContext = authenticationContext;
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        var user = userService.findByEmail(email);

        var emailField = new TextField("Email");
        emailField.setValue(user.getEmail());
        emailField.setReadOnly(true);
        emailField.setWidthFull();

        var nameField = new TextField(getTranslation("profile.name.label"));
        nameField.setValue(user.getName());
        nameField.setRequired(true);
        nameField.setMaxLength(255);
        nameField.setWidthFull();

        var meaderyField = new TextField(getTranslation("profile.meadery-name.label"));
        meaderyField.setValue(user.getMeaderyName() != null ? user.getMeaderyName() : "");
        meaderyField.setMaxLength(255);
        meaderyField.setWidthFull();

        var countryCombo = new ComboBox<String>(getTranslation("profile.country.label"));
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

        var languageSelect = new Select<String>();
        languageSelect.setLabel(getTranslation("profile.language.label"));
        var supportedLanguages = i18nProvider.getProvidedLocales().stream()
                .map(Locale::getLanguage)
                .toList();
        languageSelect.setItems(supportedLanguages);
        languageSelect.setItemLabelGenerator(lang -> LANGUAGE_LABELS.getOrDefault(lang, lang));
        languageSelect.setWidthFull();

        // Set current value: explicit preference, or derived from country
        var currentLang = user.getPreferredLanguage() != null
                ? user.getPreferredLanguage()
                : LanguageMapping.languageForCountry(user.getCountry()).getLanguage();
        languageSelect.setValue(currentLang);

        var saveButton = new Button(getTranslation("profile.save"), e -> {
            var name = nameField.getValue();
            if (name == null || name.isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("profile.name-required"));
                return;
            }
            try {
                var meadery = meaderyField.getValue();
                userService.updateProfile(user.getId(), name.trim(),
                        meadery != null && !meadery.isBlank() ? meadery.trim() : null,
                        countryCombo.getValue(), languageSelect.getValue());
                Notification.show(getTranslation("profile.updated"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                // Update session locale
                getUI().ifPresent(ui -> {
                    ui.setLocale(Locale.of(languageSelect.getValue()));
                    ui.navigate("");
                });
            } catch (Exception ex) {
                Notification.show(ex.getMessage());
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancelButton = new Button(getTranslation("profile.cancel"), e -> navigateBack());

        var buttons = new HorizontalLayout(cancelButton, saveButton);

        add(emailField, nameField, meaderyField, countryCombo, languageSelect, buttons);
        setMaxWidth("600px");
    }

    private void navigateBack() {
        getUI().ifPresent(ui -> ui.navigate(""));
    }
}
