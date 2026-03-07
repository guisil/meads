# Password Setup & Reset — Design Document

**Date:** 2026-03-07
**Branch:** `competition-module`
**Status:** Planning
**Modules affected:** `identity` (primary), `competition` (triggers)

---

## Problem

When a user is assigned an admin role (SYSTEM_ADMIN or competition ADMIN), they need
a reliable, persistent way to log in. Currently:

- Magic links expire (not suitable as the sole credential for admins)
- Access codes are visible to other admins (security concern, but deferred)
- There is no way for a user to set or reset a password
- A user can be made competition admin without their knowledge and with no credential setup

### Scenarios

1. **New user created as SYSTEM_ADMIN** (via Users page): user has no password unless
   the creating admin happens to know one. Currently no way to set one.
2. **Existing user promoted to SYSTEM_ADMIN** (via Users page role change): user may
   have been using magic links only. No password, no way to set one.
3. **Existing or new user added as competition ADMIN** (via Participants tab): same gap.
4. **User forgets password**: no reset mechanism exists.

---

## Solution

Reuse the existing JWT magic link infrastructure to generate password-setup tokens.
Add a "Set Password" view. Trigger setup links when admin roles are assigned.

### Components

#### 1. Set Password view (`/set-password`)

- Route: `/set-password?token=...`
- `@AnonymousAllowed` (user may not be logged in yet)
- Validates the JWT token (same mechanism as magic link)
- Shows two fields: Password, Confirm Password
- On submit: sets the user's password hash, redirects to `/login`
- Token is single-use (consumed on successful password set)

#### 2. Password setup token generation

Extend `JwtMagicLinkService` (or add a parallel method) to generate tokens
specifically for password setup. These can use the same JWT structure with
a different claim or shorter expiry (e.g., 7 days).

Alternatively, reuse the exact same magic link token — the Set Password view
just needs a valid token for the user's email. The distinction between
"login link" and "setup link" is just the URL path (`/login/magic` vs
`/set-password`).

**Simplest approach:** Add a `generatePasswordSetupLink(email)` method that
returns a URL pointing to `/set-password?token=...` using the same JWT
infrastructure.

#### 3. Triggers — when to generate setup links

| Trigger | Condition | Action |
|---------|-----------|--------|
| Create user as SYSTEM_ADMIN (Users page) | User has no password | Generate + log setup link |
| Edit user role to SYSTEM_ADMIN (Users page) | User has no password | Generate + log setup link |
| Add competition ADMIN (Participants tab) | User has no password | Generate + log setup link |
| "Forgot password?" (login page) | User exists | Generate + log setup link |
| Admin clicks "Send Password Reset" (Users page) | Always | Generate + log setup link |

"Log" means writing the link to the server console (same as current magic link
behavior). Email delivery is deferred — the infrastructure is the same, just
the transport changes later.

#### 4. Login page — "Forgot password?" link

Add a "Forgot password?" link near the credentials section. Clicking it:
- Shows an email input (or reuses the existing email field)
- Generates a password setup link for that email
- Shows the same "If this email is registered..." message (no enumeration)

#### 5. Password validation

Minimum 8 characters. No composition rules (following NIST SP 800-63B).
Validated in `UserService.setPassword()` — throws `IllegalArgumentException`
if too short. The Set Password view also enforces matching confirmation field.

#### 6. User entity change

`User` already has `passwordHash` field. No schema change needed. The
`hasPassword()` check is simply `passwordHash != null`.

---

## Implementation Phases

### Phase 1: Set Password view + token generation ✅ DONE

1. ✅ Add `generatePasswordSetupLink(email, duration)` to `JwtMagicLinkService`
2. ✅ Create `SetPasswordView` at `/set-password` (`@AnonymousAllowed`)
3. ✅ Add `setPasswordByToken(token, newPassword)` to `UserService`
4. ✅ Add password validation (min 8 chars) to `UserService.setPassword()` and `setPasswordByToken()`
5. ✅ Tests: `UserServiceTest` (3 new), `JwtMagicLinkServiceTest` (1 new), `SetPasswordViewTest` (4 new)

### Phase 2: Admin role assignment triggers

1. `UserListView` — when creating/editing a user with SYSTEM_ADMIN role and no password,
   generate and log the setup link after save
2. `CompetitionDetailView` (Participants tab) — when adding a competition ADMIN and the
   user has no password, generate and log the setup link
3. `UserService.hasPassword(userId)` — public API method for views to check
4. Tests: verify link generation on role assignment

### Phase 3: Forgot password + admin-triggered reset

1. Login page: "Forgot password?" flow
2. Users page: "Send Password Reset" button (replaces or augments "Send Magic Link"
   for users with admin roles)
3. Tests: forgot password flow, admin reset trigger

---

## What This Does NOT Change

- Judge/steward/entrant auth — they keep magic links + access codes
- Access code visibility — deferred concern
- Who can add competition admins — deferred (any competition admin can still add others)
- Email delivery — still logging to console (email transport is a separate concern)
- Login mechanism — no changes to SecurityConfig, form login, or JWT filter

---

## TDD Sequence

Following the project's sequencing rules (unit → repository → module integration → UI):

**Phase 1:**
1. Unit test: `UserServiceTest` — `setPassword(token, password)` validates token, encodes, saves
2. Unit test: `JwtMagicLinkServiceTest` — `generatePasswordSetupLink` returns correct URL
3. UI test: `SetPasswordViewTest` — renders fields, submits, validates, redirects

**Phase 2:**
4. UI test: `UserListViewTest` — creating SYSTEM_ADMIN generates setup link
5. UI test: `CompetitionDetailViewTest` — adding competition ADMIN generates setup link

**Phase 3:**
6. UI test: `LoginViewTest` — forgot password flow
7. UI test: `UserListViewTest` — send password reset button
