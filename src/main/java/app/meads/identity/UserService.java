package app.meads.identity;

import app.meads.BusinessRuleException;
import app.meads.identity.internal.TotpService;
import app.meads.identity.internal.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@Validated
public class UserService {

    private static final Set<String> VALID_COUNTRY_CODES = Set.of(Locale.getISOCountries());

    private final UserRepository userRepository;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final PasswordEncoder passwordEncoder;
    private final List<UserDeletionGuard> deletionGuards;
    private final TotpService totpService;

    public UserService(UserRepository userRepository, JwtMagicLinkService jwtMagicLinkService,
                       PasswordEncoder passwordEncoder, List<UserDeletionGuard> deletionGuards,
                       TotpService totpService) {
        this.userRepository = userRepository;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.passwordEncoder = passwordEncoder;
        this.deletionGuards = deletionGuards;
        this.totpService = totpService;
    }

    public User createUser(@Email @NotBlank String email, @NotBlank String name, @NotNull UserStatus status, @NotNull Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("error.user.email-exists");
        }
        User user = new User(email, name, status, role);
        var saved = userRepository.save(user);
        log.info("Created user: {} (email={}, role={}, status={})", saved.getId(), email, role, status);
        return saved;
    }

    public User updateUser(UUID userId, @NotBlank String name, Role role, UserStatus status, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        if (user.getEmail().equals(currentUserEmail)) {
            if (!role.equals(user.getRole())) {
                throw new BusinessRuleException("error.user.cannot-change-own-role");
            }
            if (!status.equals(user.getStatus())) {
                throw new BusinessRuleException("error.user.cannot-change-own-status");
            }
        }
        user.updateDetails(name, role, status);
        log.info("Updated user: {} (name={}, role={}, status={})", userId, name, role, status);
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll(Sort.by("name"));
    }

    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
    }

    public List<User> findAllByIds(Collection<UUID> ids) {
        return userRepository.findAllById(ids);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
    }

    public User findOrCreateByEmail(@Email @NotBlank String email) {
        return findOrCreateByEmail(email, email);
    }

    public User findOrCreateByEmail(@Email @NotBlank String email, @NotBlank String name) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    var user = new User(email, name, UserStatus.PENDING, Role.USER);
                    log.info("Auto-created user for email: {}", email);
                    return userRepository.save(user);
                });
    }

    public User updateProfile(@NotNull UUID userId, @NotBlank String name,
                               String meaderyName, String country, String preferredLanguage) {
        if (country != null && !VALID_COUNTRY_CODES.contains(country)) {
            throw new BusinessRuleException("error.user.invalid-country", country);
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        user.updateDetails(name, user.getRole(), user.getStatus());
        user.updateMeaderyName(meaderyName);
        user.updateCountry(country);
        user.updatePreferredLanguage(preferredLanguage);
        log.info("Profile updated for user {} ({})", user.getEmail(), userId);
        return userRepository.save(user);
    }

    public User updateMeaderyName(@NotNull UUID userId, String meaderyName) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        user.updateMeaderyName(meaderyName);
        return userRepository.save(user);
    }

    public boolean hasPassword(@NotNull UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getPasswordHash() != null)
                .orElse(false);
    }

    public boolean isEditingSelf(UUID userId, String currentUserEmail) {
        return userRepository.findById(userId)
                .map(user -> user.getEmail().equals(currentUserEmail))
                .orElse(false);
    }

    public void setPassword(UUID userId, String rawPassword) {
        validatePassword(rawPassword);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        user.assignPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        log.info("Password set for user: {} ({})", userId, user.getEmail());
    }

    public void setPasswordByToken(String token, String rawPassword) {
        String email = jwtMagicLinkService.extractEmail(token);
        validatePassword(rawPassword);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        user.assignPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        log.info("Password set via token for user: {}", email);
    }

    public String setupMfa(@NotNull UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        var secret = totpService.generateSecret();
        user.storePendingMfaSecret(secret);
        userRepository.save(user);
        log.info("MFA setup initiated for user: {}", userId);
        return totpService.generateQrUri(secret, user.getEmail());
    }

    public void confirmMfa(@NotNull UUID userId, @NotBlank String code) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new BusinessRuleException("error.mfa.invalid-code");
        }
        user.enableMfa(user.getTotpSecret());
        userRepository.save(user);
        log.info("MFA confirmed for user: {}", userId);
    }

    public boolean verifyMfaCode(@NotNull UUID userId, String code) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        return totpService.verifyCode(user.getTotpSecret(), code);
    }

    public void disableMfa(@NotNull UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        user.disableMfa();
        userRepository.save(user);
        log.info("MFA disabled for user: {}", userId);
    }

    public String completeMfaReset(@NotBlank String token) {
        String email;
        try {
            email = jwtMagicLinkService.extractEmail(token);
        } catch (JwtException ex) {
            log.warn("Rejected MFA reset with invalid token: {}", ex.getMessage());
            throw new BusinessRuleException("error.mfa.reset.invalid-token");
        }
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Rejected MFA reset for unknown email: {}", email);
                    return new BusinessRuleException("error.mfa.reset.invalid-token");
                });
        user.disableMfa();
        userRepository.save(user);
        log.info("MFA disabled via reset token for user: {}", email);
        return email;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new BusinessRuleException("error.user.password-too-short");
        }
    }

    public void removeUser(UUID userId, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("error.user.not-found"));
        if (user.getEmail().equals(currentUserEmail)) {
            throw new BusinessRuleException("error.user.cannot-remove-self");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            deletionGuards.forEach(guard -> guard.checkDeletionAllowed(userId));
            userRepository.delete(user);
            log.info("Deleted inactive user: {} ({})", userId, user.getEmail());
        } else {
            user.deactivate();
            userRepository.save(user);
            log.info("Deactivated user: {} ({})", userId, user.getEmail());
        }
    }
}
