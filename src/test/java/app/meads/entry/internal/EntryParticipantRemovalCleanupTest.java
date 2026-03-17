package app.meads.entry.internal;

import app.meads.entry.Entry;
import app.meads.entry.EntryCredit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntryParticipantRemovalCleanupTest {

    @Mock
    private EntryRepository entryRepository;

    @Mock
    private EntryCreditRepository entryCreditRepository;

    @InjectMocks
    private EntryParticipantRemovalCleanup cleanup;

    @Test
    void shouldDeleteEntriesAndCreditsForUserInCompetition() {
        var competitionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        var entries = List.of(mock(Entry.class), mock(Entry.class));
        var credits = List.of(mock(EntryCredit.class));
        given(entryRepository.findByUserIdAndCompetitionId(userId, competitionId))
                .willReturn(entries);
        given(entryCreditRepository.findByUserIdAndCompetitionId(userId, competitionId))
                .willReturn(credits);

        cleanup.cleanupForParticipant(competitionId, userId);

        verify(entryRepository).deleteAll(entries);
        verify(entryCreditRepository).deleteAll(credits);
    }

    @Test
    void shouldHandleParticipantWithNoEntriesOrCredits() {
        var competitionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        given(entryRepository.findByUserIdAndCompetitionId(userId, competitionId))
                .willReturn(List.of());
        given(entryCreditRepository.findByUserIdAndCompetitionId(userId, competitionId))
                .willReturn(List.of());

        cleanup.cleanupForParticipant(competitionId, userId);

        verify(entryRepository).deleteAll(List.of());
        verify(entryCreditRepository).deleteAll(List.of());
    }
}
