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
import app.meads.judging.internal.BosPlacementRepository;
import app.meads.judging.internal.BosView;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.MedalAwardRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.select.Select;
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
class BosViewTest {

    private static final String ADMIN_EMAIL = "bos-view-admin-test@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired DivisionCategoryRepository divisionCategoryRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired BosPlacementRepository bosPlacementRepository;
    @Autowired JudgingRepository judgingRepository;
    @Autowired MedalAwardRepository medalAwardRepository;
    @Autowired CompetitionService competitionService;
    @Autowired JudgingService judgingService;

    private Competition competition;
    private Division division;
    private User admin;

    @BeforeEach
    void setup(TestInfo testInfo) {
        admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(ADMIN_EMAIL, "BOS Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "BOS Test Competition", "bos-comp-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        var freshDivision = new Division(
                competition.getId(), "Amadora", "bos-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        // Set BOS places while still in DRAFT — locked beyond REGISTRATION_OPEN.
        freshDivision.updateBosPlaces(3);
        division = divisionRepository.save(freshDivision);
        division.advanceStatus();
        division.advanceStatus();
        division.advanceStatus();
        division = divisionRepository.save(division);

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

    private void putJudgingInBosPhase() {
        var judging = judgingService.ensureJudgingExists(division.getId());
        judging.markActive();
        var refreshedActive = judgingRepository.save(judging);
        refreshedActive.startBos();
        judgingRepository.save(refreshedActive);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderHeaderForAdminWhenJudgingInBosPhase() {
        putJudgingInBosPhase();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/bos");

        var heading = _get(H2.class);
        assertThat(heading.getText()).contains("Best of Show");
        assertThat(heading.getText()).contains("Amadora");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderPlacementsGridWithAllBosPlacesSlots() {
        putJudgingInBosPhase();

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/bos");

        var grids = _find(Grid.class);
        var placements = grids.stream()
                .filter(g -> "bos-placements-grid".equals(g.getId().orElse(null)))
                .findFirst().orElseThrow(() -> new AssertionError("Placements grid not found"));

        var rows = placements.getGenericDataView().getItems().toList();
        assertThat(rows).hasSize(3);

        var headers = placements.getColumns().stream()
                .map(c -> ((Grid.Column<?>) c).getHeaderText())
                .toList();
        assertThat(headers).containsExactly("Place", "Entry", "Mead name", "Category", "Awarded by", "Action");
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRenderCandidatesGridWithGoldMedalAwardsNotYetPlaced() {
        putJudgingInBosPhase();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var entrant = userRepository.save(new User(
                "entrant-bos-cand-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-1",
                "Hiveheart Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Wildflower", null, false, null, null);
        entry.assignFinalCategory(category.getId());
        entry = entryRepository.save(entry);

        var medalAward = new app.meads.judging.MedalAward(entry.getId(), division.getId(),
                category.getId(), app.meads.judging.Medal.GOLD, admin.getId());
        medalAwardRepository.save(medalAward);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/bos");

        var candidates = (Grid<app.meads.judging.MedalAward>) _find(Grid.class).stream()
                .filter(g -> "bos-candidates-grid".equals(g.getId().orElse(null)))
                .findFirst().orElseThrow(() -> new AssertionError("Candidates grid not found"));
        var rows = candidates.getGenericDataView().getItems().toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEntryId()).isEqualTo(entry.getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    @SuppressWarnings("unchecked")
    void shouldRecordBosPlacementWhenAssignDialogSaved() {
        putJudgingInBosPhase();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var entrant = userRepository.save(new User(
                "entrant-bos-assign-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-1",
                "Hiveheart Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Wildflower", null, false, null, null);
        entry.assignFinalCategory(category.getId());
        entry = entryRepository.save(entry);
        medalAwardRepository.save(new app.meads.judging.MedalAward(
                entry.getId(), division.getId(), category.getId(),
                app.meads.judging.Medal.GOLD, admin.getId()));

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/bos");

        var view = _get(BosView.class);
        view.openAssignDialog(1);

        var candidateSelect = (Select<app.meads.judging.MedalAward>) _get(
                Select.class, spec -> spec.withId("bos-assign-candidate-select"));
        var goldAward = candidateSelect.getListDataView().getItems().toList().get(0);
        candidateSelect.setValue(goldAward);

        _click(_get(Button.class, spec -> spec.withId("bos-assign-save-button")));

        var placements = bosPlacementRepository.findByDivisionIdOrderByPlace(division.getId());
        assertThat(placements).hasSize(1);
        assertThat(placements.get(0).getPlace()).isEqualTo(1);
        assertThat(placements.get(0).getEntryId()).isEqualTo(entry.getId());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldDeleteBosPlacementWhenDeleteDialogConfirmed() {
        putJudgingInBosPhase();
        var category = divisionCategoryRepository.save(new DivisionCategory(
                division.getId(), null, "M1A", "Dry Mead", "Desc",
                null, 1, CategoryScope.JUDGING));
        var entrant = userRepository.save(new User(
                "entrant-bos-del-" + UUID.randomUUID() + "@example.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        var entry = new Entry(division.getId(), entrant.getId(), 1, "AMA-1",
                "Hiveheart Mead", category.getId(), Sweetness.DRY,
                BigDecimal.valueOf(11.0), Carbonation.STILL,
                "Wildflower", null, false, null, null);
        entry.assignFinalCategory(category.getId());
        entry = entryRepository.save(entry);
        medalAwardRepository.save(new app.meads.judging.MedalAward(
                entry.getId(), division.getId(), category.getId(),
                app.meads.judging.Medal.GOLD, admin.getId()));
        var placement = judgingService.recordBosPlacement(division.getId(),
                entry.getId(), 1, admin.getId());

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/bos");

        var view = _get(BosView.class);
        view.openDeleteDialog(placement);
        _click(_get(Button.class, spec -> spec.withId("bos-delete-confirm-button")));

        assertThat(bosPlacementRepository.findByDivisionIdOrderByPlace(division.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SYSTEM_ADMIN")
    void shouldRenderReadOnlyBannerAndHideActionButtonsWhenJudgingComplete() {
        // Phase progression: NOT_STARTED → ACTIVE → BOS → COMPLETE.
        var judging = judgingService.ensureJudgingExists(division.getId());
        judging.markActive();
        judging = judgingRepository.save(judging);
        judging.startBos();
        judging = judgingRepository.save(judging);
        judging.completeBos();
        judgingRepository.save(judging);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/bos");

        var bannerSpans = _find(com.vaadin.flow.component.html.Span.class).stream()
                .filter(s -> "bos-complete-banner".equals(s.getId().orElse(null)))
                .toList();
        assertThat(bannerSpans).as("COMPLETE banner").isNotEmpty();

        // Candidates grid hidden when COMPLETE (read-only).
        var grids = _find(Grid.class);
        var candidatesGrid = grids.stream()
                .filter(g -> "bos-candidates-grid".equals(g.getId().orElse(null)))
                .findFirst();
        assertThat(candidatesGrid).as("candidates grid hidden when COMPLETE").isEmpty();
    }
}
