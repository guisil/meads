package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.awards.AwardsService;
import app.meads.awards.Publication;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.ZoneId;
import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName/results-admin", layout = MainLayout.class)
@PermitAll
public class AwardsAdminView extends VerticalLayout implements BeforeEnterObserver {

    private final AwardsService awardsService;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    private String compShortName;
    private String divShortName;
    private Competition competition;
    private Division division;
    private UUID currentUserId;

    public AwardsAdminView(AwardsService awardsService,
                           CompetitionService competitionService,
                           UserService userService,
                           AuthenticationContext authenticationContext) {
        this.awardsService = awardsService;
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        divShortName = event.getRouteParameters().get("divShortName").orElse(null);
        if (compShortName == null || divShortName == null) {
            event.forwardTo("");
            return;
        }
        try {
            competition = competitionService.findCompetitionByShortName(compShortName);
            division = competitionService.findDivisionByShortName(competition.getId(), divShortName);
        } catch (BusinessRuleException e) {
            event.forwardTo("");
            return;
        }
        currentUserId = getCurrentUserId();
        var user = userService.findById(currentUserId);
        if (user.getRole() != Role.SYSTEM_ADMIN
                && !competitionService.isAuthorizedForDivision(division.getId(), currentUserId)) {
            event.forwardTo("");
            return;
        }
        if (user.getRole() != Role.SYSTEM_ADMIN && !userService.hasPassword(currentUserId)) {
            event.forwardTo("");
            return;
        }
        if (division.getStatus().ordinal() < DivisionStatus.DELIBERATION.ordinal()) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName);
            return;
        }
        renderPage();
    }

    private void renderPage() {
        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        add(createActions());
        add(createPublicationHistory());
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        var user = userService.findById(currentUserId);
        boolean isSystemAdmin = user.getRole() == Role.SYSTEM_ADMIN;
        nav.add(new Anchor(
                isSystemAdmin ? "competitions" : "my-competitions",
                isSystemAdmin ? getTranslation("nav.competitions") : getTranslation("nav.my-competitions")));
        nav.add(new Span(" / "));
        nav.add(new Anchor("competitions/" + compShortName, competition.getName()));
        nav.add(new Span(" / "));
        nav.add(new Anchor(
                "competitions/" + compShortName + "/divisions/" + divShortName,
                division.getName()));
        nav.add(new Span(" / "));
        nav.add(new Span(getTranslation("awards.admin.nav.results-admin")));
        return nav;
    }

    private HorizontalLayout createHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.add(new H2(competition.getName() + " — " + division.getName()
                + " — " + getTranslation("awards.admin.nav.results-admin")));
        return header;
    }

    private HorizontalLayout createActions() {
        var actions = new HorizontalLayout();
        actions.setId("awards-admin-actions");

        if (division.getStatus() == DivisionStatus.DELIBERATION) {
            var publishBtn = new Button(getTranslation("awards.admin.publish"),
                    e -> openPublishDialog());
            publishBtn.setId("awards-publish-button");
            publishBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            publishBtn.setDisableOnClick(true);
            actions.add(publishBtn);
        } else if (division.getStatus() == DivisionStatus.RESULTS_PUBLISHED) {
            var republishBtn = new Button(getTranslation("awards.admin.republish"),
                    e -> openRepublishDialog());
            republishBtn.setId("awards-republish-button");
            republishBtn.setDisableOnClick(true);
            actions.add(republishBtn);

            var announceBtn = new Button(getTranslation("awards.admin.send-announcement"),
                    e -> openAnnouncementDialog());
            announceBtn.setId("awards-announce-button");
            announceBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            announceBtn.setDisableOnClick(true);
            actions.add(announceBtn);

            var revertBtn = new Button(getTranslation("awards.admin.revert"),
                    e -> openRevertDialog());
            revertBtn.setId("awards-revert-button");
            revertBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
            revertBtn.setDisableOnClick(true);
            actions.add(revertBtn);
        }
        return actions;
    }

    private VerticalLayout createPublicationHistory() {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3(getTranslation("awards.admin.history.heading")));
        var grid = new Grid<Publication>();
        grid.setId("awards-publication-history");
        grid.addColumn(Publication::getVersion)
                .setHeader(getTranslation("awards.admin.history.column.version"));
        grid.addColumn(p -> formatInstant(p.getPublishedAt()))
                .setHeader(getTranslation("awards.admin.history.column.published-at"));
        grid.addColumn(p -> userService.findById(p.getPublishedBy()).getName())
                .setHeader(getTranslation("awards.admin.history.column.published-by"));
        grid.addColumn(Publication::getJustification)
                .setHeader(getTranslation("awards.admin.history.column.justification"));
        grid.setItems(awardsService.getPublicationHistory(division.getId()));
        grid.setAllRowsVisible(true);
        section.add(grid);
        return section;
    }

    private void openPublishDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("awards.admin.publish.confirm.title"));
        dialog.add(new Span(getTranslation("awards.admin.publish.confirm.body")));
        var publish = new Button(getTranslation("awards.admin.publish"), e -> {
            try {
                awardsService.publish(division.getId(), currentUserId);
                Notification.show(getTranslation("awards.admin.publish.success"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                UI.getCurrent().getPage().reload();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessage(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        publish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        publish.setDisableOnClick(true);
        dialog.getFooter().add(new Button(getTranslation("button.cancel"), e -> dialog.close()), publish);
        dialog.open();
    }

    private void openRepublishDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("awards.admin.republish.title"));
        var justification = new TextArea(getTranslation("awards.admin.republish.justification.label"));
        justification.setHelperText(getTranslation("awards.admin.republish.justification.helper"));
        justification.setMaxLength(1000);
        justification.setWidthFull();
        justification.setMinHeight("120px");
        dialog.add(justification);
        var confirm = new Button(getTranslation("awards.admin.republish"), e -> {
            try {
                awardsService.republish(division.getId(), justification.getValue(), currentUserId);
                Notification.show(getTranslation("awards.admin.republish.success"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                UI.getCurrent().getPage().reload();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessage(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        dialog.getFooter().add(new Button(getTranslation("button.cancel"), e -> dialog.close()), confirm);
        dialog.open();
    }

    private void openAnnouncementDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("awards.admin.announce.title"));
        var message = new TextArea(getTranslation("awards.admin.announce.message.label"));
        message.setHelperText(getTranslation("awards.admin.announce.message.helper"));
        message.setMaxLength(2000);
        message.setWidthFull();
        message.setMinHeight("120px");
        dialog.add(message);
        var send = new Button(getTranslation("awards.admin.send-announcement"), e -> {
            try {
                awardsService.sendAnnouncement(division.getId(),
                        message.isEmpty() ? null : message.getValue(), currentUserId);
                Notification.show(getTranslation("awards.admin.announce.success"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessage(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        send.setDisableOnClick(true);
        dialog.getFooter().add(new Button(getTranslation("button.cancel"), e -> dialog.close()), send);
        dialog.open();
    }

    private void openRevertDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("awards.admin.revert.title"));
        dialog.add(new Span(getTranslation("awards.admin.revert.body")));
        var confirmField = new TextField(getTranslation("awards.admin.revert.token.label"));
        confirmField.setHelperText(getTranslation("awards.admin.revert.token.helper"));
        confirmField.setWidthFull();
        dialog.add(confirmField);
        var confirm = new Button(getTranslation("awards.admin.revert"), e -> {
            if (!"REVERT".equals(confirmField.getValue())) {
                Notification.show(getTranslation("awards.admin.revert.token.mismatch"))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
                return;
            }
            try {
                competitionService.revertDivisionStatus(division.getId(), currentUserId);
                Notification.show(getTranslation("awards.admin.revert.success"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                UI.getCurrent().getPage().reload();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessage(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirm.setDisableOnClick(true);
        dialog.getFooter().add(new Button(getTranslation("button.cancel"), e -> dialog.close()), confirm);
        dialog.open();
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(getLocale()).withZone(ZoneId.systemDefault())
                .format(instant);
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(u -> userService.findByEmail(u.getUsername()).getId())
                .orElseThrow(() -> new BusinessRuleException("error.auth.unauthorized"));
    }
}
