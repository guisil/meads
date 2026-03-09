package app.meads.competition.internal;

import app.meads.identity.AccessCodeValidator;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class CompetitionAccessCodeValidator implements AccessCodeValidator {

    private final ParticipantRepository participantRepository;
    private final UserService userService;

    CompetitionAccessCodeValidator(ParticipantRepository participantRepository,
                                   UserService userService) {
        this.participantRepository = participantRepository;
        this.userService = userService;
    }

    @Override
    public boolean validate(String email, String code) {
        var result = participantRepository.findByAccessCode(code.toUpperCase())
                .map(participant -> {
                    var user = userService.findById(participant.getUserId());
                    return user.getEmail().equalsIgnoreCase(email);
                })
                .orElse(false);
        log.debug("Access code validation for {}: {}", email, result ? "valid" : "invalid");
        return result;
    }
}
