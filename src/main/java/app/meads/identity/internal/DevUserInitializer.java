package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

@Component
class DevUserInitializer {

    private final UserRepository userRepository;
    private final MagicLinkService magicLinkService;
    private final Environment environment;

    DevUserInitializer(UserRepository userRepository, MagicLinkService magicLinkService, Environment environment) {
        this.userRepository = userRepository;
        this.magicLinkService = magicLinkService;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void initializeDevUser() {
        if (!isDevProfile()) {
            return;
        }

        String devEmail = environment.getProperty("DEV_USER_EMAIL");
        if (devEmail == null) {
            return;
        }

        if (userRepository.existsByEmail(devEmail)) {
            return;
        }

        User devUser = new User(
                UUID.randomUUID(),
                devEmail,
                "Dev User",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(devUser);
        magicLinkService.requestMagicLink(devEmail);
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }
}
