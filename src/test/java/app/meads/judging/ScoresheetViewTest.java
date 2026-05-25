package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CategoryScope;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.entry.Carbonation;
import app.meads.entry.Entry;
import app.meads.entry.Sweetness;
import app.meads.entry.internal.EntryRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.internal.ScoresheetView;
import app.meads.judging.internal.ScoresheetRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.server.VaadinServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class ScoresheetViewTest {

    private static final String JUDGE_EMAIL = "scoresheet-test-judge@example.com";
    private static final String ADMIN_EMAIL = "scoresheet-test-admin@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired DivisionCategoryRepository divisionCategoryRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired ScoresheetRepository scoresheetRepository;
    @Autowired CompetitionService competitionService;
    @Autowired JudgingService judgingService;

    private Competition competition;
    private Division division;
    private DivisionCategory category;
    private User judge;
    private User admin;

    @BeforeEach
    void setup(TestInfo testInfo) {
        judge = userRepository.findByEmail(JUDGE_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(JUDGE_EMAIL, "Test Judge", UserStatus.ACTIVE, Role.USER)));
        // Defensive: clear any preferredLanguage set by a previous test so the
        // UI renders in English (assertions are English-only).
        judge.updatePreferredLanguage(null);
        judge = userRepository.save(judge);
        admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ADMIN_EMAIL, "Test Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "Scoresheet Test Competition", "ss-comp-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        division = divisionRepository.save(new Division(
                competition.getId(), "Amadora", "ss-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
        division.advanceStatus();
        division.advanceStatus();
        division.advanceStatus();
        division = divisionRepository.save(division);

        competitionService.addParticipantByEmail(competition.getId(),
                JUDGE_EMAIL, CompetitionRole.JUDGE, admin.getId());

        category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));

        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);

        var authentication = resolveAuthentication(testInfo);
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        propagateSecurityContext(authentication);
    }

    private Authentication resolveAuthentication(TestInfo testInfo) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth;
        }
        var method = testInfo.getTestMethod().orElse(null);
        if (method == null) {
            return null;
        }
        var withMockUser = method.getAnnotation(WithMockUser.class);
        if (withMockUser == null) {
            return null;
        }
        var username = withMockUser.username().isEmpty() ? withMockUser.value() : withMockUser.username();
        if (username.isEmpty()) {
            username = "user";
        }
        var authorities = Arrays.stream(withMockUser.roles())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password("password")
                .authorities(authorities)
                .build();
        return new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    }

    private void propagateSecurityContext(Authentication authentication) {
        if (authentication != null) {
            var fakeRequest = (FakeRequest) VaadinServletRequest.getCurrent().getRequest();
            fakeRequest.setUserPrincipalInt(authentication);
            fakeRequest.setUserInRole((principal, role) ->
                    authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role)));
        }
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    private Scoresheet createScoresheetFor(User entrant, String entryCode, String meadName) {
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createRound(judging.getId(), "Table A",
                category.getId(), null, admin.getId());
        judgingService.assignJudge(table.getId(), judge.getId(), admin.getId());

        var entry = new Entry(division.getId(), entrant.getId(), 1, entryCode,
                meadName, category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Wildflower", null, false, null, null);
        entry.assignFinalCategory(category.getId());
        entry = entryRepository.save(entry);

        var sheet = new Scoresheet(table.getId(), entry.getId());
        return scoresheetRepository.save(sheet);
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldShowEntryCodeButNotMeadNameToAssignedJudge() {
        // Anonymity: judges judge to style, not to a brand. The entry code in
        // the H2 is enough to identify the sample; the mead name is reserved
        // for admin views only.
        var entrant = userRepository.save(new User(
                "entrant-ss-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-1", "Hiveheart Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("AMA-1");

        var spanTexts = _find(Span.class).stream().map(Span::getText).toList();
        assertThat(spanTexts.stream().anyMatch(t -> t != null && t.contains("Hiveheart Mead")))
                .as("mead name must be hidden from judges").isFalse();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowMeadNameWhenAdminOpensTheScoresheet() {
        // Admins (system admin or division admin) DO see the mead name — they
        // need the context for moderation, results review, etc.
        var entrant = userRepository.save(new User(
                "entrant-admin-view-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-9", "Hiveheart Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var spanTexts = _find(Span.class).stream().map(Span::getText).toList();
        assertThat(spanTexts.stream().anyMatch(t -> t != null && t.contains("Hiveheart Mead")))
                .as("mead name visible to admin").isTrue();
    }

    @Test
    @WithMockUser(username = "outsider-judge-test@example.com", roles = "USER")
    void shouldForwardAwayJudgeWhoIsNotAssignedToTheRound() {
        // Visibility tightening: judges can only see scoresheets in rounds where
        // they themselves are assigned. Another competition's judge — or even
        // an unrelated judge in the same competition — must NOT be able to open
        // a scoresheet by knowing its UUID.
        var outsider = userRepository.save(new User(
                "outsider-judge-test@example.com", "Outsider Judge",
                UserStatus.ACTIVE, Role.USER));
        competitionService.addParticipantByEmail(competition.getId(),
                outsider.getEmail(), CompetitionRole.JUDGE, admin.getId());
        var entrant = userRepository.save(new User(
                "entrant-outsider-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        // The created scoresheet's round assigns the original `judge` user, not
        // the outsider — so the outsider has no business viewing it.
        var sheet = createScoresheetFor(entrant, "AMA-11", "Out-of-bounds Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        assertThat(_find(ScoresheetView.class))
                .as("outsider judge must be forwarded away, not see ScoresheetView")
                .isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldOpenInReadOnlyForAdminAndExposeExceptionalEditButton() {
        // Admins land in view-only mode by default — editing a scoresheet on
        // behalf of a judge should be an exceptional act, not a row-click side
        // effect. The action bar (Save Draft / Submit) is replaced by a single
        // "Edit on behalf of judge" button that fires a confirm dialog before
        // unlocking the form.
        var entrant = userRepository.save(new User(
                "entrant-admin-readonly-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-10", "Read-only Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var appearance = _get(NumberField.class, spec -> spec.withId("score-Appearance"));
        assertThat(appearance.isReadOnly())
                .as("admin view: score fields should start read-only").isTrue();
        var overallComments = _get(TextArea.class, spec -> spec.withId("overall-comments"));
        assertThat(overallComments.isReadOnly())
                .as("admin view: overall-comments should start read-only").isTrue();

        var editButton = _get(Button.class, spec -> spec.withId("admin-edit-scoresheet"));
        assertThat(editButton).as("admin edit-on-behalf button must be present").isNotNull();
        assertThat(_find(Button.class).stream().anyMatch(b -> "Save Draft".equals(b.getText())))
                .as("admin view: Save Draft is hidden until edit-mode is unlocked").isFalse();
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldShowDeclaredEntryAttributesOnTheCard() {
        var entrant = userRepository.save(new User(
                "entrant-attr-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-7", "Attr Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var spanTexts = _find(Span.class).stream()
                .map(Span::getText).filter(t -> t != null).toList();
        assertThat(spanTexts).anyMatch(t -> t.contains("Sweetness") && t.contains("Dry"));
        assertThat(spanTexts).anyMatch(t -> t.contains("Carbonation") && t.contains("Still"));
        assertThat(spanTexts).anyMatch(t -> t.contains("Honey") && t.contains("Wildflower"));
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRenderFiveScoreNumberFieldsWithMaxValuesAndUpdateLiveTotal() {
        var entrant = userRepository.save(new User(
                "entrant-ss-fields-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-2", "Sunset Cyser");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var appearance = _get(NumberField.class, spec -> spec.withId("score-Appearance"));
        var aroma = _get(NumberField.class, spec -> spec.withId("score-Aroma/Bouquet"));
        var flavour = _get(NumberField.class, spec -> spec.withId("score-Flavour and Body"));
        var finish = _get(NumberField.class, spec -> spec.withId("score-Finish"));
        var overall = _get(NumberField.class, spec -> spec.withId("score-Overall Impression"));
        assertThat(appearance.getMax()).isEqualTo(12.0);
        assertThat(aroma.getMax()).isEqualTo(30.0);
        assertThat(flavour.getMax()).isEqualTo(32.0);
        assertThat(finish.getMax()).isEqualTo(14.0);
        assertThat(overall.getMax()).isEqualTo(12.0);

        appearance.setValue(10.0);
        aroma.setValue(25.0);

        var totalPreview = _get(Span.class, spec -> spec.withId("scoresheet-total"));
        assertThat(totalPreview.getText()).contains("35");
        assertThat(totalPreview.getText()).contains("100");
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldPersistScoresAndCommentsWhenSaveDraftClicked() {
        var entrant = userRepository.save(new User(
                "entrant-ss-save-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-3", "Wild Bochet");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        _get(NumberField.class, spec -> spec.withId("score-Appearance")).setValue(8.0);
        _get(NumberField.class, spec -> spec.withId("score-Aroma/Bouquet")).setValue(20.0);

        var commentsArea = _get(TextArea.class, spec -> spec.withId("overall-comments"));
        commentsArea.setValue("Promising start; lovely aroma.");

        _click(_get(Button.class, spec -> spec.withText("Save Draft")));

        var fields = scoresheetRepository.findFieldsByScoresheetId(sheet.getId());
        var appearance = fields.stream()
                .filter(f -> "Appearance".equals(f.getFieldName())).findFirst().orElseThrow();
        var aroma = fields.stream()
                .filter(f -> "Aroma/Bouquet".equals(f.getFieldName())).findFirst().orElseThrow();
        assertThat(appearance.getValue()).isEqualTo(8);
        assertThat(aroma.getValue()).isEqualTo(20);
        var refreshed = scoresheetRepository.findById(sheet.getId()).orElseThrow();
        assertThat(refreshed.getOverallComments()).isEqualTo("Promising start; lovely aroma.");
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRenderPerFieldCommentTextAreasAndPersistThemOnSaveDraft() {
        // MJP scoresheet has 5 score fields, each with an optional comment.
        // The `score_fields.comment` column has always existed; this is the
        // UI exposure that lets judges leave per-criterion feedback.
        var entrant = userRepository.save(new User(
                "entrant-per-comment-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-PC", "Per-Comment Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        // Each MJP field has a sibling TextArea with id `score-comment-<fieldName>`.
        var appearanceComment = _get(TextArea.class,
                spec -> spec.withId("score-comment-Appearance"));
        appearanceComment.setValue("Bright with a slight haze.");
        var aromaComment = _get(TextArea.class,
                spec -> spec.withId("score-comment-Aroma/Bouquet"));
        aromaComment.setValue("Apricot, honey, light yeast.");
        // Set scores too (saveDraft writes everything in one go).
        _get(NumberField.class, spec -> spec.withId("score-Appearance")).setValue(10.0);
        _get(NumberField.class, spec -> spec.withId("score-Aroma/Bouquet")).setValue(25.0);

        _click(_get(Button.class, spec -> spec.withText("Save Draft")));

        var fields = scoresheetRepository.findFieldsByScoresheetId(sheet.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        app.meads.judging.ScoreField::getFieldName, f -> f));
        assertThat(fields.get("Appearance").getComment()).isEqualTo("Bright with a slight haze.");
        assertThat(fields.get("Aroma/Bouquet").getComment()).isEqualTo("Apricot, honey, light yeast.");
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    @SuppressWarnings("unchecked")
    void shouldDefaultCommentLanguageToJudgeUserPreferredLanguage() {
        // Default-comment-language fallback: when the judge hasn't saved a
        // scoresheet language and has no JudgeProfile preference yet, fall
        // back to their User.preferredLanguage. Less click-work on first use.
        judge.updatePreferredLanguage("pt");
        userRepository.save(judge);
        var entrant = userRepository.save(new User(
                "entrant-ss-default-lang-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-CL", "Default Lang Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var combo = (ComboBox<String>) _get(ComboBox.class, spec -> spec.withId("comment-language"));
        assertThat(combo.getValue()).isEqualTo("pt");
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    @SuppressWarnings("unchecked")
    void shouldExposeCommentLanguageComboBoxWithAllIsoLanguages() {
        var entrant = userRepository.save(new User(
                "entrant-ss-lang-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-4", "Honey Storm");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var combo = (ComboBox<String>) _get(ComboBox.class, spec -> spec.withId("comment-language"));
        var items = combo.getListDataView().getItems().toList();
        // Source is all ISO 639-1 codes — no per-competition restriction.
        assertThat(items).contains("en", "pt", "es", "it", "pl", "fr", "de", "ja");
        assertThat(items).hasSizeGreaterThan(100);
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldPersistAdvanceToMedalRoundFlagWhenSaveDraftClicked() {
        var entrant = userRepository.save(new User(
                "entrant-ss-advance-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-5", "Big Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var advance = _get(Checkbox.class, spec -> spec.withId("advance-checkbox"));
        advance.setValue(true);

        _click(_get(Button.class, spec -> spec.withText("Save Draft")));

        var refreshed = scoresheetRepository.findById(sheet.getId()).orElseThrow();
        assertThat(refreshed.isAdvancedToMedalRound()).isTrue();
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldSubmitScoresheetWhenAllFieldsFilledAndConfirmClicked() {
        var entrant = userRepository.save(new User(
                "entrant-ss-submit-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-6", "Bochet");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        _get(NumberField.class, spec -> spec.withId("score-Appearance")).setValue(10.0);
        _get(NumberField.class, spec -> spec.withId("score-Aroma/Bouquet")).setValue(25.0);
        _get(NumberField.class, spec -> spec.withId("score-Flavour and Body")).setValue(28.0);
        _get(NumberField.class, spec -> spec.withId("score-Finish")).setValue(12.0);
        _get(NumberField.class, spec -> spec.withId("score-Overall Impression")).setValue(11.0);

        // Save draft first so values persist (Submit acts on the persisted state).
        _click(_get(Button.class, spec -> spec.withText("Save Draft")));

        // Open submit dialog and confirm.
        _click(_get(Button.class, spec -> spec.withId("submit-button")));
        _click(_get(Button.class, spec -> spec.withId("submit-confirm-button")));

        var refreshed = scoresheetRepository.findById(sheet.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(app.meads.judging.ScoresheetStatus.SUBMITTED);
        assertThat(refreshed.getTotalScore()).isEqualTo(86);
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRenderReadOnlyWhenScoresheetIsSubmitted() {
        var entrant = userRepository.save(new User(
                "entrant-ss-ro-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-7", "Locked Mead");
        for (var def : app.meads.judging.internal.MjpScoringFieldDefinition.MJP_FIELDS) {
            sheet.updateScore(def.fieldName(), def.maxValue() / 2, null);
        }
        sheet.submit();
        scoresheetRepository.save(sheet);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var appearance = _get(NumberField.class, spec -> spec.withId("score-Appearance"));
        assertThat(appearance.isReadOnly()).isTrue();
        var commentsArea = _get(TextArea.class, spec -> spec.withId("overall-comments"));
        assertThat(commentsArea.isReadOnly()).isTrue();
        var saveDraftButtons = _find(Button.class).stream()
                .filter(b -> "Save Draft".equals(b.getText()))
                .toList();
        assertThat(saveDraftButtons).as("Save Draft hidden when SUBMITTED").isEmpty();
        var submitButtons = _find(Button.class).stream()
                .filter(b -> "submit-button".equals(b.getId().orElse(null)))
                .toList();
        assertThat(submitButtons).as("Submit hidden when SUBMITTED").isEmpty();
    }
}
