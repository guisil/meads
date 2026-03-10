# Email Sending Design — MEADS Project

**Date:** 2026-03-10
**Status:** Approved, ready for implementation

---

## Context

All authentication links (magic login, password reset, password setup) are currently logged
to console. This design adds actual email delivery via SMTP, with HTML templates rendered
by Thymeleaf, and a dev-friendly setup using Mailpit for local email capture.

The deployment recommendation is DigitalOcean App Platform (see `2026-03-10-deployment-design.md`),
so Resend SMTP is the production email provider. The design is provider-agnostic — any SMTP
server works.

---

## Architecture

### Module placement

`EmailService` lives in the `identity` module — all email-sending call sites are either in
identity or in modules that already depend on it.

```
app.meads.identity
├── EmailService.java              ← Interface (public API)
└── internal/
    └── SmtpEmailService.java      ← Implementation (@Service, uses JavaMailSender)
```

### Dependencies

- `spring-boot-starter-mail` — `JavaMailSender` for SMTP
- `spring-boot-starter-thymeleaf` — HTML email templates (also lays groundwork for future
  PDF generation: bottle labels, scoresheets)

### Thymeleaf configuration

Thymeleaf is used **only** for email template rendering (and later PDF generation), not for
serving web views. Vaadin handles all routing. To prevent Thymeleaf's auto-configured view
resolver from interfering with Vaadin's servlet:

```properties
spring.thymeleaf.check-template-location=false
```

`SmtpEmailService` injects `SpringTemplateEngine` directly to render templates.

### Token duration

Extract scattered `Duration.ofDays(7)` literals into a private constant in `SmtpEmailService`:

```java
private static final Duration TOKEN_VALIDITY = Duration.ofDays(7);
```

All email-sending methods use this constant internally. Not exposed on the public interface —
token validity is an implementation detail.

---

## Email Types

Three email types, all sharing a single HTML template with variable slots.

### 1. Magic link (login)

- **Subject:** "Your MEADS login link"
- **Heading:** "Log in to MEADS"
- **Body:** "Click the button below to log in."
- **CTA:** "Log In" → magic link URL

### 2. Password reset

- **Subject:** "Reset your MEADS password"
- **Heading:** "Set your password"
- **Body:** "Click the button below to set a new password."
- **CTA:** "Set Password" → password setup URL

### 3. Admin password setup (competition-scoped)

- **Subject:** "Set up your MEADS admin password"
- **Heading:** "Set your admin password"
- **Body:** "You've been added as an admin for {competitionName}. Click below to set your password."
- **CTA:** "Set Password" → password setup URL
- **Footer extra:** "Questions? Contact {contactEmail}" (if present)

---

## EmailService Interface

```java
public interface EmailService {
    void sendMagicLink(String recipientEmail);
    void sendPasswordReset(String recipientEmail);
    void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail);
}
```

`SmtpEmailService` injects `JwtMagicLinkService`, `JavaMailSender`, and `SpringTemplateEngine`.
It generates the token/link internally — callers no longer deal with `Duration` or link
generation. Token validity is a private constant in the implementation.

---

## HTML Template

Single Thymeleaf template at `src/main/resources/templates/email/email-base.html`.

Table-based layout for maximum email client compatibility.

```
┌─────────────────────────────┐
│         MEADS               │  Header (text)
├─────────────────────────────┤
│                             │
│  {heading}                  │
│                             │
│  {bodyText}                 │
│                             │
│  ┌───────────────────┐      │
│  │    {ctaLabel}      │      │  Styled <a> tag (button appearance)
│  └───────────────────┘      │
│                             │
│  If link doesn't work:      │
│  {ctaUrl}                   │  Plain text fallback URL
│                             │
├─────────────────────────────┤
│  Questions? Contact         │  Only if contactEmail present
│  {contactEmail}             │
│                             │
│  MEADS — Mead Evaluation    │
│  and Awards Data System     │  Footer
└─────────────────────────────┘
```

No expiry mentioned in the email body — keeps it clean and avoids coupling to the duration
constant. Users click when they receive it; if a link expires they request a new one.

Sender identity: `MEADS <noreply@meads.app>` (configurable via `app.email.from`).

---

## Competition `contactEmail`

- **New field:** `contactEmail` (optional `String`) on `Competition` entity
- **Domain method:** `updateContactEmail(String contactEmail)`
- **Migration:** Modify existing V3 (pre-deployment) to add `contact_email VARCHAR(255)` column
- **Validation:** `@Email` in `CompetitionService` when updating (not `@NotBlank` — optional)
- **UI:** New field in `CompetitionDetailView` Settings tab
- **Email usage:** Passed to `sendPasswordSetup()` when invoked from competition context.
  Template renders "Questions? Contact {contactEmail}" footer only when non-null.

---

## Configuration & Profiles

### Properties

| Property | Dev (Mailpit) | Prod (Resend) |
|----------|---------------|---------------|
| `spring.mail.host` | `localhost` | `smtp.resend.com` |
| `spring.mail.port` | `1025` | `587` |
| `spring.mail.username` | — | `resend` |
| `spring.mail.password` | — | env var `SPRING_MAIL_PASSWORD` |
| `app.email.from` | `noreply@meads.app` | `noreply@meads.app` |

### Dev behavior

`SmtpEmailService` tries to send via Mailpit. If SMTP connection fails (Mailpit not running),
catches the exception, logs a warning + the full link to console. This preserves the current
dev workflow — email sending is best-effort in dev.

### Prod behavior

Sends via Resend SMTP. Failures logged as errors but do not crash the UI action. The admin
can resend if needed.

### Mailpit

Add to `docker-compose.yml` alongside PostgreSQL:
- SMTP on port 1025
- Web UI on port 8025 (view captured emails in browser)

---

## Call Site Changes

| Location | Before | After |
|----------|--------|-------|
| `LoginView.sendMagicLink()` | Generate link + log | `emailService.sendMagicLink(email)` |
| `LoginView.sendPasswordResetLink()` | Generate link + log | `emailService.sendPasswordReset(email)` |
| `UserListView.sendMagicLink()` | Generate link + log | `emailService.sendMagicLink(email)` |
| `UserListView.sendPasswordResetLink()` | Generate link + log | `emailService.sendPasswordReset(email)` |
| `CompetitionDetailView.sendMagicLink()` | Generate link + log | `emailService.sendMagicLink(email)` |
| `CompetitionDetailView.generatePasswordSetupLinkIfNeeded()` | Generate link + log | `emailService.sendPasswordSetup(email, compName, contactEmail)` |
| `DevUserInitializer` | Generate link + log | **No change** — keeps using `JwtMagicLinkService` directly (dev-only, uses 30-day tokens) |

Views no longer depend on `JwtMagicLinkService` directly — only `EmailService`.
`DevUserInitializer` is the exception: it runs at startup (before Mailpit may be ready),
uses a longer 30-day token validity, and is dev-profile-only. It continues to log links
to console via `JwtMagicLinkService`.

---

## Full Change Summary

| Area | Change |
|------|--------|
| `pom.xml` | Add `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf` |
| `EmailService.java` | New interface in `app.meads.identity` (public API) |
| `SmtpEmailService.java` | New impl in `app.meads.identity.internal` |
| `email-base.html` | Thymeleaf template in `src/main/resources/templates/email/` |
| `Competition.java` | Add `contactEmail` field + `updateContactEmail()` |
| `CompetitionService.java` | Support updating `contactEmail` |
| V3 migration | Add `contact_email` column to `competitions` |
| `CompetitionDetailView` | Add `contactEmail` field in Settings tab |
| `LoginView` | Replace link generation + logging with `emailService` calls |
| `UserListView` | Same |
| `CompetitionDetailView` | Same, pass competition context to `sendPasswordSetup` |
| `application.properties` | Add `app.email.from`, `spring.thymeleaf.check-template-location=false` |
| `application-dev.properties` | Mailpit SMTP config (`spring.mail.host/port`) |
| `application-prod.properties` | Resend SMTP config placeholders |
| `docker-compose.yml` | Add Mailpit service |

---

## Testing Strategy

### Unit tests

- **`SmtpEmailServiceTest`** — `@ExtendWith(MockitoExtension.class)`, mock `JavaMailSender`,
  `JwtMagicLinkService`, and `SpringTemplateEngine`. Verify:
  - Each email type calls `generateLink()` / `generatePasswordSetupLink()` with correct duration
  - `JavaMailSender.send()` is called with correct from, to, subject, and HTML body
  - Competition-scoped emails include competition name and contact email in template context
  - SMTP failure is caught and logged (not rethrown)

- **`CompetitionTest`** (existing) — add test for `updateContactEmail()` domain method

### Repository tests

- **`CompetitionRepositoryTest`** (existing) — verify `contactEmail` persists and retrieves

### UI tests

- **`LoginViewTest`** (existing) — update: mock `EmailService` instead of `JwtMagicLinkService`
- **`UserListViewTest`** (existing) — same mock swap
- **`CompetitionDetailViewTest`** (existing) — same mock swap + test `contactEmail` field in Settings tab

### Template rendering

- Thymeleaf template correctness verified implicitly through `SmtpEmailServiceTest` — the
  mocked `SpringTemplateEngine` verifies that correct variables are passed. Actual HTML
  rendering can be spot-checked via Mailpit during manual testing.

---

## Out of scope

- **i18n for email content** — deferred, will use same message bundle system when implemented
- **Logo/branding** — text-only header for now; template structure supports adding a logo later
- **Email verification** — emails are trusted (admin-created users or webhook-created)
- **Resend account setup / DNS records** — operational task, not code

---

## Design trade-offs

- **Competition context in `EmailService`:** `sendPasswordSetup()` accepts `competitionName`
  and `contactEmail` parameters, meaning the identity module's email interface has awareness
  of competition-specific context. This is a deliberate trade-off — the alternative (an
  event-based flow for sending a single email) would be over-engineered for this use case.
  The dependency direction remains valid (`competition → identity`).

---

## Ties to other priorities

- **Deployment (Priority 3):** Email config is part of the deployment env vars checklist.
  Add `SPRING_MAIL_PASSWORD` and `APP_EMAIL_FROM` to the checklist.
- **i18n (Priority 1):** Email templates will be internationalized when i18n is implemented.
  Thymeleaf supports Spring MessageSource natively.
- **Bottle labels / scoresheets (Priority 6):** Thymeleaf dependency introduced here will be
  reused for HTML-to-PDF rendering.
