package app.meads.identity;

import app.meads.BusinessRuleException;
import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.TotpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserMfaIntegrationTest {

    @Autowired
    UserService userService;

    @Autowired
    TotpService totpService;

    @Test
    void shouldSetupMfaStoringPendingSecretWithoutEnabling() {
        var user = userService.createUser("mfa-setup@test.com", "MFA User", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);

        var qrUri = userService.setupMfa(user.getId());

        assertThat(qrUri).startsWith("otpauth://totp/");
        assertThat(qrUri).contains("secret=");
        assertThat(qrUri).contains("issuer=MEADS");

        var refreshed = userService.findById(user.getId());
        assertThat(refreshed.getTotpSecret()).isNotNull();
        assertThat(refreshed.isMfaEnabled()).isFalse();
    }

    @Test
    void shouldConfirmMfaWithValidCode() {
        var user = userService.createUser("mfa-confirm@test.com", "MFA User", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        userService.setupMfa(user.getId());

        var pendingUser = userService.findById(user.getId());
        var secret = pendingUser.getTotpSecret();
        var timeStep = System.currentTimeMillis() / 1000 / 30;
        var code = String.format("%06d", totpService.generateCode(secret, timeStep));

        userService.confirmMfa(user.getId(), code);

        var confirmed = userService.findById(user.getId());
        assertThat(confirmed.isMfaEnabled()).isTrue();
        assertThat(confirmed.getTotpSecret()).isEqualTo(secret);
    }

    @Test
    void shouldRejectConfirmMfaWithWrongCode() {
        var user = userService.createUser("mfa-reject@test.com", "MFA User", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        userService.setupMfa(user.getId());

        assertThatThrownBy(() -> userService.confirmMfa(user.getId(), "000000"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.mfa.invalid-code");

        var refreshed = userService.findById(user.getId());
        assertThat(refreshed.isMfaEnabled()).isFalse();
    }

    @Test
    void shouldVerifyMfaCodeAfterEnabling() {
        var user = userService.createUser("mfa-verify@test.com", "MFA User", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        userService.setupMfa(user.getId());

        var pendingUser = userService.findById(user.getId());
        var secret = pendingUser.getTotpSecret();
        var timeStep = System.currentTimeMillis() / 1000 / 30;
        var code = String.format("%06d", totpService.generateCode(secret, timeStep));

        userService.confirmMfa(user.getId(), code);

        var newCode = String.format("%06d", totpService.generateCode(secret, timeStep));
        assertThat(userService.verifyMfaCode(user.getId(), newCode)).isTrue();
    }

    @Test
    void shouldDisableMfaAfterEnabling() {
        var user = userService.createUser("mfa-disable@test.com", "MFA User", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        userService.setupMfa(user.getId());

        var pendingUser = userService.findById(user.getId());
        var secret = pendingUser.getTotpSecret();
        var timeStep = System.currentTimeMillis() / 1000 / 30;
        var code = String.format("%06d", totpService.generateCode(secret, timeStep));
        userService.confirmMfa(user.getId(), code);

        userService.disableMfa(user.getId());

        var disabled = userService.findById(user.getId());
        assertThat(disabled.isMfaEnabled()).isFalse();
        assertThat(disabled.getTotpSecret()).isNull();
    }
}
