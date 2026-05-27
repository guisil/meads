package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CategoryScope;
import app.meads.competition.Competition;
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
        sheet.submit();
        scoresheetRepository.save(sheet);
    }

    private DivisionCategory pendingScoreBasedMedalRoundCategory() {
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED
        division.advanceStatus(); // JUDGING
        division = divisionRepository.save(division);
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc", null, 1, CategoryScope.JUDGING));
        var config = new CategoryJudgingConfig(category.getId(), MedalRoundMode.SCORE_BASED);
        categoryConfigRepository.save(config);
        var judging = judgingService.ensureJudgingExists(division.getId());
        var medalRound = new JudgingRound(judging.getId(), "Medal — M1A",
                category.getId(), null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        judgingRoundRepository.save(medalRound);
        return category;
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
    void shouldRecordExplicitWithholdWhenNoRowExists() {
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");

        navigateToMedalRound(category);

        var row = judgingService.findMedalRoundEntries(
                category.getId(), MedalRoundMode.COMPARATIVE).get(0);
        var view = _get(MedalRoundView.class);
        view.applyMedal(row, null);

        var award = medalAwardRepository.findByEntryId(row.entryId()).orElseThrow();
        assertThat(award.getMedal()).isNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldGateWithholdBehindConfirmDialog() {
        // Withhold is a sensitive action (records a deliberate no-medal
        // decision in the audit log) — the icon-button click opens a
        // ConfirmDialog before invoking applyMedal(null). The service must
        // not be called until the admin confirms.
        var category = activeMedalRoundCategory();
        var table = tableFor(category);
        advancedEntry(category, table, "AMA-1");

        navigateToMedalRound(category);

        var row = judgingService.findMedalRoundEntries(
                category.getId(), MedalRoundMode.COMPARATIVE).get(0);
        var view = _get(MedalRoundView.class);
        view.openWithholdConfirmDialog(row);

        assertThat(medalAwardRepository.findByEntryId(row.entryId()))
                .as("withhold must not fire until confirm is clicked").isEmpty();

        _click(_get(Button.class, spec -> spec.withId("medal-round-withhold-confirm")));

        var award = medalAwardRepository.findByEntryId(row.entryId()).orElseThrow();
        assertThat(award.getMedal()).isNull();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldGateClearBehindConfirmDialog() {
        // Clear deletes the audit row entirely (vs. Withhold which keeps the
        // row with medal=null). Confirm dialog warns and suggests Withhold
        // as the safer alternative for "no medal".
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

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldStartReadyMedalRoundWhenAdminConfirms() {
        var category = readyMedalRoundCategory();

        navigateToMedalRound(category);

        var view = _get(MedalRoundView.class);
        view.openStartDialog();
        _click(_get(Button.class, spec -> spec.withId("medal-round-start-confirm")));

        var medalRound = judgingService.findMedalRoundByCategoryId(category.getId()).orElseThrow();
        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
    }

    /**
     * Sets up a medal round at READY with a physical table assigned — the
     * preconditions the Start button requires. Mirrors {@link #categoryWithActiveConfig}
     * but stops at READY.
     */
    private DivisionCategory readyMedalRoundCategory() {
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED
        division.advanceStatus(); // JUDGING
        division = divisionRepository.save(division);
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc", null, 1, CategoryScope.JUDGING));
        var config = new CategoryJudgingConfig(category.getId(), MedalRoundMode.COMPARATIVE);
        categoryConfigRepository.save(config);
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        judging.markActive();
        judgingRepository.save(judging);
        var physicalTable = judgingService.createPhysicalTable(division.getId(),
                "Medal Table", admin.getId());
        var medalRound = new JudgingRound(judging.getId(), physicalTable.getId(),
                "Medal", category.getId(), null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        judgingRoundRepository.save(medalRound);
        return category;
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
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAllowAdminToChangeMedalRoundModeWhileReady() {
        var category = readyMedalRoundNoPhysicalTable();

        navigateToMedalRound(category);

        var modeSelect = _get(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("medal-round-mode-select"));
        @SuppressWarnings("unchecked")
        var select = (com.vaadin.flow.component.select.Select<MedalRoundMode>) modeSelect;
        select.setValue(MedalRoundMode.SCORE_BASED);

        var medalRound = judgingService.findMedalRoundByCategoryId(category.getId()).orElseThrow();
        assertThat(medalRound.getMedalMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldAllowAdminToAssignPhysicalTableWhileReady() {
        var category = readyMedalRoundNoPhysicalTable();
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var physicalTable = judgingService.createPhysicalTable(division.getId(),
                "Medal Table", admin.getId());

        navigateToMedalRound(category);

        var ptSelect = _get(com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("medal-round-physical-table-select"));
        @SuppressWarnings("unchecked")
        var select = (com.vaadin.flow.component.select.Select<PhysicalTable>) ptSelect;
        select.setValue(physicalTable);

        var medalRound = judgingService.findMedalRoundByCategoryId(category.getId()).orElseThrow();
        assertThat(medalRound.getPhysicalTableId()).isEqualTo(physicalTable.getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldEnableAssignJudgesButtonWhileMedalRoundActive() {
        // Mid-deliberation, admin may discover the panel needs adjusting (a
        // judge dropped out, or a head-judge was missed). Allowed while ACTIVE;
        // only locked once the medal round is COMPLETE.
        var category = activeMedalRoundCategory();

        navigateToMedalRound(category);

        var assignJudges = _get(Button.class, spec -> spec.withId("medal-round-assign-judges"));
        assertThat(assignJudges.isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldNotShowEditableModeAndPhysicalTableSelectsWhenMedalRoundActive() {
        var category = activeMedalRoundCategory();

        navigateToMedalRound(category);

        // ACTIVE medal rounds: header shows read-only status/PT info, no editable selects.
        assertThat(com.github.mvysny.kaributesting.v10.LocatorJ._find(
                com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("medal-round-mode-select"))).isEmpty();
        assertThat(com.github.mvysny.kaributesting.v10.LocatorJ._find(
                com.vaadin.flow.component.select.Select.class,
                spec -> spec.withId("medal-round-physical-table-select"))).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldEnableStartButtonForReadyScoreBasedMedalRoundWhenJudgingPhaseIsNotStarted() {
        // Small-category flow: the medal round IS the first round in the division.
        // judging.phase stays NOT_STARTED until JudgingService.startRound flips it,
        // so the UI Start button must allow starting from that state — otherwise
        // it's a chicken-and-egg deadlock. (Service-side, startRound already
        // handles NOT_STARTED → ACTIVE for any round type, see line 713-715.)
        var category = pendingScoreBasedMedalRoundCategory();
        // Get the medal round into READY with a physical table assigned so the
        // only remaining gate is judging.phase.
        var judging = judgingService.ensureJudgingExists(division.getId());
        var medalRound = judgingRoundRepository.findByJudgingId(judging.getId()).stream()
                .filter(r -> r.getType() == RoundType.MEDAL)
                .findFirst().orElseThrow();
        var pt = judgingService.createPhysicalTable(division.getId(), "Table 1",
                userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId());
        medalRound.assignToPhysicalTable(pt.getId());
        var judgeA = userRepository.save(new User(
                "mr-judge-a-" + UUID.randomUUID() + "@example.com",
                "Judge A", UserStatus.ACTIVE, Role.USER));
        var judgeB = userRepository.save(new User(
                "mr-judge-b-" + UUID.randomUUID() + "@example.com",
                "Judge B", UserStatus.ACTIVE, Role.USER));
        medalRound.assignJudge(judgeA.getId());
        medalRound.assignJudge(judgeB.getId());
        var entry = receivedEntryWithoutScoresheet(category, "001");
        medalRound.assignEntry(entry.getId());
        medalRound.markReady();
        judgingRoundRepository.save(medalRound);
        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.NOT_STARTED);

        navigateToMedalRound(category);

        var startButton = _get(Button.class, spec -> spec.withId("medal-round-start"));
        assertThat(startButton.isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldShowAllReceivedEntriesInScoreBasedAssignDialogEvenWithoutScoresheets() {
        // Small-category flow: SCORE_BASED medal round runs without a preceding
        // scoring round, so eligible entries don't have SUBMITTED scoresheets
        // yet. The Assign Entries dialog must still list them — they'll get
        // BLANK scoresheets on round start (Cycle 3 wiring).
        var category = pendingScoreBasedMedalRoundCategory();
        receivedEntryWithoutScoresheet(category, "001");
        receivedEntryWithoutScoresheet(category, "002");
        receivedEntryWithoutScoresheet(category, "003");

        navigateToMedalRound(category);
        var assignEntries = _get(Button.class, spec -> spec.withId("medal-round-assign-entries"));
        _click(assignEntries);

        @SuppressWarnings("unchecked")
        var grid = (Grid<Entry>) _get(Grid.class, spec -> spec.withId("medal-round-assign-entries-grid"));
        assertThat(grid.getGenericDataView().getItems().count()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderScoreBasedAssignDialogAsReadOnlyPreviewAndSyncOnSave() {
        // Force-all invariant: SCORE_BASED dialog is informational — every
        // RECEIVED entry in the category MUST be assigned, so the dialog
        // cannot offer a subset. Selection mode is NONE; Save click triggers
        // syncScoreBasedMedalRoundEntries which assigns any missing entries.
        var category = pendingScoreBasedMedalRoundCategory();
        receivedEntryWithoutScoresheet(category, "001");
        receivedEntryWithoutScoresheet(category, "002");
        receivedEntryWithoutScoresheet(category, "003");

        navigateToMedalRound(category);
        var assignEntries = _get(Button.class, spec -> spec.withId("medal-round-assign-entries"));
        _click(assignEntries);

        @SuppressWarnings("unchecked")
        var grid = (Grid<Entry>) _get(Grid.class, spec -> spec.withId("medal-round-assign-entries-grid"));
        // Read-only: selection mode is NONE (no checkboxes for partial picks).
        assertThat(grid.getSelectionModel().getClass().getSimpleName())
                .contains("None");

        var save = _get(Button.class, spec -> spec.withId("medal-round-assign-entries-save"));
        _click(save);

        var judgingId = judgingRepository.findByDivisionId(division.getId())
                .orElseThrow().getId();
        var medalRound = judgingRoundRepository.findByJudgingId(judgingId).stream()
                .filter(r -> r.getType() == RoundType.MEDAL)
                .findFirst().orElseThrow();
        assertThat(medalRound.getEntries()).hasSize(3);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderAssignEntriesButtonEnabledForAdminWhenMedalRoundActive() {
        // 3c: Assign Entries dialog mirrors the scoring-round equivalent.
        // The button is part of the admin action bar and stays enabled
        // through PENDING / READY / ACTIVE; only COMPLETE locks it.
        var category = activeMedalRoundCategory();

        navigateToMedalRound(category);

        var assignEntries = _get(Button.class, spec -> spec.withId("medal-round-assign-entries"));
        assertThat(assignEntries.isEnabled()).isTrue();
    }
}
