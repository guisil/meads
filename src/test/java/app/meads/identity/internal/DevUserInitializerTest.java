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

    @Test
    void shouldCreateSevenDevUsersWhenDevProfileActive() {
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(passwordEncoder.encode("admin")).willReturn("$2a$10$adminHash");
        given(passwordEncoder.encode("compadmin")).willReturn("$2a$10$compadminHash");

        devUserInitializer.initializeDevUsers();

        var captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should(times(7)).save(captor.capture());
        List<User> savedUsers = captor.getAllValues();

        // Admin user
        var admin = savedUsers.stream().filter(u -> u.getEmail().equals("admin@example.com")).findFirst().orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isEqualTo("$2a$10$adminHash");

        // Competition admin user
        var compAdmin = savedUsers.stream().filter(u -> u.getEmail().equals("compadmin@example.com")).findFirst().orElseThrow();
        assertThat(compAdmin.getRole()).isEqualTo(Role.USER);
        assertThat(compAdmin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(compAdmin.getPasswordHash()).isEqualTo("$2a$10$compadminHash");

        // Active user
        var user = savedUsers.stream().filter(u -> u.getEmail().equals("user@example.com")).findFirst().orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Pending user
        var pending = savedUsers.stream().filter(u -> u.getEmail().equals("pending@example.com")).findFirst().orElseThrow();
        assertThat(pending.getRole()).isEqualTo(Role.USER);
        assertThat(pending.getStatus()).isEqualTo(UserStatus.PENDING);

        // Judge user
        var judge = savedUsers.stream().filter(u -> u.getEmail().equals("judge@example.com")).findFirst().orElseThrow();
        assertThat(judge.getRole()).isEqualTo(Role.USER);
        assertThat(judge.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Steward user
        var steward = savedUsers.stream().filter(u -> u.getEmail().equals("steward@example.com")).findFirst().orElseThrow();
        assertThat(steward.getRole()).isEqualTo(Role.USER);
        assertThat(steward.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Entrant user
        var entrant = savedUsers.stream().filter(u -> u.getEmail().equals("entrant@example.com")).findFirst().orElseThrow();
        assertThat(entrant.getRole()).isEqualTo(Role.USER);
        assertThat(entrant.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldSkipUsersWhenTheyAlreadyExist() {
        given(userRepository.existsByEmail("admin@example.com")).willReturn(true);
        given(userRepository.existsByEmail("compadmin@example.com")).willReturn(true);
        given(userRepository.existsByEmail("user@example.com")).willReturn(false);
        given(userRepository.existsByEmail("pending@example.com")).willReturn(true);
        given(userRepository.existsByEmail("judge@example.com")).willReturn(true);
        given(userRepository.existsByEmail("steward@example.com")).willReturn(true);
        given(userRepository.existsByEmail("entrant@example.com")).willReturn(true);

        devUserInitializer.initializeDevUsers();

        // Only one user saved (user@example.com)
        then(userRepository).should(times(1)).save(any());
    }

    @Test
    void shouldGenerateMagicLinksForNonAdminDevUsers() {
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(passwordEncoder.encode("admin")).willReturn("$2a$10$adminHash");

        devUserInitializer.initializeDevUsers();

        // Magic links for non-password users only
        then(jwtMagicLinkService).should().generateLink(eq("user@example.com"), any());
        then(jwtMagicLinkService).should().generateLink(eq("pending@example.com"), any());
        then(jwtMagicLinkService).should().generateLink(eq("judge@example.com"), any());
        then(jwtMagicLinkService).should().generateLink(eq("steward@example.com"), any());
        then(jwtMagicLinkService).should().generateLink(eq("entrant@example.com"), any());
        then(jwtMagicLinkService).should(never()).generateLink(eq("admin@example.com"), any());
        then(jwtMagicLinkService).should(never()).generateLink(eq("compadmin@example.com"), any());
    }
}
