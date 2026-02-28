package app.meads.identity.internal;

import app.meads.identity.UserStatus;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserActivationListener {

    private final UserRepository userRepository;

    UserActivationListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener(AuthenticationSuccessEvent.class)
    @Transactional
    void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.PENDING)
                .ifPresent(user -> {
                    user.activate();
                    userRepository.save(user);
                });
    }
}
