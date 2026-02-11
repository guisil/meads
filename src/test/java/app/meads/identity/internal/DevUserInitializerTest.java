package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DevUserInitializerTest {

    @InjectMocks DevUserInitializer devUserInitializer;
    @Mock UserRepository userRepository;
    @Mock MagicLinkService magicLinkService;
    @Mock Environment environment;

    @Test
    void shouldNotCreateDevUserWhenDevProfileNotActive() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});

        devUserInitializer.initializeDevUser();

        then(userRepository).should(never()).save(any());
    }

    @Test
    void shouldCreateDevUserWhenDevProfileActiveAndUserDoesNotExist() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"dev"});
        given(environment.getProperty("DEV_USER_EMAIL")).willReturn("dev@example.com");
        given(userRepository.existsByEmail("dev@example.com")).willReturn(false);

        devUserInitializer.initializeDevUser();

        then(userRepository).should().save(any());
        then(magicLinkService).should().requestMagicLink("dev@example.com");
    }

    @Test
    void shouldNotCreateDevUserWhenDevProfileActiveButUserExists() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"dev"});
        given(environment.getProperty("DEV_USER_EMAIL")).willReturn("dev@example.com");
        given(userRepository.existsByEmail("dev@example.com")).willReturn(true);

        devUserInitializer.initializeDevUser();

        then(userRepository).should(never()).save(any());
    }

    @Test
    void shouldNotCreateDevUserWhenNoEmailConfigured() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"dev"});
        given(environment.getProperty("DEV_USER_EMAIL")).willReturn(null);

        devUserInitializer.initializeDevUser();

        then(userRepository).should(never()).save(any());
    }
}
