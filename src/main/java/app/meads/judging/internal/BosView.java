package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import app.meads.competition.DivisionCategory;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.User;
import app.meads.judging.BosPlacement;
import app.meads.judging.MedalAward;
import app.meads.judging.Judging;
import app.meads.judging.JudgingPhase;
import app.meads.judging.JudgingService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName/bos", layout = MainLayout.class)
@PermitAll
public class BosView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final EntryService entryService;
    private final transient AuthenticationContext authenticationContext;

    public record PlacementSlot(int place, BosPlacement placement,
                                 Entry entry, DivisionCategory category,
                                 User awardedByUser) {
        public boolean isEmpty() { return placement == null; }
    }

    private Competition competition;
    private Division division;
    private Judging judging;
    private String compShortName;
    private String divShortName;
    private UUID currentUserId;

    public BosView(CompetitionService competitionService,
                   UserService userService,
                   JudgingService judgingService,
                   EntryService entryService,
                   AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
        this.entryService = entryService;
        this.authenticationContext = authenticationContext;
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
        if (currentUserId == null) {
            event.forwardTo("");
            return;
        }
        var user = userService.findById(currentUserId);
        boolean isSystemAdmin = user.getRole() == Role.SYSTEM_ADMIN;
        boolean isDivisionAdmin = competitionService.isAuthorizedForDivision(division.getId(), currentUserId);
        if (!isSystemAdmin && !isDivisionAdmin) {
            event.forwardTo("");
            return;
        }
        if (!isSystemAdmin && !userService.hasPassword(currentUserId)) {
            event.forwardTo("");
            return;
        }

        judging = judgingService.ensureJudgingExists(division.getId());
        if (judging.getPhase() != JudgingPhase.BOS && judging.getPhase() != JudgingPhase.COMPLETE) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName + "/judging-admin");
            return;
        }

        boolean readOnly = judging.getPhase() == JudgingPhase.COMPLETE;

        removeAll();
        add(createHeader());
        if (readOnly) {
            var banner = new Span(getTranslation("bos.complete.banner"));
            banner.setId("bos-complete-banner");
            add(banner);
        }
        add(createPlacementsGrid(readOnly));
        if (!readOnly) {
            add(createCandidatesSection());
        }
        add(createBackLink());
    }

    public void openAssignDialog(int place) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("bos.assign.dialog.title", place));

        var goldAwards = judgingService.findGoldMedalAwardsForDivision(division.getId(), currentUserId);
        var placedEntryIds = placedEntryIds();
        var unplaced = goldAwards.stream()
                .filter(a -> !placedEntryIds.contains(a.getEntryId()))
                .toList();

        var candidateSelect = new Select<MedalAward>();
        candidateSelect.setId("bos-assign-candidate-select");
        candidateSelect.setLabel(getTranslation("bos.assign.candidate.label"));
        candidateSelect.setWidthFull();
        candidateSelect.setItems(unplaced);
        candidateSelect.setItemLabelGenerator(a -> a == null ? "" :
                entryCodeFor(a.getEntryId()) + " · " + meadNameFor(a.getEntryId())
                        + " · " + categoryCodeFor(a.getFinalCategoryId()));
        candidateSelect.setHelperText(getTranslation("bos.assign.helper"));

        if (unplaced.isEmpty()) {
            candidateSelect.setEnabled(false);
            candidateSelect.setPlaceholder(getTranslation("bos.assign.candidate.empty"));
        }

        dialog.add(candidateSelect);

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (candidateSelect.getValue() == null) {
                candidateSelect.setInvalid(true);
                candidateSelect.setErrorMessage(getTranslation("bos.assign.candidate.error"));
                return;
            }
            try {
                judgingService.recordBosPlacement(division.getId(),
                        candidateSelect.getValue().getEntryId(), place, currentUserId);
                dialog.close();
                refreshAfterPlacementChange();
                Notification.show(getTranslation("bos.assign.success", place))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.setId("bos-assign-save-button");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setDisableOnClick(true);
        saveButton.setEnabled(!unplaced.isEmpty());

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void refreshAfterPlacementChange() {
        UI.getCurrent().getPage().reload();
    }

    public void openReassignDialog(BosPlacement placement) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("bos.reassign.dialog.title", placement.getPlace()));

        var goldAwards = judgingService.findGoldMedalAwardsForDivision(division.getId(), currentUserId);
        var placedEntryIds = placedEntryIds();
        var candidates = goldAwards.stream()
                .filter(a -> !placedEntryIds.contains(a.getEntryId())
                        || a.getEntryId().equals(placement.getEntryId()))
                .toList();

        var candidateSelect = new Select<MedalAward>();
        candidateSelect.setId("bos-reassign-candidate-select");
        candidateSelect.setLabel(getTranslation("bos.assign.candidate.label"));
        candidateSelect.setWidthFull();
        candidateSelect.setItems(candidates);
        candidateSelect.setItemLabelGenerator(a -> a == null ? "" :
                entryCodeFor(a.getEntryId()) + " · " + meadNameFor(a.getEntryId())
                        + " · " + categoryCodeFor(a.getFinalCategoryId()));
        // Preselect current entry's award if present in list.
        candidates.stream()
                .filter(a -> a.getEntryId().equals(placement.getEntryId()))
                .findFirst()
                .ifPresent(candidateSelect::setValue);

        dialog.add(candidateSelect);

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (candidateSelect.getValue() == null) {
                candidateSelect.setInvalid(true);
                candidateSelect.setErrorMessage(getTranslation("bos.assign.candidate.error"));
                return;
            }
            try {
                int place = placement.getPlace();
                if (!candidateSelect.getValue().getEntryId().equals(placement.getEntryId())) {
                    judgingService.deleteBosPlacement(placement.getId(), currentUserId);
                    judgingService.recordBosPlacement(division.getId(),
                            candidateSelect.getValue().getEntryId(), place, currentUserId);
                }
                dialog.close();
                refreshAfterPlacementChange();
                Notification.show(getTranslation("bos.reassign.success", place))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.setId("bos-reassign-save-button");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    public void openDeleteDialog(BosPlacement placement) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("bos.delete.confirm.title"));
        dialog.add(new Span(getTranslation("bos.delete.confirm.body",
                entryCodeFor(placement.getEntryId()), placement.getPlace())));

        var confirm = new Button(getTranslation("button.delete"), e -> {
            try {
                judgingService.deleteBosPlacement(placement.getId(), currentUserId);
                dialog.close();
                refreshAfterPlacementChange();
                Notification.show(getTranslation("bos.delete.success", placement.getPlace()))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.setId("bos-delete-confirm-button");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirm.setDisableOnClick(true);

        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private VerticalLayout createCandidatesSection() {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.add(new Span(getTranslation("bos.candidates.title")));

        var grid = new Grid<>(MedalAward.class, false);
        grid.setId("bos-candidates-grid");
        grid.addColumn(a -> entryCodeFor(a.getEntryId()))
                .setHeader(getTranslation("bos.candidates.column.entry"));
        grid.addColumn(a -> meadNameFor(a.getEntryId()))
                .setHeader(getTranslation("bos.candidates.column.mead"));
        grid.addColumn(a -> categoryCodeFor(a.getFinalCategoryId()))
                .setHeader(getTranslation("bos.candidates.column.category"));

        var goldAwards = judgingService.findGoldMedalAwardsForDivision(division.getId(), currentUserId);
        var placedEntryIds = placedEntryIds();
        var unplaced = goldAwards.stream()
                .filter(a -> !placedEntryIds.contains(a.getEntryId()))
                .toList();
        grid.setItems(unplaced);
        section.add(grid);
        return section;
    }

    private Set<UUID> placedEntryIds() {
        var ids = new HashSet<UUID>();
        for (var p : judgingService.findBosPlacementsForDivision(division.getId(), currentUserId)) {
            ids.add(p.getEntryId());
        }
        return ids;
    }

    private String entryCodeFor(UUID entryId) {
        try {
            return entryService.findEntryById(entryId).getEntryCode();
        } catch (BusinessRuleException e) {
            return "";
        }
    }

    private String meadNameFor(UUID entryId) {
        try {
            return entryService.findEntryById(entryId).getMeadName();
        } catch (BusinessRuleException e) {
            return "";
        }
    }

    private String categoryCodeFor(UUID divisionCategoryId) {
        if (divisionCategoryId == null) return "";
        try {
            return competitionService.findDivisionCategoryById(divisionCategoryId).getCode();
        } catch (BusinessRuleException e) {
            return "";
        }
    }

    private Grid<PlacementSlot> createPlacementsGrid(boolean readOnly) {
        var grid = new Grid<>(PlacementSlot.class, false);
        grid.setId("bos-placements-grid");
        grid.addColumn(PlacementSlot::place).setHeader(getTranslation("bos.placements.column.place"));
        grid.addColumn(s -> s.entry() == null ? "" : s.entry().getEntryCode())
                .setHeader(getTranslation("bos.placements.column.entry"));
        grid.addColumn(s -> s.entry() == null ? "" : s.entry().getMeadName())
                .setHeader(getTranslation("bos.placements.column.mead"));
        grid.addColumn(s -> s.category() == null ? "" : s.category().getCode())
                .setHeader(getTranslation("bos.placements.column.category"));
        grid.addColumn(s -> s.awardedByUser() == null ? "" : s.awardedByUser().getEmail())
                .setHeader(getTranslation("bos.placements.column.awarded-by"));
        if (!readOnly) {
            grid.addComponentColumn(this::createPlacementActionsCell)
                    .setHeader(getTranslation("bos.placements.column.action"));
        }

        grid.setItems(loadPlacementSlots());
        return grid;
    }

    private HorizontalLayout createPlacementActionsCell(PlacementSlot slot) {
        var actions = new HorizontalLayout();
        actions.setPadding(false);
        if (slot.isEmpty()) {
            var addButton = new Button(new Icon(VaadinIcon.PLUS));
            addButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            addButton.setTooltipText(getTranslation("bos.placements.action.assign"));
            addButton.addClickListener(e -> openAssignDialog(slot.place()));
            actions.add(addButton);
        } else {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setTooltipText(getTranslation("bos.placements.action.reassign"));
            editButton.addClickListener(e -> openReassignDialog(slot.placement()));
            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setTooltipText(getTranslation("bos.placements.action.delete"));
            deleteButton.addClickListener(e -> openDeleteDialog(slot.placement()));
            actions.add(editButton, deleteButton);
        }
        return actions;
    }

    private List<PlacementSlot> loadPlacementSlots() {
        var placements = judgingService.findBosPlacementsForDivision(division.getId(), currentUserId);
        Map<Integer, BosPlacement> byPlace = new HashMap<>();
        for (var p : placements) {
            byPlace.put(p.getPlace(), p);
        }
        List<PlacementSlot> slots = new ArrayList<>();
        for (int place = 1; place <= division.getBosPlaces(); place++) {
            BosPlacement placement = byPlace.get(place);
            if (placement == null) {
                slots.add(new PlacementSlot(place, null, null, null, null));
            } else {
                var entry = entryService.findEntryById(placement.getEntryId());
                DivisionCategory category = null;
                if (entry.getFinalCategoryId() != null) {
                    try {
                        category = competitionService.findDivisionCategoryById(entry.getFinalCategoryId());
                    } catch (BusinessRuleException ignored) {
                        // category absent — fall through with null
                    }
                }
                User awardedBy = null;
                try {
                    awardedBy = userService.findById(placement.getAwardedBy());
                } catch (BusinessRuleException ignored) {
                    // user absent — fall through with null
                }
                slots.add(new PlacementSlot(place, placement, entry, category, awardedBy));
            }
        }
        return slots;
    }

    private HorizontalLayout createHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.add(new H2(getTranslation("bos.title", division.getName())));
        header.add(new Span(getTranslation("bos.header.phase") + ": " + judging.getPhase().name()));
        header.add(new Span(getTranslation("bos.header.places") + ": " + division.getBosPlaces()));
        return header;
    }

    private Anchor createBackLink() {
        return new Anchor(
                "competitions/" + compShortName + "/divisions/" + divShortName + "/judging-admin",
                getTranslation("bos.back-to-dashboard"));
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
