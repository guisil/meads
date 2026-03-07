package app.meads.identity;

import app.meads.identity.internal.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Validated
public class UserService {

    private final UserRepository userRepository;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, JwtMagicLinkService jwtMagicLinkService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(@Email @NotBlank String email, @NotBlank String name, @NotNull UserStatus status, @NotNull Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        User user = new User(email, name, status, role);
        return userRepository.save(user);
    }

    public User updateUser(UUID userId, @NotBlank String name, Role role, UserStatus status, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getEmail().equals(currentUserEmail)) {
            if (!role.equals(user.getRole())) {
                throw new IllegalArgumentException("Cannot change your own role");
            }
            if (!status.equals(user.getStatus())) {
                throw new IllegalArgumentException("Cannot change your own status");
            }
        }
        user.updateDetails(name, role, status);
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public List<User> findAllByIds(Collection<UUID> ids) {
        return userRepository.findAllById(ids);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public User findOrCreateByEmail(@Email @NotBlank String email) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    var user = new User(email, email, UserStatus.PENDING, Role.USER);
                    return userRepository.save(user);
                });
    }

    public User updateMeaderyName(@NotNull UUID userId, String meaderyName) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.updateMeaderyName(meaderyName);
        return userRepository.save(user);
    }

    public boolean isEditingSelf(UUID userId, String currentUserEmail) {
        return userRepository.findById(userId)
                .map(user -> user.getEmail().equals(currentUserEmail))
                .orElse(false);
    }

    public void setPassword(UUID userId, String rawPassword) {
        validatePassword(rawPassword);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    public void setPasswordByToken(String token, String rawPassword) {
        String email = jwtMagicLinkService.extractEmail(token);
        validatePassword(rawPassword);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }

    public void removeUser(UUID userId, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getEmail().equals(currentUserEmail)) {
            throw new IllegalArgumentException("Cannot deactivate or delete your own account");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            userRepository.delete(user);
        } else {
            user.deactivate();
            userRepository.save(user);
        }
    }
}
