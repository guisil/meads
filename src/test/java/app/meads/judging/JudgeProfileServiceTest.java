package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import app.meads.judging.internal.JudgeProfileRepository;
import app.meads.judging.internal.JudgeProfileServiceImpl;
import app.meads.judging.internal.JudgingTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class JudgeProfileServiceTest {

    @InjectMocks
    JudgeProfileServiceImpl service;

    @Mock
    JudgeProfileRepository judgeProfileRepository;

    @Mock
    JudgingTableRepository judgingTableRepository;

    @Mock
    UserService userService;

    UUID judgeUserId;
    UUID adminUserId;
    UUID otherUserId;
    User adminUser;
    User regularUser;
    User otherUser;

    @BeforeEach
    void setUp() {
        judgeUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        adminUser = new User("admin@test.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        regularUser = new User("judge@test.com", "Judge", UserStatus.ACTIVE, Role.USER);
        otherUser = new User("other@test.com", "Other", UserStatus.ACTIVE, Role.USER);
    }

    @Test
    void shouldCreateProfileWhenEnsureProfileForJudgeAndAbsent() {
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.empty());
        given(judgeProfileRepository.save(any(JudgeProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var profile = service.ensureProfileForJudge(judgeUserId);

        assertThat(profile).isNotNull();
        assertThat(profile.getUserId()).isEqualTo(judgeUserId);
        then(judgeProfileRepository).should().save(any(JudgeProfile.class));
    }

    @Test
    void shouldReturnExistingProfileWhenEnsureProfileForJudgeAndPresent() {
        var existing = new JudgeProfile(judgeUserId);
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.of(existing));

        var profile = service.ensureProfileForJudge(judgeUserId);

        assertThat(profile).isSameAs(existing);
        then(judgeProfileRepository).should(never()).save(any());
    }

    @Test
    void shouldAllowSystemAdminToCreateOrUpdateAnyProfile() {
        given(userService.findById(adminUserId)).willReturn(adminUser);
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.empty());
        given(judgeProfileRepository.save(any(JudgeProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var profile = service.createOrUpdate(judgeUserId,
                Set.of(Certification.MJP), "MJP since 2024", adminUserId);

        assertThat(profile.getCertifications()).containsExactly(Certification.MJP);
        assertThat(profile.getQualificationDetails()).isEqualTo("MJP since 2024");
    }

    @Test
    void shouldAllowSelfToCreateOrUpdateOwnProfile() {
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.empty());
        given(judgeProfileRepository.save(any(JudgeProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var profile = service.createOrUpdate(judgeUserId,
                Set.of(Certification.BJCP), "BJCP rank", judgeUserId);

        assertThat(profile.getCertifications()).containsExactly(Certification.BJCP);
    }

    @Test
    void shouldRejectCreateOrUpdateWhenNotSelfOrAdmin() {
        given(userService.findById(otherUserId)).willReturn(otherUser);

        assertThatThrownBy(() -> service.createOrUpdate(judgeUserId,
                Set.of(Certification.MJP), "details", otherUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(judgeProfileRepository).should(never()).save(any());
    }

    @Test
    void shouldUpdateExistingProfileOnCreateOrUpdate() {
        var existing = new JudgeProfile(judgeUserId);
        existing.updateCertifications(Set.of(Certification.MJP));
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.of(existing));
        given(judgeProfileRepository.save(any(JudgeProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var profile = service.createOrUpdate(judgeUserId,
                Set.of(Certification.BJCP, Certification.OTHER), "WSET 3", judgeUserId);

        assertThat(profile.getCertifications())
                .containsExactlyInAnyOrder(Certification.BJCP, Certification.OTHER);
        assertThat(profile.getQualificationDetails()).isEqualTo("WSET 3");
    }

    @Test
    void shouldFindByUserIdReturningOptional() {
        var existing = new JudgeProfile(judgeUserId);
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.of(existing));

        var found = service.findByUserId(judgeUserId);

        assertThat(found).contains(existing);
    }

    @Test
    void shouldReturnEmptyWhenFindByUserIdAndAbsent() {
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.empty());

        var found = service.findByUserId(judgeUserId);

        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdatePreferredCommentLanguageOnExistingProfile() {
        var existing = new JudgeProfile(judgeUserId);
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.of(existing));
        given(judgeProfileRepository.save(any(JudgeProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.updatePreferredCommentLanguage(judgeUserId, "pt");

        assertThat(existing.getPreferredCommentLanguage()).isEqualTo("pt");
        then(judgeProfileRepository).should().save(existing);
    }

    @Test
    void shouldCreateProfileWhenUpdatePreferredCommentLanguageAndAbsent() {
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.empty());
        given(judgeProfileRepository.save(any(JudgeProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.updatePreferredCommentLanguage(judgeUserId, "es");

        then(judgeProfileRepository).should().save(any(JudgeProfile.class));
    }

    @Test
    void shouldDeleteProfileWhenSystemAdminAndNoAssignments() {
        var existing = new JudgeProfile(judgeUserId);
        given(userService.findById(adminUserId)).willReturn(adminUser);
        given(judgingTableRepository.existsAssignmentByJudgeUserId(judgeUserId)).willReturn(false);
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.of(existing));

        service.delete(judgeUserId, adminUserId);

        then(judgeProfileRepository).should().delete(existing);
    }

    @Test
    void shouldRejectDeleteWhenNotSystemAdmin() {
        given(userService.findById(judgeUserId)).willReturn(regularUser);

        assertThatThrownBy(() -> service.delete(judgeUserId, judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(judgeProfileRepository).should(never()).delete(any(JudgeProfile.class));
    }

    @Test
    void shouldRejectDeleteWhenJudgeAssignmentReferencesUser() {
        given(userService.findById(adminUserId)).willReturn(adminUser);
        given(judgingTableRepository.existsAssignmentByJudgeUserId(judgeUserId)).willReturn(true);

        assertThatThrownBy(() -> service.delete(judgeUserId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judge-profile.has-assignments");

        then(judgeProfileRepository).should(never()).delete(any(JudgeProfile.class));
    }

    @Test
    void shouldNoOpDeleteWhenProfileAbsent() {
        given(userService.findById(adminUserId)).willReturn(adminUser);
        given(judgingTableRepository.existsAssignmentByJudgeUserId(judgeUserId)).willReturn(false);
        given(judgeProfileRepository.findByUserId(judgeUserId)).willReturn(Optional.empty());

        service.delete(judgeUserId, adminUserId);

        then(judgeProfileRepository).should(never()).delete(any(JudgeProfile.class));
    }
}
