package app.meads.identity.internal;

import app.meads.identity.EmailService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import org.springframework.core.annotation.Order;

@Slf4j
@Component
@Profile("dev")
class DevUserInitializer {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    DevUserInitializer(UserRepository userRepository, EmailService emailService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    void initializeDevUsers() {
        createDevUserIfAbsent("admin@example.com", "Dev Admin", Role.SYSTEM_ADMIN, UserStatus.ACTIVE, "admin");
        createDevUserIfAbsent("compadmin@example.com", "Competition Admin", Role.USER, UserStatus.ACTIVE, "compadmin");
        createDevUserIfAbsent("user@example.com", "Dev User", Role.USER, UserStatus.ACTIVE, null);
        createDevUserIfAbsent("pending@example.com", "Pending User", Role.USER, UserStatus.PENDING, null);
        createDevUserIfAbsent("judge@example.com", "Dev Judge", Role.USER, UserStatus.ACTIVE, null);
        createDevUserIfAbsent("steward@example.com", "Dev Steward", Role.USER, UserStatus.ACTIVE, null);
        createDevUserIfAbsent("entrant@example.com", "Dev Entrant", Role.USER, UserStatus.ACTIVE, null);
    }

    private void createDevUserIfAbsent(String email, String name, Role role, UserStatus status, String password) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = new User(email, name, status, role);
        if (password != null) {
            user.assignPasswordHash(passwordEncoder.encode(password));
            log.info("Created dev user {} with password", email);
        } else {
            emailService.sendMagicLink(email, java.util.Locale.ENGLISH);
            log.info("Sent magic link email to dev user {}", email);
        }
        userRepository.save(user);
    }

}
