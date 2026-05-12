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
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.ScoresheetRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
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

import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

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
    @Autowired EntryRepository entryRepository;
    @Autowired CategoryJudgingConfigRepository categoryJudgingConfigRepository;
    @Autowired ScoresheetRepository scoresheetRepository;
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

    @Test
    @WithMockUser(username = FRESH_USER_EMAIL, roles = "USER")
    void shouldRenderHeaderAndEmptyStateForUserWithNoAssignments() {
        UI.getCurrent().navigate("my-judging");

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("My Judging");

        var anchors = _find(Anchor.class);
        var profileAnchor = anchors.stream()
                .filter(a -> "profile".equals(a.getHref()))
                .findFirst();
        var competitionsAnchor = anchors.stream()
                .filter(a -> "competitions".equals(a.getHref()) || "my-competitions".equals(a.getHref()))
                .findFirst();
        assertThat(profileAnchor).isPresent();
        assertThat(competitionsAnchor).isPresent();
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRenderTablesGroupedByCompetitionWhenJudgeIsAssigned() {
        var judge = userRepository.findByEmail(JUDGE_EMAIL).orElseThrow();

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
        division.advanceStatus();
        divisionRepository.save(division);

        // Make the judge a participant with JUDGE role so the assignment is consistent.
        competitionService.addParticipantByEmail(competition.getId(),
                JUDGE_EMAIL, CompetitionRole.JUDGE, admin.getId());

        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table 1",
                category.getId(), null, admin.getId());
        judgingService.assignJudge(table.getId(), judge.getId(), admin.getId());

        UI.getCurrent().navigate("my-judging");

        var h3Texts = _find(H3.class).stream().map(H3::getText).toList();
        var spanTexts = _find(Span.class).stream().map(Span::getText).toList();
        assertThat(h3Texts.stream().anyMatch(t -> t != null && t.contains("MyJudging Competition")))
                .as("competition name in H3").isTrue();
        assertThat(spanTexts.stream().anyMatch(t -> t != null && t.contains("Profissional")))
                .as("division name in Span").isTrue();
        assertThat(spanTexts.stream().anyMatch(t -> t != null && t.contains("Table 1")))
                .as("table name in Span").isTrue();
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRenderResumeNextDraftAnchorWhenDraftScoresheetExists() {
        var judge = userRepository.findByEmail(JUDGE_EMAIL).orElseThrow();
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
        division.advanceStatus();
        divisionRepository.save(division);

        competitionService.addParticipantByEmail(competition.getId(),
                JUDGE_EMAIL, CompetitionRole.JUDGE, admin.getId());

        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table 1",
                category.getId(), null, admin.getId());
        judgingService.assignJudge(table.getId(), judge.getId(), admin.getId());

        var entrant = userRepository.save(new User(
                "entrant-myjudging-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-1",
                "Test", category.getId(), Sweetness.DRY, BigDecimal.valueOf(11.0),
                Carbonation.STILL, "Wildflower", null, false, null, null);
        entry = entryRepository.save(entry);
        var sheet = scoresheetRepository.save(new app.meads.judging.Scoresheet(table.getId(), entry.getId()));

        UI.getCurrent().navigate("my-judging");

        var anchors = _find(Anchor.class);
        var resumeAnchor = anchors.stream()
                .filter(a -> a.getHref() != null && a.getHref().endsWith("/scoresheets/" + sheet.getId()))
                .findFirst();
        assertThat(resumeAnchor).as("resume next draft anchor").isPresent();
    }

    @Test
    @WithMockUser(username = JUDGE_EMAIL, roles = "USER")
    void shouldRenderMedalRoundsSectionWhenAnActiveConfigCoversTheJudgesCategory() {
        var judge = userRepository.findByEmail(JUDGE_EMAIL).orElseThrow();
        var admin = userRepository.save(new User(
                "my-judging-admin-" + UUID.randomUUID() + "@example.com",
                "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var competition = competitionRepository.save(new Competition(
                "MR Competition", "mr-comp-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        var division = divisionRepository.save(new Division(
                competition.getId(), "Profissional", "mr-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
        division.advanceStatus();
        division.advanceStatus();
        division.advanceStatus();
        divisionRepository.save(division);

        competitionService.addParticipantByEmail(competition.getId(),
                JUDGE_EMAIL, CompetitionRole.JUDGE, admin.getId());

        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M2B", "Cyser", "Desc",
                null, 1, CategoryScope.JUDGING));
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table 9",
                category.getId(), null, admin.getId());
        judgingService.assignJudge(table.getId(), judge.getId(), admin.getId());

        // Construct an ACTIVE CategoryJudgingConfig for this category.
        var config = new app.meads.judging.CategoryJudgingConfig(category.getId());
        config.markReady();
        config.startMedalRound();
        categoryJudgingConfigRepository.save(config);

        UI.getCurrent().navigate("my-judging");

        var anchors = _find(Anchor.class);
        var medalRoundLink = anchors.stream()
                .filter(a -> a.getHref() != null
                        && a.getHref().endsWith("/medal-rounds/" + category.getId()))
                .findFirst();
        assertThat(medalRoundLink).as("medal round anchor").isPresent();
    }
}
