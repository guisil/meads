package app.meads.identity.internal;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Component
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final int WINDOW = 1;

    public String generateSecret() {
        var bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String generateQrUri(String secret, String email) {
        var label = URLEncoder.encode("MEADS:" + email, StandardCharsets.UTF_8);
        var issuer = URLEncoder.encode("MEADS", StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer + "&digits=6&period=30";
    }

    public boolean verifyCode(String secret, String code) {
        if (code == null || code.length() != CODE_DIGITS) return false;
        try {
            int input = Integer.parseInt(code);
            byte[] secretBytes = base32Decode(secret);
            long timeStep = System.currentTimeMillis() / 1000 / 30;
            for (long step = timeStep - WINDOW; step <= timeStep + WINDOW; step++) {
                if (generateCode(secret, step) == input) return true;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public int generateCode(String secret, long timeStep) {
        try {
            byte[] secretBytes = base32Decode(secret);
            byte[] msg = ByteBuffer.allocate(8).putLong(timeStep).array();
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0F;
            int code = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return code % (int) Math.pow(10, CODE_DIGITS);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("TOTP computation failed", e);
        }
    }

    String base32Encode(byte[] input) {
        var bits = new StringBuilder();
        for (byte b : input) {
            bits.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }
        while (bits.length() % 5 != 0) bits.append('0');
        var result = new StringBuilder();
        for (int i = 0; i < bits.length(); i += 5) {
            result.append(BASE32_ALPHABET.charAt(Integer.parseInt(bits.substring(i, i + 5), 2)));
        }
        return result.toString();
    }

    byte[] base32Decode(String input) {
        var clean = input.toUpperCase().replaceAll("=", "").replaceAll("\\s", "");
        var bits = new StringBuilder();
        for (char c : clean.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) throw new IllegalArgumentException("Invalid Base32 character: " + c);
            bits.append(String.format("%5s", Integer.toBinaryString(val)).replace(' ', '0'));
        }
        var result = new byte[bits.length() / 8];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(bits.substring(i * 8, (i + 1) * 8), 2);
        }
        return result;
    }
}
