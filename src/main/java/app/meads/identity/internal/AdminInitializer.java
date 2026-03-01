package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class AdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void initializeAdmin() {
        if (userRepository.existsByRole(Role.SYSTEM_ADMIN)) {
            return;
        }

        String adminEmail = environment.getProperty("INITIAL_ADMIN_EMAIL");
        if (adminEmail == null) {
            return;
        }

        String adminPassword = environment.getProperty("INITIAL_ADMIN_PASSWORD");
        if (adminPassword == null) {
            log.warn("INITIAL_ADMIN_EMAIL is set but INITIAL_ADMIN_PASSWORD is not — skipping admin creation");
            return;
        }

        User admin = new User(
                adminEmail,
                "System Administrator",
                UserStatus.ACTIVE,
                Role.SYSTEM_ADMIN
        );
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        userRepository.save(admin);
        log.info("Created initial admin user: {}", adminEmail);
    }
}
