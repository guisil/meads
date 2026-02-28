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
import java.util.Arrays;
import java.util.UUID;

@Component
class DevUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(DevUserInitializer.class);

    private final UserRepository userRepository;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final Environment environment;

    DevUserInitializer(UserRepository userRepository, JwtMagicLinkService jwtMagicLinkService, Environment environment) {
        this.userRepository = userRepository;
        this.jwtMagicLinkService = jwtMagicLinkService;
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
        String link = jwtMagicLinkService.generateLink(devEmail, Duration.ofDays(7));
        log.info("\n\n\tDev user magic link for {}: {}\n", devEmail, link);
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }
}
