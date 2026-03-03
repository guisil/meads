package app.meads.identity.internal;

import app.meads.identity.JwtMagicLinkService;
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

import java.time.Duration;

@Slf4j
@Component
@Profile("dev")
class DevUserInitializer {

    private final UserRepository userRepository;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final PasswordEncoder passwordEncoder;

    DevUserInitializer(UserRepository userRepository, JwtMagicLinkService jwtMagicLinkService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.passwordEncoder = passwordEncoder;
    }

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    void initializeDevUsers() {
        createDevUserIfAbsent("admin@localhost", "Dev Admin", Role.SYSTEM_ADMIN, UserStatus.ACTIVE, "admin");
        createDevUserIfAbsent("user@localhost", "Dev User", Role.USER, UserStatus.ACTIVE, null);
        createDevUserIfAbsent("pending@localhost", "Pending User", Role.USER, UserStatus.PENDING, null);
        createDevUserIfAbsent("judge@localhost", "Dev Judge", Role.USER, UserStatus.ACTIVE, null);
        createDevUserIfAbsent("steward@localhost", "Dev Steward", Role.USER, UserStatus.ACTIVE, null);
        createDevUserIfAbsent("entrant@localhost", "Dev Entrant", Role.USER, UserStatus.ACTIVE, null);
    }

    private void createDevUserIfAbsent(String email, String name, Role role, UserStatus status, String password) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = new User(email, name, status, role);
        if (password != null) {
            user.setPasswordHash(passwordEncoder.encode(password));
            log.info("Created dev user {} with password: {}", email, password);
        } else {
            String link = jwtMagicLinkService.generateLink(email, Duration.ofDays(30));
            log.info("\n\n\tDev magic link for {}: {}\n", email, link);
        }
        userRepository.save(user);
    }

}
