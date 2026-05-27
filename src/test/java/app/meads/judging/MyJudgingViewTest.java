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
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.internal.MedalRoundView;
import app.meads.judging.internal.MyJudgingView;
import app.meads.judging.internal.RoundView;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyJudgingView is a redirect-or-stub. If the judge has an ACTIVE round it
 * forwards there directly; otherwise it shows a bare "no active round"
 * message.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class MyJudgingViewTest {

    private static final String JUDGE_EMAIL = "my-judging-test-judge@example.com";
    private static final String FRESH_USER_EMAIL = "my-judging-fresh-user@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired DivisionCategoryRepository divisionCategoryRepository;
    @Autowired app.meads.judging.internal.CategoryJudgingConfigRepository categoryJudgingConfigRepository;
    @Autowired app.meads.judging.internal.JudgingRoundRepository judgingRoundRepository;
    @Autowired CompetitionService competitionService;
    @Autowired JudgingService judgingService;

    @BeforeEach
    void setup(TestInfo testInfo) {
        userRepository.findByEmail(JUDGE_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(JUDGE_EMAIL, "Test Judge", UserStatus.ACTIVE, Role.USER)));
        userRepository.findByEmail(FRESH_USER_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(FRESH_USER_EMAIL, "Fresh User", UserStatus.ACTIVE, Role.USER)));

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

    private record Fixture(Competition competition, Division division,
                            DivisionCategory category, UUID adminId, UUID judgeId,
                            String judgeEmail) {}

    /**
     * Each test that needs a judge with assignments creates a UNIQUE judge.
     * The class-level @DirtiesContext only resets at end-of-class, so the
     * shared JUDGE_EMAIL accumulates ACTIVE rounds across tests and would
     * mess up assertions like "this judge has exactly one ACTIVE round".
     * Per-test unique judges sidestep the issue cleanly.
     */
    private Fixture createJudgingFixture(String catCode) {
        var judgeEmail = "judge-" + UUID.randomUUID() + "@example.com";
        var judge = userRepository.save(new User(
                judgeEmail, "Test Judge", UserStatus.ACTIVE, Role.USER));
        var admin = userRepository.save(new User(
                "my-judging-admin-" + UUID.randomUUID() + "@example.com",
                "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var competition = competitionRepository.save(new Competition(
                "MyJudging Competition", "myjudging-comp-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        var division = divisionRepository.save(new Division(
                competition.getId(), "Profissional", "myjudging-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
        division.advanceStatus();
        division.advanceStatus();
        division.advanceStatus(); // → JUDGING
        divisionRepository.save(division);
        competitionService.addParticipantByEmail(competition.getId(),
                judgeEmail, CompetitionRole.JUDGE, admin.getId());
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, catCode, "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        return new Fixture(competition, division, category, admin.getId(),
                judge.getId(), judgeEmail);
    }

    private void authAs(String email) {
        var authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(email).password("password").authorities(authorities).build();
        var auth = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        propagateSecurityContext(auth);
    }

    @Test
    @WithMockUser(username = FRESH_USER_EMAIL, roles = "USER")
    void shouldShowEmptyStateWhenUserHasNoActiveRound() {
        UI.getCurrent().navigate("my-judging");

        assertThat(_find(MyJudgingView.class)).as("MyJudgingView rendered").isNotEmpty();
        var empty = _get(Span.class, spec -> spec.withId("my-judging-empty"));
        assertThat(empty.getText()).contains("No active round");
    }

    @Test
    void shouldShowEmptyStateWhenJudgeOnlyHasPendingAssignments() {
        var fx = createJudgingFixture("M1A");
        var judging = judgingService.ensureJudgingExists(fx.division().getId());
        var round = judgingService.createRound(judging.getId(), "Table 1",
                fx.category().getId(), null, fx.adminId());
        judgingService.assignJudge(round.getId(), fx.judgeId(), fx.adminId());

        authAs(fx.judgeEmail());
        UI.getCurrent().navigate("my-judging");

        assertThat(_find(MyJudgingView.class)).as("MyJudgingView rendered").isNotEmpty();
        var empty = _get(Span.class, spec -> spec.withId("my-judging-empty"));
        assertThat(empty.getText()).contains("No active round");
    }

    @Test
    void shouldForwardToRoundViewWhenJudgeHasActiveScoringRound() {
        var fx = createJudgingFixture("M1A");
        var judging = judgingService.ensureJudgingExists(fx.division().getId());
        var round = judgingService.createRound(judging.getId(), "Table 1",
                fx.category().getId(), null, fx.adminId());
        var table = judgingService.createPhysicalTable(
                fx.division().getId(), "Table 1", fx.adminId());
        judgingService.assignRoundToPhysicalTable(round.getId(), table.getId(), fx.adminId());
        judgingService.assignJudge(round.getId(), fx.judgeId(), fx.adminId());
        judgingService.assignJudge(round.getId(), userRepository.save(new User(
                "co-judge-" + UUID.randomUUID() + "@example.com",
                "Co-Judge", UserStatus.ACTIVE, Role.USER)).getId(), fx.adminId());
        // Need an entry assigned for the round to reach READY.
        var entrant = userRepository.save(new User(
                "entrant-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new app.meads.entry.Entry(fx.division().getId(), entrant.getId(),
                1, "AMA-1", "Mead", fx.category().getId(),
                app.meads.entry.Sweetness.DRY, java.math.BigDecimal.valueOf(11.0),
                app.meads.entry.Carbonation.STILL, "Wildflower",
                null, false, null, null);
        var er = ctx.getBean(app.meads.entry.internal.EntryRepository.class).save(entry);
        judgingService.assignEntryToRound(round.getId(), er.getId(), fx.adminId());
        judgingService.startRound(round.getId(), fx.adminId());

        authAs(fx.judgeEmail());
        UI.getCurrent().navigate("my-judging");

        // After forward, RoundView is the active view (not MyJudgingView).
        assertThat(_find(RoundView.class)).as("forwarded to RoundView").isNotEmpty();
        assertThat(_find(MyJudgingView.class)).as("MyJudgingView NOT rendered").isEmpty();
    }

    @Test
    void shouldForwardToMedalRoundViewWhenJudgeHasActiveMedalRound() {
        var fx = createJudgingFixture("M3B");
        var judging = judgingService.ensureJudgingExists(fx.division().getId());
        var config = new CategoryJudgingConfig(fx.category().getId(), MedalRoundMode.COMPARATIVE);
        categoryJudgingConfigRepository.save(config);
        var medalRound = new JudgingRound(judging.getId(), "Medal",
                fx.category().getId(), null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.assignJudge(fx.judgeId());
        medalRound.markReady();
        medalRound.start();
        judgingRoundRepository.save(medalRound);

        authAs(fx.judgeEmail());
        UI.getCurrent().navigate("my-judging");

        assertThat(_find(MedalRoundView.class)).as("forwarded to MedalRoundView").isNotEmpty();
        assertThat(_find(MyJudgingView.class)).as("MyJudgingView NOT rendered").isEmpty();
    }

    @Test
    void shouldShowEmptyStateWhenJudgeOnlyHasCompletedRound() {
        var fx = createJudgingFixture("M2A");
        var judging = judgingService.ensureJudgingExists(fx.division().getId());
        var round = new JudgingRound(judging.getId(), "Table X",
                fx.category().getId(), null);
        round.assignJudge(fx.judgeId());
        round.start();
        round.markComplete();
        judgingRoundRepository.save(round);

        authAs(fx.judgeEmail());
        UI.getCurrent().navigate("my-judging");

        assertThat(_find(MyJudgingView.class)).isNotEmpty();
        var empty = _get(Span.class, spec -> spec.withId("my-judging-empty"));
        assertThat(empty.getText()).contains("No active round");
    }
}
