package app.meads.judging.internal;

import app.meads.EntryScoresheetChecker.ScoresheetState;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetStatus;
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
class EntryScoresheetCheckerImplTest {

    @Mock ScoresheetRepository scoresheetRepository;
    @InjectMocks EntryScoresheetCheckerImpl checker;

    private Scoresheet sheet(UUID entryId, ScoresheetStatus status) {
        var sheet = mock(Scoresheet.class);
        given(sheet.getId()).willReturn(UUID.randomUUID());
        given(sheet.getEntryId()).willReturn(entryId);
        given(sheet.getStatus()).willReturn(status);
        return sheet;
    }

    @Test
    void shouldMapEachEntryToItsScoresheetStateWhenScoresheetsExist() {
        var submittedEntry = UUID.randomUUID();
        var draftEntry = UUID.randomUUID();
        var unjudgedEntry = UUID.randomUUID();
        var submitted = sheet(submittedEntry, ScoresheetStatus.SUBMITTED);
        var draft = sheet(draftEntry, ScoresheetStatus.DRAFT);
        var entryIds = List.of(submittedEntry, draftEntry, unjudgedEntry);
        given(scoresheetRepository.findByEntryIdIn(entryIds)).willReturn(List.of(submitted, draft));

        var result = checker.findScoresheetStates(entryIds);

        assertThat(result).containsOnlyKeys(submittedEntry, draftEntry);
        assertThat(result.get(submittedEntry))
                .isEqualTo(new ScoresheetState(submitted.getId(), true));
        assertThat(result.get(draftEntry))
                .isEqualTo(new ScoresheetState(draft.getId(), false));
    }
}
