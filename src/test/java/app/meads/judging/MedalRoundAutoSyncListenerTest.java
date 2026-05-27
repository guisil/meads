package app.meads.judging;

import app.meads.entry.Entry;
import app.meads.entry.EntryReceivedEvent;
import app.meads.entry.EntryService;
import app.meads.judging.internal.MedalRoundAutoSyncListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MedalRoundAutoSyncListenerTest {

    @Mock EntryService entryService;
    @Mock JudgingService judgingService;
    @InjectMocks MedalRoundAutoSyncListener listener;

    @Test
    void shouldSyncScoreBasedMedalRoundWhenEntryReceivedAndCategoryHasOne() {
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var roundId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();

        var entry = mock(Entry.class);
        given(entry.getFinalCategoryId()).willReturn(categoryId);
        given(entryService.findById(entryId)).willReturn(Optional.of(entry));

        var medalRound = mock(JudgingRound.class);
        given(medalRound.getId()).willReturn(roundId);
        given(medalRound.getMedalMode()).willReturn(MedalRoundMode.SCORE_BASED);
        given(medalRound.getStatus()).willReturn(JudgingRoundStatus.ACTIVE);
        given(judgingService.findMedalRoundByCategoryId(categoryId))
                .willReturn(Optional.of(medalRound));

        listener.on(new EntryReceivedEvent(entryId, divisionId, adminUserId));

        then(judgingService).should()
                .syncScoreBasedMedalRoundEntries(roundId, adminUserId);
    }

    @Test
    void shouldNotSyncWhenMedalRoundIsComparative() {
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();

        var entry = mock(Entry.class);
        given(entry.getFinalCategoryId()).willReturn(categoryId);
        given(entryService.findById(entryId)).willReturn(Optional.of(entry));

        var medalRound = mock(JudgingRound.class);
        given(medalRound.getMedalMode()).willReturn(MedalRoundMode.COMPARATIVE);
        given(judgingService.findMedalRoundByCategoryId(categoryId))
                .willReturn(Optional.of(medalRound));

        listener.on(new EntryReceivedEvent(entryId, divisionId, adminUserId));

        then(judgingService).should(never())
                .syncScoreBasedMedalRoundEntries(any(), any());
    }

    @Test
    void shouldNotSyncWhenMedalRoundIsComplete() {
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();

        var entry = mock(Entry.class);
        given(entry.getFinalCategoryId()).willReturn(categoryId);
        given(entryService.findById(entryId)).willReturn(Optional.of(entry));

        var medalRound = mock(JudgingRound.class);
        given(medalRound.getMedalMode()).willReturn(MedalRoundMode.SCORE_BASED);
        given(medalRound.getStatus()).willReturn(JudgingRoundStatus.COMPLETE);
        given(judgingService.findMedalRoundByCategoryId(categoryId))
                .willReturn(Optional.of(medalRound));

        listener.on(new EntryReceivedEvent(entryId, divisionId, adminUserId));

        then(judgingService).should(never())
                .syncScoreBasedMedalRoundEntries(any(), any());
    }

    @Test
    void shouldNotSyncWhenNoMedalRoundForCategory() {
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();

        var entry = mock(Entry.class);
        given(entry.getFinalCategoryId()).willReturn(categoryId);
        given(entryService.findById(entryId)).willReturn(Optional.of(entry));
        given(judgingService.findMedalRoundByCategoryId(categoryId))
                .willReturn(Optional.empty());

        listener.on(new EntryReceivedEvent(entryId, divisionId, adminUserId));

        then(judgingService).should(never())
                .syncScoreBasedMedalRoundEntries(any(), any());
    }

    @Test
    void shouldSyncEvenWhenEntryNoLongerReceivedSoZombiesGetCleaned() {
        // Sync runs regardless of the entry's current status; the method
        // itself decides what to add (RECEIVED) vs remove (zombies). This
        // event also fires on withdraw/revert so the listener path is the
        // cleanup trigger.
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var roundId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();

        var entry = mock(Entry.class);
        given(entry.getFinalCategoryId()).willReturn(categoryId);
        given(entryService.findById(entryId)).willReturn(Optional.of(entry));

        var medalRound = mock(JudgingRound.class);
        given(medalRound.getId()).willReturn(roundId);
        given(medalRound.getMedalMode()).willReturn(MedalRoundMode.SCORE_BASED);
        given(medalRound.getStatus()).willReturn(JudgingRoundStatus.ACTIVE);
        given(judgingService.findMedalRoundByCategoryId(categoryId))
                .willReturn(Optional.of(medalRound));

        listener.on(new EntryReceivedEvent(entryId, divisionId, adminUserId));

        then(judgingService).should()
                .syncScoreBasedMedalRoundEntries(roundId, adminUserId);
    }

    @Test
    void shouldNotSyncWhenEntryHasNoFinalCategory() {
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();

        var entry = mock(Entry.class);
        given(entry.getFinalCategoryId()).willReturn(null);
        given(entryService.findById(entryId)).willReturn(Optional.of(entry));

        listener.on(new EntryReceivedEvent(entryId, divisionId, adminUserId));

        then(judgingService).should(never())
                .syncScoreBasedMedalRoundEntries(any(), any());
    }
}
