package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import app.meads.judging.JudgeProfileService;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingTable;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName/scoresheets/:scoresheetId",
        layout = MainLayout.class)
@PermitAll
public class ScoresheetView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final ScoresheetRepository scoresheetRepository;
    private final JudgeProfileService judgeProfileService;
    private final EntryService entryService;
    private final transient AuthenticationContext authenticationContext;

    private Competition competition;
    private Division division;
    private JudgingTable table;
    private Scoresheet scoresheet;
    private Entry entry;
    private UUID currentUserId;
    private final Map<String, NumberField> scoreFields = new HashMap<>();
    private Span totalPreview;
    private TextArea commentsArea;
    private ComboBox<String> commentLanguageCombo;
    private Checkbox advanceCheckbox;
    private Button submitButton;

    public ScoresheetView(CompetitionService competitionService,
                          UserService userService,
                          JudgingService judgingService,
                          ScoresheetService scoresheetService,
                          ScoresheetRepository scoresheetRepository,
                          JudgeProfileService judgeProfileService,
                          EntryService entryService,
                          AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
        this.scoresheetService = scoresheetService;
        this.scoresheetRepository = scoresheetRepository;
        this.judgeProfileService = judgeProfileService;
        this.entryService = entryService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        var divShortName = event.getRouteParameters().get("divShortName").orElse(null);
        var scoresheetIdParam = event.getRouteParameters().get("scoresheetId").orElse(null);

        if (compShortName == null || divShortName == null || scoresheetIdParam == null) {
            event.forwardTo("");
            return;
        }

        UUID scoresheetId;
        try {
            scoresheetId = UUID.fromString(scoresheetIdParam);
        } catch (IllegalArgumentException e) {
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

        var maybeSheet = scoresheetService.findById(scoresheetId);
        if (maybeSheet.isEmpty()) {
            event.forwardTo("");
            return;
        }
        scoresheet = maybeSheet.get();

        var maybeTable = judgingService.findTableById(scoresheet.getTableId());
        if (maybeTable.isEmpty()) {
            event.forwardTo("");
            return;
        }
        table = maybeTable.get();

        // Sanity-check that the table belongs to this division (avoid URL confusion).
        var judging = judgingService.ensureJudgingExists(division.getId());
        if (!table.getJudgingId().equals(judging.getId())) {
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
        boolean isAssignedJudge = judgingService.isJudgeAssignedToTable(table.getId(), currentUserId);

        if (!isSystemAdmin && !isDivisionAdmin && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }
        if (!isSystemAdmin && !userService.hasPassword(currentUserId) && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }

        entry = entryService.findEntryById(scoresheet.getEntryId());

        // Hard COI: assigned judge cannot judge their own entry.
        if (isAssignedJudge && !isSystemAdmin && !isDivisionAdmin
                && entry.getUserId().equals(currentUserId)) {
            event.forwardTo("my-judging");
            return;
        }

        scoreFields.clear();

        removeAll();
        add(createHeader());
        add(createEntryCard());
        add(createScoreFieldsSection());
        add(createTotalPreview());
        add(createCommentsSection());
        add(createCommentLanguageField());
        add(createAdvanceCheckbox());
        if (scoresheet.getStatus() == app.meads.judging.ScoresheetStatus.DRAFT) {
            add(createActionBar());
        } else {
            applyReadOnlyMode();
        }
        recomputeTotalPreview();
    }

    private void applyReadOnlyMode() {
        scoreFields.values().forEach(f -> f.setReadOnly(true));
        commentsArea.setReadOnly(true);
        commentLanguageCombo.setReadOnly(true);
        advanceCheckbox.setReadOnly(true);
    }

    private VerticalLayout createCommentsSection() {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3(getTranslation("scoresheet.comments.section")));
        commentsArea = new TextArea();
        commentsArea.setId("overall-comments");
        commentsArea.setWidthFull();
        commentsArea.setMaxLength(2000);
        if (scoresheet.getOverallComments() != null) {
            commentsArea.setValue(scoresheet.getOverallComments());
        }
        section.add(commentsArea);
        return section;
    }

    private ComboBox<String> createCommentLanguageField() {
        commentLanguageCombo = new ComboBox<>(getTranslation("scoresheet.comment-language.label"));
        commentLanguageCombo.setId("comment-language");
        Set<String> options = new LinkedHashSet<>();
        if (competition.getCommentLanguages() != null) {
            options.addAll(competition.getCommentLanguages());
        }
        var judgeProfile = judgeProfileService.findByUserId(currentUserId);
        judgeProfile.map(p -> p.getPreferredCommentLanguage())
                .filter(java.util.Objects::nonNull)
                .ifPresent(options::add);
        commentLanguageCombo.setItems(options.stream()
                .sorted(java.util.Comparator.comparing(code -> displayLanguageName(code), String.CASE_INSENSITIVE_ORDER))
                .toList());
        commentLanguageCombo.setItemLabelGenerator(this::displayLanguageName);
        if (scoresheet.getCommentLanguage() != null) {
            commentLanguageCombo.setValue(scoresheet.getCommentLanguage());
        }
        return commentLanguageCombo;
    }

    private String displayLanguageName(String code) {
        if (code == null) return "";
        return new java.util.Locale(code).getDisplayLanguage(getLocale());
    }

    private Checkbox createAdvanceCheckbox() {
        advanceCheckbox = new Checkbox(getTranslation("scoresheet.advance.label"));
        advanceCheckbox.setId("advance-checkbox");
        advanceCheckbox.setValue(scoresheet.isAdvancedToMedalRound());
        return advanceCheckbox;
    }

    private HorizontalLayout createActionBar() {
        var saveDraft = new Button(getTranslation("scoresheet.action.save-draft"), e -> saveDraft());
        saveDraft.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveDraft.setDisableOnClick(true);
        saveDraft.setId("save-draft-button");

        submitButton = new Button(getTranslation("scoresheet.action.submit"), e -> openSubmitDialog());
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        submitButton.setId("submit-button");
        updateSubmitButtonEnabled();

        return new HorizontalLayout(saveDraft, submitButton);
    }

    private void updateSubmitButtonEnabled() {
        if (submitButton == null) return;
        boolean allFilled = scoreFields.size() == MjpScoringFieldDefinition.MJP_FIELDS.size()
                && scoreFields.values().stream().allMatch(f -> f.getValue() != null);
        submitButton.setEnabled(allFilled);
    }

    private void openSubmitDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("scoresheet.action.submit.confirm.title",
                entry.getEntryCode()));
        dialog.add(new Span(getTranslation("scoresheet.action.submit.confirm.body")));

        var confirm = new Button(getTranslation("scoresheet.action.submit"), e -> {
            try {
                scoresheetService.submit(scoresheet.getId(), currentUserId);
                dialog.close();
                Notification.show(getTranslation("scoresheet.action.submit.success"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().getPage().reload();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        confirm.setId("submit-confirm-button");
        confirm.setDisableOnClick(true);

        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void saveDraft() {
        try {
            for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
                var field = scoreFields.get(def.fieldName());
                if (field == null) continue;
                Integer value = field.getValue() == null ? null : field.getValue().intValue();
                scoresheetService.updateScore(scoresheet.getId(), def.fieldName(),
                        value, null, currentUserId);
            }
            scoresheetService.updateOverallComments(scoresheet.getId(),
                    commentsArea.getValue(), currentUserId);
            if (commentLanguageCombo.getValue() != null) {
                scoresheetService.setCommentLanguage(scoresheet.getId(),
                        commentLanguageCombo.getValue(), currentUserId);
            }
            scoresheetService.setAdvancedToMedalRound(scoresheet.getId(),
                    advanceCheckbox.getValue(), currentUserId);
            Notification.show(getTranslation("scoresheet.action.save-draft.success"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (BusinessRuleException ex) {
            Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private H2 createHeader() {
        return new H2(getTranslation("scoresheet.title", entry.getEntryCode()));
    }

    private VerticalLayout createEntryCard() {
        var card = new VerticalLayout();
        card.setPadding(false);
        card.add(new Span(entry.getMeadName()));
        return card;
    }

    private VerticalLayout createScoreFieldsSection() {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3(getTranslation("scoresheet.scores.section")));
        var existingByField = new HashMap<String, ScoreField>();
        for (var f : scoresheetRepository.findFieldsByScoresheetId(scoresheet.getId())) {
            existingByField.put(f.getFieldName(), f);
        }
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            var field = new NumberField(def.fieldName() + " (max " + def.maxValue() + ")");
            field.setId("score-" + def.fieldName());
            field.setMin(0);
            field.setMax(def.maxValue());
            field.setStep(1);
            field.setValueChangeMode(ValueChangeMode.EAGER);
            var existing = existingByField.get(def.fieldName());
            if (existing != null && existing.getValue() != null) {
                field.setValue(existing.getValue().doubleValue());
            }
            field.addValueChangeListener(e -> {
                recomputeTotalPreview();
                updateSubmitButtonEnabled();
            });
            scoreFields.put(def.fieldName(), field);
            section.add(field);
        }
        return section;
    }

    private Span createTotalPreview() {
        totalPreview = new Span();
        totalPreview.setId("scoresheet-total");
        return totalPreview;
    }

    private void recomputeTotalPreview() {
        int sum = scoreFields.values().stream()
                .mapToInt(f -> f.getValue() == null ? 0 : f.getValue().intValue())
                .sum();
        totalPreview.setText(getTranslation("scoresheet.total.format", sum, 100));
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
