package app.meads.order;

import app.meads.order.internal.HmacValidator;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class HmacValidatorTest {

    private static final String SECRET = "test-secret";

    @Test
    void shouldValidateCorrectSignature() throws Exception {
        var validator = new HmacValidator(SECRET, true);
        var payload = "{\"orderId\": \"123\"}";
        var signature = calculateHmac(SECRET, payload);

        assertThat(validator.isValid(signature, payload)).isTrue();
    }

    @Test
    void shouldRejectIncorrectSignature() {
        var validator = new HmacValidator(SECRET, true);
        var payload = "{\"orderId\": \"123\"}";
        var wrongSignature = "wrong-signature";

        assertThat(validator.isValid(wrongSignature, payload)).isFalse();
    }

    @Test
    void shouldRejectNullSignature() {
        var validator = new HmacValidator(SECRET, true);
        var payload = "{\"orderId\": \"123\"}";

        assertThat(validator.isValid(null, payload)).isFalse();
    }

    @Test
    void shouldRejectBlankSignature() {
        var validator = new HmacValidator(SECRET, true);
        var payload = "{\"orderId\": \"123\"}";

        assertThat(validator.isValid("", payload)).isFalse();
        assertThat(validator.isValid("   ", payload)).isFalse();
    }

    @Test
    void shouldRejectTamperedPayload() throws Exception {
        var validator = new HmacValidator(SECRET, true);
        var originalPayload = "{\"orderId\": \"123\"}";
        var signature = calculateHmac(SECRET, originalPayload);
        var tamperedPayload = "{\"orderId\": \"999\"}";

        assertThat(validator.isValid(signature, tamperedPayload)).isFalse();
    }

    @Test
    void shouldAcceptAnySignatureWhenValidationDisabled() {
        var validator = new HmacValidator(SECRET, false);
        var payload = "{\"orderId\": \"123\"}";

        assertThat(validator.isValid("any-signature", payload)).isTrue();
        assertThat(validator.isValid(null, payload)).isTrue();
        assertThat(validator.isValid("", payload)).isTrue();
    }

    @Test
    void shouldWorkWithDifferentSecrets() throws Exception {
        var validator1 = new HmacValidator("secret-1", true);
        var validator2 = new HmacValidator("secret-2", true);
        var payload = "{\"data\": \"test\"}";

        var signature1 = calculateHmac("secret-1", payload);

        assertThat(validator1.isValid(signature1, payload)).isTrue();
        assertThat(validator2.isValid(signature1, payload)).isFalse();
    }

    private String calculateHmac(String secret, String payload) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        var secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        var hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
}
