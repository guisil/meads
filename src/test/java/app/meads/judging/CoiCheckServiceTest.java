package app.meads.judging;

import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import app.meads.judging.internal.CoiCheckServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CoiCheckServiceTest {

    @InjectMocks
    CoiCheckServiceImpl service;

    @Mock
    UserService userService;

    @Mock
    EntryService entryService;

    UUID judgeUserId;
    UUID entrantUserId;
    UUID entryId;
    User judge;
    User entrant;
    Entry entry;

    @BeforeEach
    void setUp() {
        judgeUserId = UUID.randomUUID();
        entrantUserId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        judge = new User("judge@test.com", "Judge", UserStatus.ACTIVE, Role.USER);
        entrant = new User("entrant@test.com", "Entrant", UserStatus.ACTIVE, Role.USER);
        entry = org.mockito.Mockito.mock(Entry.class);
    }

    @Test
    void shouldReturnHardBlockWhenJudgeIsTheEntrant() {
        given(entry.getUserId()).willReturn(judgeUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isTrue();
        assertThat(result.softWarningKey()).isEmpty();
    }

    @Test
    void shouldReturnSoftWarningWhenMeaderyNamesAreSimilar() {
        given(entry.getUserId()).willReturn(entrantUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        judge.updateMeaderyName("Acme Meadery LLC");
        judge.updateCountry("US");
        entrant.updateMeaderyName("Acme Meads Co.");
        entrant.updateCountry("US");
        given(userService.findById(judgeUserId)).willReturn(judge);
        given(userService.findById(entrantUserId)).willReturn(entrant);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.softWarningKey()).contains("coi.warning.similar-meadery");
    }

    @Test
    void shouldReturnNoWarningWhenMeaderyNamesAreUnrelated() {
        given(entry.getUserId()).willReturn(entrantUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        judge.updateMeaderyName("Honey Hill Meadery");
        judge.updateCountry("US");
        entrant.updateMeaderyName("Bear Mountain Mead");
        entrant.updateCountry("US");
        given(userService.findById(judgeUserId)).willReturn(judge);
        given(userService.findById(entrantUserId)).willReturn(entrant);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.softWarningKey()).isEmpty();
    }

    @Test
    void shouldReturnNoWarningWhenJudgeMeaderyIsBlank() {
        given(entry.getUserId()).willReturn(entrantUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        entrant.updateMeaderyName("Honey Hill Meadery");
        entrant.updateCountry("US");
        given(userService.findById(judgeUserId)).willReturn(judge);
        given(userService.findById(entrantUserId)).willReturn(entrant);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.softWarningKey()).isEmpty();
    }
}
