package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import app.meads.judging.Certification;
import app.meads.judging.JudgeProfile;
import app.meads.judging.JudgeProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@Validated
public class JudgeProfileServiceImpl implements JudgeProfileService {

    private final JudgeProfileRepository judgeProfileRepository;
    private final JudgingTableRepository judgingTableRepository;
    private final UserService userService;

    JudgeProfileServiceImpl(JudgeProfileRepository judgeProfileRepository,
                            JudgingTableRepository judgingTableRepository,
                            UserService userService) {
        this.judgeProfileRepository = judgeProfileRepository;
        this.judgingTableRepository = judgingTableRepository;
        this.userService = userService;
    }

    @Override
    public JudgeProfile ensureProfileForJudge(UUID userId) {
        return judgeProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    var profile = new JudgeProfile(userId);
                    var saved = judgeProfileRepository.save(profile);
                    log.info("Created JudgeProfile for user {}", userId);
                    return saved;
                });
    }

    @Override
    public JudgeProfile createOrUpdate(UUID userId,
                                       Set<Certification> certifications,
                                       String qualificationDetails,
                                       UUID requestingUserId) {
        requireSelfOrSystemAdmin(userId, requestingUserId);
        var profile = judgeProfileRepository.findByUserId(userId)
                .orElseGet(() -> new JudgeProfile(userId));
        profile.updateCertifications(certifications);
        profile.updateQualificationDetails(qualificationDetails);
        log.info("Updated JudgeProfile for user {} (certifications={})", userId, certifications);
        return judgeProfileRepository.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JudgeProfile> findByUserId(UUID userId) {
        return judgeProfileRepository.findByUserId(userId);
    }

    @Override
    public void updatePreferredCommentLanguage(UUID userId, String languageCode) {
        var profile = judgeProfileRepository.findByUserId(userId)
                .orElseGet(() -> new JudgeProfile(userId));
        profile.updatePreferredCommentLanguage(languageCode);
        judgeProfileRepository.save(profile);
        log.debug("Updated preferred comment language for user {} → {}", userId, languageCode);
    }

    @Override
    public void delete(UUID userId, UUID adminUserId) {
        requireSystemAdmin(adminUserId);
        if (judgingTableRepository.existsAssignmentByJudgeUserId(userId)) {
            throw new BusinessRuleException("error.judge-profile.has-assignments");
        }
        judgeProfileRepository.findByUserId(userId).ifPresent(profile -> {
            judgeProfileRepository.delete(profile);
            log.info("Deleted JudgeProfile for user {}", userId);
        });
    }

    private void requireSelfOrSystemAdmin(UUID targetUserId, UUID requestingUserId) {
        if (targetUserId.equals(requestingUserId)) {
            return;
        }
        requireSystemAdmin(requestingUserId);
    }

    private void requireSystemAdmin(UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
    }
}
