# Email Sending Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace console-logged authentication links with actual SMTP email delivery using HTML templates.

**Architecture:** `EmailService` interface in `app.meads.identity` (public API) with `SmtpEmailService` implementation in `internal/`. Uses `spring-boot-starter-mail` for SMTP and Thymeleaf for HTML email templates. Dev profile uses Mailpit (local SMTP trap); prod uses Resend SMTP.

**Tech Stack:** Spring Boot Mail, Thymeleaf, Mailpit (dev), Resend (prod)

**Spec:** `docs/plans/2026-03-10-email-sending-design.md`

---

## File Structure

### New files
| File | Responsibility |
|------|---------------|
| `src/main/java/app/meads/identity/EmailService.java` | Public interface: `sendMagicLink`, `sendPasswordReset`, `sendPasswordSetup` |
| `src/main/java/app/meads/identity/internal/SmtpEmailService.java` | Implementation: Thymeleaf rendering, JavaMailSender, fallback logging |
| `src/main/resources/templates/email/email-base.html` | Thymeleaf HTML email template (table-based layout) |
| `src/test/java/app/meads/identity/internal/SmtpEmailServiceTest.java` | Unit tests for SmtpEmailService |

### Modified files
| File | Change |
|------|--------|
| `pom.xml` | Add `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf` |
| `src/main/resources/application.properties` | Add `app.email.from`, `spring.thymeleaf.check-template-location=false` |
| `src/main/resources/application-dev.properties` | Add Mailpit SMTP config |
| `src/main/resources/application-prod.properties` | Add Resend SMTP config placeholders |
| `src/test/resources/application.properties` | Add `app.email.from`, `spring.thymeleaf.check-template-location=false` |
| `docker-compose.yml` | Add Mailpit service |
| `src/main/java/app/meads/competition/Competition.java` | Add `contactEmail` field + `updateContactEmail()` |
| `src/main/resources/db/migration/V3__create_competitions_table.sql` | Add `contact_email` column |
| `src/main/java/app/meads/competition/CompetitionService.java` | Add `updateCompetitionContactEmail()` method |
| `src/main/java/app/meads/identity/LoginView.java` | Replace `JwtMagicLinkService` with `EmailService` |
| `src/main/java/app/meads/identity/internal/UserListView.java` | Replace `JwtMagicLinkService` with `EmailService` |
| `src/main/java/app/meads/competition/internal/CompetitionDetailView.java` | Replace `JwtMagicLinkService` with `EmailService`, add `contactEmail` field in Settings, pass competition context |

---

## Chunk 1: Infrastructure & EmailService Core

### Task 1: Add dependencies and configuration

- [ ] **Step 1: Add Maven dependencies**

Add to `pom.xml` after the `spring-boot-starter-webmvc` dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

- [ ] **Step 2: Update application.properties**

Add to `src/main/resources/application.properties`:

```properties
app.email.from=MEADS <noreply@meads.app>
spring.thymeleaf.check-template-location=false
```

- [ ] **Step 3: Update application-dev.properties**

Add to `src/main/resources/application-dev.properties`:

```properties
spring.mail.host=localhost
spring.mail.port=1025
```

- [ ] **Step 4: Update application-prod.properties**

Add to `src/main/resources/application-prod.properties`:

```properties
spring.mail.host=smtp.resend.com
spring.mail.port=587
spring.mail.username=resend
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

- [ ] **Step 5: Update test application.properties**

Add to `src/test/resources/application.properties`:

```properties
app.email.from=MEADS <noreply@meads.app>
spring.thymeleaf.check-template-location=false
spring.mail.host=localhost
spring.mail.port=1025
```

Note: `spring.mail.host/port` in test properties prevents auto-configuration warnings.
`SmtpEmailService` catches SMTP failures gracefully, so tests won't fail if Mailpit isn't running.

- [ ] **Step 6: Add Mailpit to docker-compose.yml**

Add the mailpit service to `docker-compose.yml`:

```yaml
  mailpit:
    image: axllent/mailpit
    container_name: meads-mailpit
    ports:
      - "1025:1025"
      - "8025:8025"
```

- [ ] **Step 7: Verify the project compiles**

Run: `mvn compile -q`
Expected: BUILD SUCCESS (no compilation errors from new dependencies)

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/resources/application.properties src/main/resources/application-dev.properties src/main/resources/application-prod.properties src/test/resources/application.properties docker-compose.yml
git commit -m "Add spring-boot-starter-mail, Thymeleaf deps, Mailpit config"
```

---

### Task 2: Create EmailService interface

- [ ] **Step 1: Create the interface**

Create `src/main/java/app/meads/identity/EmailService.java`:

```java
package app.meads.identity;

public interface EmailService {

    void sendMagicLink(String recipientEmail);

    void sendPasswordReset(String recipientEmail);

    void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail);
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/app/meads/identity/EmailService.java
git commit -m "Add EmailService interface to identity module public API"
```

---

### Task 3: Create Thymeleaf email template

- [ ] **Step 1: Create the template directory and file**

Create `src/main/resources/templates/email/email-base.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title th:text="${subject}">Email</title>
</head>
<body style="margin: 0; padding: 0; background-color: #f4f4f4; font-family: Arial, Helvetica, sans-serif;">
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color: #f4f4f4;">
        <tr>
            <td align="center" style="padding: 40px 0;">
                <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="background-color: #ffffff; border-radius: 8px; overflow: hidden;">
                    <!-- Header -->
                    <tr>
                        <td style="background-color: #1a1a2e; padding: 24px 40px; text-align: center;">
                            <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: bold; letter-spacing: 2px;">MEADS</h1>
                        </td>
                    </tr>
                    <!-- Body -->
                    <tr>
                        <td style="padding: 40px;">
                            <h2 style="margin: 0 0 16px 0; color: #1a1a2e; font-size: 20px;" th:text="${heading}">Heading</h2>
                            <p style="margin: 0 0 32px 0; color: #333333; font-size: 16px; line-height: 1.5;" th:text="${bodyText}">Body text</p>
                            <!-- CTA Button -->
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin: 0 auto;">
                                <tr>
                                    <td style="border-radius: 6px; background-color: #1a1a2e;">
                                        <a th:href="${ctaUrl}" th:text="${ctaLabel}" style="display: inline-block; padding: 14px 32px; color: #ffffff; text-decoration: none; font-size: 16px; font-weight: bold;">Button</a>
                                    </td>
                                </tr>
                            </table>
                            <!-- Fallback URL -->
                            <p style="margin: 32px 0 0 0; color: #888888; font-size: 12px; line-height: 1.4;">
                                If the button doesn't work, copy and paste this link into your browser:<br/>
                                <a th:href="${ctaUrl}" th:text="${ctaUrl}" style="color: #1a1a2e; word-break: break-all;">https://example.com</a>
                            </p>
                        </td>
                    </tr>
                    <!-- Contact footer (conditional) -->
                    <tr th:if="${contactEmail != null}">
                        <td style="padding: 0 40px 24px 40px; border-top: 1px solid #eeeeee;">
                            <p style="margin: 24px 0 0 0; color: #666666; font-size: 14px;">
                                Questions? Contact
                                <a th:href="'mailto:' + ${contactEmail}" th:text="${contactEmail}" style="color: #1a1a2e;">organizer@example.com</a>
                            </p>
                        </td>
                    </tr>
                    <!-- Footer -->
                    <tr>
                        <td style="background-color: #f8f8f8; padding: 20px 40px; text-align: center; border-top: 1px solid #eeeeee;">
                            <p style="margin: 0; color: #999999; font-size: 12px;">
                                MEADS &mdash; Mead Evaluation and Awards Data System
                            </p>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/email/email-base.html
git commit -m "Add Thymeleaf HTML email template"
```

---

### Task 4: SmtpEmailService — TDD cycle 1 (sendMagicLink)

- [ ] **Step 1: Write the first failing test**

Create `src/test/java/app/meads/identity/internal/SmtpEmailServiceTest.java`:

```java
package app.meads.identity.internal;

import app.meads.identity.JwtMagicLinkService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    JavaMailSender mailSender;

    @Mock
    JwtMagicLinkService jwtMagicLinkService;

    @Mock
    ITemplateEngine templateEngine;

    @Mock
    MimeMessage mimeMessage;

    SmtpEmailService emailService;

    @BeforeEach
    void setup() {
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(templateEngine.process(eq("email/email-base"), any(IContext.class)))
                .willReturn("<html>rendered</html>");
        emailService = new SmtpEmailService(mailSender, jwtMagicLinkService,
                templateEngine, "MEADS <noreply@meads.app>");
    }

    @Test
    void shouldSendMagicLinkEmail() {
        given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/login/magic?token=abc123");

        emailService.sendMagicLink("user@example.com");

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("Log in to MEADS");
        assertThat(ctx.getVariable("ctaLabel")).isEqualTo("Log In");
        assertThat(ctx.getVariable("ctaUrl")).isEqualTo("http://localhost:8080/login/magic?token=abc123");
        assertThat(ctx.getVariable("contactEmail")).isNull();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=SmtpEmailServiceTest#shouldSendMagicLinkEmail -Dsurefire.useFile=false`
Expected: COMPILATION FAILURE — `SmtpEmailService` does not exist

### Task 5: SmtpEmailService implementation (GREEN)

- [ ] **Step 1: Create the implementation**

Create `src/main/java/app/meads/identity/internal/SmtpEmailService.java`:

```java
package app.meads.identity.internal;

import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Duration;

@Slf4j
@Service
class SmtpEmailService implements EmailService {

    private static final Duration TOKEN_VALIDITY = Duration.ofDays(7);
    private static final String TEMPLATE_NAME = "email/email-base";

    private final JavaMailSender mailSender;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final ITemplateEngine templateEngine;
    private final String fromAddress;

    SmtpEmailService(JavaMailSender mailSender,
                     JwtMagicLinkService jwtMagicLinkService,
                     ITemplateEngine templateEngine,
                     @Value("${app.email.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendMagicLink(String recipientEmail) {
        var link = jwtMagicLinkService.generateLink(recipientEmail, TOKEN_VALIDITY);
        var ctx = new Context();
        ctx.setVariable("subject", "Your MEADS login link");
        ctx.setVariable("heading", "Log in to MEADS");
        ctx.setVariable("bodyText", "Click the button below to log in.");
        ctx.setVariable("ctaLabel", "Log In");
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, "Your MEADS login link", ctx, link);
    }

    @Override
    public void sendPasswordReset(String recipientEmail) {
        var link = jwtMagicLinkService.generatePasswordSetupLink(recipientEmail, TOKEN_VALIDITY);
        var ctx = new Context();
        ctx.setVariable("subject", "Reset your MEADS password");
        ctx.setVariable("heading", "Set your password");
        ctx.setVariable("bodyText", "Click the button below to set a new password.");
        ctx.setVariable("ctaLabel", "Set Password");
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, "Reset your MEADS password", ctx, link);
    }

    @Override
    public void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail) {
        var link = jwtMagicLinkService.generatePasswordSetupLink(recipientEmail, TOKEN_VALIDITY);
        var ctx = new Context();
        ctx.setVariable("subject", "Set up your MEADS admin password");
        ctx.setVariable("heading", "Set your admin password");
        ctx.setVariable("bodyText",
                "You've been added as an admin for " + competitionName + ". Click below to set your password.");
        ctx.setVariable("ctaLabel", "Set Password");
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("contactEmail", contactEmail);
        sendEmail(recipientEmail, "Set up your MEADS admin password", ctx, link);
    }

    private void sendEmail(String to, String subject, Context thymeleafContext, String fallbackLink) {
        try {
            var htmlBody = templateEngine.process(TEMPLATE_NAME, thymeleafContext);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent: subject='{}', to={}", subject, to);
        } catch (MailException | MessagingException e) {
            log.warn("Failed to send email to {} (subject='{}'): {}. Link: {}",
                    to, subject, e.getMessage(), fallbackLink);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `mvn test -Dtest=SmtpEmailServiceTest#shouldSendMagicLinkEmail -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/app/meads/identity/EmailService.java src/main/java/app/meads/identity/internal/SmtpEmailService.java src/test/java/app/meads/identity/internal/SmtpEmailServiceTest.java
git commit -m "Add SmtpEmailService with sendMagicLink and Thymeleaf rendering"
```

---

### Task 5b: SmtpEmailService — TDD cycle 2 (remaining methods)

Add these tests one at a time to `SmtpEmailServiceTest.java`, running after each to verify they pass.
Since `SmtpEmailService` already has the implementation, each test should pass immediately (fast cycle pattern — the infrastructure is proven, we're adding coverage).

- [ ] **Step 1: Add sendPasswordReset test**

```java
@Test
void shouldSendPasswordResetEmail() {
    given(jwtMagicLinkService.generatePasswordSetupLink(eq("user@example.com"), any()))
            .willReturn("http://localhost:8080/set-password?token=xyz789");

    emailService.sendPasswordReset("user@example.com");

    verify(mailSender).send(any(MimeMessage.class));
    var contextCaptor = ArgumentCaptor.forClass(IContext.class);
    verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
    var ctx = contextCaptor.getValue();
    assertThat(ctx.getVariable("heading")).isEqualTo("Set your password");
    assertThat(ctx.getVariable("ctaLabel")).isEqualTo("Set Password");
    assertThat(ctx.getVariable("ctaUrl")).isEqualTo("http://localhost:8080/set-password?token=xyz789");
    assertThat(ctx.getVariable("contactEmail")).isNull();
}
```

- [ ] **Step 2: Add sendPasswordSetup with competition context test**

```java
@Test
void shouldSendPasswordSetupEmailWithCompetitionContext() {
    given(jwtMagicLinkService.generatePasswordSetupLink(eq("admin@example.com"), any()))
            .willReturn("http://localhost:8080/set-password?token=setup456");

    emailService.sendPasswordSetup("admin@example.com", "CHIP 2026", "organizer@chip.com");

    verify(mailSender).send(any(MimeMessage.class));
    var contextCaptor = ArgumentCaptor.forClass(IContext.class);
    verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
    var ctx = contextCaptor.getValue();
    assertThat(ctx.getVariable("heading")).isEqualTo("Set your admin password");
    assertThat(ctx.getVariable("bodyText")).contains("CHIP 2026");
    assertThat(ctx.getVariable("contactEmail")).isEqualTo("organizer@chip.com");
}
```

- [ ] **Step 3: Add sendPasswordSetup without contact email test**

```java
@Test
void shouldSendPasswordSetupEmailWithoutContactEmail() {
    given(jwtMagicLinkService.generatePasswordSetupLink(eq("admin@example.com"), any()))
            .willReturn("http://localhost:8080/set-password?token=setup456");

    emailService.sendPasswordSetup("admin@example.com", "CHIP 2026", null);

    verify(mailSender).send(any(MimeMessage.class));
    var contextCaptor = ArgumentCaptor.forClass(IContext.class);
    verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
    var ctx = contextCaptor.getValue();
    assertThat(ctx.getVariable("contactEmail")).isNull();
}
```

- [ ] **Step 4: Add SMTP failure resilience test**

Add these imports at the top of the file:

```java
import org.springframework.mail.MailSendException;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
```

```java
@Test
void shouldNotThrowWhenSmtpFails() {
    given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
            .willReturn("http://localhost:8080/login/magic?token=abc123");
    doThrow(new MailSendException("SMTP connection refused"))
            .when(mailSender).send(any(MimeMessage.class));

    assertThatCode(() -> emailService.sendMagicLink("user@example.com"))
            .doesNotThrowAnyException();
}
```

- [ ] **Step 5: Add token validity tests**

```java
@Test
void shouldUseSevenDayTokenValidityForMagicLink() {
    given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
            .willReturn("http://localhost:8080/login/magic?token=abc");

    emailService.sendMagicLink("user@example.com");

    var durationCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(jwtMagicLinkService).generateLink(eq("user@example.com"), durationCaptor.capture());
    assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofDays(7));
}

@Test
void shouldUseSevenDayTokenValidityForPasswordReset() {
    given(jwtMagicLinkService.generatePasswordSetupLink(eq("user@example.com"), any()))
            .willReturn("http://localhost:8080/set-password?token=abc");

    emailService.sendPasswordReset("user@example.com");

    var durationCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(jwtMagicLinkService).generatePasswordSetupLink(eq("user@example.com"), durationCaptor.capture());
    assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofDays(7));
}
```

- [ ] **Step 6: Run all SmtpEmailService tests**

Run: `mvn test -Dtest=SmtpEmailServiceTest -Dsurefire.useFile=false`
Expected: All 7 tests PASS

- [ ] **Step 7: Run the full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests PASS (431+ tests)

- [ ] **Step 8: Commit**

```bash
git add src/test/java/app/meads/identity/internal/SmtpEmailServiceTest.java
git commit -m "Add comprehensive SmtpEmailService test coverage"
```

---

## Chunk 2: Competition contactEmail

### Task 6: Add contactEmail to Competition entity

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/app/meads/competition/CompetitionTest.java`:

```java
@Test
void shouldUpdateContactEmail() {
    var competition = createCompetition();

    competition.updateContactEmail("organizer@example.com");

    assertThat(competition.getContactEmail()).isEqualTo("organizer@example.com");
}

@Test
void shouldClearContactEmail() {
    var competition = createCompetition();
    competition.updateContactEmail("organizer@example.com");

    competition.updateContactEmail(null);

    assertThat(competition.getContactEmail()).isNull();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=CompetitionTest#shouldUpdateContactEmail -Dsurefire.useFile=false`
Expected: COMPILATION FAILURE — `updateContactEmail` and `getContactEmail` do not exist

- [ ] **Step 3: Add the field and method to Competition.java**

Add to `src/main/java/app/meads/competition/Competition.java`, after the `logoContentType` field:

```java
@Column(name = "contact_email")
private String contactEmail;
```

Add the domain method after `updateLogo()`:

```java
public void updateContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
}
```

- [ ] **Step 4: Run the entity tests to verify they pass**

Run: `mvn test -Dtest=CompetitionTest -Dsurefire.useFile=false`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/app/meads/competition/Competition.java src/test/java/app/meads/competition/CompetitionTest.java
git commit -m "Add contactEmail field to Competition entity"
```

---

### Task 7: Add contactEmail to database migration and verify persistence

- [ ] **Step 1: Write the failing repository test**

Add to `src/test/java/app/meads/competition/CompetitionRepositoryTest.java`:

```java
@Test
void shouldSaveAndRetrieveCompetitionWithContactEmail() {
    var competition = new Competition("Contact Email Test", "contact-email-test",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), "Lisbon");
    competition.updateContactEmail("organizer@chip.com");

    competitionRepository.save(competition);
    var found = competitionRepository.findById(competition.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getContactEmail()).isEqualTo("organizer@chip.com");
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=CompetitionRepositoryTest#shouldSaveAndRetrieveCompetitionWithContactEmail -Dsurefire.useFile=false`
Expected: FAIL — `contact_email` column does not exist in the DB schema

- [ ] **Step 3: Update the V3 migration**

Add `contact_email` column to `src/main/resources/db/migration/V3__create_competitions_table.sql` — add this line before the closing `);`:

```sql
    contact_email  VARCHAR(255),
```

The full migration should become:

```sql
CREATE TABLE competitions (
    id                 UUID         PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    short_name         VARCHAR(100) NOT NULL UNIQUE,
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL,
    location           VARCHAR(500),
    logo               BYTEA,
    logo_content_type  VARCHAR(100),
    contact_email      VARCHAR(255),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE
);
```

- [ ] **Step 4: Run the repository tests to verify they pass**

Run: `mvn test -Dtest=CompetitionRepositoryTest -Dsurefire.useFile=false`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V3__create_competitions_table.sql src/test/java/app/meads/competition/CompetitionRepositoryTest.java
git commit -m "Add contact_email column to competitions table (V3 in-place)"
```

---

### Task 8: Add updateCompetitionContactEmail to CompetitionService

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/app/meads/competition/CompetitionServiceTest.java`.

The test class uses `@ExtendWith(MockitoExtension.class)` with `@Mock` repositories, `@InjectMocks CompetitionService`, and helper methods `createCompetition()` and `createAdmin()`. Follow this pattern:

```java
@Test
void shouldUpdateCompetitionContactEmail() {
    var admin = createAdmin();
    var competition = createCompetition();
    given(userService.findById(admin.getId())).willReturn(admin);
    given(competitionRepository.findById(competition.getId()))
            .willReturn(Optional.of(competition));
    given(competitionRepository.save(any(Competition.class)))
            .willAnswer(inv -> inv.getArgument(0));

    var result = competitionService.updateCompetitionContactEmail(
            competition.getId(), "contact@example.com", admin.getId());

    assertThat(result.getContactEmail()).isEqualTo("contact@example.com");
    then(competitionRepository).should().save(any(Competition.class));
}
```

**Note:** Ensure `import java.util.Optional;` is present. Check if `given` and `then` are already imported (they should be from existing tests).

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=CompetitionServiceTest#shouldUpdateCompetitionContactEmail -Dsurefire.useFile=false`
Expected: COMPILATION FAILURE — `updateCompetitionContactEmail` does not exist

- [ ] **Step 3: Add the method to CompetitionService**

Add to `src/main/java/app/meads/competition/CompetitionService.java`, after `updateCompetitionLogo()`:

```java
public Competition updateCompetitionContactEmail(@NotNull UUID competitionId,
                                                   @Email String contactEmail,
                                                   @NotNull UUID requestingUserId) {
    var competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
    requireAuthorized(competitionId, requestingUserId);
    competition.updateContactEmail(contactEmail);
    log.info("Updated contact email for competition: {} ({})", competitionId, contactEmail);
    return competitionRepository.save(competition);
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=CompetitionServiceTest -Dsurefire.useFile=false`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/app/meads/competition/CompetitionService.java src/test/java/app/meads/competition/CompetitionServiceTest.java
git commit -m "Add updateCompetitionContactEmail to CompetitionService"
```

---

## Chunk 3: Wire EmailService into call sites

### Task 9: Update LoginView to use EmailService

- [ ] **Step 1: Modify LoginView.java**

In `src/main/java/app/meads/identity/LoginView.java`:

Replace the `JwtMagicLinkService` field and constructor parameter with `EmailService`:

```java
// Change field from:
private final JwtMagicLinkService jwtMagicLinkService;
private final UserService userService;

// To:
private final EmailService emailService;
private final UserService userService;
```

Update constructor:

```java
public LoginView(EmailService emailService, UserService userService) {
    this.emailService = emailService;
    this.userService = userService;
    // ... rest unchanged
}
```

Replace `sendMagicLink()` method:

```java
private void sendMagicLink() {
    String emailValue = emailField.getValue();
    if (!StringUtils.hasText(emailValue) || emailField.isInvalid()) {
        emailField.setInvalid(true);
        emailField.setErrorMessage("Please enter a valid email address");
        return;
    }
    try {
        var user = userService.findByEmail(emailValue);
        if (user.getPasswordHash() != null) {
            log.info("User {} has a password — magic link not sent", emailValue);
        } else {
            emailService.sendMagicLink(emailValue);
        }
    } catch (IllegalArgumentException ex) {
        log.info("Magic link requested for non-existent email: {}", emailValue);
    }
    Notification.show("If this email is registered, a login link has been sent.");
}
```

Replace `sendPasswordResetLink()` method:

```java
private void sendPasswordResetLink() {
    String emailValue = emailField.getValue();
    if (!StringUtils.hasText(emailValue) || emailField.isInvalid()) {
        emailField.setInvalid(true);
        emailField.setErrorMessage("Please enter a valid email address");
        return;
    }
    try {
        userService.findByEmail(emailValue);
        emailService.sendPasswordReset(emailValue);
    } catch (IllegalArgumentException ex) {
        log.info("Password reset requested for non-existent email: {}", emailValue);
    }
    Notification.show("If this email is registered, a password reset link has been sent.");
}
```

Remove the `import java.time.Duration;` line and the `import app.meads.identity.JwtMagicLinkService;` line (if present — it may be implicit since both are in the same package).

- [ ] **Step 2: Run LoginView tests to verify they pass**

Run: `mvn test -Dtest=LoginViewTest -Dsurefire.useFile=false`
Expected: All tests PASS. `LoginViewTest` uses `@SpringBootTest` with real Spring context — `SmtpEmailService` is auto-wired via `EmailService` interface. No mock changes needed since the test doesn't mock `JwtMagicLinkService` directly (it uses real beans).

**If tests fail:** The constructor signature changed from `(JwtMagicLinkService, UserService)` to `(EmailService, UserService)`. Since the test uses Spring autowiring, this should be transparent. If it fails, check that no test manually constructs `LoginView`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/app/meads/identity/LoginView.java
git commit -m "Wire EmailService into LoginView, replace JwtMagicLinkService"
```

---

### Task 10: Update UserListView to use EmailService

- [ ] **Step 1: Modify UserListView.java**

In `src/main/java/app/meads/identity/internal/UserListView.java`:

Replace `JwtMagicLinkService` field and constructor parameter with `EmailService`:

Update the constructor to accept `EmailService` instead of `JwtMagicLinkService`. Read the file first to find the exact constructor signature.

Replace `sendMagicLink(User user)` method:

```java
public void sendMagicLink(User user) {
    emailService.sendMagicLink(user.getEmail());
    var notification = Notification.show("Login link sent successfully");
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
}
```

Replace `sendPasswordResetLink(User user)` method:

```java
public void sendPasswordResetLink(User user) {
    emailService.sendPasswordReset(user.getEmail());
    var notification = Notification.show("Password reset link sent successfully");
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
}
```

Replace `generatePasswordSetupLinkIfNeeded(User user)` method (around line 346):

```java
private void generatePasswordSetupLinkIfNeeded(User user) {
    if (user.getRole() == Role.SYSTEM_ADMIN && !userService.hasPassword(user.getId())) {
        emailService.sendPasswordReset(user.getEmail());
        Notification.show("Password setup link sent to " + user.getEmail());
    }
}
```

**Note:** This uses `sendPasswordReset` (not `sendPasswordSetup`) because there's no competition
context here — it's a system admin created via the admin panel. The password reset email is
appropriate: "Click to set your password."

Remove unused `JwtMagicLinkService` import and `Duration` import.

- [ ] **Step 2: Run UserListView tests to verify they pass**

Run: `mvn test -Dtest=UserListViewTest -Dsurefire.useFile=false`
Expected: All tests PASS. Like `LoginViewTest`, this uses `@SpringBootTest` with real autowiring — the constructor change from `JwtMagicLinkService` to `EmailService` is transparent.

**If tests fail:** Check if `UserListViewTest` has assertions on notification text like "check server logs" that no longer match. Update notification assertions to match the new text (e.g., "Password reset link sent successfully").

- [ ] **Step 3: Commit**

```bash
git add src/main/java/app/meads/identity/internal/UserListView.java
git commit -m "Wire EmailService into UserListView, replace JwtMagicLinkService"
```

---

### Task 11: Update CompetitionDetailView to use EmailService and add contactEmail field

- [ ] **Step 1: Modify CompetitionDetailView.java**

In `src/main/java/app/meads/competition/internal/CompetitionDetailView.java`:

**Constructor:** Replace `JwtMagicLinkService` with `EmailService`:

```java
private final EmailService emailService;
// Remove: private final JwtMagicLinkService jwtMagicLinkService;

public CompetitionDetailView(CompetitionService competitionService,
                              UserService userService,
                              EmailService emailService,
                              AuthenticationContext authenticationContext) {
    this.competitionService = competitionService;
    this.userService = userService;
    this.emailService = emailService;
    this.authenticationContext = authenticationContext;
}
```

**Replace `sendMagicLink(User user)` method:**

```java
private void sendMagicLink(User user) {
    emailService.sendMagicLink(user.getEmail());
    var notification = Notification.show("Login link sent to " + user.getEmail());
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
}
```

**Replace `generatePasswordSetupLinkIfNeeded` method:**

```java
private void generatePasswordSetupLinkIfNeeded(String email, CompetitionRole role) {
    if (role == CompetitionRole.ADMIN) {
        try {
            var user = userService.findByEmail(email);
            if (!userService.hasPassword(user.getId())) {
                emailService.sendPasswordSetup(email, competition.getName(),
                        competition.getContactEmail());
                Notification.show("Password setup link sent to " + email);
            }
        } catch (IllegalArgumentException ignored) {
            // User not found — shouldn't happen since we just added them
        }
    }
}
```

**Add contactEmail field to Settings tab** — in `createSettingsTab()`, add after the `locationField`:

```java
var contactEmailField = new EmailField("Contact Email");
contactEmailField.setValue(competition.getContactEmail() != null ? competition.getContactEmail() : "");
contactEmailField.setHelperText("Reply-to address for competition emails (optional)");
contactEmailField.setClearButtonVisible(true);
```

In the save button click listener, after the `competitionService.updateCompetition(...)` call, add:

```java
var contactEmailValue = StringUtils.hasText(contactEmailField.getValue())
        ? contactEmailField.getValue() : null;
competitionService.updateCompetitionContactEmail(
        competitionId, contactEmailValue, getCurrentUserId());
```

Add the `contactEmailField` to the tab's `add(...)` call.

Remove unused `JwtMagicLinkService` import and `Duration` import. Add `EmailService` import.

- [ ] **Step 2: Run CompetitionDetailView tests to verify they pass**

Run: `mvn test -Dtest=CompetitionDetailViewTest -Dsurefire.useFile=false`
Expected: All tests PASS. Uses `@SpringBootTest` with real autowiring.

**If tests fail:** Check notification text assertions. The old text was "Login link generated for ... (check server logs)" — new text is "Login link sent to ...". Update any assertions that match on notification text.

- [ ] **Step 3: Run the full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/app/meads/competition/internal/CompetitionDetailView.java
git commit -m "Wire EmailService into CompetitionDetailView, add contactEmail settings field"
```

---

## Chunk 4: Final verification and documentation

### Task 12: Run full test suite and verify ModulithStructure

- [ ] **Step 1: Run ModulithStructureTest**

Run: `mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false`
Expected: PASS — module boundaries still valid (`EmailService` is in identity's public API)

- [ ] **Step 2: Run full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests PASS

- [ ] **Step 3: Record test count**

Note the final test count from the output.

---

### Task 13: Update documentation

- [ ] **Step 1: Update CLAUDE.md**

In the package layout, add to the `app.meads.identity` section:

```
├── EmailService.java                    ← Interface for email delivery (public API)
```

And in `internal/`:

```
    ├── SmtpEmailService.java            ← SMTP email sender with Thymeleaf templates
```

Update the migration note: "Current highest version" stays V13 (no new migration).

- [ ] **Step 2: Update SESSION_CONTEXT.md**

Update:
- Test count
- Identity module description: mention email sending
- Competition module: mention `contactEmail` field
- Move "Email sending implementation" from "What's Next" to completed in the identity module section
- Update deployment design checklist reference (new env vars: `SPRING_MAIL_PASSWORD`, `APP_EMAIL_FROM`)
- Add `SmtpEmailServiceTest` to test file listing

- [ ] **Step 3: Update walkthrough/manual-test.md**

Add test steps for:
- Verifying Mailpit captures emails (start docker-compose, request magic link, check localhost:8025)
- Verifying contactEmail field in competition settings
- Verifying password setup email includes competition name

- [ ] **Step 4: Update deployment design doc**

Add `SPRING_MAIL_PASSWORD` and `APP_EMAIL_FROM` to the deployment configuration checklist in `docs/plans/2026-03-10-deployment-design.md`.

- [ ] **Step 5: Commit all documentation**

```bash
git add CLAUDE.md docs/SESSION_CONTEXT.md docs/walkthrough/manual-test.md docs/plans/2026-03-10-deployment-design.md
git commit -m "Update docs for email sending implementation"
```
