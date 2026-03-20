package app.meads.competition.internal;

import app.meads.BusinessRuleException;
import app.meads.identity.UserDeletionGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
class CompetitionUserDeletionGuard implements UserDeletionGuard {

    private final ParticipantRepository participantRepository;

    CompetitionUserDeletionGuard(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public void checkDeletionAllowed(UUID userId) {
        if (participantRepository.existsByUserId(userId)) {
            log.warn("Blocked user deletion: user {} has participant records", userId);
            throw new BusinessRuleException("error.user.cannot-delete-has-data");
        }
    }
}
