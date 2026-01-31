package app.meads.order.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
@Slf4j
public class HmacValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String hmacSecret;
    private final boolean validationEnabled;

    public HmacValidator(
        @Value("${meads.webhook.hmac-secret:dev-secret}") String hmacSecret,
        @Value("${meads.webhook.hmac-validation-enabled:true}") boolean validationEnabled
    ) {
        this.hmacSecret = hmacSecret;
        this.validationEnabled = validationEnabled;
    }

    public boolean isValid(String signature, String payload) {
        if (!validationEnabled) {
            log.warn("HMAC validation is disabled - accepting all requests");
            return true;
        }

        if (signature == null || signature.isBlank()) {
            log.warn("Missing HMAC signature");
            return false;
        }

        try {
            var expectedSignature = calculateHmac(payload);
            var isValid = MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
            );

            if (!isValid) {
                log.warn("HMAC signature mismatch");
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error validating HMAC signature", e);
            return false;
        }
    }

    private String calculateHmac(String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        var mac = Mac.getInstance(HMAC_SHA256);
        var secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKey);
        var hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
}
