package app.meads.identity.internal;

import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @InjectMocks
    AdminInitializer adminInitializer;

    @Mock
    UserRepository userRepository;

    @Mock
    JwtMagicLinkService jwtMagicLinkService;

    @Mock
    Environment environment;

    @Test
    void shouldCreatePendingAdminWhenNoAdminExistsAndEmailIsSet() {
        // Given - no admin exists
        given(userRepository.existsByRole(Role.SYSTEM_ADMIN)).willReturn(false);

        // And - INITIAL_ADMIN_EMAIL is set
        given(environment.getProperty("INITIAL_ADMIN_EMAIL")).willReturn("admin@example.com");

        // When - initialization runs
        adminInitializer.initializeAdmin();

        // Then - should create a PENDING admin user
        then(userRepository).should().save(any());

        // And - should generate magic link
        then(jwtMagicLinkService).should().generateLink(eq("admin@example.com"), any());
    }

    @Test
    void shouldDoNothingWhenAdminAlreadyExists() {
        // Given - admin already exists
        given(userRepository.existsByRole(Role.SYSTEM_ADMIN)).willReturn(true);

        // When - initialization runs
        adminInitializer.initializeAdmin();

        // Then - should not create any user
        then(userRepository).should(never()).save(any());

        // And - should not generate magic link
        then(jwtMagicLinkService).should(never()).generateLink(any(), any());
    }

    @Test
    void shouldDoNothingWhenEmailNotSet() {
        // Given - no admin exists
        given(userRepository.existsByRole(Role.SYSTEM_ADMIN)).willReturn(false);

        // And - INITIAL_ADMIN_EMAIL is not set
        given(environment.getProperty("INITIAL_ADMIN_EMAIL")).willReturn(null);

        // When - initialization runs
        adminInitializer.initializeAdmin();

        // Then - should not create any user
        then(userRepository).should(never()).save(any());

        // And - should not generate magic link
        then(jwtMagicLinkService).should(never()).generateLink(any(), any());
    }
}
