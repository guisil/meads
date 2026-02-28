package app.meads.identity.internal;

import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@Component
class DevUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(DevUserInitializer.class);

    private final UserRepository userRepository;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    DevUserInitializer(UserRepository userRepository, JwtMagicLinkService jwtMagicLinkService,
                       PasswordEncoder passwordEncoder, Environment environment) {
        this.userRepository = userRepository;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void initializeDevUsers() {
        if (!isDevProfile()) {
            return;
        }

        createDevUserIfAbsent("admin@localhost", "Dev Admin", Role.SYSTEM_ADMIN, UserStatus.ACTIVE, "admin");
        createDevUserIfAbsent("user@localhost", "Dev User", Role.USER, UserStatus.ACTIVE, null);
        createDevUserIfAbsent("pending@localhost", "Pending User", Role.USER, UserStatus.PENDING, null);
    }

    private void createDevUserIfAbsent(String email, String name, Role role, UserStatus status, String password) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = new User(UUID.randomUUID(), email, name, status, role);
        if (password != null) {
            user.setPasswordHash(passwordEncoder.encode(password));
            log.info("Created dev user {} with password: {}", email, password);
        } else {
            String link = jwtMagicLinkService.generateLink(email, Duration.ofDays(30));
            log.info("\n\n\tDev magic link for {}: {}\n", email, link);
        }
        userRepository.save(user);
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }
}
