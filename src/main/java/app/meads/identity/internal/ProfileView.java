package app.meads.identity.internal;

import app.meads.BusinessRuleException;
import app.meads.LanguageMapping;
import app.meads.MainLayout;
import app.meads.MeadsI18NProvider;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;


@Slf4j
@Route(value = "profile", layout = MainLayout.class)
@PermitAll
@PageTitle("My Profile")
public class ProfileView extends VerticalLayout {

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
        languageSelect.setItemLabelGenerator(MeadsI18NProvider::getLanguageLabel);
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
                e.getSource().setEnabled(true);
                return;
            }
            try {
                var meadery = meaderyField.getValue();
                userService.updateProfile(user.getId(), name.trim(),
                        meadery != null && !meadery.isBlank() ? meadery.trim() : null,
                        countryCombo.getValue(), languageSelect.getValue());
                e.getSource().setEnabled(true);
                Notification.show(getTranslation("profile.updated"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                // Full browser navigation so MainLayout reconstructs with new locale
                getUI().ifPresent(ui -> ui.getPage().setLocation("/"));
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancelButton = new Button(getTranslation("profile.cancel"), e -> navigateBack());

        var buttons = new HorizontalLayout(cancelButton, saveButton);

        add(emailField, nameField, meaderyField, countryCombo, languageSelect, buttons);

        if (user.getRole() == Role.SYSTEM_ADMIN) {
            add(new Hr());
            add(buildMfaSection(user, userService));
        }

        setMaxWidth("600px");
    }

    private VerticalLayout buildMfaSection(User user, UserService userService) {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        section.add(new H3(getTranslation("profile.mfa.title")));

        if (user.isMfaEnabled()) {
            var statusSpan = new Span(getTranslation("profile.mfa.status.enabled"));
            statusSpan.getStyle().set("color", "var(--lumo-success-color)");
            var disableButton = new Button(getTranslation("profile.mfa.disable"), e -> {
                userService.disableMfa(user.getId());
                Notification.show(getTranslation("profile.mfa.disabled"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.getPage().setLocation("/profile"));
            });
            disableButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
            section.add(statusSpan, disableButton);
        } else {
            var statusSpan = new Span(getTranslation("profile.mfa.status.disabled"));
            statusSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
            var setupButton = new Button(getTranslation("profile.mfa.setup"), e ->
                    openMfaSetupDialog(user, userService));
            setupButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            section.add(statusSpan, setupButton);
        }

        return section;
    }

    private void openMfaSetupDialog(User user, UserService userService) {
        var qrUri = userService.setupMfa(user.getId());
        var secret = extractSecretFromUri(qrUri);

        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("profile.mfa.setup-dialog.title"));

        var content = new VerticalLayout();
        content.setPadding(false);
        content.setAlignItems(FlexComponent.Alignment.CENTER);
        content.add(new Paragraph(getTranslation("profile.mfa.setup-dialog.instructions")));

        var qrImage = generateQrCodeImage(qrUri);
        if (qrImage != null) {
            content.add(qrImage);
        }

        var secretField = new TextField(getTranslation("profile.mfa.setup-dialog.secret-label"));
        secretField.setValue(secret);
        secretField.setReadOnly(true);
        secretField.setWidthFull();
        secretField.getStyle().set("font-family", "monospace");

        var codeField = new TextField(getTranslation("profile.mfa.setup-dialog.code-label"));
        codeField.setMaxLength(6);
        codeField.setPlaceholder("000000");
        codeField.setWidthFull();

        content.setAlignSelf(FlexComponent.Alignment.STRETCH, secretField);
        content.setAlignSelf(FlexComponent.Alignment.STRETCH, codeField);
        content.add(secretField, codeField);
        dialog.add(content);

        var confirmButton = new Button(getTranslation("profile.mfa.setup-dialog.confirm"), e -> {
            var code = codeField.getValue().trim();
            if (code.isBlank()) {
                codeField.setInvalid(true);
                return;
            }
            try {
                userService.confirmMfa(user.getId(), code);
                dialog.close();
                Notification.show(getTranslation("profile.mfa.enabled"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.getPage().setLocation("/profile"));
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancelButton = new Button(getTranslation("profile.mfa.setup-dialog.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private Image generateQrCodeImage(String uri) {
        try {
            var bitMatrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 200, 200);
            var binaryImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            var rgbImage = new BufferedImage(binaryImage.getWidth(), binaryImage.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = rgbImage.createGraphics();
            g.drawImage(binaryImage, 0, 0, null);
            g.dispose();
            var out = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "PNG", out);
            var base64 = Base64.getEncoder().encodeToString(out.toByteArray());
            var img = new Image("data:image/png;base64," + base64, "QR code");
            img.setWidth("200px");
            img.setHeight("200px");
            return img;
        } catch (WriterException | IOException e) {
            log.warn("Failed to generate MFA QR code: {}", e.getMessage());
            return null;
        }
    }

    private String extractSecretFromUri(String uri) {
        var secretParam = "secret=";
        var start = uri.indexOf(secretParam);
        if (start < 0) return "";
        start += secretParam.length();
        var end = uri.indexOf('&', start);
        return end < 0 ? uri.substring(start) : uri.substring(start, end);
    }

    private void navigateBack() {
        getUI().ifPresent(ui -> ui.navigate(""));
    }
}
