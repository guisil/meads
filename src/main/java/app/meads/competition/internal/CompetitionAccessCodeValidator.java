package app.meads.competition.internal;

import app.meads.identity.AccessCodeValidator;
import app.meads.identity.UserService;
import org.springframework.stereotype.Component;

@Component
class CompetitionAccessCodeValidator implements AccessCodeValidator {

    private final EventParticipantRepository eventParticipantRepository;
    private final UserService userService;

    CompetitionAccessCodeValidator(EventParticipantRepository eventParticipantRepository,
                                   UserService userService) {
        this.eventParticipantRepository = eventParticipantRepository;
        this.userService = userService;
    }

    @Override
    public boolean validate(String email, String code) {
        return eventParticipantRepository.findByAccessCode(code.toUpperCase())
                .map(ep -> {
                    var user = userService.findById(ep.getUserId());
                    return user.getEmail().equalsIgnoreCase(email);
                })
                .orElse(false);
    }
}
