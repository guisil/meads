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
import app.meads.entry.EntryService;
import app.meads.entry.Sweetness;
import app.meads.entry.internal.EntryRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingRoundRepository;
import app.meads.judging.internal.MedalAwardRepository;
import app.meads.judging.internal.MedalRoundView;
import app.meads.judging.internal.MjpScoringFieldDefinition;
import app.meads.judging.internal.ScoresheetRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
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
class MedalRoundViewTest {

    private static final String ADMIN_EMAIL = "medal-round-admin-test@example.com";
    private static final String ENTRANT_EMAIL = "medal-round-entrant-test@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired DivisionCategoryRepository divisionCategoryRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired EntryService entryService;
    @Autowired CategoryJudgingConfigRepository categoryConfigRepository;
    @Autowired JudgingRepository judgingRepository;
    @Autowired JudgingRoundRepository judgingRoundRepository;
    @Autowired ScoresheetRepository scoresheetRepository;
    @Autowired MedalAwardRepository medalAwardRepository;
    @Autowired JudgingService judgingService;
    @Autowired ScoresheetService scoresheetService;
    @Autowired CompetitionService competitionService;

    private Competition competition;
    private Division division;
    private int entryNum = 1;

    @BeforeEach
    void setup(TestInfo testInfo) {
        userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ADMIN_EMAIL, "Medal Round Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN)));
        userRepository.findByEmail(ENTRANT_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ENTRANT_EMAIL, "Medal Round Entrant", UserStatus.ACTIVE, Role.USER)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "Medal Round Test Competition", "medal-round-comp-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        division = divisionRepository.save(new Division(
                competition.getId(), "Amadora", "medal-round-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));

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

    // === Scenario helpers ===

    private DivisionCategory activeMedalRoundCategory() {
        return categoryWithActiveConfig(MedalRoundMode.COMPARATIVE);
    }

    private DivisionCategory categoryWithActiveConfig(MedalRoundMode mode) {
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED
        division.advanceStatus(); // JUDGING
        division = divisionRepository.save(division);
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc", null, 1, CategoryScope.JUDGING));
        var config = new CategoryJudgingConfig(category.getId(), mode);
        categoryConfigRepository.save(config);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var medalRound = new JudgingRound(judging.getId(), "Medal",
                category.getId(), null);
        medalRound.convertToMedalRound(mode);
        medalRound.markReady();
        medalRound.start();
        judgingRoundRepository.save(medalRound);
        return category;
    }

    private void submittedScoreBasedEntry(DivisionCategory category, JudgingRound table,
                                          String code, int total) {
        var entrant = userRepository.save(new User(
                "mr-sb-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(),
                entryNum++, code, code + " Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL, "Wildflower", null, false, null, null);
        entry.submit();
        entry.markReceived();
        // Bypass entryService.assignFinalCategory to avoid firing
        // EntryReceivedEvent — this test's setup pre-creates SUBMITTED sheets
        // on a separate scoring round and depends on the derivation fallback
        // in findMedalRoundEntries (medal round's explicit entries set stays
        // empty so the legacy code path runs). The auto-sync listener path
        // is exercised by other tests; here it would conflict with the
        // pre-created sheets on UNIQUE entry_id.
        entry.assignFinalCategory(category.getId());
        entry = entryRepository.save(entry);
        var sheet = new Scoresheet(table.getId(), entry.getId());
        int deficit = 100 - total;
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            int value = def.maxValue();
            if (deficit > 0 && def.maxValue() >= deficit) {
                value = def.maxValue() - deficit;
                deficit = 0;
            }
            sheet.updateScore(def.fieldName(), value, null);
        }
        sheet.markFilled();
        sheet.submit();
        scoresheetRepository.save(sheet);
    }

    private JudgingRound tableFor(DivisionCategory category) {
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        return judgingService.createRound(judging.getId(), "Table A",
                category.getId(), null, admin.getId());
    }

    private void advancedEntry(DivisionCategory category, JudgingRound table, String code) {
        var entrant = userRepository.save(new User(
                "mr-entrant-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(),
                entryNum++, code, code + " Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL, "Wildflower", null, false, null, null);
        entry.submit();
        entry.markReceived();
        // See submittedScoreBasedEntry — bypass the service path to avoid
        // listener-triggered sync conflicting with the pre-created sheets.
        entry.assignFinalCategory(category.getId());
        entry = entryRepository.save(entry);
        var sheet = new Scoresheet(table.getId(), entry.getId());
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            sheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        sheet.setAdvancedToMedalRound(true);
        sheet.markFilled();
        sheet.submit();
        scoresheetRepository.save(sheet);
    }

    private Entry receivedEntryWithoutScoresheet(DivisionCategory category, String code) {
        var entrant = userRepository.save(new User(
                "mr-rs-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(),
                entryNum++, code, code + " Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL, "Wildflower", null, false, null, null);
        entry.submit();
        entry.markReceived();
        entry = entryRepository.save(entry);
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        entryService.assignFinalCategory(entry.getId(), category.getId(), admin.getId());
        return entry;
    }

    private void navigateToMedalRound(DivisionCategory category) {
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/medal-rounds/" + category.getId());
    }

    // === Tests ===

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldRedirectUnauthorizedUserAwayFromMedalRound() {
        var category = activeMedalRoundCategory();

        navigateToMedalRound(category);

        assertThat(_find(MedalRoundView.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderHeaderAndGridForAdmin() {
        var category = activeMedalRoundCategory();

        navigateToMedalRound(category);

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("Amadora");
        assertThat(heading.getText()).contains("M1A");
        var grid = _get(Grid.class, spec -> spec.withId("medal-round-grid"));
        assertThat(grid).isNotNull();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    @WithMockUser(username = "mr-columns-judge@example.com", roles = "USER")
    void shouldHideEntryNumberAndMeadNameColumnsFromJudgesOnMedalRoundGrid() {
        // Anonymity: same rule as RoundView/ScoresheetView. Judges on a
        // SCORE_BASED small-category medal round (when they're assigned and
        // the round is ACTIVE) DO open MedalRoundView, so the admin-only
        // Entry # cross-reference and the entrant's brand label (mead name)
        // must both be hidden.
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");
        // Attach a judge to the medal round so they can access MedalRoundView.
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judge = userRepository.save(new User(
                "mr-columns-judge@example.com", "Columns Judge",
                UserStatus.ACTIVE, Role.USER));
        competitionService.addParticipantByEmail(competition.getId(),
                judge.getEmail(), CompetitionRole.JUDGE, admin.getId());
        var judgingId = judgingRepository.findByDivisionId(division.getId())
                .orElseThrow().getId();
        var medalRound = judgingRoundRepository.findByJudgingId(judgingId).stream()
                .filter(r -> r.getType() == RoundType.MEDAL
                        && category.getId().equals(r.getDivisionCategoryId()))
                .findFirst().orElseThrow();
        judgingService.assignJudge(medalRound.getId(), judge.getId(), admin.getId());

        navigateToMedalRound(category);

        var grid = _get(Grid.class, spec -> spec.withId("medal-round-grid"));
        var headers = grid.getColumns().stream()
                .map(c -> ((Grid.Column) c).getHeaderText())
                .filter(h -> h instanceof String s && !s.isBlank())
                .map(Object::toString)
                .toList();
        assertThat(headers).doesNotContain("Entry #", "Mead Name");
        assertThat(headers).contains("Code");
        // In a COMPARATIVE round judges award medals by tasting, independently of
        // the prelim scores — so the Total (and the always-SUBMITTED Status) are
        // hidden from them.
        assertThat(headers).doesNotContain("Total", "Status");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderSeparateEntryNumberCodeAndMeadNameColumnsOnMedalRoundGrid() {
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");

        navigateToMedalRound(category);

        var grid = _get(Grid.class, spec -> spec.withId("medal-round-grid"));
        var headers = grid.getColumns().stream()
                .map(c -> ((Grid.Column) c).getHeaderText())
                .filter(h -> h instanceof String s && !s.isBlank())
                .map(Object::toString)
                .toList();
        assertThat(headers).contains("Entry #", "Code", "Mead Name");
        assertThat(headers).doesNotContain("Entry");
        // Status is hidden on a COMPARATIVE round (prelim sheets are always
        // SUBMITTED here); admins still see Total for context.
        assertThat(headers).doesNotContain("Status");
        assertThat(headers).contains("Total");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderScoresheetStatusColumnOnScoreBasedMedalRound() {
        // Scoresheet status is meaningful only on a SCORE_BASED round, where the
        // medal round owns the sheets (BLANK → FILLED → SUBMITTED). The
        // COMPARATIVE case hides it (sheets always SUBMITTED here) — asserted in
        // shouldRenderSeparateEntryNumberCodeAndMeadNameColumnsOnMedalRoundGrid.
        var category = categoryWithActiveConfig(MedalRoundMode.SCORE_BASED);
        var table = tableFor(category);
        submittedScoreBasedEntry(category, table, "AMA-1", 90);

        navigateToMedalRound(category);

        var grid = _get(Grid.class, spec -> spec.withId("medal-round-grid"));
        var headers = grid.getColumns().stream()
                .map(c -> ((Grid.Column) c).getHeaderText())
                .filter(h -> h instanceof String s && !s.isBlank())
                .map(Object::toString)
                .toList();
        assertThat(headers).contains("Status");

        var row = judgingService.findMedalRoundEntries(
                category.getId(), MedalRoundMode.SCORE_BASED).get(0);
        assertThat(row.scoresheetStatus()).isEqualTo(ScoresheetStatus.SUBMITTED);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowAdminPhrasedExplanationToAdmins() {
        var category = activeMedalRoundCategory();

        navigateToMedalRound(category);

        var explanation = _get(Span.class, spec -> spec.withId("round-explanation"));
        // Admins observe; judges act. The admin copy is phrased in the third person.
        assertThat(explanation.getText()).startsWith("Judges");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowPhysicalTableLineInHeader() {
        var category = activeMedalRoundCategory();

        navigateToMedalRound(category);

        var line = _get(com.vaadin.flow.component.html.Span.class,
                spec -> spec.withId("medal-round-physical-table-line"));
        // No physical table is assigned by default — should show the unassigned placeholder.
        assertThat(line.getText()).contains("Table").contains("Not assigned");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowAssignedJudgesLineToAdminOnMedalRound() {
        var category = activeMedalRoundCategory();
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var alice = userRepository.save(new User(
                "mr-judges-alice@example.com", "Alice Judge", UserStatus.ACTIVE, Role.USER));
        var bob = userRepository.save(new User(
                "mr-judges-bob@example.com", "Bob Judge", UserStatus.ACTIVE, Role.USER));
        competitionService.addParticipantByEmail(competition.getId(),
                alice.getEmail(), CompetitionRole.JUDGE, admin.getId());
        competitionService.addParticipantByEmail(competition.getId(),
                bob.getEmail(), CompetitionRole.JUDGE, admin.getId());
        var judgingId = judgingRepository.findByDivisionId(division.getId()).orElseThrow().getId();
        var medalRound = judgingRoundRepository.findByJudgingId(judgingId).stream()
                .filter(r -> r.getType() == RoundType.MEDAL
                        && category.getId().equals(r.getDivisionCategoryId()))
                .findFirst().orElseThrow();
        judgingService.assignJudge(medalRound.getId(), alice.getId(), admin.getId());
        judgingService.assignJudge(medalRound.getId(), bob.getId(), admin.getId());

        navigateToMedalRound(category);

        var line = _get(Span.class, spec -> spec.withId("medal-round-judges-line"));
        assertThat(line.getText()).contains("Judges:", "Alice Judge", "Bob Judge");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldListAdvancedEntriesInComparativeMode() {
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");
        advancedEntry(category, table, "AMA-2");

        navigateToMedalRound(category);

        var rows = judgingService.findMedalRoundEntries(category.getId(), MedalRoundMode.COMPARATIVE);
        assertThat(rows).hasSize(2);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAwardGoldMedalWhenAdminAppliesMedal() {
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");

        navigateToMedalRound(category);

        var row = judgingService.findMedalRoundEntries(
                category.getId(), MedalRoundMode.COMPARATIVE).get(0);
        var view = _get(MedalRoundView.class);
        view.applyMedal(row, Medal.GOLD);

        var award = medalAwardRepository.findByEntryId(row.entryId()).orElseThrow();
        assertThat(award.getMedal()).isEqualTo(Medal.GOLD);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldGateClearBehindConfirmDialog() {
        // Clear deletes the medal award row; the confirm dialog gates it so the
        // service is not called until the admin confirms.
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");

        navigateToMedalRound(category);

        var row = judgingService.findMedalRoundEntries(
                category.getId(), MedalRoundMode.COMPARATIVE).get(0);
        var view = _get(MedalRoundView.class);
        view.applyMedal(row, Medal.GOLD);
        var rowAfterAward = judgingService.findMedalRoundEntries(
                category.getId(), MedalRoundMode.COMPARATIVE).get(0);

        view.openClearConfirmDialog(rowAfterAward);

        assertThat(medalAwardRepository.findByEntryId(rowAfterAward.entryId()))
                .as("clear must not fire until confirm is clicked").isPresent();

        _click(_get(Button.class, spec -> spec.withId("medal-round-clear-confirm")));

        assertThat(medalAwardRepository.findByEntryId(rowAfterAward.entryId())).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldUpdateSummaryLineAfterAwardingMedal() {
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");

        navigateToMedalRound(category);

        var row = judgingService.findMedalRoundEntries(
                category.getId(), MedalRoundMode.COMPARATIVE).get(0);
        var view = _get(MedalRoundView.class);
        view.applyMedal(row, Medal.GOLD);

        var summary = _get(Span.class, spec -> spec.withId("medal-round-summary"));
        assertThat(summary.getText()).contains("1 Gold");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowTiedSlotBannerInScoreBasedMode() {
        var category = categoryWithActiveConfig(MedalRoundMode.SCORE_BASED);
        var table = tableFor(category);
        submittedScoreBasedEntry(category, table, "AMA-1", 90);
        submittedScoreBasedEntry(category, table, "AMA-2", 90);
        submittedScoreBasedEntry(category, table, "AMA-3", 80);

        navigateToMedalRound(category);

        var banner = _get(Span.class, spec -> spec.withId("medal-round-ties-banner"));
        assertThat(banner.getText()).contains("tied");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldFinalizeMedalRoundWhenAdminConfirms() {
        var category = activeMedalRoundCategory();

        navigateToMedalRound(category);

        var view = _get(MedalRoundView.class);
        view.openFinalizeDialog();
        _click(_get(Button.class, spec -> spec.withId("medal-round-finalize-confirm")));

        var medalRound = judgingService.findMedalRoundByCategoryId(category.getId()).orElseThrow();
        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.COMPLETE);
    }

    /**
     * Sets up a medal round at READY with NO physical table — simulates the
     * cascade-auto-created medal round. Admin needs to assign a physical table
     * before the round can be started.
     */
    private DivisionCategory readyMedalRoundNoPhysicalTable() {
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED
        division.advanceStatus(); // JUDGING
        division = divisionRepository.save(division);
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc", null, 1, CategoryScope.JUDGING));
        var config = new CategoryJudgingConfig(category.getId(), MedalRoundMode.COMPARATIVE);
        categoryConfigRepository.save(config);
        var judging = judgingService.ensureJudgingExists(division.getId());
        judging.markActive();
        judgingRepository.save(judging);
        var medalRound = new JudgingRound(judging.getId(), "Medal",
                category.getId(), null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        judgingRoundRepository.save(medalRound);
        return category;
    }

    @Test
    @WithMockUser(username = "md-details-judge@example.com", roles = "USER")
    void shouldOpenMeadDetailsDialogForJudgeOnComparativeMedalRoundWithoutMeadName() {
        // P17: the eye "mead details" dialog lets a COMPARATIVE judge see the
        // entry's characteristics — without the brand name (and without the
        // prelim scoresheet, which they can't open here).
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judge = userRepository.save(new User(
                "md-details-judge@example.com", "Details Judge", UserStatus.ACTIVE, Role.USER));
        competitionService.addParticipantByEmail(competition.getId(),
                judge.getEmail(), CompetitionRole.JUDGE, admin.getId());
        var judgingId = judgingRepository.findByDivisionId(division.getId()).orElseThrow().getId();
        var medalRound = judgingRoundRepository.findByJudgingId(judgingId).stream()
                .filter(r -> r.getType() == RoundType.MEDAL
                        && category.getId().equals(r.getDivisionCategoryId()))
                .findFirst().orElseThrow();
        judgingService.assignJudge(medalRound.getId(), judge.getId(), admin.getId());
        var entry = entryRepository.findAll().stream()
                .filter(e -> "AMA-1".equals(e.getEntryCode())).findFirst().orElseThrow();

        navigateToMedalRound(category);

        // The per-row eye button lives in a Grid component column (Karibu can't
        // click it); exercise the public open method it delegates to.
        _get(MedalRoundView.class).openMeadDetailsDialog(entry.getId());

        var dialog = _get(Dialog.class, spec -> spec.withId("mead-details-dialog"));
        assertThat(dialog.getHeaderTitle()).contains("AMA-1");
        var values = _find(dialog, TextField.class).stream().map(TextField::getValue).toList();
        assertThat(values).noneMatch(v -> v.contains("Mead")); // brand name "AMA-1 Mead" hidden
        assertThat(values).isNotEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldNotShowEditableModeOrTableSelectsInMedalRoundView() {
        // Round configuration (mode, table, schedule, judges) lives on the
        // unified Rounds grid now. The MedalRoundView header is read-only — even
        // at READY it must not render the old mode/table Selects. Mode editing is
        // covered by JudgingAdminViewTest's grid Edit-dialog test.
        var category = readyMedalRoundNoPhysicalTable();

        navigateToMedalRound(category);

        assertThat(com.github.mvysny.kaributesting.v10.LocatorJ._find(
                com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("medal-round-mode-select"))).isEmpty();
        assertThat(com.github.mvysny.kaributesting.v10.LocatorJ._find(
                com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("medal-round-physical-table-select"))).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldLetAssignedJudgeFinalizeScoreBasedMedalRoundEndToEnd() {
        // The whole point of the SCORE_BASED flow: an assigned judge scores the
        // sheets and finalizes the round themselves — no admin hand-off. Once
        // every sheet is FILLED, finalize submits them, populates medals from the
        // totals, and completes the round.
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED
        division.advanceStatus(); // JUDGING
        division = divisionRepository.save(division);
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc", null, 1, CategoryScope.JUDGING));
        categoryConfigRepository.save(new CategoryJudgingConfig(category.getId(), MedalRoundMode.SCORE_BASED));
        var judging = judgingService.ensureJudgingExists(division.getId());
        var judge = userRepository.save(new User(
                "mr-fin-judge-" + UUID.randomUUID() + "@example.com",
                "Judge", UserStatus.ACTIVE, Role.USER));
        var top = receivedEntryWithoutScoresheet(category, "001");
        var second = receivedEntryWithoutScoresheet(category, "002");
        var medalRound = new JudgingRound(judging.getId(), "Medal — M1A", category.getId(), null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignJudge(judge.getId());
        medalRound.assignEntry(top.getId());
        medalRound.assignEntry(second.getId());
        medalRound.markReady();
        medalRound.start();
        judgingRoundRepository.save(medalRound);
        fillSheet(medalRound, top, 92, judge.getId());
        fillSheet(medalRound, second, 81, judge.getId());

        judgingService.finalizeMedalRound(medalRound.getId(), judge.getId());

        var reloaded = judgingRoundRepository.findById(medalRound.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JudgingRoundStatus.COMPLETE);
        assertThat(scoresheetRepository.findByEntryId(top.getId()).orElseThrow().getStatus())
                .isEqualTo(ScoresheetStatus.SUBMITTED);
        var awards = medalAwardRepository.findByFinalCategoryId(category.getId());
        assertThat(awards).anyMatch(a -> a.getEntryId().equals(top.getId()) && a.getMedal() == Medal.GOLD);
        assertThat(awards).anyMatch(a -> a.getEntryId().equals(second.getId()) && a.getMedal() == Medal.SILVER);
        // Finalizing confirms the (auto-populated) awards so they propagate to
        // results + BOS — otherwise the gold never shows up as a BOS candidate.
        assertThat(awards).allMatch(MedalAward::isConfirmed);
        var adminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        assertThat(judgingService.findGoldMedalAwardsForDivision(division.getId(), adminId))
                .as("the finalized gold must be an eligible BOS candidate")
                .anyMatch(a -> a.getEntryId().equals(top.getId()));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldNotDuplicateEntryWhenScoringCascadeReadiesMedalRound() {
        // COMPARATIVE prelim flow: the entry lives in its SCORING round's entries
        // set. When the round finalizes, the cascade must NOT also add it to the
        // medal round's entries (judging_round_entries.entry_id is globally unique
        // — that would trip the constraint). The medal candidates derive from the
        // advance-flagged scoresheets instead.
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED
        division.advanceStatus(); // JUDGING
        division = divisionRepository.save(division);
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc", null, 1, CategoryScope.JUDGING));
        categoryConfigRepository.save(new CategoryJudgingConfig(category.getId(), MedalRoundMode.COMPARATIVE));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var entry = receivedEntryWithoutScoresheet(category, "AMA-1");

        var judging = judgingService.ensureJudgingExists(division.getId());
        var scoringRound = new JudgingRound(judging.getId(), "Panel A", category.getId(), null);
        scoringRound.assignEntry(entry.getId());
        scoringRound.markReady();
        scoringRound.start();
        judgingRoundRepository.save(scoringRound);

        var sheet = new Scoresheet(scoringRound.getId(), entry.getId());
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            sheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        sheet.setAdvancedToMedalRound(true);
        sheet.setFilledBy(admin.getId());
        sheet.markFilled();
        scoresheetRepository.save(sheet);

        // Cascade fires here — must not throw a duplicate-key DataIntegrityViolation.
        scoresheetService.finalizeScoringRound(scoringRound.getId(), admin.getId());

        var rows = judgingService.findMedalRoundEntries(category.getId(), MedalRoundMode.COMPARATIVE);
        assertThat(rows).extracting(MedalRoundEntryRow::entryId).containsExactly(entry.getId());
    }

    /** Builds a FILLED (not submitted) scoresheet on the round with the given total. */
    private void fillSheet(JudgingRound round, Entry entry, int total, UUID judgeId) {
        var sheet = new Scoresheet(round.getId(), entry.getId());
        int deficit = 100 - total;
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            int value = def.maxValue();
            if (deficit > 0 && def.maxValue() >= deficit) {
                value = def.maxValue() - deficit;
                deficit = 0;
            }
            sheet.updateScore(def.fieldName(), value, null);
        }
        sheet.setFilledBy(judgeId);
        sheet.markFilled();
        scoresheetRepository.save(sheet);
    }

}
