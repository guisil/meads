package app.meads.identity;

import app.meads.identity.internal.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Validated
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(@Email @NotBlank String email, @NotBlank String name, UserStatus status, Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        User user = new User(UUID.randomUUID(), email, name, status, role);
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

    public boolean isEditingSelf(UUID userId, String currentUserEmail) {
        return userRepository.findById(userId)
                .map(user -> user.getEmail().equals(currentUserEmail))
                .orElse(false);
    }

    public void deleteUser(UUID userId, String currentUserEmail) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getEmail().equals(currentUserEmail)) {
            throw new IllegalArgumentException("Cannot disable or delete your own account");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            userRepository.delete(user);
        } else {
            user.updateDetails(user.getName(), user.getRole(), UserStatus.DISABLED);
            userRepository.save(user);
        }
    }
}
