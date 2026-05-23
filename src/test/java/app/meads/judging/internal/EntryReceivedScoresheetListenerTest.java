package app.meads.judging.internal;

import app.meads.entry.EntryReceivedEvent;
import app.meads.judging.ScoresheetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class EntryReceivedScoresheetListenerTest {

    @Mock
    private ScoresheetService scoresheetService;

    @InjectMocks
    private EntryReceivedScoresheetListener listener;

    @Test
    void shouldEnsureScoresheetWhenEntryReceived() {
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();

        listener.on(new EntryReceivedEvent(entryId, divisionId));

        then(scoresheetService).should().ensureScoresheetForEntry(entryId);
    }
}
