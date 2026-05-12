package app.meads.awards.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionRepository;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class AwardsPublicResultsViewTest {

    @Autowired ApplicationContext ctx;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;

    private Competition competition;
    private Division division;

    @BeforeEach
    void setup() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        competition = competitionRepository.save(new Competition(
                "Public Results Test", "pubres-" + suffix,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Test"));
        var d = new Division(competition.getId(), "Amateur", "pub-div-" + suffix,
                ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division = divisionRepository.save(d);

        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldForwardToRootWhenStatusNotPublished() {
        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/results");

        // Status is DRAFT — view should forward to root, so no awards heading
        var headings = _find(H2.class);
        assertThat(headings.stream().anyMatch(h -> h.getId().orElse("").equals("awards-public-heading")))
                .isFalse();
    }

    @Test
    void shouldRenderHeadingWhenStatusPublished() {
        // Advance to RESULTS_PUBLISHED (5 advances from DRAFT)
        for (int i = 0; i < 5; i++) {
            division.advanceStatus();
        }
        division = divisionRepository.save(division);

        UI.getCurrent().navigate("competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/results");

        var heading = _get(H2.class, spec -> spec.withId("awards-public-heading"));
        assertThat(heading.getText()).contains("Public Results Test");
        assertThat(heading.getText()).contains("Amateur");
    }
}
