package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    PasswordEncoder passwordEncoder;

    @Mock
    Environment environment;

    @Test
    void shouldCreateActiveAdminWithPasswordWhenBothEnvVarsSet() {
        // Given - no admin exists
        given(userRepository.existsByRole(Role.SYSTEM_ADMIN)).willReturn(false);
        given(environment.getProperty("INITIAL_ADMIN_EMAIL")).willReturn("admin@example.com");
        given(environment.getProperty("INITIAL_ADMIN_PASSWORD")).willReturn("secretPassword");
        given(passwordEncoder.encode("secretPassword")).willReturn("$2a$10$encodedHash");

        // When
        adminInitializer.initializeAdmin();

        // Then - should create an ACTIVE admin with password hash and SYSTEM_ADMIN role
        var captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        var savedUser = captor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("admin@example.com");
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$encodedHash");
    }

    @Test
    void shouldDoNothingWhenAdminAlreadyExists() {
        given(userRepository.existsByRole(Role.SYSTEM_ADMIN)).willReturn(true);

        adminInitializer.initializeAdmin();

        then(userRepository).should(never()).save(any());
    }

    @Test
    void shouldDoNothingWhenEmailNotSet() {
        given(userRepository.existsByRole(Role.SYSTEM_ADMIN)).willReturn(false);
        given(environment.getProperty("INITIAL_ADMIN_EMAIL")).willReturn(null);

        adminInitializer.initializeAdmin();

        then(userRepository).should(never()).save(any());
    }

    @Test
    void shouldDoNothingWhenPasswordNotSet() {
        given(userRepository.existsByRole(Role.SYSTEM_ADMIN)).willReturn(false);
        given(environment.getProperty("INITIAL_ADMIN_EMAIL")).willReturn("admin@example.com");
        given(environment.getProperty("INITIAL_ADMIN_PASSWORD")).willReturn(null);

        adminInitializer.initializeAdmin();

        then(userRepository).should(never()).save(any());
    }
}
