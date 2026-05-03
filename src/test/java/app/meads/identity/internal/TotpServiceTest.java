package app.meads.identity.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    void shouldGenerateBase32SecretMatchingAlphabet() {
        var secret = totpService.generateSecret();
        assertThat(secret).matches("[A-Z2-7]+");
        assertThat(secret).hasSizeGreaterThanOrEqualTo(16);
    }

    @Test
    void shouldVerifyCodeForCurrentTimeStep() {
        var secret = totpService.generateSecret();
        long timeStep = System.currentTimeMillis() / 1000 / 30;
        String code = String.format("%06d", totpService.generateCode(secret, timeStep));
        assertThat(totpService.verifyCode(secret, code)).isTrue();
    }

    @Test
    void shouldVerifyCodeForAdjacentTimeSteps() {
        var secret = totpService.generateSecret();
        long timeStep = System.currentTimeMillis() / 1000 / 30;
        String prevCode = String.format("%06d", totpService.generateCode(secret, timeStep - 1));
        String nextCode = String.format("%06d", totpService.generateCode(secret, timeStep + 1));
        assertThat(totpService.verifyCode(secret, prevCode)).isTrue();
        assertThat(totpService.verifyCode(secret, nextCode)).isTrue();
    }

    @Test
    void shouldRejectCodeOutsideWindow() {
        var secret = totpService.generateSecret();
        long timeStep = System.currentTimeMillis() / 1000 / 30;
        String staleCode = String.format("%06d", totpService.generateCode(secret, timeStep - 2));
        assertThat(totpService.verifyCode(secret, staleCode)).isFalse();
    }

    @Test
    void shouldRejectWrongCode() {
        var secret = totpService.generateSecret();
        // Generate a code for a far-future timestep — won't match current window
        String wrongCode = String.format("%06d", totpService.generateCode(secret, 999_999_999L));
        assertThat(totpService.verifyCode(secret, wrongCode)).isFalse();
    }

    @Test
    void shouldGenerateQrUri() {
        var uri = totpService.generateQrUri("JBSWY3DPEHPK3PXP", "user@example.com");
        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(uri).contains("issuer=MEADS");
    }

    @Test
    void shouldBase32RoundTrip() {
        byte[] original = "Hello, TOTP!".getBytes();
        String encoded = totpService.base32Encode(original);
        byte[] decoded = totpService.base32Decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }
}
