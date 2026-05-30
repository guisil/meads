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
    private Span saveStatus;
    private boolean editable;

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
        // Created up-front so the per-field auto-save listeners (attached while
        // building the score section below) always have a status target.
        saveStatus = new Span();
        saveStatus.setId("scoresheet-save-status");
        saveStatus.getStyle().set("color", "var(--lumo-secondary-text-color)");
        // BLANK, DRAFT and FILLED are editable by judges; editing scored content
        // on a FILLED sheet demotes it to DRAFT (the judge must re-Save).
        // SUBMITTED is read-only unless an admin reverts it. Admins land in
        // read-only mode by default for any status — they must explicitly
        // confirm "Edit on behalf of judge" to unlock the form.
        var status = scoresheet.getStatus();
        editable = (status == app.meads.judging.ScoresheetStatus.BLANK
                    || status == app.meads.judging.ScoresheetStatus.DRAFT
                    || status == app.meads.judging.ScoresheetStatus.FILLED)
                   && (!isAdminView || adminEditMode);
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
        // "Additional comments" — optional, low-emphasis (no section heading).
        // The required per-criterion comments carry the judging justification;
        // this field is just for any closing remarks to the entrant.
        commentsArea = new TextArea(getTranslation("scoresheet.additional-comments.label"));
        commentsArea.setId("overall-comments");
        commentsArea.setWidthFull();
        commentsArea.setMaxLength(2000);
        if (scoresheet.getOverallComments() != null) {
            commentsArea.setValue(scoresheet.getOverallComments());
        }
        commentsArea.addValueChangeListener(e -> autoSave(() ->
                scoresheetService.updateOverallComments(scoresheet.getId(),
                        commentsArea.getValue(), currentUserId)));
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
        commentLanguageCombo.addValueChangeListener(e -> {
            if (commentLanguageCombo.getValue() != null) {
                autoSave(() -> scoresheetService.setCommentLanguage(scoresheet.getId(),
                        commentLanguageCombo.getValue(), currentUserId));
            }
        });
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
        advanceCheckbox.addValueChangeListener(e -> autoSave(() ->
                scoresheetService.setAdvancedToMedalRound(scoresheet.getId(),
                        advanceCheckbox.getValue(), currentUserId)));
        return advanceCheckbox;
    }

    private HorizontalLayout createActionBar() {
        var save = new Button(getTranslation("scoresheet.action.save"), e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setId("save-button");
        return new HorizontalLayout(save, saveStatus);
    }

    /**
     * The validating "Save": flushes the freshest in-memory form state (auto-save
     * persists each field on blur, but a value typed and not yet blurred would
     * otherwise be missed), then promotes the sheet DRAFT → FILLED. On success
     * the judge returns to the round, where the round-level Finalize submits all
     * FILLED sheets at once.
     */
    private void save() {
        try {
            syncFormStateToDraft();
            scoresheetService.markFilled(scoresheet.getId(), currentUserId);
            Notification.show(getTranslation("scoresheet.action.save.success"))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            navigateToRound();
        } catch (BusinessRuleException ex) {
            Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Persists a single change as the judge works (on blur), updating the inline
     * save-status indicator. Validation errors (e.g. a frozen division) surface
     * as a notification; the explicit "Save" button is what enforces the full
     * per-criterion completeness rules.
     */
    private void autoSave(Runnable persist) {
        if (!editable || saveStatus == null) {
            return;
        }
        saveStatus.setText(getTranslation("scoresheet.save.status.saving"));
        try {
            persist.run();
            saveStatus.setText(getTranslation("scoresheet.save.status.saved"));
        } catch (BusinessRuleException ex) {
            saveStatus.setText(getTranslation("scoresheet.save.status.error"));
            Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void autoSaveField(String fieldName) {
        var nf = scoreFields.get(fieldName);
        var cf = scoreCommentFields.get(fieldName);
        Integer value = (nf == null || nf.getValue() == null) ? null : nf.getValue().intValue();
        String comment = (cf == null || !StringUtils.hasText(cf.getValue())) ? null : cf.getValue();
        autoSave(() -> scoresheetService.updateScore(scoresheet.getId(), fieldName,
                value, comment, currentUserId));
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
     * roundId. Hardcoding {@code /rounds/} sent admins to the wrong view
     * with a broken Actions column.
     */
    private String roundViewUrl() {
        var base = "competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName();
        if (table.getType() == app.meads.judging.RoundType.MEDAL) {
            return base + "/medal-rounds/" + table.getDivisionCategoryId();
        }
        return base + "/rounds/" + table.getId();
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
        card.add(attributeLine("entries.view.initial-category", initialCategoryLabel()));
        card.add(attributeLine("entries.view.final-category", finalCategoryLabel()));
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

    private String initialCategoryLabel() {
        return categoryLabelFor(entry.getInitialCategoryId());
    }

    private String finalCategoryLabel() {
        return categoryLabelFor(entry.getFinalCategoryId());
    }

    private String categoryLabelFor(java.util.UUID categoryId) {
        if (categoryId == null) {
            return "—";
        }
        var category = competitionService.findDivisionCategoryById(categoryId);
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
            field.setValueChangeMode(ValueChangeMode.ON_BLUR);
            var existing = existingByField.get(def.fieldName());
            if (existing != null && existing.getValue() != null) {
                field.setValue(existing.getValue().doubleValue());
            }
            // Listener added AFTER the initial setValue so loading the persisted
            // value doesn't trigger a spurious auto-save on render.
            field.addValueChangeListener(e -> {
                recomputeTotalPreview();
                autoSaveField(def.fieldName());
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
            comment.addValueChangeListener(e -> autoSaveField(def.fieldName()));
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
