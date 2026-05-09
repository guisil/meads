package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.internal.JudgeProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JudgeProfileRepositoryTest {

    @Autowired
    JudgeProfileRepository judgeProfileRepository;

    @Autowired
    UserRepository userRepository;

    private User createUser(String email) {
        return userRepository.save(new User(email, "Judge", UserStatus.ACTIVE, Role.USER));
    }

    @Test
    void shouldSaveAndFindEmptyProfileByUserId() {
        var user = createUser("judge1@test.com");

        var profile = new JudgeProfile(user.getId());
        judgeProfileRepository.save(profile);

        var found = judgeProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(found.getUserId()).isEqualTo(user.getId());
        assertThat(found.getCertifications()).isEmpty();
        assertThat(found.getQualificationDetails()).isNull();
        assertThat(found.getPreferredCommentLanguage()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNull();
    }

    @Test
    void shouldPersistCertificationsAndQualificationDetails() {
        var user = createUser("judge2@test.com");

        var profile = new JudgeProfile(user.getId());
        profile.updateCertifications(Set.of(Certification.MJP, Certification.BJCP));
        profile.updateQualificationDetails("MJP since 2024");
        judgeProfileRepository.save(profile);

        var found = judgeProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(found.getCertifications()).containsExactlyInAnyOrder(Certification.MJP, Certification.BJCP);
        assertThat(found.getQualificationDetails()).isEqualTo("MJP since 2024");
    }

    @Test
    void shouldPersistPreferredCommentLanguage() {
        var user = createUser("judge3@test.com");

        var profile = new JudgeProfile(user.getId());
        profile.updatePreferredCommentLanguage("pt");
        judgeProfileRepository.save(profile);

        var found = judgeProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(found.getPreferredCommentLanguage()).isEqualTo("pt");
    }

    @Test
    void shouldClearPreferredCommentLanguageWithNull() {
        var user = createUser("judge4@test.com");

        var profile = new JudgeProfile(user.getId());
        profile.updatePreferredCommentLanguage("pt");
        judgeProfileRepository.save(profile);

        var saved = judgeProfileRepository.findByUserId(user.getId()).orElseThrow();
        saved.updatePreferredCommentLanguage(null);
        judgeProfileRepository.save(saved);

        var found = judgeProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(found.getPreferredCommentLanguage()).isNull();
    }

    @Test
    void shouldReplaceCertificationsSet() {
        var user = createUser("judge5@test.com");

        var profile = new JudgeProfile(user.getId());
        profile.updateCertifications(Set.of(Certification.MJP, Certification.BJCP));
        judgeProfileRepository.save(profile);

        var saved = judgeProfileRepository.findByUserId(user.getId()).orElseThrow();
        saved.updateCertifications(Set.of(Certification.OTHER));
        judgeProfileRepository.save(saved);

        var found = judgeProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(found.getCertifications()).containsExactly(Certification.OTHER);
    }
}
