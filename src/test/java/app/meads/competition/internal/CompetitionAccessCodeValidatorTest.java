package app.meads.competition.internal;

import app.meads.competition.Participant;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CompetitionAccessCodeValidatorTest {

    @InjectMocks
    CompetitionAccessCodeValidator validator;

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    UserService userService;

    @Test
    void shouldValidateCorrectAccessCode() {
        var user = new User("judge@test.com", "Judge",
                UserStatus.ACTIVE, Role.USER);
        var participant = new Participant(UUID.randomUUID(), user.getId());
        participant.assignAccessCode("AB3K9XYZ");
        given(participantRepository.findByAccessCode("AB3K9XYZ"))
                .willReturn(Optional.of(participant));
        given(userService.findById(user.getId())).willReturn(user);

        assertThat(validator.validate("judge@test.com", "AB3K9XYZ")).isTrue();
    }

    @Test
    void shouldRejectWhenEmailDoesNotMatch() {
        var user = new User("other@test.com", "Other",
                UserStatus.ACTIVE, Role.USER);
        var participant = new Participant(UUID.randomUUID(), user.getId());
        participant.assignAccessCode("AB3K9XYZ");
        given(participantRepository.findByAccessCode("AB3K9XYZ"))
                .willReturn(Optional.of(participant));
        given(userService.findById(user.getId())).willReturn(user);

        assertThat(validator.validate("judge@test.com", "AB3K9XYZ")).isFalse();
    }

    @Test
    void shouldRejectWhenCodeDoesNotExist() {
        given(participantRepository.findByAccessCode("NOTEXIST"))
                .willReturn(Optional.empty());

        assertThat(validator.validate("judge@test.com", "NOTEXIST")).isFalse();
    }

    @Test
    void shouldNormalizeCodeToUppercase() {
        var user = new User("judge@test.com", "Judge",
                UserStatus.ACTIVE, Role.USER);
        var participant = new Participant(UUID.randomUUID(), user.getId());
        participant.assignAccessCode("AB3K9XYZ");
        given(participantRepository.findByAccessCode("AB3K9XYZ"))
                .willReturn(Optional.of(participant));
        given(userService.findById(user.getId())).willReturn(user);

        assertThat(validator.validate("judge@test.com", "ab3k9xyz")).isTrue();
    }
}
