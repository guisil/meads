package app.meads.user.internal;

import app.meads.user.TokenPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Controller for magic link authentication.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

  private final AccessTokenRepository accessTokenRepository;
  private final UserRepository userRepository;

  /**
   * Verifies a magic link token and authenticates the user.
   */
  @GetMapping("/auth/verify")
  public String verifyMagicLink(
      @RequestParam("token") String rawToken,
      RedirectAttributes redirectAttributes) {

    try {
      log.info("Attempting to verify magic link token");
      log.debug("Raw token: {}", rawToken);

      // Hash the token to look it up
      String tokenHash = hashToken(rawToken);
      log.debug("Token hash: {}", tokenHash);

      // Find the token
      var accessToken = accessTokenRepository
          .findByTokenHashAndUsedFalseAndExpiresAtAfter(tokenHash, Instant.now())
          .orElseThrow(() -> {
            log.error("Token not found or expired. Hash: {}", tokenHash);
            return new IllegalArgumentException("Invalid or expired token");
          });

      log.info("Token found: id={}, email={}, expiresAt={}",
          accessToken.getId(), accessToken.getEmail(), accessToken.getExpiresAt());

      // Verify it's a LOGIN token
      if (accessToken.getPurpose() != TokenPurpose.LOGIN) {
        throw new IllegalArgumentException("Invalid token purpose");
      }

      // Get the user (should exist since we created tokens only for existing users)
      var user = accessToken.getUser();
      if (user == null) {
        // Fallback: look up by email
        user = userRepository.findByEmail(accessToken.getEmail())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
      }

      // Mark token as used
      accessToken.setUsed(true);
      accessTokenRepository.save(accessToken);

      // Authenticate the user
      var authorities = user.getIsSystemAdmin()
          ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
          : List.of(new SimpleGrantedAuthority("ROLE_USER"));

      var authentication = new UsernamePasswordAuthenticationToken(
          user.getEmail(),
          null,
          authorities
      );

      SecurityContextHolder.getContext().setAuthentication(authentication);

      log.info("User authenticated successfully: {}", user.getEmail());

      // Redirect to admin page if admin, otherwise to home
      if (user.getIsSystemAdmin()) {
        return "redirect:/admin/users";
      } else {
        return "redirect:/";
      }

    } catch (Exception e) {
      log.error("Error verifying magic link", e);
      redirectAttributes.addFlashAttribute("error", "Invalid or expired magic link");
      return "redirect:/login-error";
    }
  }

  /**
   * Hashes a token using SHA-256.
   */
  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }
}
