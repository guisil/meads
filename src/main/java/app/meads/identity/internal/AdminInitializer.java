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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
class AdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final Environment environment;

    AdminInitializer(UserRepository userRepository, JwtMagicLinkService jwtMagicLinkService, Environment environment) {
        this.userRepository = userRepository;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void initializeAdmin() {
        if (!userRepository.existsByRole(Role.SYSTEM_ADMIN)) {
            String adminEmail = environment.getProperty("INITIAL_ADMIN_EMAIL");
            if (adminEmail != null) {
                User admin = new User(
                        UUID.randomUUID(),
                        adminEmail,
                        "System Administrator",
                        UserStatus.PENDING,
                        Role.SYSTEM_ADMIN
                );
                userRepository.save(admin);
                String link = jwtMagicLinkService.generateLink(adminEmail, Duration.ofDays(7));
                log.info("\n\n\tAdmin magic link for {}: {}\n", adminEmail, link);
            }
        }
    }
}
