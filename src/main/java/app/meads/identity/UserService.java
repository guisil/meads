package app.meads.identity;

import app.meads.identity.internal.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void deleteUser(UUID userId, String currentUserEmail) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getEmail().equals(currentUserEmail)) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            userRepository.delete(user);
        } else {
            user.updateDetails(user.getName(), user.getRole(), UserStatus.DISABLED);
            userRepository.save(user);
        }
    }
}
