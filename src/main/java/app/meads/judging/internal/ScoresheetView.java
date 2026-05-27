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
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingRoundStatus;
import app.meads.judging.ScoreField;
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
import org.springframework.util.StringUtils;

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
    private JudgingRound table;
    private Scoresheet scoresheet;
    private Entry entry;
    private UUID currentUserId;
    private boolean isAdminView;
    /**
     * Latched only after the admin explicitly confirms "Edit on behalf of judge".
     * Defaults to false so a row-click in the round grid opens the form in view
     * mode — editing a scoresheet on behalf of a judge is an exceptional act.
     */
    private boolean adminEditMode;
    private final Map<String, NumberField> scoreFields = new HashMap<>();
    private final Map<String, TextArea> scoreCommentFields = new HashMap<>();
    private H3 totalPreview;
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

        var maybeTable = judgingService.findRoundById(scoresheet.getRoundId());
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
        boolean isAssignedJudge = judgingService.isJudgeAssignedToRound(table.getId(), currentUserId);

        if (!isSystemAdmin && !isDivisionAdmin && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }
        if (!isSystemAdmin && !userService.hasPassword(currentUserId) && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }
        isAdminView = isSystemAdmin || isDivisionAdmin;

        // Judges can only open a scoresheet while its owning round is
        // ACTIVE. Admins retain full access at any status.
        if (!isAdminView && table.getStatus() != JudgingRoundStatus.ACTIVE) {
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

        renderBody();
    }

    /**
     * Renders / re-renders the scoresheet body. Pulled out of {@code beforeEnter}
     * so the admin "Edit on behalf of judge" confirm dialog can call it again
     * after flipping {@link #adminEditMode}.
     */
    private void renderBody() {
        scoreFields.clear();
        scoreCommentFields.clear();
        removeAll();
        add(createBackToRoundAnchor());
        add(createHeader());
        add(createEntryCard());
        add(createScoreFieldsSection());
        add(createTotalPreview());
        add(createCommentsSection());
        add(createCommentLanguageField());
        // "Advance to medal round" is a SCORING-round concept — judges flag
        // entries to bring forward. A MEDAL-round-owned sheet (small-category
        // SCORE_BASED flow) is already at the medal round, so the checkbox is
        // meaningless. Hide it and skip the corresponding service call below.
        if (!isMedalRoundSheet()) {
            add(createAdvanceCheckbox());
        }
        // BLANK and DRAFT are editable by judges; SUBMITTED is read-only unless
        // the admin reverts it (which flips back to DRAFT). Admins land in
        // read-only mode by default for any status — they must explicitly
        // confirm "Edit on behalf of judge" to unlock the form.
        var status = scoresheet.getStatus();
        boolean editable = (status == app.meads.judging.ScoresheetStatus.BLANK
                            || status == app.meads.judging.ScoresheetStatus.DRAFT)
                            && (!isAdminView || adminEditMode);
        if (editable) {
            add(createActionBar());
        } else {
            applyReadOnlyMode();
            if (isAdminView && status != app.meads.judging.ScoresheetStatus.SUBMITTED) {
                add(createAdminEditButton());
            }
        }
        recomputeTotalPreview();
    }

    private void applyReadOnlyMode() {
        scoreFields.values().forEach(f -> f.setReadOnly(true));
        scoreCommentFields.values().forEach(c -> c.setReadOnly(true));
        commentsArea.setReadOnly(true);
        commentLanguageCombo.setReadOnly(true);
        if (advanceCheckbox != null) {
            advanceCheckbox.setReadOnly(true);
        }
    }

    private boolean isMedalRoundSheet() {
        return judgingService.findRoundById(scoresheet.getRoundId())
                .map(r -> r.getType() == app.meads.judging.RoundType.MEDAL)
                .orElse(false);
    }

    private Button createAdminEditButton() {
        var editButton = new Button(getTranslation("scoresheet.admin.edit.button"));
        editButton.setId("admin-edit-scoresheet");
        editButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        editButton.addClickListener(e -> openAdminEditConfirmDialog());
        return editButton;
    }

    private void openAdminEditConfirmDialog() {
        var dialog = new com.vaadin.flow.component.confirmdialog.ConfirmDialog();
        dialog.setHeader(getTranslation("scoresheet.admin.edit.confirm.title"));
        dialog.setText(getTranslation("scoresheet.admin.edit.confirm.body"));
        dialog.setCancelable(true);
        dialog.setConfirmText(getTranslation("scoresheet.admin.edit.confirm.proceed"));
        dialog.setConfirmButtonTheme("primary");
        dialog.addConfirmListener(e -> {
            adminEditMode = true;
            renderBody();
        });
        dialog.open();
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
        commentLanguageCombo.setItems(java.util.Arrays.stream(java.util.Locale.getISOLanguages())
                .sorted(java.util.Comparator.comparing(code -> displayLanguageName(code), String.CASE_INSENSITIVE_ORDER))
                .toList());
        commentLanguageCombo.setItemLabelGenerator(this::displayLanguageName);
        String defaultLanguage = scoresheet.getCommentLanguage();
        if (defaultLanguage == null) {
            defaultLanguage = judgeProfileService.findByUserId(currentUserId)
                    .map(p -> p.getPreferredCommentLanguage())
                    .orElse(null);
        }
        if (defaultLanguage == null) {
            defaultLanguage = userService.findById(currentUserId).getPreferredLanguage();
        }
        if (defaultLanguage != null) {
            commentLanguageCombo.setValue(defaultLanguage);
        }
        return commentLanguageCombo;
    }

    private String displayLanguageName(String code) {
        if (code == null) return "";
        return java.util.Locale.of(code).getDisplayLanguage(getLocale());
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
                // Submit validates against persisted state, so flush any in-flight
                // form edits to the draft first. Otherwise a user who typed more
                // characters in a comment but didn't click Save Draft first would
                // see submit complain about the old (shorter) value.
                syncFormStateToDraft();
                scoresheetService.submit(scoresheet.getId(), currentUserId);
                dialog.close();
                Notification.show(getTranslation("scoresheet.action.submit.success"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                navigateToRound();
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
            syncFormStateToDraft();
            Notification.show(getTranslation("scoresheet.action.save-draft.success"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (BusinessRuleException ex) {
            Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Pushes the in-memory form state (scores, per-criterion comments, overall
     * comments, language, advance flag) to the persisted draft. Shared by
     * Save Draft and Submit so that submit validation operates on the freshest
     * data. Throws {@link BusinessRuleException} on validation failures —
     * callers wrap with a user-facing notification.
     */
    private void syncFormStateToDraft() {
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            var field = scoreFields.get(def.fieldName());
            if (field == null) continue;
            Integer value = field.getValue() == null ? null : field.getValue().intValue();
            var commentField = scoreCommentFields.get(def.fieldName());
            String comment = commentField == null
                    ? null
                    : (StringUtils.hasText(commentField.getValue()) ? commentField.getValue() : null);
            scoresheetService.updateScore(scoresheet.getId(), def.fieldName(),
                    value, comment, currentUserId);
        }
        scoresheetService.updateOverallComments(scoresheet.getId(),
                commentsArea.getValue(), currentUserId);
        if (commentLanguageCombo.getValue() != null) {
            scoresheetService.setCommentLanguage(scoresheet.getId(),
                    commentLanguageCombo.getValue(), currentUserId);
        }
        if (advanceCheckbox != null) {
            scoresheetService.setAdvancedToMedalRound(scoresheet.getId(),
                    advanceCheckbox.getValue(), currentUserId);
        }
    }

    private void navigateToRound() {
        UI.getCurrent().navigate(roundViewUrl());
    }

    /**
     * Routes back to the appropriate per-round view based on the round's type.
     * SCORING sheets live in RoundView (the entries-list per round); medal-
     * round-owned sheets (small-category SCORE_BASED flow) belong with
     * MedalRoundView, which is keyed by divisionCategoryId rather than
     * roundId. Hardcoding {@code /tables/} sent admins to the wrong view
     * with a broken Actions column.
     */
    private String roundViewUrl() {
        var base = "competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName();
        if (table.getType() == app.meads.judging.RoundType.MEDAL) {
            return base + "/medal-rounds/" + table.getDivisionCategoryId();
        }
        return base + "/tables/" + table.getId();
    }

    private H2 createHeader() {
        return new H2(getTranslation("scoresheet.title", entry.getEntryCode()));
    }

    private com.vaadin.flow.component.html.Anchor createBackToRoundAnchor() {
        var anchor = new com.vaadin.flow.component.html.Anchor(roundViewUrl(),
                getTranslation("scoresheet.back-to-round"));
        anchor.setId("scoresheet-back-to-round");
        return anchor;
    }

    private VerticalLayout createEntryCard() {
        var card = new VerticalLayout();
        card.setPadding(false);
        card.setSpacing(false);
        // Anonymity rule: judges judge to style, not to a brand. Mead name is
        // reserved for admin views (moderation, results review).
        if (isAdminView) {
            var meadName = new Span(entry.getMeadName());
            meadName.getStyle().set("font-weight", "600");
            card.add(meadName);
        }
        // Judges work from poured samples (coded glasses, not the labelled bottle),
        // so the declared attributes they judge to style against must be on screen.
        card.add(attributeLine("entries.view.category", categoryLabel()));
        card.add(attributeLine("entries.view.sweetness",
                getTranslation("entry.sweetness." + entry.getSweetness().name())));
        card.add(attributeLine("entries.view.carbonation",
                getTranslation("entry.carbonation." + entry.getCarbonation().name())));
        card.add(attributeLine("entries.view.abv",
                entry.getAbv() == null ? "—" : entry.getAbv().toPlainString() + "%"));
        card.add(attributeLine("entries.view.honey", orDash(entry.getHoneyVarieties())));
        if (StringUtils.hasText(entry.getOtherIngredients())) {
            card.add(attributeLine("entries.view.other-ingredients", entry.getOtherIngredients()));
        }
        if (entry.isWoodAged()) {
            card.add(attributeLine("entries.view.wood-details", orDash(entry.getWoodAgeingDetails())));
        }
        if (StringUtils.hasText(entry.getAdditionalInformation())) {
            card.add(attributeLine("entries.view.additional-info", entry.getAdditionalInformation()));
        }
        return card;
    }

    private Span attributeLine(String labelKey, String value) {
        return new Span(getTranslation(labelKey) + ": " + value);
    }

    private String categoryLabel() {
        if (entry.getFinalCategoryId() == null) {
            return "—";
        }
        var category = competitionService.findDivisionCategoryById(entry.getFinalCategoryId());
        return category.getCode() + " — " + category.getName();
    }

    private String orDash(String value) {
        return StringUtils.hasText(value) ? value : "—";
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
            // Each criterion gets a row: full-width label on the left so the
            // criterion + max read clearly, a narrow NumberField on the right
            // sized for 2 digits + the step buttons. A previous iteration set
            // the NumberField itself to full width; that fixed label truncation
            // but left the input cavernous (cap is 32, so 2 digits is plenty).
            var row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
            row.setSpacing(true);
            var label = new Span(def.fieldName() + " (max " + def.maxValue() + ")");
            label.getStyle().set("flex", "1");
            var field = new NumberField();
            field.setId("score-" + def.fieldName());
            field.setMin(0);
            field.setMax(def.maxValue());
            field.setStep(1);
            field.setStepButtonsVisible(true);
            field.setWidth("8em");
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
            row.add(label, field);
            section.add(row);

            var comment = new TextArea();
            comment.setId("score-comment-" + def.fieldName());
            comment.setPlaceholder(getTranslation("scoresheet.scores.comment.placeholder"));
            comment.setWidthFull();
            comment.setMaxLength(2000);
            if (existing != null && existing.getComment() != null) {
                comment.setValue(existing.getComment());
            }
            scoreCommentFields.put(def.fieldName(), comment);
            section.add(comment);
        }
        return section;
    }

    private H3 createTotalPreview() {
        totalPreview = new H3();
        totalPreview.setId("scoresheet-total");
        // Inline styling keeps the running total visually loud — judges glance
        // at it constantly while filling in scores. Avoid burying it in the
        // surrounding text rhythm.
        totalPreview.getStyle().set("font-size", "var(--lumo-font-size-xxl)");
        totalPreview.getStyle().set("margin", "var(--lumo-space-m) 0");
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
