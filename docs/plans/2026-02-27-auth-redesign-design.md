# Auth Redesign — Design Document

**Date:** 2026-02-27
**Branch:** `auth-mechanism-decision`
**Status:** Design approved, implementation plan not yet written

---

## Problem

The current authentication uses Spring Security's One-Time Token (OTT) mechanism with
magic links. This has several issues:

1. **`/login` bug** — Spring Security's default OTT page appears instead of the Vaadin LoginView
2. **Single-use tokens** — OTT tokens are consumed on first use; the requirement is reusable links
3. **In-memory token storage** — tokens are lost on restart, not suitable for long-lived links
4. **Single auth mechanism** — the app needs different auth mechanisms for different user types
5. **No password support** — admins need a more secure, email-independent login

---

## Requirements

### User Types and Auth Mechanisms

| User type | Primary login | Fallback | Notes |
|-----------|--------------|----------|-------|
| Entrant | Magic link (reusable, valid days/weeks) | — | Created by admin or via webhook |
| Judge | Magic link (valid for competition duration) | Access code (8-char, entered on login page) | Added by admin; may also be an entrant |
| Steward | Magic link (valid for competition duration) | Access code (same as judge) | Added by admin |
| Competition Admin | Magic link | Access code | Manages one competition |
| System Admin | Password | TOTP (later) | System-wide superuser |

### Role Model

**Global roles (on User entity):**
- `USER` — base role for all non-admin users
- `SYSTEM_ADMIN` — system-wide superuser, has all capabilities

**Competition-scoped roles (future, on competition participant records):**
- `COMPETITION_ADMIN` — manages the competition
- `JUDGE` — evaluates entries
- `STEWARD` — assists during competition
- `ENTRANT` — submits entries

**Rules:**
- Global roles remain on `User.role` (single enum, unchanged)
- Competition-scoped roles live on `CompetitionParticipant` entity (competition module, not yet built)
- A user can hold different roles across different competitions
- ENTRANT + JUDGE can coexist in the same competition (hard rule: judge can't judge own entries — enforced in judging module, not auth)
- COMPETITION_ADMIN, STEWARD are exclusive with other roles in the same competition
- SYSTEM_ADMIN implicitly has all competition-level capabilities
- Authorization for competition-level actions is checked at the application layer (service methods), not via Spring Security `@RolesAllowed`

### Token Lifecycle

| Role | Valid from | Valid until |
|---|---|---|
| Entrant | Link generation | Competition results + configurable buffer (e.g., 14 days) |
| Judge/Steward | Link generation | Competition end date |
| Access code | Competition start | Competition end |

The caller (competition module) determines the duration and passes it to
`JwtMagicLinkService.generateLink(email, duration)`. For now (no competition module),
a default duration (e.g., 7 days) is used for development/testing.

### Login UX

Single `/login` page with three sections:
1. **Default:** Email field + "Send magic link" button (works for all users)
2. **Toggle/section:** "I have an access code" — email + code fields + login button
3. **Separate section:** "Admin login" — email + password fields

Unified experience after login — UI adapts based on global role and competition participations.

---

## Design

### Authentication Mechanism 1: JWT Magic Links

**Replaces:** Spring Security OTT (`oneTimeTokenLogin`, `InMemoryOneTimeTokenService`,
`MagicLinkService`, `MagicLinkLandingController`, `MagicLinkSuccessHandler`)

**JWT claims:**
- `sub` — user email (unique identifier, consistent with Spring Security username)
- `iat` — issued at
- `exp` — expiry (caller determines duration)
- `jti` — unique token ID (for optional revocation later)

**Flow:**
1. System generates magic link: `/login/magic?token=<signed-jwt>`
2. User clicks link
3. `MagicLinkAuthenticationFilter` (custom, positioned before `UsernamePasswordAuthenticationFilter`) intercepts GET `/login/magic`
4. Filter validates JWT signature + expiry
5. Loads user via `DatabaseUserDetailsService`
6. Creates `Authentication`, establishes session
7. Redirects to home/dashboard

**Properties:**
- Reusable — same link works until expiry (not single-use)
- Stateless — no server-side token storage
- Signing key — `app.auth.jwt-secret` property, configurable per environment
- Library — `jjwt` (io.jsonwebtoken)

**Key simplification vs OTT:** Current OTT flow is 4 steps (generate token → landing page →
auto-submit form → OTT filter). JWT flow is 1 step: click link → filter validates → session.
This eliminates the `/login` bug entirely.

### Authentication Mechanism 2: Access Codes

**For:** Judges and stewards (fallback when email is unavailable)

**Flow:**
1. Admin creates competition participant → system generates random 8-char alphanumeric code
2. Code stored on `CompetitionParticipant` record
3. Magic link email includes the code as text ("Click this link, or enter code `ABC123`")
4. Admin can see/regenerate codes in competition management UI
5. Judge enters email + code on login page
6. `AccessCodeAuthenticationProvider` validates via `AccessCodeValidator` interface
7. Creates `Authentication`, establishes session

**Module boundary:**
- `AccessCodeValidator` interface defined in identity module (public API)
- Implementation provided by competition module (has access to participant records)
- Provider is wired up but dormant until competition module provides the implementation

```java
// In identity module root (public API)
public interface AccessCodeValidator {
    boolean validate(String email, String code);
}
```

### Authentication Mechanism 3: Password (Admins)

**Flow:**
1. Admin account created with password (set by SYSTEM_ADMIN or during initial setup)
2. Admin enters email + password on login page
3. Standard Spring Security `DaoAuthenticationProvider` + `BCryptPasswordEncoder`
4. `DatabaseUserDetailsService` returns password hash
5. Creates `Authentication`, establishes session

**TOTP:** Deferred to a later phase. Password-only is sufficient to start.

### Security Configuration

Single `SecurityFilterChain`:

```
SecurityFilterChain
├── formLogin() → /login (admin password auth)
│   └── DaoAuthenticationProvider + BCryptPasswordEncoder
├── MagicLinkAuthenticationFilter (custom, before UsernamePasswordAuthenticationFilter)
│   └── Intercepts GET /login/magic?token=<jwt>
│   └── Validates JWT, loads user, creates Authentication
├── AccessCodeAuthenticationProvider (custom)
│   └── Handles AccessCodeAuthenticationToken (email + code)
│   └── POST /login/code
└── Vaadin integration
    └── vaadin().loginView(LoginView.class)
```

### Revocation

JWTs are stateless — no built-in individual revocation. Mitigations:
- Reasonable expiry times tied to competition lifecycle
- Admin can disable user account (DISABLED status blocks login at `DatabaseUserDetailsService` level)
- Access codes can be regenerated (invalidates old code)
- `jti` claim enables a revocation list if needed later

---

## File Changes

### Delete

| File | Reason |
|---|---|
| `MagicLinkService.java` | Replaced by `JwtMagicLinkService` |
| `MagicLinkLandingController.java` | JWT links authenticate directly, no landing page |
| `MagicLinkSuccessHandler.java` | No longer needed |
| `templates/magic-link-landing.html` | Template for deleted landing controller |

### Modify

| File | Changes |
|---|---|
| `User.java` | Add nullable `passwordHash` field, `setPasswordHash()` method |
| `SecurityConfig.java` | Rewrite: `formLogin` + JWT filter + access code provider, remove OTT |
| `DatabaseUserDetailsService.java` | Return password hash when present (instead of empty string) |
| `LoginView.java` | Rewrite: three login paths (magic link, access code, admin password) |
| `AdminInitializer.java` | Set default password hash for seeded admin account |
| `DevUserInitializer.java` | Adjust for dev magic links if needed |
| `UserService.java` | Add method to set admin password (hashed) |
| `pom.xml` | Add `jjwt` dependency |

### New

| File | Location | Purpose |
|---|---|---|
| `JwtMagicLinkService.java` | Module root (public API) | Generate and validate JWT magic links |
| `AccessCodeValidator.java` | Module root (public API) | Interface for access code validation |
| `MagicLinkAuthenticationFilter.java` | `internal/` | Spring Security filter for JWT auth |
| `AccessCodeAuthenticationProvider.java` | `internal/` | Spring Security provider for access codes |
| `AccessCodeAuthenticationToken.java` | `internal/` | Custom `Authentication` token |
| `V4__add_password_hash_to_users.sql` | `db/migration/` | Add nullable `password_hash` column |

### Test Impact

- All OTT-related tests need rewriting (magic link flow tests, landing controller tests)
- `SecurityConfig` tests need updating for new filter chain
- `LoginView` tests need rewriting for new UI
- New tests for: JWT generation/validation, magic link filter, access code provider, password auth
- `AdminInitializer` tests need updating for password seeding
- `UserActivationListener` tests should still pass (listens to generic `AuthenticationSuccessEvent`)

---

## Out of Scope

- `AccessCodeValidator` implementation (needs competition participant records)
- Competition-scoped token expiry logic (needs competition dates)
- TOTP second factor for admins
- Email delivery of magic links (still logging to console for dev)
- Password reset flow for admins
- JWT revocation list
- Competition-scoped role authorization checks

---

## Open Questions (resolved during design)

| Question | Resolution |
|---|---|
| Should JWT contain user UUID? | No — `sub` uses email (unique, consistent with Spring Security username) |
| How to handle access code validation across module boundary? | Interface in identity module, implementation in competition module |
| Should roles be enforced as mutually exclusive? | ENTRANT + JUDGE can coexist; hard rule is "judge can't judge own entries" (judging module concern) |
| Magic link implementation differ by role? | No — same implementation, different expiry passed as parameter |
| Should access code be coupled to magic link? | No — completely separate auth mechanism |

---

## Next Steps

1. Write implementation plan (use `writing-plans` skill)
2. Implement in TDD cycles per CLAUDE.md workflow
3. After identity module auth is done, competition module can implement `AccessCodeValidator`
