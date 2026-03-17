package app.meads.entry.internal;

import app.meads.competition.ParticipantRemovalCleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
class EntryParticipantRemovalCleanup implements ParticipantRemovalCleanup {

    private final EntryRepository entryRepository;
    private final EntryCreditRepository entryCreditRepository;

    EntryParticipantRemovalCleanup(EntryRepository entryRepository,
                                    EntryCreditRepository entryCreditRepository) {
        this.entryRepository = entryRepository;
        this.entryCreditRepository = entryCreditRepository;
    }

    @Override
    public void cleanupForParticipant(UUID competitionId, UUID userId) {
        var entries = entryRepository.findByUserIdAndCompetitionId(userId, competitionId);
        var credits = entryCreditRepository.findByUserIdAndCompetitionId(userId, competitionId);
        entryRepository.deleteAll(entries);
        entryCreditRepository.deleteAll(credits);
        if (!entries.isEmpty() || !credits.isEmpty()) {
            log.info("Cleaned up participant data: userId={}, competitionId={}, entries={}, credits={}",
                    userId, competitionId, entries.size(), credits.size());
        }
    }
}
