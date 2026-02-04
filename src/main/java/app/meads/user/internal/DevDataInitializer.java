package app.meads.user.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Initializes development data for the user module.
 * Only runs when the 'dev' profile is active.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

  private final UserRepository userRepository;

  @Override
  public void run(ApplicationArguments args) {
    createDefaultAdminUser();
  }

  private void createDefaultAdminUser() {
    String adminEmail = "admin@meads.app";

    if (userRepository.existsByEmail(adminEmail)) {
      log.info("Development admin user already exists: {}", adminEmail);
      return;
    }

    var adminUser = User.builder()
        .email(adminEmail)
        .displayName("Dev Admin")
        .displayCountry("US")
        .isSystemAdmin(true)
        .build();

    userRepository.save(adminUser);

    log.info("=".repeat(80));
    log.info("DEVELOPMENT USER CREATED");
    log.info("Email: {}", adminEmail);
    log.info("System Admin: Yes");
    log.info("");
    log.info("To login:");
    log.info("1. Go to http://localhost:8080/admin/users");
    log.info("2. Select the admin user and click 'Send Magic Link'");
    log.info("3. Copy the magic link from the console logs");
    log.info("=".repeat(80));
  }
}
