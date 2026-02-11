package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class AdminInitializer {

    private final UserRepository userRepository;
    private final MagicLinkService magicLinkService;
    private final Environment environment;

    AdminInitializer(UserRepository userRepository, MagicLinkService magicLinkService, Environment environment) {
        this.userRepository = userRepository;
        this.magicLinkService = magicLinkService;
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
                magicLinkService.requestMagicLink(adminEmail);
            }
        }
    }
}
