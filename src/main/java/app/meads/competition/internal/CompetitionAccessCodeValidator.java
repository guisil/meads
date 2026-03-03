package app.meads.competition.internal;

import app.meads.identity.AccessCodeValidator;
import app.meads.identity.UserService;
import org.springframework.stereotype.Component;

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
        return participantRepository.findByAccessCode(code.toUpperCase())
                .map(participant -> {
                    var user = userService.findById(participant.getUserId());
                    return user.getEmail().equalsIgnoreCase(email);
                })
                .orElse(false);
    }
}
