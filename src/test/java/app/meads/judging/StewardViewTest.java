package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.CategoryScope;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionRole;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.Participant;
import app.meads.competition.ParticipantRole;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.competition.internal.ParticipantRepository;
import app.meads.competition.internal.ParticipantRoleRepository;
import app.meads.entry.Carbonation;
import app.meads.entry.Entry;
import app.meads.entry.Sweetness;
import app.meads.entry.internal.EntryRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.internal.ScoresheetRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class StewardViewTest {

    private static final String ADMIN_EMAIL = "steward-view-admin-test@example.com";
    private static final String STEWARD_EMAIL = "steward-view-steward-test@example.com";
    private static final String NON_STEWARD_EMAIL = "steward-view-other-test@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired DivisionCategoryRepository divisionCategoryRepository;
    @Autowired ParticipantRepository participantRepository;
    @Autowired ParticipantRoleRepository participantRoleRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired ScoresheetRepository scoresheetRepository;
    @Autowired JudgingService judgingService;

    private Competition competition;
    private Division division;

    @BeforeEach
    void setup(TestInfo testInfo) {
        userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ADMIN_EMAIL, "Steward View Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN)));
        userRepository.findByEmail(STEWARD_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(STEWARD_EMAIL, "The Steward", UserStatus.ACTIVE, Role.USER)));
        userRepository.findByEmail(NON_STEWARD_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(NON_STEWARD_EMAIL, "Not A Steward", UserStatus.ACTIVE, Role.USER)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "Steward View Test Competition", "steward-view-comp-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        division = divisionRepository.save(new Division(
                competition.getId(), "Amadora", "steward-view-div-" + suffix,
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

    private void makeSteward(String email) {
        var user = userRepository.findByEmail(email).orElseThrow();
        var participant = participantRepository.save(
                new Participant(competition.getId(), user.getId()));
        participantRoleRepository.save(
                new ParticipantRole(participant.getId(), CompetitionRole.STEWARD));
    }

    @Test
    @WithMockUser(username = STEWARD_EMAIL, roles = "USER")
    void shouldShowTablesAndEntriesForSteward() {
        makeSteward(STEWARD_EMAIL);
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED
        division.advanceStatus(); // JUDGING
        division = divisionRepository.save(division);
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc", null, 1, CategoryScope.JUDGING));
        var admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        var judging = judgingService.ensureJudgingExists(division.getId());
        var table = judgingService.createTable(judging.getId(), "Table A",
                category.getId(), null, admin.getId());
        var entrant = userRepository.save(new User(
                "sv-entrant-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = entryRepository.save(new Entry(division.getId(), entrant.getId(), 1,
                "AMA-1", "Steward Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL, "Honey", null, false, null, null));
        scoresheetRepository.save(new Scoresheet(table.getId(), entry.getId()));

        UI.getCurrent().navigate("my-stewarding");

        var spanTexts = _find(Span.class).stream()
                .map(Span::getText).filter(t -> t != null).toList();
        assertThat(spanTexts).anyMatch(t -> t.contains("Table A"));
        assertThat(spanTexts).anyMatch(t -> t.contains("AMA-1") && t.contains("Steward Mead"));
    }

    @Test
    @WithMockUser(username = NON_STEWARD_EMAIL, roles = "USER")
    void shouldShowEmptyStateForNonSteward() {
        UI.getCurrent().navigate("my-stewarding");

        var empty = _get(Span.class, spec -> spec.withId("steward-empty"));
        assertThat(empty.getText()).isNotBlank();
    }
}
