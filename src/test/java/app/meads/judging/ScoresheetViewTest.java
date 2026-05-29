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
    @Autowired app.meads.judging.internal.JudgingRoundRepository judgingRoundRepository;
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
        // Judges can only open scoresheets on an ACTIVE round. Bypass the
        // service's start preconditions via direct entity moves — these tests
        // are about scoresheet behavior, not round-start gates.
        table.markReady();
        table.start();
        judgingRoundRepository.save(table);

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
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldShowBothInitialAndFinalCategoryLines() {
        // Informative: the scoresheet card shows the entry's initial category
        // (what the entrant registered under) and its final category (where the
        // admin placed it for judging). Judges + admins both see both lines.
        var entrant = userRepository.save(new User(
                "entrant-cats-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMACAT", "Category Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var spanTexts = _find(Span.class).stream().map(Span::getText).toList();
        assertThat(spanTexts.stream().anyMatch(t -> t != null && t.startsWith("Initial Category:")))
                .as("Initial Category line must be present").isTrue();
        assertThat(spanTexts.stream().anyMatch(t -> t != null && t.startsWith("Final Category:")))
                .as("Final Category line must be present").isTrue();
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
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRouteBackAnchorToMedalRoundViewForMedalOwnedScoresheet() {
        // Small-category SCORE_BASED flow: the sheet's roundId is a MEDAL
        // round. RoundView (at /tables/<roundId>) is built for SCORING rounds
        // and renders the wrong actions list — admin needs to land on
        // MedalRoundView (keyed by divisionCategoryId).
        var judging = judgingService.ensureJudgingExists(division.getId());
        var medalRound = judgingService.createMedalRound(judging.getId(),
                category.getId(), admin.getId());
        judgingService.updateMedalRoundMode(medalRound.getId(),
                MedalRoundMode.SCORE_BASED, admin.getId());
        judgingService.assignJudge(medalRound.getId(), judge.getId(), admin.getId());
        // Judge access requires ACTIVE round status. Bypass service preconds.
        medalRound.markReady();
        medalRound.start();
        judgingRoundRepository.save(medalRound);
        var entrant = userRepository.save(new User(
                "entrant-mr-anchor-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-99",
                "Medal-owned Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Wildflower", null, false, null, null);
        entry.assignFinalCategory(category.getId());
        entry = entryRepository.save(entry);
        var sheet = scoresheetRepository.save(
                new Scoresheet(medalRound.getId(), entry.getId()));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var anchor = _find(com.vaadin.flow.component.html.Anchor.class).stream()
                .filter(a -> "scoresheet-back-to-round".equals(a.getId().orElse(null)))
                .findFirst().orElseThrow();
        assertThat(anchor.getHref())
                .as("back anchor on a medal-owned sheet must point at MedalRoundView")
                .contains("/medal-rounds/" + category.getId())
                .doesNotContain("/tables/");
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRenderFormPolish_stepButtons_prominentTotal_backToRoundAnchor() {
        // C2 form polish: NumberFields expose +/- step buttons (no need to
        // type to nudge a score), the running total preview is rendered as a
        // prominent H3 (not a low-emphasis Span), and a "back to round" anchor
        // gives judges a one-click return to RoundView from any scoresheet.
        var entrant = userRepository.save(new User(
                "entrant-polish-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-12", "Polished Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var appearance = _get(NumberField.class, spec -> spec.withId("score-Appearance"));
        assertThat(appearance.isStepButtonsVisible())
                .as("+/- step buttons must be visible on score NumberFields").isTrue();

        var total = _get(com.vaadin.flow.component.html.H3.class,
                spec -> spec.withId("scoresheet-total"));
        assertThat(total).as("total must be rendered as a prominent H3").isNotNull();

        var anchors = _find(com.vaadin.flow.component.html.Anchor.class).stream()
                .filter(a -> "scoresheet-back-to-round".equals(a.getId().orElse(null)))
                .toList();
        assertThat(anchors).as("back-to-round anchor must be present").hasSize(1);
        assertThat(anchors.get(0).getHref())
                .as("back anchor href must point at the round's RoundView")
                .contains("/tables/");
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
        assertThat(_find(Button.class).stream().anyMatch(b -> "save-button".equals(b.getId().orElse(null))))
                .as("admin view: Save is hidden until edit-mode is unlocked").isFalse();
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

        var totalPreview = _get(com.vaadin.flow.component.html.H3.class,
                spec -> spec.withId("scoresheet-total"));
        assertThat(totalPreview.getText()).contains("35");
        assertThat(totalPreview.getText()).contains("100");
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldAutoSaveScoresAndAdditionalCommentsOnChange() {
        // Auto-save: each score / comment change persists on blur — there is no
        // explicit "Save Draft" button anymore. The sheet stays DRAFT until the
        // judge clicks the validating "Save" (which promotes it to FILLED).
        var entrant = userRepository.save(new User(
                "entrant-ss-save-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-3", "Wild Bochet");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        _get(NumberField.class, spec -> spec.withId("score-Appearance")).setValue(8.0);
        _get(NumberField.class, spec -> spec.withId("score-Aroma/Bouquet")).setValue(20.0);
        _get(TextArea.class, spec -> spec.withId("overall-comments"))
                .setValue("Promising start; lovely aroma.");

        var fields = scoresheetRepository.findFieldsByScoresheetId(sheet.getId());
        var appearance = fields.stream()
                .filter(f -> "Appearance".equals(f.getFieldName())).findFirst().orElseThrow();
        var aroma = fields.stream()
                .filter(f -> "Aroma/Bouquet".equals(f.getFieldName())).findFirst().orElseThrow();
        assertThat(appearance.getValue()).isEqualTo(8);
        assertThat(aroma.getValue()).isEqualTo(20);
        var refreshed = scoresheetRepository.findById(sheet.getId()).orElseThrow();
        assertThat(refreshed.getOverallComments()).isEqualTo("Promising start; lovely aroma.");
        assertThat(refreshed.getStatus()).isEqualTo(ScoresheetStatus.DRAFT);
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldAutoSavePerFieldCommentsOnChange() {
        // MJP scoresheet has 5 score fields, each with a per-criterion comment
        // (`score_fields.comment` column). The comments auto-save on change.
        var entrant = userRepository.save(new User(
                "entrant-per-comment-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-PC", "Per-Comment Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        // Set scores first, then the sibling comment TextAreas (id `score-comment-<fieldName>`).
        _get(NumberField.class, spec -> spec.withId("score-Appearance")).setValue(10.0);
        _get(NumberField.class, spec -> spec.withId("score-Aroma/Bouquet")).setValue(25.0);
        _get(TextArea.class, spec -> spec.withId("score-comment-Appearance"))
                .setValue("Bright with a slight haze.");
        _get(TextArea.class, spec -> spec.withId("score-comment-Aroma/Bouquet"))
                .setValue("Apricot, honey, light yeast.");

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
    void shouldAutoSaveAdvanceToMedalRoundFlagOnChange() {
        var entrant = userRepository.save(new User(
                "entrant-ss-advance-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-5", "Big Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        _get(Checkbox.class, spec -> spec.withId("advance-checkbox")).setValue(true);

        var refreshed = scoresheetRepository.findById(sheet.getId()).orElseThrow();
        assertThat(refreshed.isAdvancedToMedalRound()).isTrue();
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldMarkSheetFilledWhenSaveClickedWithCompleteSheet() {
        // The validating "Save" promotes a complete sheet DRAFT → FILLED — it no
        // longer submits (the round-level Finalize does that). All five fields
        // must be scored and each per-criterion comment must clear the 15-char
        // floor.
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
        _get(TextArea.class, spec -> spec.withId("score-comment-Appearance"))
                .setValue("crystal clear appearance");
        _get(TextArea.class, spec -> spec.withId("score-comment-Aroma/Bouquet"))
                .setValue("subtle honey and stone fruit");
        _get(TextArea.class, spec -> spec.withId("score-comment-Flavour and Body"))
                .setValue("balanced, medium-bodied");
        _get(TextArea.class, spec -> spec.withId("score-comment-Finish"))
                .setValue("clean, lingering finish");
        _get(TextArea.class, spec -> spec.withId("score-comment-Overall Impression"))
                .setValue("well-crafted bochet example");

        _click(_get(Button.class, spec -> spec.withId("save-button")));

        var refreshed = scoresheetRepository.findById(sheet.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(app.meads.judging.ScoresheetStatus.FILLED);
        assertThat(refreshed.getTotalScore())
                .as("Save must not compute the total — the round Finalize does").isNull();
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRejectSaveAndStayDraftWhenSheetIsIncomplete() {
        // Save validates: an incomplete sheet (a missing score and/or a missing
        // per-criterion comment) cannot be promoted to FILLED — it stays DRAFT.
        var entrant = userRepository.save(new User(
                "entrant-ss-incomplete-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var sheet = createScoresheetFor(entrant, "AMA-IC", "Incomplete Mead");

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        // Four of five fields scored; Overall Impression left blank and uncommented.
        _get(NumberField.class, spec -> spec.withId("score-Appearance")).setValue(10.0);
        _get(NumberField.class, spec -> spec.withId("score-Aroma/Bouquet")).setValue(25.0);
        _get(NumberField.class, spec -> spec.withId("score-Flavour and Body")).setValue(28.0);
        _get(NumberField.class, spec -> spec.withId("score-Finish")).setValue(12.0);
        _get(TextArea.class, spec -> spec.withId("score-comment-Appearance"))
                .setValue("crystal clear appearance");
        _get(TextArea.class, spec -> spec.withId("score-comment-Aroma/Bouquet"))
                .setValue("subtle honey and stone fruit");
        _get(TextArea.class, spec -> spec.withId("score-comment-Flavour and Body"))
                .setValue("balanced, medium-bodied");
        _get(TextArea.class, spec -> spec.withId("score-comment-Finish"))
                .setValue("clean, lingering finish");

        _click(_get(Button.class, spec -> spec.withId("save-button")));

        var refreshed = scoresheetRepository.findById(sheet.getId()).orElseThrow();
        assertThat(refreshed.getStatus())
                .as("an incomplete sheet must not be promoted to FILLED")
                .isEqualTo(app.meads.judging.ScoresheetStatus.DRAFT);
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
        sheet.markFilled();
        sheet.submit();
        scoresheetRepository.save(sheet);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId());

        var appearance = _get(NumberField.class, spec -> spec.withId("score-Appearance"));
        assertThat(appearance.isReadOnly()).isTrue();
        var commentsArea = _get(TextArea.class, spec -> spec.withId("overall-comments"));
        assertThat(commentsArea.isReadOnly()).isTrue();
        var saveButtons = _find(Button.class).stream()
                .filter(b -> "save-button".equals(b.getId().orElse(null)))
                .toList();
        assertThat(saveButtons).as("Save hidden when SUBMITTED").isEmpty();
    }
}
