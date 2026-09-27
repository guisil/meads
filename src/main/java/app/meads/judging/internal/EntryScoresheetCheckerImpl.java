package app.meads.judging.internal;

import app.meads.EntryScoresheetChecker;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class EntryScoresheetCheckerImpl implements EntryScoresheetChecker {

    private final ScoresheetRepository scoresheetRepository;

    EntryScoresheetCheckerImpl(ScoresheetRepository scoresheetRepository) {
        this.scoresheetRepository = scoresheetRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ScoresheetState> findScoresheetStates(Collection<UUID> entryIds) {
        return scoresheetRepository.findByEntryIdIn(entryIds).stream()
                .collect(Collectors.toMap(Scoresheet::getEntryId, sheet -> new ScoresheetState(
                        sheet.getId(), sheet.getStatus() == ScoresheetStatus.SUBMITTED)));
    }
}
