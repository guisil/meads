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
        config.markReady();
        config.startMedalRound();
        categoryConfigRepository.save(config);
        return category;
    }

    private void submittedScoreBasedEntry(DivisionCategory category, JudgingTable table,
                                          String code, int total) {
        var entrant = userRepository.save(new User(
                "mr-sb-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = entryRepository.save(new Entry(division.getId(), entrant.getId(),
                entryNum++, code, code + " Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL, "Wildflower", null, false, null, null));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        entryService.assignFinalCategory(entry.getId(), category.getId(), admin.getId());
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

    private JudgingTable tableFor(DivisionCategory category) {
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        return judgingService.createTable(judging.getId(), "Table A",
                category.getId(), null, admin.getId());
    }

    private void advancedEntry(DivisionCategory category, JudgingTable table, String code) {
        var entrant = userRepository.save(new User(
                "mr-entrant-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = entryRepository.save(new Entry(division.getId(), entrant.getId(),
                entryNum++, code, code + " Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL, "Wildflower", null, false, null, null));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        entryService.assignFinalCategory(entry.getId(), category.getId(), admin.getId());
        var sheet = new Scoresheet(table.getId(), entry.getId());
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            sheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        sheet.setAdvancedToMedalRound(true);
        sheet.submit();
        scoresheetRepository.save(sheet);
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

        var config = categoryConfigRepository.findByDivisionCategoryId(category.getId()).orElseThrow();
        assertThat(config.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.COMPLETE);
    }
}
