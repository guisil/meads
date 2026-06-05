package app.meads.awards.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class MyResultsViewTest {

    private static final String ENTRANT_EMAIL = "my-results-entrant@example.com";

    @Autowired ApplicationContext ctx;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired UserRepository userRepository;

    private Competition competition;
    private Division division;

    @BeforeEach
    void setup(TestInfo testInfo) {
        userRepository.findByEmail(ENTRANT_EMAIL).orElseGet(() ->
                userRepository.save(new User(ENTRANT_EMAIL, "Entrant",
                        UserStatus.ACTIVE, Role.USER)));

        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "My Results Test", "myres-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        var d = new Division(competition.getId(), "Amateur", "myres-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division = divisionRepository.save(d);

        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);

        var auth = resolveAuthentication(testInfo);
        if (auth != null) {
            SecurityContextHolder.getContext().setAuthentication(auth);
            propagateSecurityContext(auth);
        }
    }

    private Authentication resolveAuthentication(TestInfo testInfo) {
        var method = testInfo.getTestMethod().orElse(null);
        if (method == null) {
            return null;
        }
        var withMockUser = method.getAnnotation(WithMockUser.class);
        if (withMockUser == null) {
            return null;
        }
        var username = withMockUser.username().isEmpty() ? withMockUser.value() : withMockUser.username();
        var authorities = Arrays.stream(withMockUser.roles())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        var ud = org.springframework.security.core.userdetails.User.builder()
                .username(username).password("p").authorities(authorities).build();
        return new UsernamePasswordAuthenticationToken(ud, null, authorities);
    }

    private void propagateSecurityContext(Authentication authentication) {
        var fakeRequest = (FakeRequest) VaadinServletRequest.getCurrent().getRequest();
        fakeRequest.setUserPrincipalInt(authentication);
        fakeRequest.setUserInRole((p, r) -> authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + r)));
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldForwardAwayWhenStatusNotPublished() {
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-results");

        // Status is DRAFT — view should have forwarded away, no my-results-heading rendered.
        assertThat(_find(H2.class).stream()
                .anyMatch(h -> "my-results-heading".equals(h.getId().orElse(""))))
                .isFalse();
    }

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldRenderHeadingAndGridWhenPublished() {
        for (int i = 0; i < 5; i++) {
            division.advanceStatus();
        }
        division = divisionRepository.save(division);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-results");

        var heading = _get(H2.class, spec -> spec.withId("my-results-heading"));
        assertThat(heading.getText()).contains("My Results Test");
        assertThat(heading.getText()).contains("Amateur");
        _get(Grid.class, spec -> spec.withId("my-results-grid"));
    }

    @Test
    void shouldRenderEntrantScoresheetDialogWithCommentsAdvancedMedalBosAndNoJudgeLabel() {
        var field = new app.meads.awards.AnonymizedScoresheetView.FieldScore(
                "Aroma", 20, 24, "Lovely floral nose");
        var sheet = new app.meads.awards.AnonymizedScoresheetView.AnonymizedScoresheet(
                1, "en", 88, true, java.util.List.of(field), "Well made.");
        var meadDetails = new app.meads.awards.AnonymizedScoresheetView.MeadDetails(
                app.meads.entry.Sweetness.DRY, app.meads.entry.Strength.STANDARD,
                java.math.BigDecimal.valueOf(12.5), app.meads.entry.Carbonation.STILL,
                "Wildflower honey", "Orange peel", true, "French oak 6 months",
                "Bottle conditioned");
        var view = new app.meads.awards.AnonymizedScoresheetView(
                UUID.randomUUID(), UUID.randomUUID(), "PRO-3", "Golden Hour",
                "M1A", "Dry Mead", "data:image/png;base64,iVBORw0KGgo=", meadDetails,
                app.meads.judging.Medal.GOLD, 1,
                java.util.List.of(sheet));

        var dialog = new EntrantScoresheetDialog(view);
        dialog.open();

        var allText = _find(com.vaadin.flow.component.Component.class).stream()
                .map(c -> c.getElement().getText())
                .filter(t -> t != null && !t.isBlank())
                .toList();

        // Header title shows the entrant's own prefixed NUMBER, not the anonymized code.
        var title = _get(com.vaadin.flow.component.html.Span.class,
                spec -> spec.withId("entrant-scoresheet-title"));
        assertThat(title.getText()).contains("PRO-3").contains("Golden Hour");

        // Competition logo is shown in the dialog header (top-left, by the title).
        _get(com.vaadin.flow.component.html.Image.class,
                spec -> spec.withId("entrant-scoresheet-logo"));

        // Full mead details are shown below the mead name.
        _get(com.vaadin.flow.component.orderedlayout.VerticalLayout.class,
                spec -> spec.withId("entrant-scoresheet-mead-details"));
        assertThat(allText).anyMatch(t -> t.contains("Wildflower honey"));
        assertThat(allText).anyMatch(t -> t.contains("French oak 6 months"));

        // Per-criterion comment is shown.
        assertThat(allText).anyMatch(t -> t.contains("Lovely floral nose"));

        // Advance-to-medal info is shown (outside the comments card).
        _get(com.vaadin.flow.component.html.Span.class,
                spec -> spec.withId("entrant-scoresheet-advanced"));

        // Outcome banner: medal won + Best of Show placement.
        _get(com.vaadin.flow.component.orderedlayout.HorizontalLayout.class,
                spec -> spec.withId("entrant-scoresheet-outcome"));
        var medal = _get(com.vaadin.flow.component.html.Span.class,
                spec -> spec.withId("entrant-scoresheet-medal"));
        assertThat(medal.getText()).contains("Gold");
        var bos = _get(com.vaadin.flow.component.html.Span.class,
                spec -> spec.withId("entrant-scoresheet-bos"));
        assertThat(bos.getText()).contains("Best of Show").contains("1");

        // Total is rendered prominently (its own element) and shows the value.
        var total = _get(com.vaadin.flow.component.html.Span.class,
                spec -> spec.withId("entrant-scoresheet-total-1"));
        assertThat(total.getText()).contains("88");
        assertThat(total.getStyle().get("font-weight")).isEqualTo("700");

        // No judge identity / "Judge N" labelling anywhere in the dialog.
        assertThat(allText).noneMatch(t -> t.contains("Judge"));
    }

    @Test
    void shouldNotRenderOutcomeBannerWhenNoMedalOrBos() {
        var field = new app.meads.awards.AnonymizedScoresheetView.FieldScore(
                "Aroma", 18, 24, "Pleasant nose");
        var sheet = new app.meads.awards.AnonymizedScoresheetView.AnonymizedScoresheet(
                1, "en", 75, false, java.util.List.of(field), "Decent.");
        var view = new app.meads.awards.AnonymizedScoresheetView(
                UUID.randomUUID(), UUID.randomUUID(), "PRO-4", "Quiet One",
                "M1A", "Dry Mead", null, null, null, null, java.util.List.of(sheet));

        var dialog = new EntrantScoresheetDialog(view);
        dialog.open();

        assertThat(_find(com.vaadin.flow.component.orderedlayout.HorizontalLayout.class).stream()
                .anyMatch(h -> "entrant-scoresheet-outcome".equals(h.getId().orElse(""))))
                .isFalse();
    }

    @Test
    @WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
    void shouldRenderSearchFieldAboveResultsGrid() {
        for (int i = 0; i < 5; i++) {
            division.advanceStatus();
        }
        division = divisionRepository.save(division);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-results");

        // The redesigned grid is searchable.
        _get(com.vaadin.flow.component.textfield.TextField.class,
                spec -> spec.withId("my-results-search"));
    }
}
