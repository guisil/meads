package app.meads.competition.internal;

import app.meads.competition.CompetitionParticipant;
import app.meads.competition.CompetitionRole;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CompetitionAccessCodeValidatorTest {

    @InjectMocks
    CompetitionAccessCodeValidator validator;

    @Mock
    CompetitionParticipantRepository participantRepository;

    @Mock
    UserService userService;

    @Test
    void shouldValidateCorrectAccessCode() {
        var userId = UUID.randomUUID();
        var participant = new CompetitionParticipant(UUID.randomUUID(),
                UUID.randomUUID(), userId, CompetitionRole.JUDGE);
        participant.assignAccessCode("AB3K9XYZ");
        var user = new User(userId, "judge@test.com", "Judge",
                UserStatus.ACTIVE, Role.USER);
        given(participantRepository.findByAccessCode("AB3K9XYZ"))
                .willReturn(List.of(participant));
        given(userService.findById(userId)).willReturn(user);

        assertThat(validator.validate("judge@test.com", "AB3K9XYZ")).isTrue();
    }

    @Test
    void shouldRejectWhenCodeDoesNotExist() {
        given(participantRepository.findByAccessCode("NOTEXIST"))
                .willReturn(List.of());

        assertThat(validator.validate("judge@test.com", "NOTEXIST")).isFalse();
    }

    @Test
    void shouldRejectWhenEmailDoesNotMatch() {
        var userId = UUID.randomUUID();
        var participant = new CompetitionParticipant(UUID.randomUUID(),
                UUID.randomUUID(), userId, CompetitionRole.JUDGE);
        participant.assignAccessCode("AB3K9XYZ");
        var user = new User(userId, "other@test.com", "Other",
                UserStatus.ACTIVE, Role.USER);
        given(participantRepository.findByAccessCode("AB3K9XYZ"))
                .willReturn(List.of(participant));
        given(userService.findById(userId)).willReturn(user);

        assertThat(validator.validate("judge@test.com", "AB3K9XYZ")).isFalse();
    }

    @Test
    void shouldNormalizeCodeToUppercase() {
        var userId = UUID.randomUUID();
        var participant = new CompetitionParticipant(UUID.randomUUID(),
                UUID.randomUUID(), userId, CompetitionRole.JUDGE);
        participant.assignAccessCode("AB3K9XYZ");
        var user = new User(userId, "judge@test.com", "Judge",
                UserStatus.ACTIVE, Role.USER);
        given(participantRepository.findByAccessCode("AB3K9XYZ"))
                .willReturn(List.of(participant));
        given(userService.findById(userId)).willReturn(user);

        assertThat(validator.validate("judge@test.com", "ab3k9xyz")).isTrue();
    }
}
