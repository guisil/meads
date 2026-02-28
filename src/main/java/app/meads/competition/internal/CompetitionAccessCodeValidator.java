package app.meads.competition.internal;

import app.meads.identity.AccessCodeValidator;
import app.meads.identity.UserService;
import org.springframework.stereotype.Component;

@Component
class CompetitionAccessCodeValidator implements AccessCodeValidator {

    private final CompetitionParticipantRepository participantRepository;
    private final UserService userService;

    CompetitionAccessCodeValidator(CompetitionParticipantRepository participantRepository,
                                   UserService userService) {
        this.participantRepository = participantRepository;
        this.userService = userService;
    }

    @Override
    public boolean validate(String email, String code) {
        var matches = participantRepository.findByAccessCode(code.toUpperCase());
        return matches.stream()
                .filter(p -> p.getRole().requiresAccessCode())
                .anyMatch(p -> {
                    var user = userService.findById(p.getUserId());
                    return user.getEmail().equalsIgnoreCase(email);
                });
    }
}
