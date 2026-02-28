package app.meads.identity.internal;

import app.meads.identity.JwtMagicLinkService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DevUserInitializerTest {

    @InjectMocks DevUserInitializer devUserInitializer;
    @Mock UserRepository userRepository;
    @Mock JwtMagicLinkService jwtMagicLinkService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock Environment environment;

    @Test
    void shouldNotCreateDevUsersWhenDevProfileNotActive() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});

        devUserInitializer.initializeDevUsers();

        then(userRepository).should(never()).save(any());
    }

    @Test
    void shouldCreateThreeDevUsersWhenDevProfileActive() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"dev"});
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(passwordEncoder.encode("admin")).willReturn("$2a$10$adminHash");

        devUserInitializer.initializeDevUsers();

        var captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should(times(3)).save(captor.capture());
        List<User> savedUsers = captor.getAllValues();

        // Admin user
        var admin = savedUsers.stream().filter(u -> u.getEmail().equals("admin@localhost")).findFirst().orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isEqualTo("$2a$10$adminHash");

        // Active user
        var user = savedUsers.stream().filter(u -> u.getEmail().equals("user@localhost")).findFirst().orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Pending user
        var pending = savedUsers.stream().filter(u -> u.getEmail().equals("pending@localhost")).findFirst().orElseThrow();
        assertThat(pending.getRole()).isEqualTo(Role.USER);
        assertThat(pending.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void shouldSkipUsersWhenTheyAlreadyExist() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"dev"});
        given(userRepository.existsByEmail("admin@localhost")).willReturn(true);
        given(userRepository.existsByEmail("user@localhost")).willReturn(false);
        given(userRepository.existsByEmail("pending@localhost")).willReturn(true);

        devUserInitializer.initializeDevUsers();

        // Only one user saved (user@localhost)
        then(userRepository).should(times(1)).save(any());
    }

    @Test
    void shouldGenerateMagicLinksForNonAdminDevUsers() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"dev"});
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(passwordEncoder.encode("admin")).willReturn("$2a$10$adminHash");

        devUserInitializer.initializeDevUsers();

        // Magic links for user@localhost and pending@localhost, not admin@localhost
        then(jwtMagicLinkService).should().generateLink(eq("user@localhost"), any());
        then(jwtMagicLinkService).should().generateLink(eq("pending@localhost"), any());
        then(jwtMagicLinkService).should(never()).generateLink(eq("admin@localhost"), any());
    }
}
