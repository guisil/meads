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
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
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
        add(createInfoPanel());
        add(createScoreFieldsSection());
        add(createTotalPreview());
        // "Progression to medal round" is a SCORING-round concept — judges flag
        // entries to bring forward. A MEDAL-round-owned sheet (small-category
        // SCORE_BASED flow) is already at the medal round, so the box is
        // meaningless. Hide it (and skip the service call) for medal sheets.
        if (!isMedalRoundSheet()) {
            add(createProgressionCard());
        }
        add(createOtherInformationCard());
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

    /**
     * "Other Information" box — the comment language above the optional
     * "Additional comments" field, matching the reference MJP layout.
     */
    private VerticalLayout createOtherInformationCard() {
        var card = borderedCard("scoresheet.other-info");
        card.setWidthFull();
        var combo = createCommentLanguageField();
        combo.setWidthFull();
        card.add(combo);
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
        card.add(commentsArea);
        return card;
    }

    /**
     * "Progression to Medal Round" box (SCORING sheets only) — the judge's
     * advance-to-medal-round flag in its own labelled card with a medal icon.
     */
    private VerticalLayout createProgressionCard() {
        var card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");
        var header = new H3("🏅 " + getTranslation("scoresheet.progression.title"));
        header.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");
        card.add(header);
        advanceCheckbox = new Checkbox(getTranslation("scoresheet.advance.label"));
        advanceCheckbox.setId("advance-checkbox");
        advanceCheckbox.setValue(scoresheet.isAdvancedToMedalRound());
        advanceCheckbox.addValueChangeListener(e -> autoSave(() ->
                scoresheetService.setAdvancedToMedalRound(scoresheet.getId(),
                        advanceCheckbox.getValue(), currentUserId)));
        card.add(advanceCheckbox);
        return card;
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

    /**
     * Two side-by-side info cards (wrapping to a single column on narrow screens):
     * Basic information about the sample (categories + declared characteristics)
     * and Additional information about the mead (honey, ingredients, wood, notes).
     * Anonymity rule: the mead name appears on the Basic card for admins only —
     * judges judge to style, not to a brand.
     */
    private HorizontalLayout createInfoPanel() {
        var panel = new HorizontalLayout();
        panel.setWidthFull();
        panel.setAlignItems(Alignment.STRETCH);
        panel.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-m)");
        panel.add(createBasicInfoCard(), createMeadInfoCard());
        return panel;
    }

    private VerticalLayout createBasicInfoCard() {
        var card = infoCard("scoresheet.basic-info");
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
        card.add(attributeLine("entries.view.strength",
                getTranslation("entry.strength." + entry.getStrength().name())));
        card.add(attributeLine("entries.view.carbonation",
                getTranslation("entry.carbonation." + entry.getCarbonation().name())));
        card.add(attributeLine("entries.view.abv",
                entry.getAbv() == null ? "—" : entry.getAbv().toPlainString() + "%"));
        return card;
    }

    private VerticalLayout createMeadInfoCard() {
        // All remaining mead fields are shown, even when empty (rendered as "—"),
        // so the judge sees a consistent, complete picture per sample.
        var card = infoCard("scoresheet.mead-info");
        card.add(attributeLine("entries.view.honey", orDash(entry.getHoneyVarieties())));
        card.add(attributeLine("entries.view.other-ingredients", orDash(entry.getOtherIngredients())));
        card.add(attributeLine("entries.view.wood-aged",
                getTranslation(entry.isWoodAged()
                        ? "entries.view.wood-aged.yes" : "entries.view.wood-aged.no")));
        card.add(attributeLine("entries.view.wood-details", orDash(entry.getWoodAgeingDetails())));
        card.add(attributeLine("entries.view.additional-info", orDash(entry.getAdditionalInformation())));
        return card;
    }

    /** A bordered card with a (default-size) H3 header. */
    private VerticalLayout borderedCard(String headerKey) {
        var card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");
        var header = new H3(getTranslation(headerKey));
        header.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");
        card.add(header);
        return card;
    }

    /** A bordered card sized to sit side-by-side in the wrapping info panel. */
    private VerticalLayout infoCard(String headerKey) {
        var card = borderedCard(headerKey);
        card.getStyle().set("flex", "1 1 320px");
        return card;
    }

    /** A label/value line with the label in bold to separate it from the value. */
    private Span attributeLine(String labelKey, String value) {
        var label = new Span(getTranslation(labelKey) + ": ");
        label.getStyle().set("font-weight", "600");
        return new Span(label, new Span(value));
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
        section.setSpacing(false);
        var existingByField = new HashMap<String, ScoreField>();
        for (var f : scoresheetRepository.findFieldsByScoresheetId(scoresheet.getId())) {
            existingByField.put(f.getFieldName(), f);
        }
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            section.add(createCriterionCard(def, existingByField));
        }
        return section;
    }

    /**
     * One bordered card per MJP criterion: a title bar, then two columns that
     * wrap to a single column on narrow screens — the descriptor rubric (the six
     * quality bands with their score ranges + descriptions) on the left, and the
     * judge's score input + per-criterion comment on the right.
     */
    private VerticalLayout createCriterionCard(MjpScoringFieldDefinition.FieldDefinition def,
                                               Map<String, ScoreField> existingByField) {
        var card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin-bottom", "var(--lumo-space-m)");
        var title = new H3(getTranslation("scoresheet.criterion." + def.slug()));
        title.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");
        card.add(title);

        var columns = new HorizontalLayout();
        columns.setWidthFull();
        // STRETCH so the score column matches the (taller) rubric column height,
        // giving the comments area room to grow to the bottom of the card.
        columns.setAlignItems(Alignment.STRETCH);
        // flex-wrap lets the two columns stack on narrow viewports; each column
        // keeps a sensible min-width via its flex-basis.
        columns.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-l)");
        columns.add(createRubricColumn(def), createScoreColumn(def, existingByField));
        card.add(columns);
        return card;
    }

    private VerticalLayout createRubricColumn(MjpScoringFieldDefinition.FieldDefinition def) {
        var col = new VerticalLayout();
        col.setPadding(false);
        col.setSpacing(false);
        col.getStyle().set("flex", "1 1 280px");
        for (var b : def.bands()) {
            var bandRow = new HorizontalLayout();
            bandRow.setWidthFull();
            bandRow.setPadding(false);
            bandRow.setSpacing(false);
            bandRow.getStyle().set("align-items", "baseline").set("gap", "var(--lumo-space-s)");
            // Fixed-width band-name column so every range starts at the same x —
            // well right-of-centre AND aligned across the rows.
            var name = new Span(getTranslation(b.band().nameKey()));
            name.getStyle().set("font-weight", "600")
                    .set("flex-grow", "0").set("flex-shrink", "0").set("flex-basis", "68%");
            var range = new Span(b.low() + "–" + b.high());
            range.getStyle().set("color", "var(--lumo-secondary-text-color)").set("white-space", "nowrap");
            bandRow.add(name, range);
            col.add(bandRow);
            var desc = new Span(getTranslation(def.descriptionKey(b.band())));
            desc.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("margin-bottom", "var(--lumo-space-xs)");
            col.add(desc);
        }
        return col;
    }

    private VerticalLayout createScoreColumn(MjpScoringFieldDefinition.FieldDefinition def,
                                             Map<String, ScoreField> existingByField) {
        var col = new VerticalLayout();
        col.setPadding(false);
        col.setSpacing(false);
        // Score column shares the card width ~50/50 with the rubric column (both
        // grow equally). Contents are left-aligned so the "Your score" label, the
        // score ticker and the "Comments" label line up with the left edge of the
        // (full-width) comments field.
        col.getStyle().set("flex", "1 1 320px");
        col.setAlignItems(Alignment.START);

        var scoreLabel = new Span(getTranslation("scoresheet.your-score"));
        scoreLabel.getStyle().set("font-weight", "600");
        col.add(scoreLabel);

        var field = new NumberField();
        field.setId("score-" + def.fieldName());
        field.setMin(0);
        field.setMax(def.maxValue());
        field.setStep(1);
        field.setStepButtonsVisible(true);
        field.setWidth("10em");
        // ON_CHANGE (not ON_BLUR): the +/- step buttons dispatch a `change`
        // event but never `blur`, so under ON_BLUR a stepper click silently
        // failed to auto-save. `change` also fires on blur-after-typing, so
        // typed values still persist the same way.
        field.setValueChangeMode(ValueChangeMode.ON_CHANGE);
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
        col.add(field);

        var max = new Span(getTranslation("scoresheet.max-note", def.maxValue()));
        max.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-bottom", "var(--lumo-space-s)");
        col.add(max);

        // "Comments" label styled exactly like "Your score" (a bold Span), not the
        // built-in field caption.
        var commentsLabel = new Span(getTranslation("scoresheet.comments.label"));
        commentsLabel.getStyle().set("font-weight", "600");
        col.add(commentsLabel);

        var comment = new TextArea();
        comment.setId("score-comment-" + def.fieldName());
        comment.setPlaceholder(getTranslation("scoresheet.scores.comment.placeholder"));
        comment.setWidthFull();
        comment.setMaxLength(2000);
        // Lighten the placeholder so it doesn't read like a pre-filled value.
        comment.getStyle().set("--vaadin-input-field-placeholder-color",
                "var(--lumo-tertiary-text-color)");
        if (existing != null && existing.getComment() != null) {
            comment.setValue(existing.getComment());
        }
        comment.addValueChangeListener(e -> autoSaveField(def.fieldName()));
        scoreCommentFields.put(def.fieldName(), comment);
        col.add(comment);
        // The comment fills the remaining width and height of the score column
        // (down to the bottom of the card); overflowing text scrolls inside the
        // field rather than stretching the whole card taller.
        comment.setHeightFull();
        comment.getStyle().set("min-height", "6em");
        col.setFlexGrow(1, comment);
        return col;
    }

    private VerticalLayout createTotalPreview() {
        totalPreview = new H3();
        totalPreview.setId("scoresheet-total");
        // Inline styling keeps the running total visually loud — judges glance
        // at it constantly while filling in scores. Centered in its own banded
        // card so it reads as the page's running tally, not body text.
        totalPreview.getStyle().set("font-size", "var(--lumo-font-size-xxl)").set("margin", "0");
        var card = new VerticalLayout(totalPreview);
        card.setWidthFull();
        card.setPadding(true);
        card.setAlignItems(Alignment.CENTER);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("margin", "var(--lumo-space-m) 0");
        return card;
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
