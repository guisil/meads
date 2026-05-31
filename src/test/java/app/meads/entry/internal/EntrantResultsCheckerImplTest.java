package app.meads.entry.internal;

import app.meads.competition.DivisionStatus;
import app.meads.entry.EntrantDivisionOverview;
import app.meads.entry.EntryService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EntrantResultsCheckerImplTest {

    @Mock EntryService entryService;
    @Mock UserService userService;
    @InjectMocks EntrantResultsCheckerImpl checker;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(userService.findByEmail("e@example.com")).willReturn(user);
    }

    private EntrantDivisionOverview overview(String compShort, String divShort, DivisionStatus status) {
        return new EntrantDivisionOverview(UUID.randomUUID(), "Comp", compShort,
                UUID.randomUUID(), "Div", divShort, 0, 0, status);
    }

    @Test
    void shouldReturnEmptyWhenNoDivisionHasPublishedResults() {
        given(entryService.findEntrantDivisionOverviews(userId)).willReturn(List.of(
                overview("chip-2026", "amadora", DivisionStatus.JUDGING)));

        assertThat(checker.resultsLandingPath("e@example.com")).isEmpty();
    }

    @Test
    void shouldReturnThatDivisionsResultsPathWhenExactlyOnePublished() {
        given(entryService.findEntrantDivisionOverviews(userId)).willReturn(List.of(
                overview("chip-2026", "amadora", DivisionStatus.JUDGING),
                overview("chip-2026", "profissional", DivisionStatus.RESULTS_PUBLISHED)));

        assertThat(checker.resultsLandingPath("e@example.com"))
                .contains("competitions/chip-2026/divisions/profissional/my-results");
    }

    @Test
    void shouldReturnTheHubWhenSeveralDivisionsPublished() {
        given(entryService.findEntrantDivisionOverviews(userId)).willReturn(List.of(
                overview("chip-2026", "amadora", DivisionStatus.RESULTS_PUBLISHED),
                overview("chip-2026", "profissional", DivisionStatus.RESULTS_PUBLISHED)));

        assertThat(checker.resultsLandingPath("e@example.com")).contains("my-entries");
    }
}
