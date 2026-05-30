# Manual Walkthrough / Test

Comprehensive manual test plan for MEADS. Covers every user-facing behavior and API
endpoint across identity, competition, and entry modules. Organized by workflow area
with checkboxes for progress tracking.

**Date:** 2026-05-12
**Seeded data:** Dev profile (`spring.profiles.active=dev`)

> Section 12 (Judging Module) drives Amadora through `REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING`. Section 12.18 explains how to clean up afterwards if you want Amadora to remain testable for entry-side flows; alternatively, run §12 against Amadora last or use the seeded `Test Competition 2026 > Open` division for further entry-side experiments.

---

## 1. Prerequisites

### Start the application

```bash
docker-compose up -d          # Start PostgreSQL + Mailpit
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Wait for startup to complete. Magic link emails for dev users will be sent to Mailpit.

**Mailpit web UI:** `http://localhost:8025` — all emails sent by the app are captured here,
including dev user magic links sent by `DevUserInitializer` at startup.

### Dev users

| Email | Name | Role | Status | Credential |
|-------|------|------|--------|------------|
| `admin@example.com` | Dev Admin | SYSTEM_ADMIN | ACTIVE | Password: `admin` |
| `compadmin@example.com` | Competition Admin | USER | ACTIVE | Password: `compadmin` |
| `user@example.com` | Dev User | USER | ACTIVE | Magic link (see Mailpit) |
| `pending@example.com` | Pending User | USER | PENDING | Magic link (see Mailpit) |
| `judge@example.com` | Dev Judge | USER | ACTIVE | Magic link (see Mailpit) |
| `judge2@example.com` | Dev Judge 2 | USER | ACTIVE | Magic link (see Mailpit) |
| `judge3@example.com` | Dev Judge 3 | USER | ACTIVE | Magic link (see Mailpit) |
| `judge4@example.com` | Dev Judge 4 | USER | ACTIVE | Magic link (see Mailpit) |
| `judge5@example.com` | Dev Judge 5 | USER | ACTIVE | Magic link (see Mailpit) |
| `judge6@example.com` | Dev Judge 6 | USER | ACTIVE | Magic link (see Mailpit) |
| `steward@example.com` | Dev Steward | USER | ACTIVE | Magic link (see Mailpit) |
| `entrant@example.com` | Dev Entrant | USER | ACTIVE | Magic link (see Mailpit) |
| `proentrant1@example.com` | Pro Entrant 1 | USER | ACTIVE | Magic link (see Mailpit) |
| `proentrant2@example.com` | Pro Entrant 2 | USER | ACTIVE | Magic link (see Mailpit) |
| `proentrant3@example.com` | Pro Entrant 3 | USER | ACTIVE | Magic link (see Mailpit) |
| `proentrant4@example.com` | Pro Entrant 4 | USER | ACTIVE | Magic link (see Mailpit) |

### Seeded competition data (CHIP 2026)

- **Competition:** CHIP 2026 (June 11-14, 2026, Amarante, Portugal)
- **Divisions:** Amadora (Amateur, REGISTRATION_OPEN) and Profissional (Commercial, **pre-staged at JUDGING** so an admin can jump straight into §12.6+ without the registration ramp-up)
- **Entry limits:** 3 per subcategory, 5 per main category (both divisions)
- **Entry prefixes:** Amadora = "AMA", Profissional = "PRO"
- **Categories:** Full MJP catalog minus M4B and M4D (Profissional also has JUDGING-scope categories cloned from REGISTRATION)
- **Participants:**
  - `compadmin@example.com` -- ADMIN
  - `judge@example.com` -- JUDGE (has access code; MJP cert, preferred language `pt`)
  - `judge2@example.com` -- JUDGE (meadery name "Hidroméis do Minho" — matches `user@example.com`'s meadery, **triggers soft-COI** badge on `user@`'s entries in §12.6.3)
  - `judge3@example.com` -- JUDGE + ENTRANT (1 RECEIVED entry "Judge's Secret Mead" in Amadora M1A — **triggers hard-COI** block in §12.6.3 + §12.11.3)
  - `judge4@example.com` -- JUDGE (MJP + BJCP certs, preferred `es`)
  - `judge5@example.com` -- JUDGE (OTHER cert, "WSET Level 3", preferred `it`)
  - `judge6@example.com` -- JUDGE (no profile certs, preferred `en`)
  - `steward@example.com` -- STEWARD (has access code)
  - `user@example.com` -- ENTRANT (5 credits in Amadora, meadery "Hidroméis do Minho")
  - `entrant@example.com` -- ENTRANT (3 credits in Amadora)
  - `buyer1@example.com` -- ENTRANT (2 credits in Amadora, added via webhook)
  - `buyer2@example.com` -- ENTRANT (3 credits in Profissional, added via webhook)
  - `proentrant1..4@example.com` -- ENTRANT (5 credits each in Profissional, all credits used)
- **Product mappings:** CHIP-AMA (Amadora, product ID 1001), CHIP-PRO (Profissional, product ID 1002)
- **Amadora entries (11 total — 3 DRAFT, 2 SUBMITTED, 5 RECEIVED, 1 WITHDRAWN):**
  - `user@example.com` (5): Wildflower Traditional (DRAFT, M1A), Blueberry Bliss (SUBMITTED, M2C), Oak-Aged Bochet (DRAFT, M1A), Honey Reserve (RECEIVED, M1B), Strawberry Fields (RECEIVED, M2C)
  - `entrant@example.com` (3): Lavender Metheglin (DRAFT, M3B), Rosemary & Sage (SUBMITTED, M3B), Mountain Honey (RECEIVED, M1B)
  - `buyer1@example.com` (2, admin-added): Apple Mead (RECEIVED, M4A), Sunset Mead (WITHDRAWN, M1A)
  - `judge3@example.com` (1, admin-added): Judge's Secret Mead (RECEIVED, M1A) — hard-COI seed
- **Profissional entries (20 total — all RECEIVED with final categories assigned, division at JUDGING):**
  - Split across `proentrant1..4@example.com` (5 each)
  - Final categories cover M1A (5), M1B (4), M2A (4), M2C (4), M3B (3) — enough density for medal rounds + Best of Show
- **Webhook orders:**
  - JS-1001: buyer1@example.com (Maria Silva), 2x CHIP-AMA → 2 credits in Amadora, buyer added as ENTRANT
  - JS-1002: buyer2@example.com (João Santos), 3x CHIP-PRO → 3 credits in Profissional, buyer added as ENTRANT

### Second competition (minimal)

- **Competition:** Test Competition 2026 (September 1-30, 2026, Porto, Portugal)
- **Division:** Open (MJP, DRAFT, full catalog)
- **Participants:** `compadmin@example.com` -- ADMIN

### Email types (Mailpit reference)

All emails use the Thymeleaf template `email/email-base.html` — dark header with MEADS logo,
CTA button, fallback URL, and optional contact footer.

| Trigger | Subject | Heading | CTA Label | Contact Footer |
|---------|---------|---------|-----------|----------------|
| "Get Login Link" on login page (no password) | Your MEADS login link | Log in to MEADS | Log In | No |
| "Get Login Link" on login page (has password) | MEADS login reminder | Login Reminder | None | No |
| "Forgot password?" on login page | Reset your MEADS password | Set your password | Set Password | No |
| "Password Reset" (key icon) in Users admin | Reset your MEADS password | Set your password | Set Password | No |
| New SYSTEM_ADMIN created without password | Reset your MEADS password | Set your password | Set Password | No |
| New competition ADMIN added without password | Set up your MEADS admin password | Set your admin password | Set Password | Yes (if competition has contactEmail) |
| Webhook order awards credits | [MEADS] Entry credits received — {division} | Entry Credits Received | View My Entries | Yes (if competition has contactEmail) |
| Admin manually adds credits | [MEADS] Entry credits received — {division} | Entry Credits Received | View My Entries | Yes (if competition has contactEmail) |
| Entrant submits entries | [MEADS] Entries submitted — {division} | Entries Submitted | View My Entries | No |
| Order requires manual review | [MEADS] Order requires review — {competition} | Order Requires Review | (none) | No |
| Judging round started (to each assigned judge) | [MEADS] Judging round ready — {round} | Your judging round is ready | Log in to MEADS | No |
| Submitted scoresheet reopened by an admin (to the judge who filled it) | [MEADS] Scoresheet reopened — {entry code} | A scoresheet needs your attention | Log in to MEADS | No |
| Medal round activated (to each judge covering that category) | [MEADS] Medal round ready — {category} | A medal round is ready | Log in to MEADS | No |

---

## 2. Authentication

**Covers:** `LoginViewTest`, `SetPasswordViewTest`, `AdminPasswordAuthenticationTest`,
`JwtMagicLinkAuthenticationTest`, `RootUrlRedirectTest`, `LogoutFlowTest`,
`UserActivationListenerTest`, `SecurityConfigTest`

### Password login (system admin)

- [ ] Navigate to `http://localhost:8080`
- [ ] **Expected:** Redirected to `/login`
- [ ] Enter email: `admin@example.com`
- [ ] Expand "Login with credentials" section
- [ ] Enter password: `admin`
- [ ] Click "Login"
- [ ] **Expected:** Redirected to `/competitions` (SYSTEM_ADMIN default landing page)

### Password login (competition admin)

- [ ] Log out
- [ ] Enter email: `compadmin@example.com`
- [ ] Expand "Login with credentials" section
- [ ] Enter password: `compadmin`
- [ ] Click "Login"
- [ ] **Expected:** Redirected to `/my-competitions` (competition admin default landing page)

### Magic link login

- [ ] Log out (or open incognito window)
- [ ] Navigate to `/login`
- [ ] Enter email: `user@example.com`
- [ ] Click "Get Login Link"
- [ ] **Expected:** Notification "If this email is registered, a login link has been sent."
- [ ] Open Mailpit (`http://localhost:8025`) — find the email for `user@example.com`
- [ ] **Expected:** Email with subject "Your MEADS login link", heading "Log in to MEADS", "Log In" button
- [ ] Click the "Log In" button (or copy the link from the email)
- [ ] **Expected:** Authenticated as `user@example.com`, redirected to `/my-entries` (regular user with credits)

### Credentials reminder for password user

- [ ] Log out (or open incognito window)
- [ ] Navigate to `/login`
- [ ] Enter email: `admin@example.com` (has a password)
- [ ] Click "Get Login Link"
- [ ] **Expected:** Same notification "If this email is registered, a login link has been sent."
- [ ] **Expected:** Mailpit shows "MEADS login reminder" email for `admin@example.com` (tells user to use credentials)

### Email rate limiting

- [ ] Stay on `/login` with `admin@example.com` in the email field
- [ ] Click "Get Login Link" again immediately
- [ ] **Expected:** Same notification shown, but server logs show "Rate limited: email type 'credentials-reminder' for admin@example.com" (NO second email)
- [ ] **Expected:** No second email in Mailpit
- [ ] Test with magic link user: enter `user@example.com`, click "Get Login Link" twice quickly
- [ ] **Expected:** Only one magic link email in Mailpit, second is rate-limited (5-min cooldown)

### Magic link validation (blank email)

- [ ] Navigate to `/login`
- [ ] Leave email blank, click "Get Login Link"
- [ ] **Expected:** Email field shows error "Please enter a valid email address"

### Access code login (judge)

- [ ] Log in as `compadmin@example.com` (password: `compadmin`)
- [ ] Navigate to "My Competitions" in the sidebar, click CHIP 2026 row
- [ ] Click the "Participants" tab
- [ ] Find `judge@example.com` in the grid, note the 8-character access code
- [ ] Log out
- [ ] Enter email: `judge@example.com`
- [ ] Expand "Login with credentials" section
- [ ] Enter code: the access code
- [ ] Click "Login"
- [ ] **Expected:** Authenticated as `judge@example.com`, redirected to `/`

### Access code login (steward)

- [ ] Repeat the above with `steward@example.com` and its access code
- [ ] **Expected:** Authenticated as `steward@example.com`

### Unauthenticated redirect

- [ ] Log out
- [ ] Navigate to `http://localhost:8080/users`
- [ ] **Expected:** Redirected to `/login`

### Failed login

- [ ] Enter email: `admin@example.com`
- [ ] Expand "Login with credentials" section
- [ ] Enter password: `wrong`
- [ ] Click "Login"
- [ ] **Expected:** Error notification "Invalid email or password. Please try again."

### Forgot password?

- [ ] Navigate to `/login`
- [ ] Enter email: `user@example.com`
- [ ] Expand "Login with credentials" section
- [ ] Click "Forgot password?"
- [ ] **Expected:** Notification "If this email is registered, a password reset link has been sent."
- [ ] Open Mailpit — find the email for `user@example.com`
- [ ] **Expected:** Email with subject "Reset your MEADS password", heading "Set your password", "Set Password" button
- [ ] **Expected:** Link format `http://localhost:8080/set-password?token=...`

### Forgot password? (non-existent email — no enumeration)

- [ ] Navigate to `/login`
- [ ] Enter email: `nonexistent@example.com`
- [ ] Expand "Login with credentials" section
- [ ] Click "Forgot password?"
- [ ] **Expected:** Same notification "If this email is registered, a password reset link has been sent."
- [ ] **Expected:** No email in Mailpit for `nonexistent@example.com` (user doesn't exist)

### Forgot password? (blank email)

- [ ] Navigate to `/login`
- [ ] Leave email blank
- [ ] Expand "Login with credentials" section
- [ ] Click "Forgot password?"
- [ ] **Expected:** Email field shows error "Please enter a valid email address"

### PENDING user activation on first login

- [ ] Copy the magic link for `pending@example.com` from the server startup logs
- [ ] Paste the URL in the browser
- [ ] **Expected:** Authenticated as `pending@example.com`, redirected to `/`
- [ ] Log in as `admin@example.com`, navigate to `/users`
- [ ] **Expected:** `pending@example.com` status is now ACTIVE (was PENDING before first login)

### Logout

- [ ] While logged in, click the user menu in the top navbar, then click "Logout"
- [ ] **Expected:** Redirected to `/login`
- [ ] Navigate to `http://localhost:8080/`
- [ ] **Expected:** Redirected to `/login` (session ended)

### Set Password via token link

- [ ] Log in as `admin@example.com`, navigate to `/users`
- [ ] Click "Password Reset" for a non-password user (e.g., `user@example.com`)
- [ ] Open Mailpit — find the password reset email for `user@example.com`
- [ ] Copy the "Set Password" link from the email (format: `http://localhost:8080/set-password?token=...`)
- [ ] Open the URL in a browser (can be logged out)
- [ ] **Expected:** Set Password page with info message "Once you set a password, you'll need to use your credentials to log in — login links will no longer work for your account."
- [ ] **Expected:** "Password" and "Confirm Password" fields visible
- [ ] Enter mismatched passwords → click "Set Password"
- [ ] **Expected:** "Passwords do not match" error on confirm field
- [ ] Enter a matching password shorter than 8 characters → click "Set Password"
- [ ] **Expected:** Error notification with "at least 8 characters"
- [ ] Enter a valid matching password (8+ chars) → press Enter (or click "Set Password")
- [ ] **Expected:** "Password set successfully" notification, redirected to `/login`
- [ ] Log in with the user's email and the new password
- [ ] **Expected:** Successful login

### MFA setup (SYSTEM_ADMIN)

- [ ] Log in as `admin@example.com` (password: `admin`)
- [ ] Navigate to `/profile`
- [ ] **Expected:** "Two-Factor Authentication" section visible below profile fields
- [ ] **Expected:** Status "2FA is not enabled", "Set Up 2FA" button visible
- [ ] Click "Set Up 2FA"
- [ ] **Expected:** Dialog opens with "Set Up Two-Factor Authentication" heading
- [ ] **Expected:** "Secret Key" field (read-only) with a Base32 secret (e.g. `JBSWY3DPEHPK3PXP...`)
- [ ] **Expected:** "Verification Code" field and "Enable 2FA" button
- [ ] Open your authenticator app (Google Authenticator, Authy, etc.), add account manually with the secret key
- [ ] Enter the 6-digit code from the app → click "Enable 2FA"
- [ ] **Expected:** Dialog closes, notification "Two-factor authentication enabled", page reloads
- [ ] **Expected:** Profile page now shows "2FA is enabled" and "Disable 2FA" button

### MFA login flow

- [ ] Log out
- [ ] Navigate to `/login`, enter `admin@example.com` + password `admin` → click "Login"
- [ ] **Expected:** Redirected to `/mfa` (not to `/competitions`)
- [ ] **Expected:** Page shows "Two-Factor Authentication" heading + "Verification Code" field + "Verify" button
- [ ] Enter the 6-digit code from your authenticator app
- [ ] Click "Verify"
- [ ] **Expected:** Redirected to `/competitions` (successfully authenticated)

### MFA rejection for wrong code

- [ ] Log out
- [ ] Navigate to `/login`, enter `admin@example.com` + password → click "Login"
- [ ] At `/mfa`, enter `000000` (invalid code) → click "Verify"
- [ ] **Expected:** Error notification "Invalid verification code. Please try again."
- [ ] **Expected:** Still on `/mfa` page (not redirected)

### MFA disable

- [ ] Log in as `admin@example.com` (via MFA flow)
- [ ] Navigate to `/profile`
- [ ] Click "Disable 2FA"
- [ ] **Expected:** Notification "Two-factor authentication disabled", page reloads
- [ ] **Expected:** Profile page shows "2FA is not enabled" and "Set Up 2FA" button
- [ ] Log out and log in again
- [ ] **Expected:** Redirected directly to `/competitions` (no MFA prompt)

### MFA not shown for regular users

- [ ] Log in as `user@example.com` (magic link)
- [ ] Navigate to `/profile`
- [ ] **Expected:** No "Two-Factor Authentication" section (only visible for SYSTEM_ADMIN)

### MFA email reset ("Lost your device?")

Pre-requisite: enable MFA on `admin@example.com` first (see "MFA setup" above).

- [ ] Log out. Log in as `admin@example.com` / `admin`
- [ ] **Expected:** redirected to `/mfa`
- [ ] **Expected:** A "Lost your device?" button is visible below the Verify button (small, tertiary style)
- [ ] Click "Lost your device?"
- [ ] **Expected:** notification "If your account has 2FA enabled, a reset link has been emailed to you. Check your inbox."
- [ ] **Check Mailpit:** email "Disable two-factor authentication on your MEADS account" arrives, body explains the link is valid for 1 hour, CTA button "Disable 2FA"
- [ ] Click "Lost your device?" again immediately
- [ ] **Expected:** notification still shown but no second email in Mailpit (rate-limited, 5 min cooldown)
- [ ] Click the **Disable 2FA** button in the email — opens `/mfa-reset?token=...`
- [ ] **Expected:** page shows heading "Two-Factor Authentication Disabled", body confirming the disable, "Continue to Login" button
- [ ] Click "Continue to Login" — navigates to `/login`
- [ ] Log in as `admin@example.com` / `admin`
- [ ] **Expected:** straight to `/competitions` (no `/mfa` step — MFA is now disabled)
- [ ] Navigate to `/profile` — **Expected:** "2FA is not enabled" + "Set Up 2FA" button (status reset)
- [ ] **Test invalid token:** open `/mfa-reset?token=garbage.token.here` in a fresh tab
- [ ] **Expected:** page shows heading "Reset Link Problem" + red error "The 2FA reset link is invalid or has expired. Request a new one." + Continue to Login button
- [ ] **Test missing token:** open `/mfa-reset` (no query param)
- [ ] **Expected:** forwarded to `/login` (no error shown)

---

## 3. Navigation & Layout

**Covers:** `MainLayoutTest`, `RootUrlRedirectTest`

### Main layout structure

- [ ] Log in as `admin@example.com`
- [ ] **Expected:** Top navbar shows MEADS logo (left) and user menu (user icon + `admin@example.com`, right)
- [ ] Click the user menu
- [ ] **Expected:** Dropdown opens with "My Profile" and "Logout" options
- [ ] **Expected:** Left sidebar (drawer) starts collapsed
- [ ] Click the drawer toggle (hamburger icon)
- [ ] **Expected:** Sidebar expands, shows: Competitions, Users, and version number at the bottom

### SYSTEM_ADMIN nav items

- [ ] While logged in as `admin@example.com` (SYSTEM_ADMIN)
- [ ] **Expected:** Side nav shows "Competitions", "Users" -- no "My Entries"

### Competition admin nav items

- [ ] Log out, log in as `compadmin@example.com` (competition admin, regular USER)
- [ ] **Expected:** Side nav shows "My Competitions", "My Entries" -- no "Competitions" or "Users"

### Regular user nav items

- [ ] Log out, log in as `user@example.com` (regular USER with credits, not competition admin)
- [ ] **Expected:** Side nav shows "My Entries" only -- no "Competitions", "Users", or "My Competitions"

### My Profile (user menu)

- [ ] While logged in as any user
- [ ] **Expected:** User menu (top right corner) contains "My Profile" above the logout option
- [ ] Click the user menu, then "My Profile"
- [ ] **Expected:** Navigated to `/profile`

### Profile self-edit

- [ ] **Expected:** Page title "My Profile"
- [ ] **Expected:** Fields: Email (read-only), Name, Meadery Name, Country (ComboBox), Cancel button, Save button
- [ ] **Expected:** Email shows current user's email and is not editable
- [ ] **Expected:** Name is pre-populated with current user's name
- [ ] Change Name to `Updated Name`, set Meadery Name to `Test Meadery`, select Country `Portugal`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Profile updated" (green), navigated to default page
- [ ] Navigate back to `/profile`
- [ ] **Expected:** Fields retain the saved values
- [ ] Clear Meadery Name and Country, revert Name, click "Save"
- [ ] **Expected:** Changes saved successfully (meadery name and country can be null)
- [ ] Navigate to `/profile`, click "Cancel"
- [ ] **Expected:** Navigated to default page without saving

---

## 4. User Management

**Covers:** `UserListViewTest`, `UserServiceTest`, `UserServiceValidationTest`

*Log in as `admin@example.com` for all steps.*

### User list grid

- [ ] Navigate to `/users`
- [ ] **Expected:** Page title "Users"
- [ ] **Expected:** Filter field with search icon and placeholder "Filter by email or name..."
- [ ] **Expected:** Grid with columns: Name (sortable), Email (sortable), Meadery (sortable), Country (sortable, full name localized to the current UI language), Role (sortable), Status (sortable), Actions (icon buttons)
- [ ] **Expected:** Grid contains at least 7 dev users (admin, compadmin, user, pending/active, judge, steward, entrant)
- [ ] Type a name fragment in the filter field
- [ ] **Expected:** Grid filters immediately (EAGER mode), showing only matching users
- [ ] Clear the filter
- [ ] **Expected:** All users visible again
- [ ] Click the "Email" column header
- [ ] **Expected:** Grid sorts by email (ascending/descending toggle)

### Create user -- success

- [ ] Click "Create User"
- [ ] **Expected:** Dialog with fields: Email, Name, Role (default: USER) -- no Status field (always PENDING)
- [ ] Enter email: `newuser@test.com`, name: `New User`
- [ ] Click "Save"
- [ ] **Expected:** Notification "User created successfully" (green)
- [ ] **Expected:** Grid now shows `newuser@test.com`

### Create user -- blank email

- [ ] Click "Create User"
- [ ] Leave email blank, enter name: `Test`
- [ ] Click "Save"
- [ ] **Expected:** Email field shows error "Email is required"

### Create user -- invalid email format

- [ ] Click "Create User"
- [ ] Enter email: `not-an-email`, name: `Test`
- [ ] Click "Save"
- [ ] **Expected:** Email field shows validation error

### Create user -- duplicate email

- [ ] Click "Create User"
- [ ] Enter email: `admin@example.com`, name: `Duplicate`
- [ ] Click "Save"
- [ ] **Expected:** Email field shows error "Email already exists"

### Create user -- blank name

- [ ] Click "Create User"
- [ ] Enter email: `valid@test.com`, leave name blank
- [ ] Click "Save"
- [ ] **Expected:** Name field shows error "Name is required"

### Edit user -- success

- [ ] Find `newuser@test.com` in the grid
- [ ] Click "Edit"
- [ ] **Expected:** Dialog with pre-populated fields (email read-only, name editable, meadery name, country, role, status)
- [ ] Change name to `Updated User`, set Meadery Name to `Admin Meadery`, select Country `United States`
- [ ] Click "Save"
- [ ] **Expected:** Notification "User saved successfully" (green)
- [ ] **Expected:** Grid shows updated name

### Edit user -- cancel

- [ ] Click "Edit" on any user
- [ ] Change the name
- [ ] Click "Cancel"
- [ ] **Expected:** Dialog closes, no changes saved

### Self-edit restrictions

- [ ] Click "Edit" on `admin@example.com` (yourself)
- [ ] **Expected:** Role and Status dropdowns are disabled (cannot change your own role/status)
- [ ] **Expected:** Name field is still editable

### Deactivate user (soft delete)

- [ ] Find `newuser@test.com` (status: PENDING or ACTIVE)
- [ ] **Expected:** Ban icon button with tooltip "Deactivate"
- [ ] Click the ban icon button
- [ ] **Expected:** Notification "User deactivated successfully" (green)
- [ ] **Expected:** User status changes to INACTIVE in the grid

### Hard delete -- success (no participant data)

- [ ] Find `newuser@test.com` (now INACTIVE)
- [ ] **Expected:** Trash icon button with tooltip "Delete"
- [ ] Click the trash icon button
- [ ] **Expected:** Confirmation dialog: "Are you sure you want to permanently delete user newuser@test.com? This action cannot be undone."
- [ ] Click "Confirm"
- [ ] **Expected:** Notification "User deleted successfully" (green)
- [ ] **Expected:** User removed from grid

### Hard delete -- blocked (has participant data)

- [ ] Find `buyer1@example.com` (has participant record in CHIP 2026 as ENTRANT)
- [ ] Click the ban icon (Deactivate)
- [ ] **Expected:** "User deactivated successfully" — status changes to INACTIVE
- [ ] Click the trash icon (Delete)
- [ ] Click "Confirm" in the confirmation dialog
- [ ] **Expected:** Error notification about associated data in competitions — NOT deleted
- [ ] **Expected:** User remains in the grid as INACTIVE
- [ ] Edit `buyer1@example.com`, change status back to ACTIVE, save to restore state

### Send magic link (no-password user)

- [ ] Find `user@example.com` in the grid (no password set)
- [ ] **Expected:** "Send Login Link" icon button (envelope icon) is visible
- [ ] Click the envelope icon button ("Send Login Link")
- [ ] **Expected:** Notification "Login link sent successfully" (green)
- [ ] **Expected:** Email appears in Mailpit with subject "Your MEADS login link"

### Send magic link button hidden for password users

- [ ] Find `admin@example.com` in the grid (has password)
- [ ] **Expected:** "Send Login Link" icon button (envelope) is NOT visible (only Edit, Deactivate, Password Reset icons)
- [ ] Find `compadmin@example.com` in the grid (has password)
- [ ] **Expected:** "Send Login Link" icon button (envelope) is NOT visible

### Send password reset link

- [ ] Find any user in the grid
- [ ] Click the key icon button (tooltip: "Password Reset")
- [ ] **Expected:** Notification "Password reset link sent successfully" (green)
- [ ] **Expected:** Password reset email appears in Mailpit with subject "Reset your MEADS password"

### Password setup link on SYSTEM_ADMIN creation

- [ ] Click "Create User"
- [ ] Fill: email `newadmin@test.com`, name `New Admin`, role `SYSTEM_ADMIN`
- [ ] Click "Save"
- [ ] **Expected:** "User created successfully" notification (green)
- [ ] **Expected:** "Password setup link sent successfully" notification
- [ ] **Expected:** Password reset email appears in Mailpit for `newadmin@test.com`

### Self-delete prevention

- [ ] Find `admin@example.com` (yourself)
- [ ] Click the ban icon button (tooltip: "Deactivate")
- [ ] **Expected:** Error notification (cannot deactivate your own account)

---

## 5. Competition Management

**Covers:** `CompetitionListViewTest`, `CompetitionServiceTest` (create/update/delete)

*Log in as `admin@example.com` for all steps.*

### Competition list grid

- [ ] Navigate to `/competitions`
- [ ] **Expected:** Page title "Competitions"
- [ ] **Expected:** Filter field with search icon and placeholder "Filter by name..."
- [ ] **Expected:** Grid with columns: Name (sortable), Start Date (sortable), End Date (sortable), Location (sortable), Actions (icon buttons)
- [ ] **Expected:** Grid shows "CHIP 2026" and "Test Competition 2026"
- [ ] Type "CHIP" in the filter field
- [ ] **Expected:** Grid filters immediately, showing only "CHIP 2026"
- [ ] Clear the filter
- [ ] **Expected:** All competitions visible again

### Access denied for regular user

- [ ] Log in as `user@example.com`
- [ ] Navigate to `/competitions`
- [ ] **Expected:** Redirected to `/` (root) -- not an error page

### Create competition -- success

- [ ] Log in as `admin@example.com`, navigate to `/competitions`
- [ ] Click "Create Competition"
- [ ] **Expected:** Dialog with fields: Name, Short Name, Start Date, End Date, Location, Logo upload (max 2.5 MB, PNG/JPEG)
- [ ] Enter name: `Test Comp`, short name: `test-comp`, start: tomorrow, end: next week, location: `Porto`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Competition created successfully" (green)
- [ ] **Expected:** Grid shows new competition

### Create competition -- blank name

- [ ] Click "Create Competition"
- [ ] Leave name blank, fill in dates
- [ ] Click "Save"
- [ ] **Expected:** Name field shows error "Name is required"

### Create competition -- missing dates

- [ ] Click "Create Competition"
- [ ] Enter name, leave start date blank
- [ ] Click "Save"
- [ ] **Expected:** Start date field shows error "Start date is required"

### Edit competition

- [ ] Click the pencil icon button (tooltip: "Edit") on `Test Comp`
- [ ] **Expected:** Dialog with pre-populated fields
- [ ] Change name to `Updated Comp`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Competition updated successfully" (green)

### Delete competition -- success (no divisions)

- [ ] Click the trash icon button (tooltip: "Delete") on `Updated Comp`
- [ ] **Expected:** Confirmation dialog: "Are you sure you want to delete \"Updated Comp\"?"
- [ ] Click "Delete"
- [ ] **Expected:** Notification "Competition deleted successfully" (green)
- [ ] **Expected:** Competition removed from grid

### Delete competition -- participant warning in dialog

- [ ] Create another competition: `Part Test`, short name `part-test`, dates, location
- [ ] Click into `Part Test`, go to Participants tab, add `judge@example.com` as JUDGE
- [ ] Go back to `/competitions`
- [ ] Click the trash icon on `Part Test`
- [ ] **Expected:** Confirmation dialog: "Are you sure you want to delete \"Part Test\"? This will also remove all 1 participant(s) and their roles."
- [ ] Click "Delete"
- [ ] **Expected:** Notification "Competition deleted successfully" (green) — participant cleaned up
- [ ] **Expected:** Competition removed from grid

### Delete competition -- blocked (has divisions)

- [ ] Click the trash icon button on "CHIP 2026"
- [ ] Click "Delete" in the confirmation dialog
- [ ] **Expected:** Error notification (cannot delete competition with divisions)

### Navigate to competition detail

- [ ] Click the "CHIP 2026" row in the grid
- [ ] **Expected:** Navigated to `/competitions/{shortName}` (CompetitionDetailView)

---

## 6. Competition Detail (CHIP 2026)

**Covers:** `CompetitionDetailViewTest`, `CompetitionServiceTest` (divisions, participants, settings)

*Log in as `compadmin@example.com` for all steps unless noted (competition admin, not system admin).*

### Breadcrumb and header

- [ ] Navigate to CHIP 2026 via "My Competitions" in the sidebar, click CHIP 2026 row
- [ ] **Expected:** Breadcrumb "My Competitions / CHIP 2026" (for competition admin) or "Competitions / CHIP 2026" (for SYSTEM_ADMIN)
- [ ] **Expected:** "My Competitions" (or "Competitions") is a clickable link back to the list
- [ ] **Expected:** Competition name "CHIP 2026" displayed
- [ ] **Expected:** Date range "Jun 11–14, 2026" (or similar formatted range)
- [ ] **Expected:** Location "Amarante, Portugal"

### Divisions tab

- [ ] **Expected:** Default tab is "Divisions"
- [ ] **Expected:** Grid with columns: Name, Status, Scoring, Registration Deadline (ISO format with timezone), Actions (icon buttons)
- [ ] **Expected:** "Amadora" row -- Status badge "Registration Open", Scoring "MJP"
- [ ] **Expected:** "Profissional" row -- Status badge "Registration Open", Scoring "MJP"
- [ ] **Expected:** Each row has Advance (forward icon), Revert (backwards icon, hidden for DRAFT), and Delete (trash icon) buttons
- [ ] **Expected:** Clicking a division row navigates to the division detail

### Create division

- [ ] Click "Create Division"
- [ ] **Expected:** Dialog with fields: Name, Short Name, Scoring System (default: MJP), Max Entries per Subcategory, Max Entries per Main Category, Max Total Entries, Registration Deadline (date+time picker), Timezone (combo box, default UTC)
- [ ] Enter name: `Test Division`, short name: `test-division`
- [ ] Optionally set entry limits (step buttons, clear button, helper text matching Settings tab)
- [ ] Set registration deadline to a future date/time
- [ ] Select timezone (e.g., `Europe/Lisbon`)
- [ ] Click "Save"
- [ ] **Expected:** Notification "Division created successfully" (green)
- [ ] **Expected:** New division appears in grid with status "Draft"
- [ ] Try saving without setting deadline → **Expected:** "Registration deadline is required" error on the field

### Advance division status

- [ ] Find `Test Division` (status: Draft, no categories yet)
- [ ] Click the forward icon button (tooltip: "Advance Status")
- [ ] **Expected:** Confirmation dialog: "Advance division 'Test Division' from Draft to Registration Open?"
- [ ] Click "Advance"
- [ ] **Expected:** Error notification — `RegistrationCategoryAdvanceGuard` blocks the advance because no REGISTRATION categories exist. Message: *"Cannot open registration: add at least one registration category first"* (key `error.division.cannot-open-registration-without-categories`).
- [ ] Navigate into the division → Categories tab → add at least one catalog or custom category (covered in §7).
- [ ] Return to the Divisions grid and retry "Advance Status".
- [ ] **Expected:** Notification "Status advanced successfully" (green); status badge changes to "Registration Open".

### Delete division -- success (no entries/credits/products)

- [ ] Click the trash icon button on `Test Division`
- [ ] **Expected:** Confirmation dialog with warning about removing categories
- [ ] Click "Delete"
- [ ] **Expected:** Notification "Division deleted successfully" (green)
- [ ] **Expected:** Division removed from grid

### Delete division -- blocked (has entries/credits/products)

- [ ] Click the trash icon button on `Amadora` (has entries, credits, product mappings)
- [ ] Click "Delete" in the confirmation dialog
- [ ] **Expected:** Error notification about associated data (entries, credits, or product mappings)
- [ ] **Expected:** Division remains in the grid — NOT deleted

### View division detail

- [ ] Click the "Amadora" row in the grid
- [ ] **Expected:** Navigated to `/competitions/{compShortName}/divisions/{divShortName}` (DivisionDetailView)

### Participants tab

- [ ] Click the "Participants" tab
- [ ] **Expected:** Filter field with search icon and placeholder "Filter by name or email..."
- [ ] **Expected:** Grid with columns: Name (sortable), Email (sortable), Meadery (sortable), Country (sortable), Roles (sortable), Access Code, Actions (edit pencil + envelope + remove X icons, header "Actions"). All columns resizable. One row per participant with comma-separated roles.
- [ ] **Expected:** Rows for compadmin (Admin, no code), judge (Judge, 8-char code), steward (Steward, 8-char code), user (Entrant, no code), entrant (Entrant, no code)
- [ ] **Expected:** Envelope icon (send login link) shown only for participants without passwords (magic-link-only users)
- [ ] **Expected:** Edit icon (pencil) opens dialog with role checkboxes + name/meadery/country fields
- [ ] Type a name fragment in the filter field
- [ ] **Expected:** Grid filters immediately (EAGER mode), showing only matching participants
- [ ] Clear the filter
- [ ] **Expected:** All participants visible again

### Add participant

- [ ] Click "Add Participant"
- [ ] **Expected:** Dialog with fields: Email, Role (default: Judge), Name, Meadery Name, Country
- [ ] Enter email: `newjudge@test.com`, optionally fill name/meadery/country
- [ ] Click "Add"
- [ ] **Expected:** Notification "Participant added successfully" (green)
- [ ] **Expected:** New participant appears in grid with role "Judge" and an 8-char access code
- [ ] **Expected:** Name/meadery/country only applied if user didn't already have them set

### Add participant -- blank email

- [ ] Click "Add Participant"
- [ ] Leave email blank, click "Add"
- [ ] **Expected:** Error "Email is required"

### Send login link

- [ ] Find a participant without a password (e.g., `user@example.com` or `judge@example.com`)
- [ ] Click the envelope icon (tooltip: "Send login link")
- [ ] **Expected:** Notification "Login link sent to user@example.com" (green)
- [ ] **Expected:** Login email appears in Mailpit with subject "Your MEADS login link"

### Edit participant roles

- [ ] Find a participant with one role (e.g., `judge@example.com` — Judge)
- [ ] Click the pencil icon (tooltip: "Edit")
- [ ] **Expected:** Dialog with role checkboxes (Judge checked), name/meadery/country fields (read-only if already set on user)
- [ ] Check "Entrant" checkbox (JUDGE + ENTRANT is the only allowed combination)
- [ ] Click "Save"
- [ ] **Expected:** Roles column now shows "Entrant, Judge" (comma-separated)
- [ ] Edit again, try checking "Admin" or "Steward" alongside existing roles
- [ ] **Expected:** Error notification about invalid role combination
- [ ] Uncheck "Entrant" to restore original single role, save

### Remove participant

- [ ] Find `newjudge@test.com` in the grid
- [ ] Click the X icon button (tooltip: "Remove")
- [ ] **Expected:** Confirmation dialog: "Remove newjudge@test.com from this competition?" (mentions all roles)
- [ ] Click "Remove"
- [ ] **Expected:** Notification "Participant removed" (green)
- [ ] **Expected:** Participant completely removed from grid (participant entity and all roles deleted)

### Settings tab

- [ ] Click the "Settings" tab
- [ ] **Expected:** Form with: Name, Short Name, Start Date, End Date, Location, Contact Email, Shipping Address, Phone, Website, Shared tables checkbox, Logo label ("Logo") above upload field (max 2.5 MB, PNG/JPEG), Save button
- [ ] **Expected:** Fields pre-populated with CHIP 2026 data
- [ ] **Expected:** Contact Email field with helper text "Shown in emails sent to competition participants" and clear button
- [ ] **Expected:** Shared tables checkbox label "Shared tables across divisions" + helper "When on, starting a round at e.g. \"Table 1\" locks \"Table 1\" in every other division of this competition until the round completes. Turn off if each division has its own independent physical setup." Default ON for new competitions. (Effect tested in §12.6.0.1.)
- [ ] Enter contact email: `organizer@chip.com`
- [ ] Change location to `Porto, Portugal`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Competition updated successfully" (green)
- [ ] **Expected:** Contact email is saved (refresh page to verify it persists)
- [ ] Revert location back to `Amarante, Portugal` and save

### Contact email in password setup emails

- [ ] With `organizer@chip.com` set as contact email, go to the Participants tab
- [ ] Add a new ADMIN participant: `newadmin@test.com`
- [ ] **Expected:** Notification "Participant added successfully" + "Password setup email sent to newadmin@test.com"
- [ ] Open Mailpit — find the email for `newadmin@test.com`
- [ ] **Expected:** Email with subject "Set up your MEADS admin password", mentions "CHIP 2026" in body
- [ ] **Expected:** Footer shows "Questions? Contact organizer@chip.com" with mailto link
- [ ] Remove `newadmin@test.com` from participants and clear the contact email to restore state

### Documents tab

- [ ] Click "Documents" tab (4th tab)
- [ ] **Expected:** "Add Document" button visible, empty grid

#### Add link document

- [ ] Click "Add Document"
- [ ] **Expected:** Dialog with Name, Type (PDF/Link selector defaulting to PDF), Upload component, Language dropdown (placeholder "All languages")
- [ ] Change Type to "Link"
- [ ] **Expected:** Upload component hides, URL field appears
- [ ] Enter Name: `MJP Guidelines`, URL: `https://meadjudging.com/guidelines`, leave Language as "All languages"
- [ ] Click "Save"
- [ ] **Expected:** Notification "Document added successfully" (green)
- [ ] **Expected:** Document appears in grid with Name "MJP Guidelines", Type badge "LINK", Language "All"

#### Add PDF document

- [ ] Click "Add Document"
- [ ] **Expected:** Type defaults to PDF, Upload component visible
- [ ] Enter Name: `Competition Rules`, select Language: `English`
- [ ] Upload a small test PDF (any PDF under 10 MB)
- [ ] Click "Save"
- [ ] **Expected:** Notification "Document added successfully" (green)
- [ ] **Expected:** Two documents in grid, "Competition Rules" shows Language "English"

#### Reorder documents

- [ ] Click the down arrow on "MJP Guidelines" row
- [ ] **Expected:** "MJP Guidelines" moves to second position, "Competition Rules" is first
- [ ] Click the up arrow on "MJP Guidelines" row
- [ ] **Expected:** "MJP Guidelines" moves back to first position

#### Edit document name

- [ ] Click the edit (pencil) icon on "MJP Guidelines" row
- [ ] **Expected:** Dialog with name field pre-filled with "MJP Guidelines"
- [ ] Change name to `MJP Category Guide`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Document name updated" (green), grid updated

#### Document actions

- [ ] Click the external link icon on "MJP Category Guide" row
- [ ] **Expected:** Link opens in new tab
- [ ] Click the download icon on "Competition Rules" row
- [ ] **Expected:** PDF file downloads

#### Delete document

- [ ] Click the trash icon on "Competition Rules" row
- [ ] **Expected:** Confirmation dialog "Are you sure you want to delete "Competition Rules"?"
- [ ] Click "Delete"
- [ ] **Expected:** Notification "Document deleted" (green), only "MJP Category Guide" remains

### Authorization -- regular user redirected

- [ ] Log in as `judge@example.com` (not a competition ADMIN)
- [ ] Navigate directly to `/competitions/chip-2026` (use the URL from earlier)
- [ ] **Expected:** Redirected to `/` (root) -- judge is not authorized for competition admin

---

## 7. Division Detail (Amadora)

**Covers:** `DivisionDetailViewTest`, `CompetitionServiceTest` (categories, settings, status)

*Log in as `compadmin@example.com` for all steps unless noted.*

### Header

- [ ] Navigate to Amadora division detail
- [ ] **Expected:** Header shows "CHIP 2026 — Amadora" with competition logo (if set)
- [ ] **Expected:** Status badge "Registration Open"
- [ ] **Expected:** Scoring system "MJP"

### Breadcrumb

- [ ] **Expected:** Breadcrumb shows "My Competitions / CHIP 2026 / Amadora" (or "Competitions / CHIP 2026 / Amadora" for SYSTEM_ADMIN)
- [ ] **Expected:** "My Competitions" and "CHIP 2026" are clickable links
- [ ] Click "CHIP 2026" link in the breadcrumb
- [ ] **Expected:** Navigated back to CompetitionDetailView

### Categories tab (TreeGrid)

- [ ] Navigate back to Amadora division detail
- [ ] **Expected:** Default tab is "Categories"
- [ ] **Expected:** TreeGrid with columns: Code, Name, Description, (Remove icon)
- [ ] **Expected:** Grid expands to fit content (no fixed height / empty scrollable area)
- [ ] **Expected:** Main categories as tree roots: M1 (Traditional Mead), M2 (Fruit Meads), M3 (Spiced Meads), M4 (Specialty Meads)
- [ ] Expand M1
- [ ] **Expected:** Sub-categories nested under M1: M1A, M1B, M1C
- [ ] Expand M4
- [ ] **Expected:** M4B (Historical Mead) and M4D (Honey Alcoholic Beverage) are NOT present (excluded for CHIP)
- [ ] **Expected:** "Add Category" button is enabled (status is REGISTRATION_OPEN, which allows modification)
- [ ] **Expected:** Remove buttons are X icons with "Remove" tooltip
- [ ] Hover over a long description
- [ ] **Expected:** Tooltip shows the full description text

### Add catalog category

- [ ] Click "Add Category"
- [ ] **Expected:** Dialog with two tabs: "From Catalog", "Custom"
- [ ] On "From Catalog" tab, select a category from the dropdown (e.g., M4B if available in catalog)
- [ ] Click "Add"
- [ ] **Expected:** Notification "Category added" (green)
- [ ] **Expected:** Category appears in the TreeGrid

### Add custom category

- [ ] Click "Add Category"
- [ ] Switch to the "Custom" tab
- [ ] Enter code: `X1A`, name: `Test Category`, description: `Test description`
- [ ] Optionally select a parent category
- [ ] Click "Add"
- [ ] **Expected:** Notification "Custom category added" (green)
- [ ] **Expected:** Custom category appears in the TreeGrid

### Remove category

- [ ] Find the custom category `X1A` in the grid
- [ ] Click the X icon (tooltip: "Remove")
- [ ] **Expected:** Confirmation dialog: "Remove \"X1A — Test Category\" from this division?"
- [ ] Click "Remove"
- [ ] **Expected:** Notification "Category removed" (green)
- [ ] **Expected:** Category removed from grid
- [ ] Also remove the catalog category added earlier to restore original state

### Settings tab

- [ ] Click the "Settings" tab
- [ ] **Expected:** Fields: Name, Short Name, Entry Prefix, Scoring System, Max Entries per Subcategory, Max Entries per Main Category, Max Total Entries, Meadery Name Required (checkbox), Registration Deadline (date+time picker), Timezone (combo box), Status (read-only), Save button
- [ ] **Expected:** Registration Deadline shows the seeded deadline value; Timezone shows the seeded timezone
- [ ] **Expected:** Registration Deadline and Timezone are editable (Amadora is REGISTRATION_OPEN — deadline editable in DRAFT and REGISTRATION_OPEN)
- [ ] **Expected:** Name, Short Name are always editable (regardless of status)
- [ ] **Expected:** Entry Prefix is disabled (not DRAFT — Amadora is REGISTRATION_OPEN). Only editable in DRAFT to prevent label inconsistency
- [ ] **Expected:** "Meadery Name Required" checkbox is disabled (not DRAFT — Amadora is REGISTRATION_OPEN)
- [ ] **Expected:** Entry Prefix: helper text "Short prefix for entry numbers (e.g. AMA), up to 5 characters", maxLength 5
- [ ] **Expected:** Entry limit fields have step buttons, clear button, helper text (e.g. "Per entrant per subcategory (empty = unlimited)")
- [ ] **Expected:** Entry limit fields show seeded values: 3 per subcategory, 5 per main category, 10 total
- [ ] **Expected:** Entry limit fields are disabled (not DRAFT — Amadora is REGISTRATION_OPEN)
- [ ] **Expected:** Scoring System is only editable in DRAFT status (disabled for Amadora since it's REGISTRATION_OPEN)
- [ ] **Expected:** Save button is always enabled
- [ ] Change name to `Amadora (Updated)`, click "Save"
- [ ] **Expected:** Notification "Settings saved successfully" (green)
- [ ] Revert name back to `Amadora` and save

### Manage Entries button

- [ ] **Expected:** "Manage Entries" button visible in the header area (next to "Advance Status")
- [ ] Click "Manage Entries"
- [ ] **Expected:** Navigated to `/competitions/{compShortName}/divisions/{divShortName}/entry-admin`
- [ ] Navigate back to division detail

### Advance status from division detail

- [ ] **Expected:** "Advance Status" button visible (since status is not RESULTS_PUBLISHED)
- [ ] **Do NOT click** -- this would advance Amadora beyond REGISTRATION_OPEN, affecting later tests

### Revert status from division detail

- [ ] **Expected:** "Revert Status" button visible (since status is not DRAFT)
- [ ] **Do NOT click on Amadora** -- reverting to DRAFT is blocked by the entry guard (entries exist)
- [ ] To test revert, use Test Competition 2026 > Open division (or a fresh test division):
  - Advance from DRAFT to REGISTRATION_OPEN
  - Click "Revert Status"
  - **Expected:** Confirmation dialog: "Revert from Registration Open to Draft?"
  - Click "Revert"
  - **Expected:** Status reverts to DRAFT, page reloads
  - **Expected:** "Revert Status" button is now hidden (DRAFT has no previous status)

### Revert blocked by entry guard

- [ ] Navigate to Amadora division detail (REGISTRATION_OPEN, has entries)
- [ ] Click "Revert Status"
- [ ] Click "Revert" in the confirmation dialog
- [ ] **Expected:** Error notification "Cannot revert to DRAFT: division has entries"
- [ ] **Expected:** Status remains REGISTRATION_OPEN

### Authorization -- unauthorized user redirected

- [ ] Log in as `user@example.com` (regular USER, not competition ADMIN)
- [ ] Navigate directly to `/competitions/chip-2026/divisions/amadora`
- [ ] **Expected:** Page loads (user has credits in this division, so MyEntriesView would be accessible, but DivisionDetailView requires ADMIN)
- [ ] **Note:** Check whether regular entrant can see division detail or is redirected

### Judging Categories tab (status ≥ REGISTRATION_CLOSED)

- [ ] Advance Amadora to REGISTRATION_CLOSED first (Advance Status button)
- [ ] **Expected:** TabSheet now has three tabs: Categories, Judging Categories, Settings; default selected is Judging Categories
- [ ] **Expected:** Categories tab (registration categories) is read-only — no Add or Remove buttons
- [ ] **Initialize:** Judging Categories tab shows "Initialize Judging Categories" button (no grid yet)
- [ ] **Pre-initialization Final Category check:** open Entry Admin → Entries tab → edit a SUBMITTED entry — **Expected:** Final Category Select is **disabled** with helper text "Initialize judging categories first to assign a final category". Cancel the edit dialog and return to Judging Categories tab.
- [ ] Click "Initialize Judging Categories" — **Expected:** grid populated with clones of the registration categories (same codes/names/parent hierarchy); "Add Judging Category" button appears; init button disappears
- [ ] **Categories tab no-duplicates check:** click the Categories tab — **Expected:** grid still shows each registration code exactly once (e.g. M1A appears once, not twice). The cloned JUDGING-scope rows must NOT appear in this tab.
- [ ] **Add Category dialog parent select check:** on the Categories tab, click "Add Category" → switch to the "Custom" tab inside the dialog → open the "Parent Category" select — **Expected:** each top-level code (M1, M2, M3, M4) appears exactly once (no JUDGING-scope duplicates). Cancel the dialog.
- [ ] **Add Judging Category:** dialog with Code → Name → Description → Parent (optional) fields stacked vertically; blank fields show per-field errors; successful add appears in grid
- [ ] **Remove (leaf):** X icon → "Remove \"CX1 — ...\"?" confirm → "Judging category removed" notification; row gone
- [ ] **Assign Final Category on an entry:** go to Entries tab on Entry Admin → edit a SUBMITTED entry → **Expected:** the primary **Category** dropdown lists each subcategory exactly once (no duplicates from the cloned JUDGING-scope rows). Final Category dropdown lists JUDGING-scope **leaves only** — e.g. M1A/M1B/M1C are shown but M1 (the parent) is not; a standalone custom judging category with no children IS shown. Clearable; pick one, Save; entry's Final Category column updates from "—" to the picked code
- [ ] **Deletion guard (leaf):** try to remove the judging category assigned to the entry — **Expected:** error notification "Cannot remove judging category: it is referenced by one or more entries"; row stays
- [ ] **Deletion guard (parent of referenced child):** try to remove the PARENT of the assigned judging category — **Expected:** same friendly error notification (NOT a stack trace or silent failure); row stays
- [ ] **Deletion guard (in judging use, no entries):** try to remove a judging category that has **no entries** but is already in judging use — i.e. it has a scoring/medal configuration, rounds, or awards (e.g. a category you created a medal round for) — **Expected:** friendly error *"Cannot remove this category: it is in use for judging…"* (`error.category.judging-in-use`), **not** a raw FK `DataIntegrityViolationException`. (The entry guard alone misses this case because no entry references the category.)
- [ ] **Cleanup:** clear the Final Category on the entry (set to empty, Save), then re-attempt the leaf remove → success

---

## 8. Entry Admin (Amadora)

**Covers:** `DivisionEntryAdminViewTest`, `EntryServiceTest` (credits, entries, products)

*Log in as `compadmin@example.com` for all steps.*

### Navigate to Entry Admin

- [ ] From Amadora division detail, click "Manage Entries"
- [ ] **Expected:** Breadcrumb "My Competitions / CHIP 2026 / Amadora / Entry Admin" — first 3 segments are clickable links
- [ ] **Expected:** Header shows "CHIP 2026 — Amadora — Entry Admin" with competition logo (if set)
- [ ] **Expected:** TabSheet with 4 tabs: Credits, Entries, Products, Orders

### Credits tab

- [ ] **Expected:** Default tab is "Credits"
- [ ] **Expected:** Filter field: "Filter by name or email..."
- [ ] **Expected:** Grid with columns: Name, Email, Credits, Entries, Actions (edit icon)
- [ ] **Expected:** `user@example.com` -- Credits: 5, Entries: 3
- [ ] **Expected:** `entrant@example.com` -- Credits: 3, Entries: 1
- [ ] **Expected:** `buyer1@example.com` -- Credits: 2, Entries: 0 (from webhook order)
- [ ] **Expected:** Columns are sortable
- [ ] Type in filter field to filter by name or email

### Add credits

- [ ] Click "Add Credits"
- [ ] **Expected:** Dialog with fields: Entrant Email, Amount (default: 1), footer buttons: Cancel (left), Add (right)
- [ ] Enter email: `user@example.com`, amount: `2`
- [ ] Click "Add"
- [ ] **Expected:** Notification "Credits added" (green)
- [ ] **Expected:** `user@example.com` credits now shows 7
- [ ] **Check Mailpit:** credit notification email sent to `user@example.com`, subject "[MEADS] Entry credits received — Amadora", CTA "View My Entries" is a magic link URL

### Adjust credits

- [ ] Click the edit icon (pencil) on `user@example.com` row
- [ ] **Expected:** Dialog "Adjust Credits — Dev User", field "Adjustment" (default 1), helper shows current balance
- [ ] **Expected:** Footer: Cancel (left), Save (right)
- [ ] Use positive values to add credits, negative to remove
- [ ] Click "Save" or Cancel

### Mutual exclusivity -- add credits to different division

- [ ] Navigate to Profissional division entry-admin (via CompetitionDetailView > Profissional > View > Manage Entries)
- [ ] Click "Add Credits"
- [ ] Enter email: `user@example.com`, amount: `1`
- [ ] Click "Add"
- [ ] **Expected:** Error notification -- mutual exclusivity violation (user already has credits in Amadora)

### Entries tab

- [ ] Navigate back to Amadora entry-admin
- [ ] Click the "Entries" tab
- [ ] **Expected:** Filter field: "Filter by mead name, entrant, or entry code..." + status dropdown ("All statuses")
- [ ] **Expected:** Grid with columns: Entry # (with AMA prefix, e.g. "AMA-1"), Code, Mead Name, Category (code with tooltip for full name), Final Category (code with tooltip, or "—" if not set), Entrant, Meadery, Country, Status, Actions (view/edit/←/→/withdraw/delete icons)
- [ ] **Expected:** Meadery column shows user's meadery name (or empty if not set)
- [ ] **Expected:** Country column shows the display name based on the user's ISO country code, localized to the current UI language (e.g. "Portugal" in English, "Portogallo" in Italian) — switch the language in the top-right menu to confirm the column updates
- [ ] **Expected:** 4 entries total (3 from user@example.com, 1 from entrant@example.com), sorted by entry number
- [ ] **Expected:** Wildflower Traditional and Blueberry Bliss -- Status: SUBMITTED
- [ ] **Expected:** Oak-Aged Bochet and Lavender Metheglin -- Status: DRAFT
- [ ] **Expected:** Columns are sortable
- [ ] **Expected:** Delete button (trash, rightmost) only enabled for DRAFT entries
- [ ] **Expected:** Withdraw button (ban) disabled for WITHDRAWN entries
- [ ] **Expected:** `←` (revert) button disabled for DRAFT entries; `→` (advance) button disabled for RECEIVED and WITHDRAWN entries
- [ ] **Expected:** `←` tooltip: "← Revert to Draft" for SUBMITTED/WITHDRAWN, "← Revert to Submitted" for RECEIVED
- [ ] **Expected:** `→` tooltip: "→ Submit" for DRAFT, "→ Mark as Received" for SUBMITTED
- [ ] **Expected:** Summary row below the grid shows "Credits balance: N  |  Total entries: 4 (Draft: 2, Submitted: 2, Received: 0, Withdrawn: 0)"
- [ ] **Expected:** View button (eye) opens read-only dialog showing all entry fields, status, and entrant email
- [ ] **Expected:** Edit button opens confirmation dialog ("Are you sure you want to edit this entry's data?"), then full edit dialog with all fields (mead name, category, sweetness, strength (read-only, auto-derived from ABV), ABV, carbonation, honey, other ingredients, wood aged, wood ageing details, additional info)
- [ ] **Expected:** Edit works for entries in any status except WITHDRAWN
- [ ] **Expected:** Delete button opens confirmation dialog
- [ ] **Expected:** Withdraw button opens confirmation dialog

### Advance entry status (admin)

- [ ] Find a DRAFT entry in the grid (e.g., "Oak-Aged Bochet")
- [ ] Click the `→` button on that entry
- [ ] **Expected:** Confirmation dialog titled "Submit" with message 'Submit entry AMA-{N} "Oak-Aged Bochet"?'
- [ ] Click "Cancel" to dismiss
- [ ] Click `→` again then confirm
- [ ] **Expected:** Notification "Entry status updated" (green)
- [ ] **Expected:** Entry status changes to SUBMITTED in the grid
- [ ] Click `→` on the now-SUBMITTED entry
- [ ] **Expected:** Confirmation dialog titled "Mark as Received"
- [ ] Confirm — **Expected:** Entry status changes to RECEIVED; `→` is now disabled for that entry
- [ ] **Expected:** Summary row reflects the new counts after each status change (e.g. Draft count decreases, Submitted/Received count increases)

### Revert entry status (admin)

- [ ] Find a RECEIVED entry in the grid
- [ ] Click the `←` button on that entry
- [ ] **Expected:** Confirmation dialog: "Revert to Submitted" with the entry name
- [ ] Click "Cancel" to dismiss
- [ ] Click `←` again then confirm
- [ ] **Expected:** Notification "Entry status updated" (green)
- [ ] **Expected:** Entry status changes to SUBMITTED; `←` tooltip now says "← Revert to Draft"
- [ ] Find a WITHDRAWN entry (withdraw one first if needed)
- [ ] Click `←` on the WITHDRAWN entry
- [ ] **Expected:** Confirmation dialog: "Revert to Draft"
- [ ] Confirm — **Expected:** Entry status changes to DRAFT

### Entry labels -- individual download (admin)

- [ ] **Expected:** SUBMITTED and RECEIVED entries have a download icon (download-alt) in the Actions column
- [ ] **Expected:** DRAFT and WITHDRAWN entries do NOT have a download icon
- [ ] Click the download icon on a SUBMITTED entry
- [ ] **Expected:** Browser downloads a PDF file named `label-AMA-{N}.pdf`
- [ ] **Expected:** PDF is A4 landscape with instruction header (line 1: print/attach, line 2: shipping address if set, line 3: Tel. + Web. if set) and 3 identical labels
- [ ] **Expected:** Each label shows: competition name, division name, entry ID, mead name (2-line fixed height), category code, sweetness/strength/carbonation (with field names: "Sweetness: dry | Strength: standard | Carbonation: still"), ingredients (Honey/Other/Wood, each with 2-line fixed height), QR code (left) + notes area (right), disclaimer "FREE SAMPLES. NOT FOR RESALE."

### Entry labels -- batch download (admin)

- [ ] **Expected:** "Download all labels" button exists in the Entries tab toolbar (next to filter field)
- [ ] Click "Download all labels"
- [ ] **Expected:** Confirmation dialog: "Download all labels" with message "This will generate labels for N entries. Continue?"
- [ ] **Expected:** Dialog footer has "Cancel" button and "Download" anchor/link
- [ ] Click "Cancel" to dismiss
- [ ] Click "Download all labels" again, then click "Download" in the dialog
- [ ] **Expected:** Dialog closes after clicking Download
- [ ] **Expected:** Browser downloads `all-labels.pdf` containing one page per qualifying entry (SUBMITTED + RECEIVED)
- [ ] If no qualifying entries exist, clicking the button shows notification "No submitted or received entries to generate labels for"

### Products tab

- [ ] Click the "Products" tab
- [ ] **Expected:** Grid with columns: Product ID, SKU, Product Name, Credits/Unit, Actions (edit/delete icons)
- [ ] **Expected:** Row: Product ID 1001, SKU "CHIP-AMA", Product Name "CHIP Amadora Entry", Credits/Unit 1
- [ ] **Expected:** Columns are sortable
- [ ] **Expected:** Edit opens dialog with Product Name and Credits Per Unit fields
- [ ] **Expected:** Delete opens confirmation dialog

### Add product mapping

- [ ] Click "Add Mapping"
- [ ] **Expected:** Dialog with fields: Jumpseller Product ID, SKU (optional), Product Name, Credits Per Unit (default: 1)
- [ ] Footer: Cancel (left), Add (right)
- [ ] Leave Product ID empty, click "Add"
- [ ] **Expected:** Field-level error "Product ID is required"
- [ ] Leave Product Name empty, click "Add"
- [ ] **Expected:** Field-level error "Product name is required"
- [ ] Enter product ID: `9999`, product name: `Test Product`, credits: `2`
- [ ] Click "Add"
- [ ] **Expected:** Notification "Product mapping added" (green)
- [ ] **Expected:** New mapping appears in grid

### Registration-closed guards (Credits + Products tabs)

*To test these guards, advance Amadora's status past REGISTRATION_OPEN (to REGISTRATION_CLOSED or beyond) via DivisionDetailView > Advance Status, then navigate back to Entry Admin.*

- [ ] Navigate to DivisionDetailView for Amadora and advance status to REGISTRATION_CLOSED
- [ ] Navigate to Amadora Entry Admin
- [ ] **Expected:** Credits tab: "Add Credits" button is disabled; hover shows tooltip "Registration is closed"
- [ ] **Expected:** Credits tab: Edit (adjust) icon is disabled for all rows; hover shows tooltip "Registration is closed"
- [ ] **Expected:** Products tab: "Add Mapping" button is disabled; hover shows tooltip "Registration is closed"
- [ ] **Expected:** Products tab: Edit and Delete icons are disabled for all rows; hover shows tooltip "Registration is closed"

### Admin Add Entry (Entries tab)

- [ ] Click the "Entries" tab
- [ ] **Expected:** "Add Entry" button visible in the toolbar (always enabled regardless of division status)
- [ ] Click "Add Entry"
- [ ] **Expected:** Confirmation dialog: "Add entry without consuming a credit?" with a warning message and "Add Entry"/"Cancel" buttons
- [ ] Click "Add Entry" to proceed
- [ ] **Expected:** Full entry form dialog opens with: Entrant Email, Category (subcategories only), Mead Name, Sweetness, ABV, Strength (read-only, auto-updates with ABV), Carbonation, Honey Varieties, Other Ingredients, Wood Aged checkbox, Wood Ageing Details, Additional Information
- [ ] Enter email `entrant@example.com`, fill all required fields, click "Add Entry"
- [ ] **Expected:** Notification "Entry added" (green), entry appears in grid, summary row updates
- [ ] Click the "Credits" tab — **Expected:** `entrant@example.com` row shows the new entry count (e.g. 2 instead of 1) without manual refresh. Repeat the round trip after deleting/withdrawing/reverting an entry — the Credits tab Entries column must stay in sync.
- [ ] Try entering an unknown email (e.g. `unknown@example.com`) and submitting
- [ ] **Expected:** Error notification "User not found" (or similar)
- [ ] Leave required fields empty and click "Add Entry"
- [ ] **Expected:** Field-level error messages for missing required fields
- [ ] Revert Amadora status back to REGISTRATION_OPEN after testing

### Orders tab

- [ ] Click the "Orders" tab
- [ ] **Expected:** Filter field: "Filter by order ID or customer email..."
- [ ] **Expected:** Grid with columns: Order ID, Customer (with tooltip), Status, Awarded Credits, Pending Credits, Date (ISO-8601 UTC), Review Reason (with tooltip), Note, Actions (edit icon)
- [ ] **Expected:** All columns are resizable and sortable
- [ ] **Expected:** Customer email column has tooltip showing full email on hover
- [ ] **Expected:** Review Reason column has tooltip showing full reason text on hover (useful for long reasons without resizing)
- [ ] **Expected:** 1 seeded order: JS-1001 (buyer1@example.com, PROCESSED)
- [ ] **Expected:** Edit icon opens dialog with Status (dropdown) and Admin Note fields
- [ ] **Expected:** Admin can change order status (e.g. NEEDS_REVIEW → PROCESSED after manual resolution)
- [ ] **Expected:** Orders with NEEDS_REVIEW status show review reason (e.g., "Mutual exclusivity conflict..." or "Incompatible role conflict...")

---

## 9. Webhook -- Order Paid (API)

**Covers:** `JumpsellerWebhookControllerTest`, `WebhookServiceTest`

*Not a UI test — uses curl/Postman against the running application.*

### HMAC signature helper

The webhook endpoint requires an `Jumpseller-Hmac-Sha256` header with an HMAC-SHA256
signature of the request body, computed using the hooks token as the secret key.

The dev token is configured in `application.properties` as `dev-jumpseller-hooks-token`.

To compute the signature for a given payload:

```bash
echo -n '<payload>' | openssl dgst -sha256 -hmac 'dev-jumpseller-hooks-token' -binary | base64
```

Or as a reusable shell function:

```bash
sign() { echo -n "$1" | openssl dgst -sha256 -hmac 'dev-jumpseller-hooks-token' -binary | base64; }
```

#### Alternative: Postman

1. Create a Postman environment variable `hooks_token` = `dev-jumpseller-hooks-token`
2. Add a **Pre-request Script** to automatically compute the signature:
   ```javascript
   const payload = pm.request.body.raw;
   const token = pm.environment.get("hooks_token");
   const signature = CryptoJS.HmacSHA256(payload, token).toString(CryptoJS.enc.Base64);
   pm.environment.set("hmac_signature", signature);
   ```
3. Set headers:
   - `Jumpseller-Hmac-Sha256`: `{{hmac_signature}}`
   - `Content-Type`: `application/json`
4. Method: **POST**, URL: `http://localhost:8080/api/webhooks/jumpseller/order-paid`
5. Body: **raw / JSON** — paste each test payload below
6. For the invalid signature test (9.4), temporarily replace `{{hmac_signature}}` with `deadbeef`

### Successful order -- mapped product creates credits

This uses seeded product mapping: product ID `1001` → Amadora division (SKU `CHIP-AMA`, 1 credit/unit).

```bash
PAYLOAD='{"order":{"id":"WH-001","customer":{"email":"webhooktest@example.com"},"shipping_address":{"name":"Webhook","surname":"Tester","country_code":"PT"},"products":[{"id":"1001","sku":"CHIP-AMA","name":"CHIP Amadora Entry","qty":3}]}}'
SIGNATURE=$(sign "$PAYLOAD")

curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: $SIGNATURE" \
  -d "$PAYLOAD"
```

- [ ] **Expected:** HTTP 200
- [ ] Verify in UI: Log in as `compadmin@example.com`, navigate to Amadora entry-admin
  - **Credits tab:** `webhooktest@example.com` appears with 3 credits
  - **Orders tab:** Order `WH-001` appears with status PROCESSED
- [ ] Verify user creation: Log in as `admin@example.com`, navigate to `/users`
  - **Expected:** `webhooktest@example.com` exists (PENDING status, created automatically)
- [ ] Verify country enrichment: Edit `webhooktest@example.com` in users list
  - **Expected:** Country field shows "Portugal" (enriched from webhook `shipping_address.country_code`)
- [ ] **Check Mailpit:** credit notification email sent to `webhooktest@example.com`, subject "[MEADS] Entry credits received — Amadora", body says "3 entry credits", CTA button "Continue" (magic link URL)

### Duplicate order -- idempotency

```bash
# Send the exact same payload again (same order ID "WH-001")
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: $SIGNATURE" \
  -d "$PAYLOAD"
```

- [ ] **Expected:** HTTP 200 (accepted but silently skipped)
- [ ] Verify in UI: Credits for `webhooktest@example.com` are still 3 (not doubled)

### Non-mapped product -- ignored

```bash
PAYLOAD2='{"order":{"id":"WH-002","customer":{"email":"webhooktest@example.com"},"shipping_address":{"name":"Webhook","surname":"Tester"},"products":[{"id":"9876","sku":"TSHIRT","name":"Conference T-Shirt","qty":1}]}}'
SIGNATURE2=$(sign "$PAYLOAD2")

curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: $SIGNATURE2" \
  -d "$PAYLOAD2"
```

- [ ] **Expected:** HTTP 200
- [ ] Verify in UI: Order `WH-002` does NOT appear in Amadora's Orders tab (no line items linked to this division since the product was unmapped)
- [ ] Credits for `webhooktest@example.com` remain at 3 (no credits for non-mapped products)

### Invalid signature -- rejected

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: deadbeef" \
  -d '{"order":{"id":"WH-003","customer":{"email":"test@example.com"},"shipping_address":{"name":"Test","surname":"User"},"products":[]}}'
```

- [ ] **Expected:** HTTP 401 (Unauthorized)
- [ ] Verify: No order `WH-003` in the Orders tab

### Mutual exclusivity conflict

`webhooktest@example.com` already has credits in Amadora. An order for Profissional
(product ID `1002`) in the same competition (CHIP 2026) should be flagged.

```bash
PAYLOAD3='{"order":{"id":"WH-004","customer":{"email":"webhooktest@example.com"},"shipping_address":{"name":"Webhook","surname":"Tester"},"products":[{"id":"1002","sku":"CHIP-PRO","name":"CHIP Profissional Entry","qty":1}]}}'
SIGNATURE3=$(sign "$PAYLOAD3")

curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: $SIGNATURE3" \
  -d "$PAYLOAD3"
```

- [ ] **Expected:** HTTP 200 (webhook accepted, conflict handled internally)
- [ ] Verify in UI:
  - Amadora Orders tab: No `WH-004` (this order targets Profissional)
  - Profissional entry-admin Orders tab: `WH-004` appears with status NEEDS_REVIEW
  - Profissional Credits tab: `webhooktest@example.com` does NOT appear (no credits awarded)
- [ ] **Check Mailpit:** admin alert email(s) sent to competition admin(s) for CHIP 2026, subject "[MEADS] Order requires review — CHIP 2026", body includes competition name and affected division(s)

### Mixed order -- some mapped, some conflicting

```bash
PAYLOAD4='{"order":{"id":"WH-005","customer":{"email":"newbuyer@example.com"},"shipping_address":{"name":"New","surname":"Buyer"},"products":[{"id":"1001","sku":"CHIP-AMA","name":"CHIP Amadora Entry","qty":2},{"id":"1002","sku":"CHIP-PRO","name":"CHIP Profissional Entry","qty":1}]}}'
SIGNATURE4=$(sign "$PAYLOAD4")

curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: $SIGNATURE4" \
  -d "$PAYLOAD4"
```

- [ ] **Expected:** HTTP 200
- [ ] Verify in UI:
  - Amadora Credits tab: `newbuyer@example.com` appears with 2 credits
  - Profissional Credits tab: `newbuyer@example.com` does NOT appear (mutual exclusivity conflict)
  - Amadora Orders tab: `WH-005` appears with status PARTIALLY_PROCESSED
- [ ] **Check Mailpit:** admin alert email(s) sent for PARTIALLY_PROCESSED order, body includes competition name and affected division(s)
- [ ] **Check Mailpit:** credit notification email sent to `newbuyer@example.com` for 2 credits in Amadora (the processed portion)

### Registration-closed division — flagged

Advance Amadora to REGISTRATION_CLOSED first (DivisionDetailView > Advance Status).
Then post an order targeting Amadora (product `1001`).

```bash
PAYLOAD5='{"order":{"id":"WH-006","customer":{"email":"latebuyer@example.com"},"shipping_address":{"name":"Late","surname":"Buyer"},"products":[{"id":"1001","sku":"CHIP-AMA","name":"CHIP Amadora Entry","qty":2}]}}'
SIGNATURE5=$(sign "$PAYLOAD5")

curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: $SIGNATURE5" \
  -d "$PAYLOAD5"
```

- [ ] **Expected:** HTTP 200
- [ ] Verify in UI (Amadora entry-admin):
  - **Orders tab:** `WH-006` appears with status NEEDS_REVIEW; Review Reason column / tooltip reads "Registration closed: division no longer accepting new credits"
  - **Credits tab:** `latebuyer@example.com` does NOT appear (no credits awarded)
  - **Users grid:** `latebuyer@example.com` exists (created by webhook) but is NOT a participant in CHIP 2026
- [ ] **Check Mailpit:** admin alert email sent for NEEDS_REVIEW order. No "Entry credits received" email sent to `latebuyer@example.com`
- [ ] Revert Amadora back to REGISTRATION_OPEN after testing (if entries exist this is blocked — leave closed and continue with later tests, or test on a fresh division)

---

## 10. My Entries Overview (entrant hub)

**Covers:** `EntryServiceTest` (findEntrantDivisionOverviews), `MainLayoutTest`

### Navigate as entrant (auto-redirect)

- [ ] Log in as `user@example.com` (has credits in only one division)
- [ ] **Expected:** Automatically redirected to `/competitions/chip-2026/divisions/amadora/my-entries`
- [ ] **Expected:** Breadcrumb: "My Entries / CHIP 2026 / Amadora"

### Navigate as admin

- [ ] Log in as `admin@example.com`
- [ ] Navigate to `/my-entries` directly (sidebar does not show "My Entries" for SYSTEM_ADMIN)
- [ ] **Expected:** Empty state "You have no entries in any competition." (admin has no credits)

---

## 11. My Entries (Amadora -- entrant view)

**Covers:** `MyEntriesViewTest`, `EntryServiceTest` (create/update/delete/submit entries)

### Navigate as entrant

- [ ] Log in as `user@example.com`
- [ ] Navigate via "My Entries" → click Amadora link (or go directly to `/competitions/chip-2026/divisions/amadora/my-entries`)
- [ ] **Expected:** Header shows "CHIP 2026 — Amadora — My Entries" with competition logo (if set)

### Competition documents

- [ ] **Expected:** "Competition Documents" section visible (if documents were added in section 6)
- [ ] **Expected:** Only documents matching the entrant's locale (or with no language set) appear
- [ ] **Expected:** "MJP Category Guide" appears as a clickable link (opens in new tab) — it has no language set, so it shows for all locales
- [ ] **Expected:** "Competition Rules" only appears if entrant's language matches the document's language
- [ ] If no documents were added, this section should not appear

### Credit balance display

- [ ] **Expected:** Credit info shows: "Credits: N remaining (M total, K used)"
- [ ] **Expected:** Total should be 7 (5 original + 2 added in section 8), used should be 3
- [ ] **Expected:** Limits info shows: "Limits: 10 total, 5 per main category, 3 per subcategory"

### Process info box

- [ ] **Expected:** Blue info box below credits: "Use your entry credits to add meads, then submit them when ready. Submitted entries cannot be edited. Once all credits are used and all entries are submitted, you'll receive a confirmation email with a summary of your entries."

### Registration deadline / closed notice display

- [ ] **Expected:** When division is REGISTRATION_OPEN: "Registration closes: [date]" shown below credit info (no timezone)
- [ ] **Expected:** Date format is locale-aware short format (e.g. EN: "6/30/26, 11:59 PM", PT: "30/06/2026, 23:59", PL: "30.06.2026, 23:59")
- [ ] When division is REGISTRATION_CLOSED or beyond: **Expected:** "Registration is closed" shown in red (replaces deadline text); Submit All Drafts button is disabled

### Entries grid

- [ ] **Expected:** Grid with columns: Entry #, Mead Name, Category, Final Category, Status (badge), Actions
- [ ] **Expected:** Entry # column shows prefixed format (e.g. AMA-1), narrow (~110px), Status shows styled badge (like division status)
- [ ] **Expected:** Category column shows code (e.g. M1A) with tooltip showing full category name
- [ ] **Expected:** Final Category shows "—" for all entries (not yet assigned)
- [ ] **Expected:** Actions column has icons: view (eye), edit (pencil), submit (check) — edit/submit only enabled for DRAFT
- [ ] **Expected:** All columns are resizable and sortable
- [ ] **Expected:** Filter bar above grid: text field (filter by mead name) + status dropdown (All statuses / Draft / Submitted / etc.)
- [ ] **Expected:** 3 entries:
  - Wildflower Traditional -- M1A -- Draft (badge)
  - Blueberry Bliss -- M2C -- Submitted (badge)
  - Oak-Aged Bochet -- M1A -- Draft (badge)

### Add entry -- success

- [ ] **Expected:** "Add Entry" button is enabled (remaining credits > 0)
- [ ] Click "Add Entry"
- [ ] **Expected:** Dialog (600px wide) with full-width fields: Mead Name, Category (subcategories only, shows "code — name"), category hint (initially hidden), Sweetness, ABV (%), Carbonation, Honey Varieties, Other Ingredients, Wood Aged checkbox, Additional Information. Note: Strength is NOT shown — it is auto-derived from ABV.
- [ ] Select Category: M1B
- [ ] **Expected:** Category hint appears below the dropdown: "Traditional mead: only honey (and optionally wood). Expected sweetness: Medium."
- [ ] Change Category to M2A
- [ ] **Expected:** Hint updates to: "Pome fruit melomel: apples, pears, quince."
- [ ] Fill in: Mead Name: `Spring Blossom`, Category: M1B, Sweetness: Medium, ABV: 12.0, Carbonation: Still, Honey: `Orange blossom`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Entry created" (green)
- [ ] **Expected:** New entry appears in grid with status DRAFT

### Add entry -- wood aged toggle

- [ ] Click "Add Entry"
- [ ] Check the "Wood Aged" checkbox
- [ ] **Expected:** "Wood Ageing Details" field becomes visible
- [ ] Fill in all required fields plus wood ageing details: `American oak, 3 months`
- [ ] Click "Save"
- [ ] **Expected:** Entry created successfully

### Add entry -- validation (blank mead name)

*Pre-requisite: entrant must have remaining credits. If all credits are used, add more via entry-admin Credits tab first.*

- [ ] Click "Add Entry"
- [ ] Leave mead name blank, fill in other required fields
- [ ] Click "Save"
- [ ] **Expected:** Mead name field shows error "Mead name is required"

### Add entry -- validation (missing required fields)

*Pre-requisite: entrant must have remaining credits.*

- [ ] Click "Add Entry"
- [ ] Enter only mead name, leave other required fields empty
- [ ] Click "Save"
- [ ] **Expected:** Each missing required field shows its own inline error (e.g. "Category is required", "Sweetness is required", etc.)

### View entry details

- [ ] Find "Blueberry Bliss" (SUBMITTED) in the grid
- [ ] Click the view (eye) icon in the Actions column
- [ ] **Expected:** Read-only dialog with title "Entry AMA-N — Blueberry Bliss" showing all entry fields (Mead Name, Category (code — name format), Sweetness, Strength (auto-derived from ABV), ABV, Carbonation, Honey Varieties, Status, etc.). Entry code is NOT shown (only visible to admins).
- [ ] Click "Close"

### Edit draft entry

- [ ] Find "Wildflower Traditional" (DRAFT) in the grid
- [ ] Click the edit (pencil) icon in the Actions column
- [ ] **Expected:** Dialog pre-populated with entry data, all fields full-width
- [ ] Change mead name to `Wildflower Traditional (Updated)`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Entry updated" (green)
- [ ] **Expected:** Grid shows updated name

### Submit single entry

- [ ] Find a DRAFT entry in the grid
- [ ] Click the submit (check) icon in the Actions column
- [ ] **Expected:** Confirmation dialog: "Submit entry AMA-N (Mead Name)? Submitted entries can no longer be edited."
- [ ] Click "Cancel" (don't submit yet)

### Filter and sort

- [ ] Type "Wild" in the mead name filter field
- [ ] **Expected:** Grid filters to show only entries with "Wild" in the name
- [ ] Clear the filter, select "Draft" from the status dropdown
- [ ] **Expected:** Grid shows only DRAFT entries
- [ ] Click the "Entry #" column header
- [ ] **Expected:** Entries sorted by entry number

### Submit all drafts

- [ ] **Expected:** "Submit All Drafts" button is enabled (there are DRAFT entries)
- [ ] Click "Submit All Drafts"
- [ ] **Expected:** Confirmation dialog: "Submit N draft entries? Submitted entries can no longer be edited."
- [ ] Click "Submit"
- [ ] **Expected:** Notification "N entries submitted" (green)
- [ ] **Expected:** All previously DRAFT entries now show status SUBMITTED
- [ ] **Expected:** "Submit All Drafts" button is now disabled (no more drafts)
- [ ] **Expected:** "Add Entry" button may still be enabled if credits remain
- [ ] **Check Mailpit:** If all credits are used and no drafts remain, submission confirmation email sent to entrant, subject "[MEADS] Entries submitted — Amadora", body includes per-entry summary (number, name, category) and link to MyEntriesView. If credits remain unused, NO email is sent.

### Entry labels -- individual download (entrant)

- [ ] **Expected:** SUBMITTED entries show a download icon (download-alt) in the Actions column
- [ ] **Expected:** DRAFT entries do NOT have a download icon
- [ ] Click the download icon on a SUBMITTED entry
- [ ] **Expected:** Browser downloads a PDF file named `label-AMA-{N}.pdf`
- [ ] **Expected:** PDF is A4 landscape with 3 identical labels per page

### Entry labels -- batch download (entrant)

- [ ] **Expected:** "Download all labels" button exists in the toolbar area (wrapped in an Anchor)
- [ ] If there are DRAFT entries: **Expected:** button is disabled with tooltip "Submit all draft entries before downloading labels"
- [ ] After all entries are submitted: button is enabled
- [ ] Click "Download all labels"
- [ ] **Expected:** Browser downloads `all-labels.pdf` containing one page per SUBMITTED entry
- [ ] **Expected:** No confirmation dialog for entrants (direct download)

### Meadery name required -- warning and submit blocking

*Pre-requisite: Set `meaderyNameRequired` on a division in DRAFT status, then advance to REGISTRATION_OPEN.
Or use a test division where the flag is already set.*

- [ ] Ensure your user has NO meadery name set (clear it via My Profile)
- [ ] Navigate to a division with `meaderyNameRequired = true`
- [ ] **Expected:** Warning banner at the top: "This division requires a meadery name..." with link to "My Profile"
- [ ] **Expected:** "Submit All Drafts" button is disabled
- [ ] **Expected:** Individual submit buttons (check icons) in the grid are disabled
- [ ] Click the "My Profile" link in the warning banner
- [ ] **Expected:** Navigated to `/profile`
- [ ] Set a meadery name and save
- [ ] Navigate back to the division's My Entries page
- [ ] **Expected:** Warning banner is gone
- [ ] **Expected:** "Submit All Drafts" and individual submit buttons are enabled

### Entry limit enforcement

**Subcategory limit (3 per subcategory):**

- [ ] Create entries to test the subcategory limit (max 3 per subcategory in Amadora)
- [ ] After reaching 3 non-withdrawn entries in M1A (Traditional Mead Dry), attempt to create a 4th
- [ ] **Expected:** Error "Entry limit reached for this subcategory (max 3)"
- [ ] **Edit into a full subcategory:** with M1A at the 3-entry limit, edit a DRAFT entry currently in a different subcategory (e.g. M1B or M2A) and change its Category to M1A → Save → **Expected:** the same subcategory-limit error notification; entry's category not changed.

**Main category limit (5 per main category):**

- [ ] Create entries across multiple M1 subcategories (M1A, M1B, M1C) to total 5
- [ ] Attempt to create a 6th entry under any M1 subcategory
- [ ] **Expected:** Error "Entry limit reached for this main category (max 5)"
- [ ] **Cross-main edit into a full main category:** with M1 at the 5-entry limit, edit a DRAFT entry that is currently in a DIFFERENT main category (e.g. M2A) and change its Category to any M1.x → Save → **Expected:** main-category-limit error notification; entry's category not changed.
- [ ] **Cross-subcategory edit within the same main category at the limit:** with M1 at the 5-entry limit (say 3 in M1A + 2 in M1B), edit a DRAFT entry from M1A to M1C → **Expected:** allowed (still 5 total in M1; the existing entry is being moved, not added).

---

## 12. Judging Module

**Covers:** `JudgingAdminViewTest`, `RoundViewTest`, `ScoresheetViewTest`,
`MyJudgingViewTest`, `MedalRoundViewTest`, `BosViewTest`, `JudgingServiceTest`,
`JudgingServiceMedalRoundTest`, `ScoresheetServiceTest`,
`JudgeProfileServiceTest`, `MeaderyNameNormalizerTest`, `CoiCheckServiceTest`,
`JudgingDivisionStatusRevertGuardTest`, `JudgingMinJudgesLockGuardTest`,
`JudgingErrorKeyCoverageTest`, `JudgingNotificationListenerTest`. Plus the seven aggregate repository tests
(`JudgingRepositoryTest`, `JudgingRoundRepositoryTest`,
`CategoryJudgingConfigRepositoryTest`, `ScoresheetRepositoryTest`,
`MedalAwardRepositoryTest`, `BosPlacementRepositoryTest`,
`JudgeProfileRepositoryTest`).

This section assumes Amadora has been walked through Sections 6–11 already.
The judging flow advances Amadora to JUDGING for the duration of this section
and can be reverted afterwards if you want the entry-side flows to remain
testable. Steps below are admin-driven unless noted.

### 12.1 Prerequisites — advance Amadora to REGISTRATION_CLOSED

*Log in as `compadmin@example.com`.*

- [ ] Navigate to CHIP 2026 → Amadora division detail.
- [ ] **Verify:** Current status is `REGISTRATION_OPEN`.
- [ ] Click "Advance Status" → confirm "Advance from Registration Open to Registration Closed?".
- [ ] **Expected:** Status badge updates to `REGISTRATION_CLOSED`.
- [ ] **Expected:** A new "Judging Categories" tab appears between "Categories" and "Settings".

#### 12.1.1 Advance-to-judging guard (no judging categories yet)

- [ ] Click "Advance Status" → confirm "Advance from Registration Closed to Judging?".
- [ ] **Expected:** Error notification — `JudgingCategoryAdvanceGuard` blocks because judging categories haven't been initialized yet. Message: *"Cannot start judging: initialize judging categories first"* (key `error.division.cannot-start-judging-without-categories`).
- [ ] **Expected:** Status stays at `REGISTRATION_CLOSED`.
- [ ] Leave the division at `REGISTRATION_CLOSED` — §12.4 initializes the judging categories, then §12.4.x advances to JUDGING.

### 12.2 Division Settings — judging fields

*Stay on Amadora division detail, Settings tab.*

- [ ] **Expected:** A "Judging" sub-section appears at the bottom of Settings with two `IntegerField`s:
  - **BOS places** (defaults to 1, helper text "Number of Best of Show placements awarded for this division.")
  - **Minimum judges per round** (defaults to 2, helper text "Hard minimum enforced when starting a round.")
- [ ] **Expected:** Both fields are editable at REGISTRATION_CLOSED — judging hasn't started yet so neither lock applies. BOS places locks at JUDGING (`Division.updateBosPlaces` rejects when `status.ordinal() >= JUDGING.ordinal()`); minimum judges locks once a round has `status != PENDING` (cross-module `MinJudgesPerTableLockGuard`).
- [ ] Change "BOS places" from 1 to 3, click "Save".
- [ ] **Expected:** Notification "Settings saved successfully".
- [ ] Refresh — value persists at 3.
- [ ] Change "Minimum judges per round" from 2 to 3, click "Save".
- [ ] **Expected:** Notification "Settings saved successfully".
- [ ] Refresh — value persists at 3.
- [ ] Change minimum judges back to 2 and save. (Leave BOS places at 3 for the rest of §12 — Amadora awards 3 BOS placements.)

### 12.4 Initialize judging categories

*Back on Amadora division detail → Judging Categories tab (now the default tab for status ≥ REGISTRATION_CLOSED).*

- [ ] **Expected:** Empty state with an "Initialize Judging Categories" button.
- [ ] Click "Initialize Judging Categories".
- [ ] **Expected:** All REGISTRATION-scope categories are cloned into JUDGING scope (same codes, names, descriptions, hierarchy).
- [ ] **Expected:** Grid appears with columns: Code, Name, Description, (Remove icon).
- [ ] **Expected:** "Add Judging Category" button replaces the "Initialize" button.

#### 12.4.1 Add / remove judging category

- [ ] Click "Add Judging Category" → enter Code `X9A`, Name `Test Combo`, Description `Combined for judging`, leave Parent empty → Save.
- [ ] **Expected:** Row appears in the JUDGING grid.
- [ ] Click the Remove (X) icon on `X9A` → confirm.
- [ ] **Expected:** Row removed.
- [ ] **Try:** Add a judging category with a code that already exists in the JUDGING grid (e.g. `M1A`).
- [ ] **Expected:** **Rejected** — `UNIQUE(division_id, code, scope)` blocks duplicates *within* a scope. The fact that `M1A` already exists in both REGISTRATION and JUDGING (cloned by Initialize) is the proof that the constraint allows the same code across *different* scopes.

#### 12.4.2 Stay at REGISTRATION_CLOSED — judging setup happens here

`JudgingAdminView` is accessible starting at `REGISTRATION_CLOSED`. Admins set up
rounds, judges, entries, and judging-category configs at REG_CLOSED, then advance
the division to `JUDGING` only when they're ready to actually start judging. The
`startRound` service method requires `>= JUDGING` (key
`error.round.cannot-start-before-judging`) — that's the only setup op gated to
JUDGING+; everything else on the Judging Admin view works at REG_CLOSED.

- [ ] Confirm Amadora is still at `REGISTRATION_CLOSED` — **do not** click Advance yet.
- [ ] **Expected:** A "Manage Judging" button is visible in the division header (alongside "Manage Entries"). This is new — pre-cycle 10 the button only appeared at JUDGING+. (Confirms the lowered gate; full setup walk follows in §12.6.)

### 12.5 Assign final categories to entries

For an entry to be judged it must (a) be in **RECEIVED** status — the bottle has
physically arrived and been checked in — and (b) have `finalCategoryId` set to a
JUDGING-scope category. Entries that are still SUBMITTED (bottle not arrived) or
WITHDRAWN get **no scoresheet** when a table starts (see §12.6.4).

The new `EntryFinalCategoryAdvanceGuard` (entry.internal) blocks the
`REGISTRATION_CLOSED → JUDGING` advance whenever any SUBMITTED or RECEIVED entry in
the division still has no `finalCategoryId` — admins must assign them all before
entering judging.

*Navigate to Amadora → Entry Admin → Entries tab.*

- [ ] Mark at least 2 entries as **RECEIVED** using the `→` advance arrows
  (DRAFT → SUBMITTED → RECEIVED). Leave one entry SUBMITTED (not received) so you
  can confirm it is excluded from judging.
- [ ] For each RECEIVED **and** SUBMITTED entry that you want judged: click the Edit
  (pencil) icon → confirm in the warning dialog.
- [ ] **Expected:** The edit dialog includes a "Final Category" Select (clearable, populated from JUDGING-scope categories).
- [ ] Pick a category (e.g. `M1A — Traditional Mead (Dry)`), Save.
- [ ] **Expected:** Notification "Entry updated"; Final Category column shows the chosen value.
- [ ] **Leave one RECEIVED entry without a final category** for the §12.5.1 guard rejection check.

#### 12.5.0 Bulk auto-assign final categories (convenience)

Once judging categories exist (`allowsJudgingCategoryManagement()`), the Entries
tab toolbar shows an **"Auto-assign final categories"** button (id
`auto-assign-final-categories-button`). It runs
`EntryService.assignFinalCategoriesByCode`, which sets `finalCategoryId` on every
SUBMITTED/RECEIVED entry that still has none, matching the entry's
initialCategory **code** to a JUDGING-scope category with the same code.

- [ ] On Amadora's Entry Admin → Entries tab, click **"Auto-assign final categories"**.
- [ ] **Expected:** Confirmation dialog explains the scope (SUBMITTED/RECEIVED entries only; DRAFT and WITHDRAWN skipped; existing assignments untouched). Click **"Assign now"**.
- [ ] **Expected:** Green notification "Assigned N final category/categories." with N = number actually modified.
- [ ] **Expected:** The Final Category column populates for all eligible entries whose codes match.
- [ ] Entries whose initial-category code has no matching JUDGING category remain unassigned and must be set manually via the Edit dialog.

#### 12.5.1 Advance-to-judging guard rejection (entries missing final category)

- [ ] Go back to Amadora division header. Click "Advance Status" → confirm.
- [ ] **Expected:** Error notification — `EntryFinalCategoryAdvanceGuard` blocks
  because at least one SUBMITTED/RECEIVED entry has no final category. Message
  names the count: *"Cannot start judging: {N} submitted or received entry/entries
  still have no final category. Assign them in the Entry Admin view first."* (key
  `error.division.cannot-start-judging-entries-without-final-category`).
- [ ] **Expected:** Status stays at `REGISTRATION_CLOSED`.
- [ ] Return to Entry Admin → assign the last entry's final category.
- [ ] **Do not advance to JUDGING yet** — the next step (§12.6) sets up rounds at
  REGISTRATION_CLOSED. We advance to JUDGING in §12.6.4 right before starting the
  first round.

#### 12.5.2 Defense-in-depth — the JudgingAdminView warning

The `JudgingAdminView` "{N} entries have no judging category…" warning still
exists for defense-in-depth (e.g., if a final category gets cleared during JUDGING,
or you bypass the guard by `assignFinalCategory(null)` later). It should normally
show 0 here.

- [ ] On Judging Admin, confirm no red warning line appears below the header.

### 12.6 JudgingAdminView — Rounds tab

> **⚠ The (c) round-admin + scoresheet redesign landed (v0.4.0). Some detailed
> substeps in §12.6–§12.12 below predate it — follow this summary wherever they
> differ:**
> - **Unified Rounds grid:** medal rows now carry the SAME inline action icons as
>   scoring rows — ✏ Edit · 👥 Assign Judges · 📦 Assign Entries · ▶ Start · ↶ Revert ·
>   🗑 Delete · 👁 Open. On a **COMPLETE** round, ✏ Edit and 👥 Assign Judges are
>   **disabled** (nothing left to edit; judges can't be reassigned) — only 👁 Open (and
>   ↶ Revert/Reopen where applicable) remain meaningful. The **Type** column is a colored
>   badge (`Scoring` / `Medal — Comparative` / `Medal — Score-based`). A **Status**
>   multi-select filter (every status selected by default) sits next to the Type filter.
> - **Add Round dialog:** choosing Type = MEDAL reveals a **medal-mode** Select
>   (Comparative / Score-based) — the mode is now chosen at create time. The
>   **Scheduled** field is a **date + time** picker (`yyyy-MM-dd HH:mm`), and is
>   available on medal rounds too.
> - **Scoresheet (ScoresheetView):** no "Save Draft" and no per-sheet "Submit".
>   Fields **auto-save on blur** (a "Saving…/Draft saved ✓" indicator shows); a single
>   validating **Save** button promotes the sheet DRAFT → **FILLED** (requires all 5
>   scores + each per-criterion comment ≥ 15 chars). The former "Overall comments" is
>   now the optional **"Additional comments"**.
> - **Finishing a scoring round:** a round-level **Finalize** button on RoundView
>   (judge *or* admin; enabled only when every scoresheet is FILLED) submits them all
>   and completes the round. Admins get **Reopen** on a COMPLETE round (drops its
>   sheets back to FILLED).
> - **Medal rounds:** Start + Revert live on the grid now (medal Revert clears the
>   round's awards). MedalRoundView keeps the per-row medal actions, **Finalize** (lists
>   the medals being committed **and how many entries get no medal** — finalize never
>   blocks on undecided entries; there is no Withhold), and **Reopen**. The old **Reset**
>   button is gone.

*Amadora is still at `REGISTRATION_CLOSED`. Click "Manage Judging" on Amadora
division detail. (This button is visible from REGISTRATION_CLOSED onwards — see
§12.4.2.)*

- [ ] **Expected:** URL is `/competitions/chip-2026/divisions/amadora/judging-admin`.
- [ ] **Expected:** Breadcrumb: `My Competitions / CHIP 2026 / Amadora / Judging Admin`.
- [ ] **Expected:** H2 header `CHIP 2026 — Amadora — Judging Admin` with competition logo.
- [ ] **Expected:** TabSheet with **four** tabs in this order: `Tables`, `Rounds`, `Results`, `Best of Show`. (The tab is labelled "Tables" but the underlying entity, i18n keys, grid id, and route segments are still `physical-tables*`.)
- [ ] **Expected default tab:** `computeDefaultTabIndex()` picks the tab based on state — *no tables yet* → Tables; *all rounds COMPLETE* → Results; otherwise → **Rounds**. Since the dev seed pre-creates 3 tables for Amadora, the default here is **Rounds**. Click the **Tables** tab to walk §12.6.0.

#### 12.6.0 Tables tab

A table is a fixed station within the division ("Table 1", "Table 2"). Multiple rounds can run at the same table over time, but only one round can be **active** there simultaneously. The dev seed pre-creates 3 tables for Amadora and 5 for Profissional — admins can add more.

- [ ] On the Tables tab, **Expected**: grid with columns Label, Actions. Actions column sits at the right (auto-width, flex-grow 0). Grid auto-sizes its height to fit all rows. All columns are sortable + resizable. Shows the seeded `Table 1` / `Table 2` / `Table 3` for Amadora.
- [ ] **Expected (when competition.sharedTables is ON):** A banner above the **+ Add Table** button reads "Shared tables is ON for this competition — starting a round here also locks the same-label table in other divisions." Set in the competition's Settings tab — see §11 (CompetitionDetailView Settings → "Shared tables across divisions" checkbox, default ON for new competitions).
- [ ] Click **"+ Add Table"** → enter label `Test Table` → Save → notification "Table added"; row appears.
- [ ] Edit the new row → change to `Test Table A` → Save → notification "Table updated".
- [ ] **Try** to add another with label `Test Table A` → **Expected**: error "A table named 'Test Table A' already exists in this division." (key `error.physical-table.label-duplicate`).
- [ ] Delete `Test Table A` → confirm → notification "Table deleted".

##### 12.6.0.1 Cross-division shared-tables busy-check (sharedTables=true)

`competition.sharedTables` (default `true` for new competitions) makes the busy-check span all divisions of the competition. When ON: starting a round at, say, Amadora's `Table 1` also locks Profissional's `Table 1` for as long as the round is ACTIVE. Matching is by label across the competition's per-division table records. (Turn off in competition settings if each division has its own independent physical setup.)

- [ ] Verify CHIP 2026 has Shared tables ON (Competition Detail → Settings → "Shared tables across divisions" checkbox ticked).
- [ ] On Amadora, start any scoring round at `Table 1` (set up entries + 2 judges first, advance Amadora to JUDGING per §12.4.2 / §12.6.4.0 — once Amadora has an ACTIVE round at `Table 1`, this check fires across to Profissional).
- [ ] Switch to Profissional → Manage Judging → Rounds. **Try** to start a round at Profissional's `Table 1` (any pre-staged round there).
- [ ] **Expected:** Error notification *"Table 'Table 1' is already in use by an active round in another division of this competition. Stop that round before starting this one (or turn off Shared tables in competition settings)."* (key `error.round.physical-table-busy-shared`).
- [ ] Either revert/finish the Amadora round OR turn off Shared tables, then re-try the Profissional start → **Expected:** success.
- [ ] (Note) The judge active-conflict check has always been cross-competition (uses `findAll()`); the shared-tables flag only governs physical-table label matching across divisions.

#### 12.6.1 Add a scoring round

*Switch to the **Rounds** tab.*

- [ ] **Expected:** Toolbar with a **"+ Add Round"** button, a **Type filter** ComboBox (All / Scoring / Medal; default All), and a **Status filter** `CheckboxGroup` (`rounds-status-filter`) listing PENDING / READY / ACTIVE / COMPLETE — **every status ticked by default** (unticking everything is treated as "show all"). The two filters compose.
- [ ] **Expected:** Grid columns: **Type** (a colored Lumo **badge** — `Scoring` / `Medal — Comparative` / `Medal — Score-based`), Name, **Category** (the **code only**, e.g. `M1A`; hover the cell for a tooltip with the full `code — name`), Table (label or "—"), Status, Judges (count), **Entries (count)**, **Scheduled** (`yyyy-MM-dd HH:mm`, blank if unset; column is fixed-width so the full date+time always fits), Actions. The Entries column shows how many entries are assigned (via 📦 Assign Entries).
- [ ] **Status semantics (scoring rounds):** the Status column flips automatically between `PENDING` and `READY` as configuration changes. A scoring round is `READY` when **all** of: (a) physical table assigned, (b) ≥ Minimum judges per round, (c) ≥ 1 entry assigned, (d) division is at JUDGING. Any other state shows `PENDING` — the row tells admins what is and isn't ready to start. Dynamic conflicts (table busy elsewhere, judge on another active round) are **not** part of READY — they remain Start-time errors so the message can be specific. Medal rounds use a separate `READY` semantics: the cascade flips a medal round to `READY` when every scoring round in its category COMPLETEs.
- [ ] Click "+ Add Round".
- [ ] **Expected:** Dialog with Type Select (default `SCORING`), Name text field, Category Select (filtered to JUDGING-scope categories), Table Select (populated from the Tables tab), and a **Scheduled date + time picker** (`DateTimePicker`). When Type = `MEDAL`, the Name field hides and a **Medal mode Select** (`add-round-medal-mode`: Comparative / Score-based) appears — see §12.6.8.
- [ ] Leave Type = `SCORING`, leave Name blank → Save.
- [ ] **Expected:** Inline error "Name is required" on the field.
- [ ] Enter Name = `M1A Panel`, leave Category empty → Save.
- [ ] **Expected:** Inline error "Category is required".
- [ ] Pick Category = `M1A — Traditional Mead (Dry)`, leave Table empty → Save.
- [ ] **Expected:** Inline error "Table is required."
- [ ] Pick Table = `Table 1`, set Scheduled = today + 7 days at e.g. 14:00 → Save.
- [ ] **Expected:** Notification "Round added"; row appears in the grid with Type = `Scoring`, Status = `PENDING` (not READY yet — judges + entries still missing, and Amadora is still at REGISTRATION_CLOSED).

#### 12.6.2 Edit a scoring round

- [ ] Click ✏ Edit on the new row.
- [ ] **Expected:** Dialog with Name, **Table** (a `Select` of the division's physical tables, `edit-table-physical-table`), and **Scheduled** (a date + time picker). Category is not editable after creation. (Medal rounds open the same Edit dialog — name + table + date/time.)
- [ ] **Expected (table reassignment):** the Table Select is enabled while the round is PENDING/READY and pre-selects the round's current table. Pick a different table → Save → **Expected:** the round's physical table changes (verify on the grid's Table column). Once the round is ACTIVE or later, the Select is disabled with helper text *"The table can only be changed before the round starts."* (key `judging-admin.tables.dialog.physical-table.locked`).
- [ ] Change name to `M1A Panel A`, Save.
- [ ] **Expected:** Notification "Round updated"; grid reflects new name.

#### 12.6.3 Assign judges (with COI badges)

The dev seed already pre-stages **6 judges** (`judge@`, `judge2@`, …, `judge6@`) as JUDGE participants in CHIP 2026, all with JudgeProfiles + assorted certifications. It also pre-stages a soft-COI: `judge@` + `judge2@` share meadery `Hidroméis do Minho`, matching one of `user@`'s Amadora entries. So no setup needed for the COI badges — just open the dialog.

- [ ] On Judging Admin → Rounds tab → click 👥 Assign Judges on the scoring row.
- [ ] **Expected:** Dialog with a multi-select `Grid<User>` titled `assign-judges-grid`. Columns: Name, Meadery, Country, Conflict of Interest. All 6 seeded judges visible.
- [ ] **Expected (soft COI):** rows for `judge@` and `judge2@` show an orange **"Similar meadery to entry #N"** badge for `user@`'s M1A entry.
- [ ] **Expected (hard COI):** if any seeded judge happens to have a self-entry in M1A, a red **"Self-entry — cannot judge"** badge appears. (Not in the default seed — to exercise this manually you'd add credits to a judge + submit + assign final category M1A.) Attempting to tick the hard-COI row reverts the selection and shows error *"Judge "{name}" cannot be assigned: they own entry #{N} in this category."* (key `error.coi.assign-hard-block`). The service rejects the same way as a defense-in-depth check if the UI is bypassed.
- [ ] Select 2 judges (e.g. `judge3@` + `judge4@` — they have no COI on M1A), click Save.
- [ ] **Expected:** Notification "Judge assignments updated"; row's `Judges` count shows `2`.
- [ ] **Try:** Open dialog again and uncheck both judges → Save.
- [ ] **Expected:** Notification; count goes back to 0.
- [ ] Re-select both judges before continuing.

#### 12.6.4 Start a scoring round

Scoring rounds require an **explicit entry assignment** before they can start. Use the 📦 Assign Entries button on the row to pick the entries this round will judge (typically all RECEIVED entries with `finalCategoryId = M1A`, but split-category scenarios have a subset — see §12.6.7.1). The earlier "all entries with matching final category" auto-populate fallback was removed in cycle 9 — admin must opt in.

- [ ] Click 📦 **Assign Entries** on the row → multi-select grid → pick at least 1 entry → Save → notification *"Entry assignments updated"*.
- [ ] (Try) Click ▶ **Start** without first assigning entries on a new round → **Expected:** error *"Assign at least one entry to this round before starting it. Use the Assign Entries button."*

##### 12.6.4.0 Start is gated to JUDGING — advance Amadora now

Amadora is still at `REGISTRATION_CLOSED`. `startRound` is the one judging op that
requires `DivisionStatus >= JUDGING`; everything else (rounds, judges, entries,
medal-round setup) is fine at REG_CLOSED.

- [ ] (Try) Click ▶ **Start** on a round with entries assigned while still at REG_CLOSED.
- [ ] **Expected:** Error notification *"Advance the division to Judging before starting rounds. Setup is allowed at Registration Closed; starting is not."* (key `error.round.cannot-start-before-judging`). Status stays `PENDING`.
- [ ] Navigate back to the Amadora division header (open in a new tab or use the breadcrumb) → click "Advance Status" → confirm "Advance from Registration Closed to Judging?".
- [ ] **Expected:** Status badge updates to `JUDGING` (both the `JudgingCategoryAdvanceGuard` and `EntryFinalCategoryAdvanceGuard` are satisfied — judging categories exist (§12.4) and every SUBMITTED/RECEIVED entry has a final category (§12.5)).
- [ ] Return to the Judging Admin view → continue below.

- [ ] Click ▶ Start on the round (with entries assigned).
- [ ] **Expected:** Confirmation dialog body explains scoresheet creation.
- [ ] Click Start.
- [ ] **Expected:** Notification "Round started"; Status column changes from `PENDING` to `ACTIVE`.
- [ ] (Try starting a *second* scoring round at the same table — Add Round → pick a different category, same Table `Table 1`, assign 2 judges → Start. **Expected**: error "This table already has an active round…")
- [ ] (Try assigning one of `M1A Panel`'s judges to a new round at a *different* table, then start that new round. **Expected**: error "One or more assigned judges … are already on another active round.")
- [ ] **Expected:** A scoresheet is auto-created per **RECEIVED** entry in the round's category — SUBMITTED-but-not-received and WITHDRAWN entries are skipped.
- [ ] **Expected:** ▶ Start button becomes disabled (already started).
- [ ] **Expected:** 🗑 Delete button is disabled with tooltip "Cannot delete a started round or one with assigned judges".
- [ ] **Check Mailpit:** each assigned judge receives a "Judging round ready" email, subject "[MEADS] Judging round ready — {round}", heading "Your judging round is ready", body names the round, category, division and competition, CTA button "Log in to MEADS" (magic link). `JudgingNotificationListener` handles `RoundStartedEvent`.

#### 12.6.4.1 Revert an ACTIVE scoring round (mistake correction)

An ACTIVE scoring row exposes a ↶ **Revert** button (between Assign Entries and Delete). It returns the round to `READY` and deletes every BLANK scoresheet, so the admin can fix a mistake (wrong table started, wrong entries assigned, wrong judges) and start again. It is blocked as soon as **any** judge has touched a scoresheet — DRAFT (judge saved at least once) or SUBMITTED (key `error.round.cannot-revert-touched-scoresheets`) — because that content represents real judging work that revert would destroy. To clear the block, admins delete each touched scoresheet via the per-row 🗑 button on the round drill-in first, then revert.

- [ ] On the ACTIVE `M1A Panel A` row, **Expected:** ↶ Revert button is enabled (tooltip: *"Revert"*); PENDING/READY/COMPLETE rows show the button disabled (tooltip: *"Only ACTIVE rounds can be reverted."*).
- [ ] Click ↶ **Revert** → confirmation dialog *"Revert round M1A Panel A?"*, body warns it returns the round to READY + deletes drafts + only for mistake correction.
- [ ] Click **Revert** to confirm.
- [ ] **Expected:** Notification *"Round reverted"*; row's Status flips `ACTIVE` → `READY`; draft scoresheets are gone (verify on Round drill-in if curious).
- [ ] ▶ Start the round again to put it back into ACTIVE for the rest of §12.6 — entries/judges/table are still assigned, so it starts straight away.
- [ ] **(Optional — verify the touched-scoresheets guard.)** Skip this for now and revisit after §12.10–§12.11: once any judge has saved scores on a scoresheet (DRAFT) or submitted one, try ↶ Revert. **Expected:** error *"Cannot revert: {N} scoresheet(s) have judging work in progress (saved as draft or submitted). Delete or revert those scoresheets first if you really need to roll back the round."* The block fires the moment a judge enters any score (auto-save promotes the sheet BLANK → DRAFT) — they don't need to Save or finalize.

#### 12.6.5 minJudgesPerRound lock — verify settings tab

- [ ] Navigate back to Amadora division detail → Settings.
- [ ] **Expected:** "Minimum judges per round" is now `setReadOnly(true)` — locked because a round has `status != PENDING`.
- [ ] **Expected:** Tooltip on the field explains the lock.

#### 12.6.6 Try to remove a judge below the minimum

*From Rounds tab → 👥 Assign Judges on the started scoring round.*

- [ ] Uncheck both judges, click Save.
- [ ] **Expected:** Error "Removing this judge would drop the round below the required minimum of 2 judges."
- [ ] Close — both judges remain assigned.

#### 12.6.7 Delete a not-started, no-judges scoring round (negative + positive)

- [ ] Add a second scoring round (`Throwaway`, category `M2A — Pome Fruit Melomel`, Table `Table 2`, no Scheduled).
- [ ] **Expected:** New row, Status = `PENDING`.
- [ ] Click 🗑 Delete → confirm.
- [ ] **Expected:** Notification "Round deleted"; row removed.

#### 12.6.7.1 Assign entries to a scoring round (split-category demo)

Each scoring round in the new model explicitly owns the set of entries it judges. Entries are 1:1 with scoring rounds — an entry can't be on two rounds at once. The walkthrough uses Profissional (pre-staged at JUDGING) which the seed has split M1A into two scoring rounds.

- [ ] Switch to the Profissional division: navigate to CHIP 2026 → Divisions → Profissional → "Manage Judging" → Rounds tab.
- [ ] **Expected (from dev seed):** Two PENDING scoring rounds for M1A: `M1A Panel A` (Table 1, 2 judges, 2 entries assigned) and `M1A Panel B` (Table 2, 2 judges, 3 entries assigned). Plus one PENDING medal round for M1B (Table 4, 3 judges).
- [ ] Click 📦 Assign Entries on the `M1A Panel A` row.
- [ ] **Expected:** Dialog titled "Assign entries to M1A Panel A" with helper text explaining 1:1 constraint, plus a multi-select grid with columns Entry / Meadery / Current round.
- [ ] **Expected:** The 5 RECEIVED M1A entries are listed. The 2 pre-assigned to Panel A are pre-selected; the 3 on Panel B show `Current round: M1A Panel B`.
- [ ] Try to also select one of Panel B's entries (a row currently assigned elsewhere) → Save.
- [ ] **Expected:** Error notification "This entry is already assigned to round 'M1A Panel B'. Remove it from there first." Dialog stays open.
- [ ] Close. Click 📦 Assign Entries on the `M1A Panel B` row → uncheck one of its entries → Save → notification "Entry assignments updated".
- [ ] Back on `M1A Panel A`: open Assign Entries again → the newly-freed entry shows `Current round: — Unassigned —`. Select it → Save → assignments updated.
- [ ] Re-balance to whatever you prefer before continuing.
- [ ] (Try) Start one of the M1A rounds → then open Assign Entries on it. **Expected:** dialog still opens (entry assignments are editable through ACTIVE). On ACTIVE rounds, adding an entry auto-creates its BLANK scoresheet; removing an entry deletes the scoresheet if it's still BLANK, but is blocked if the scoresheet is already SUBMITTED (key `error.entry.cannot-unassign-submitted`). After the round reaches COMPLETE the 📦 button disables with tooltip *"Entry assignments are locked once the round is COMPLETE."* (key `error.entry.cannot-change-on-complete-round` defends the same at the service level).
- [ ] (Try) After reverting an ACTIVE round (see §12.6.4.1), open 📦 Assign Entries on the now-READY row. **Expected:** dialog opens and current assignments are editable — useful for fixing the mistake that prompted the revert.

#### 12.6.8 Add a medal round

Medal rounds are auto-created by the scoring-completion cascade — when every scoring round in a category reaches COMPLETE, a medal `JudgingRound` (type=MEDAL) appears in the grid at status `READY`. You can also add one explicitly via the Add Round dialog if you want to pre-stage with a custom table or before any scoring rounds finish.

- [ ] Click "+ Add Round" → switch Type to `MEDAL`.
- [ ] **Expected:** Name field disappears (medal rounds are auto-named `Medal — {category code}`, e.g. `Medal — M1B`) and a **Medal mode Select** appears (`add-round-medal-mode`, default Comparative). The mode is chosen **at create time** — the old post-create header switch is gone. The **Scheduled date+time picker stays available for medal rounds** — set it here and it persists on the new row (it is no longer dropped at create time).
- [ ] Pick Category = `M1B — Traditional Mead (Semi-Sweet)`, leave Mode = `Comparative`, Table = `Table 2`, Save.
- [ ] **Expected:** Notification "Round added"; new row with the Type badge = `Medal — Comparative`, Status = `PENDING`, Name = `Medal — M1B`.
- [ ] **Try** to add a second medal round for the same category → **Expected:** error *"A medal round for this category already exists. Only one medal round per category is allowed."* (One medal round per category — redesign decision #5.)
- [ ] **Expected (unified actions):** the medal row now carries the **same inline action set as a scoring row** — ✏ Edit (name + table + date/time), 👥 Assign Judges, 📦 Assign Entries, ▶ Start, ↶ Revert, 🗑 Delete, 👁 Open. ↶ Revert on a medal round returns it to READY **and clears its medal awards** (the medal-only "Reset" is gone — Revert covers it; confirm body warns the awards are cleared and the scoresheets are kept). 🗑 Delete is enabled only while PENDING with no judges and no medal awards. The per-row 🥇🥈🥉 medal-awarding + Finalize/Reopen live in the `MedalRoundView` drill-in (👁 Open) — see §12.12.
- [ ] **Expected (📦 on a COMPARATIVE medal row is disabled):** for a **COMPARATIVE** medal round the 📦 Assign Entries icon is **disabled** with a tooltip explaining the candidates come from the judges' "advance to medal round" flags on the scoring sheets (they can't be hand-assigned — the entries live in their scoring rounds, and an entry can be on only one round). It stays **enabled** on SCORING rows and on **SCORE_BASED** medal rows (where it opens the read-only Sync dialog, §12.6.8.1).
- [ ] **Note:** A pre-staged COMPARATIVE medal round shows an empty entries grid until scoring rounds in its category COMPLETE (its pool is the advance-flagged sheets). To award medals before scoring completes, use a SCORE_BASED medal round (§12.6.8.1) or the dev-seeded Profissional M1B medal round once Profissional scoring rounds are finalized.

#### 12.6.8.1 Small-category flow — SCORE_BASED medal round runs scoring directly

When a category has few entries and you want to skip the preliminary scoring round entirely, a SCORE_BASED medal round can own the scoresheets directly. It acts like a hybrid: scoring happens at the medal round itself, and medals come from those scoresheets at the end (gold/silver/bronze by total, stop on tie).

**Force-all invariant (Cycle A):** Every RECEIVED entry in the category MUST be on the medal round, and nothing else. Partial assignment doesn't make sense when the medal round IS the only judging venue. The Assign Entries dialog is read-only (no checkboxes); the "Sync now" footer button reconciles in both directions — adds any missing RECEIVED entries AND removes zombies (entries no longer RECEIVED: withdrawn, reverted, moved category). Subsequent state changes auto-sync via an `EntryReceivedEvent` listener (fires on RECEIVED transitions in OR out of that state). Manual unassign of a RECEIVED entry is rejected (`error.entry.cannot-unassign-from-score-based`); the only ways to drop a RECEIVED entry are to withdraw it (auto-sync removes the zombie) or move its final category. Non-RECEIVED entries can be manually unassigned as an escape hatch. Entries with a SUBMITTED scoresheet on the round are never auto-removed (committed work isn't silently dropped — sync logs a warning and skips).

- [ ] Pick (or create) a small category with no scoring rounds yet — e.g., a new judging category with 3 RECEIVED entries.
- [ ] Click "+ Add Round" → Type = `MEDAL` → **Mode = `Score-based`** → Category = the small one → Table = any → Save. (Mode is set at create time; the row's Type badge reads `Medal — Score-based`.)
- [ ] Click 📦 **Assign Entries** on the medal row (grid) — the dialog is mode-aware; for SCORE_BASED it is **read-only + a "Sync now" footer** (the same flow is also reachable inside MedalRoundView via 👁 Open).
- [ ] **Expected:** Dialog is a **read-only preview** (no checkboxes — selection mode NONE). Lists every RECEIVED entry in the category. The Total column shows `—` for entries with no sheet yet. Helper text reads "Every RECEIVED entry in this category is automatically part of this medal round…". Footer button reads "Sync now" (not "Save").
- [ ] Click **Sync now** → notification "Medal-round entries updated"; the round now contains all 3 entries.
- [ ] (Optional regression check — auto-sync add) Without closing the page, in another tab / as `compadmin@`, mark a NEW entry as RECEIVED in the same category. **Expected:** within a few seconds the new entry is automatically added to the medal round (via `EntryReceivedEvent` → `MedalRoundAutoSyncListener` → `syncScoreBasedMedalRoundEntries`). Re-open the Assign Entries dialog to confirm the count grew.
- [ ] (Optional regression check — auto-sync cleanup) Withdraw one of the assigned entries (Entry Admin → ✖ Withdraw). **Expected:** within a few seconds the withdrawn entry is automatically removed from the medal round (zombie cleanup path on the same listener). Re-open the dialog to confirm the count dropped.
- [ ] (Try) Attempt a manual unassign of a RECEIVED entry via direct API. **Expected:** rejected with `error.entry.cannot-unassign-from-score-based` ("Can't remove an entry from a SCORE_BASED medal round manually — every RECEIVED entry in the category is assigned automatically. Withdraw the entry or change its final category to remove it.")
- [ ] (Try) Attempt a manual unassign of a NON-RECEIVED (e.g. WITHDRAWN) entry via direct API. **Expected:** succeeds — escape hatch lets admin clean stale zombie data manually.
- [ ] Click **Assign Judges** → pick at least minJudgesPerRound judges → Save.
- [ ] **Expected:** Round status auto-flips PENDING → READY once table + judges (≥ minJudgesPerRound) + entries (≥ 1) + division ≥ JUDGING are all satisfied.
- [ ] Click ▶ **Start** on the medal row (grid) → confirmation → notification "Round started"; status → ACTIVE.
- [ ] **Expected:** BLANK scoresheets are created for every assigned entry (mirroring how a scoring round behaves at start).
- [ ] Log in as one of the assigned judges → land directly on the medal round (Cycle C redirect).
- [ ] **Expected:** Judges see the standard ScoresheetView form (per-criterion scores + comments) for each assigned entry. Score each and click **Save** (sheet → FILLED). The medal grid now carries a **Status** column (next to Total) that tracks each sheet's scoresheet status — `BLANK` → `FILLED` → `SUBMITTED` (`—` for an entry with no sheet yet) — so the scoring progress of a medal round is visible at a glance, just like a scoring round. The grid **Total** column fills in alongside it as each sheet is saved (no need to submit first).
- [ ] When the **last** sheet is FILLED (none BLANK/DRAFT left), the system auto-populates medals from the FILLED totals — 🥇🥈🥉 appear in the grid's Current medal column. If two entries tie at a medal boundary, the ties banner shows and the medals for the tied slot are left for manual resolution (use the per-row medal buttons).
- [ ] **(Recompute on edit — verifies the auto-populate reconcile.)** Before finalizing, re-open one entry's scoresheet, change its scores so the ranking flips (e.g. push the current silver above the current gold), Save (→ FILLED). **Expected:** back on the medal grid the medals **recompute** from the new totals — the medals swap to match the new order. Now edit again so two entries **tie** at a medal boundary, Save → **Expected:** the tied slot's medals clear and the ties banner appears (Finalize disables). Resolve by awarding the medal to one tied entry (a manual award is preserved across later auto-recomputes).
- [ ] **As the judge** (no admin needed), click **Finalize**. The button is enabled only once every sheet is FILLED and no tie is open (otherwise it's disabled with a tooltip explaining why). The confirm dialog lists the actual medal counts (not zeros) plus the bold "no medal" count. **For a judge it does NOT claim the round can be reopened** (Reopen is admin-only) — that reassurance + the admin warning show only to admins.
- [ ] **Expected:** Confirm → all sheets submitted, medals committed, round → COMPLETE in one step. An admin *can* Finalize too, but isn't required.
- [ ] **(Reopen reverts sheets — verifies the SCORE_BASED reopen fix.)** As **`compadmin@`**, on the now-COMPLETE round click **Reopen** (admin-only) → **Expected:** round → ACTIVE and its **SUBMITTED scoresheets drop back to FILLED** (editable again; opening one and editing demotes it to DRAFT). Existing medal awards are kept so they can be reassigned. (Before the fix the medals were reassignable but the sheets stayed locked.)

#### 12.6.9 Type + Status filters

- [ ] Set the Type filter to `Scoring` → **Expected:** only the scoring rows remain in the grid.
- [ ] Set the Type filter to `Medal` → **Expected:** only the medal rows remain.
- [ ] Set the Type filter back to `All` → **Expected:** all rows visible.
- [ ] In the **Status filter** (`rounds-status-filter`), untick `PENDING` → **Expected:** PENDING rows disappear; other statuses remain. Re-tick it.
- [ ] Untick **every** status → **Expected:** all rows still show (empty selection = "no constraint", not "hide everything").
- [ ] Combine: Type = `Medal` + only `ACTIVE` ticked → **Expected:** just active medal rounds. Reset both filters before continuing.

#### 12.6.10 Open buttons drill into per-round views

- [ ] Click the Open button on a Scoring row → **Expected:** navigates to `RoundView` (`/competitions/.../divisions/.../rounds/{roundId}`) — see §12.10.
- [ ] Click the Open button on a Medal row → **Expected:** navigates to `MedalRoundView` (`/competitions/.../divisions/.../medal-rounds/{divisionCategoryId}`) — see §12.12.

### 12.7 JudgingAdminView — Results tab

*Click "Results" tab.*

The Results tab is a read-only summary of every round that has reached COMPLETE status. Before any rounds complete, it shows an empty-state caption "No completed rounds yet."

- [ ] **Expected (initially):** Empty-state caption "No completed rounds yet." (no scoring rounds are COMPLETE — the Round 1 cascade fires only after every scoresheet in the round is SUBMITTED).
- [ ] After completing scoresheets in §12.10–12.11 (M1A Panel A goes COMPLETE), come back to the Results tab.
- [ ] **Expected:** Grid columns: Type, Name, Category, Table, Outcome, Actions.
- [ ] **Expected:** The COMPLETE scoring row shows Outcome = `{N} scoresheets submitted` where N = the count of submitted scoresheets at the round.
- [ ] After a medal round goes COMPLETE (§12.12), come back here.
- [ ] **Expected:** The COMPLETE medal row shows Outcome as one glyph per medal slot (no counts — each medal is unique per category): `🥇 🥈 🥉` when all three are awarded, with `🚫` standing in for any medal that was not awarded (e.g. `🥇 🚫 🥉` = no silver).
- [ ] Click the Open button on a COMPLETE row → **Expected:** navigates to the same per-round view as the Rounds tab (RoundView for SCORING, MedalRoundView for MEDAL).

### 12.8 JudgingAdminView — Best of Show tab

*Click "Best of Show" tab.*

- [ ] **Expected (Judging.phase = ACTIVE):** Phase badge `Phase: Active`, configured BOS places line, "Manage placements →" anchor, and three sections: header, GOLD candidates (empty until medal rounds complete), placements (1 empty row).
- [ ] **Expected (GOLD candidates grid columns):** `Entry #` (prefixed, e.g. `PRO-1`), `Code`, `Mead`, `Category` — the entry number and the code are now separate columns (the code used to sit alone under an "Entry" header).
- [ ] **(Awards confirmed on finalize — verifies the BOS-candidate fix.)** After finalizing the M3B (SCORE_BASED) and M1A (COMPARATIVE) medal rounds, return here → **Expected:** their golds appear in the GOLD candidates grid. Before the fix, an auto-populated SCORE_BASED gold stayed `confirmed=false` and never showed ("No GOLD medals were awarded"); finalizing now confirms the awards.
- [ ] **(Empty / un-judged categories don't block — verifies the config/BOS-gate fix.)** Profissional has JUDGING categories with **no entries** (e.g. M4-series) and categories with entries but **no medal round set up** (M2A, M2C if you didn't run them). **Expected:** these do **not** block "Start BOS" — only categories that actually have a medal round must be COMPLETE. (Before the fix, merely opening this tab created a config for every category and required each to have a COMPLETE medal round, so Start BOS could never enable.)
- [ ] **(Empty category is deletable — verifies the deletion guard + no-eager-config.)** On a fresh DB, an entry-less judging category (e.g. M4S) has no config/round/award → DivisionDetail → Judging Categories → remove it → **Expected:** succeeds (no `category_judging_configs` FK crash). A category that *is* in judging use (has a config / round / award) instead gives the clean error *"Cannot remove this category: it is in use for judging…"* (§12.4.1).
- [ ] **Expected:** "Start BOS" button is disabled with tooltip "All medal rounds must be COMPLETE before BOS can start." until every medal `JudgingRound` in the division is `COMPLETE` (categories with no medal round are ignored — they don't block).
- [ ] After all medal rounds COMPLETE, click "Start BOS" → confirm.
- [ ] **Expected:** Notification "BOS started"; phase badge updates to `Phase: BOS`; "Finalize BOS" and "Reset BOS" appear.

### 12.9 MyJudgingView (Cycle C: redirect-or-stub)

**Design (Cycle C):** A judge has at most one ACTIVE round at any time (enforced by `JudgingService.assignJudge`'s active-conflict check). MyJudgingView reflects that:
- If the judge has an ACTIVE round → forward directly to RoundView (for SCORING) or MedalRoundView (for MEDAL).
- If none → show a bare "No active round right now" message.

There is no hub view, no "Resume next draft" shortcut, and no list of upcoming / past rounds. Judges land where they need to work, or are told there's nothing live.

Additionally, **RootView** now redirects judges (any user with a `JudgeAssignment`) to `/my-judging` after login, which then applies the redirect logic above. This makes the "log in → see your live work" UX automatic.

Access tightening: judges can only open **ACTIVE** rounds. RoundView, MedalRoundView, and ScoresheetView all gate non-admin access by `round.status == ACTIVE`; PENDING/READY/COMPLETE rounds forward unauthorized judges to `""` (root → re-redirects).

*Log out as compadmin, log in as `judge@example.com` (use the magic link from Mailpit; access code also works).*

- [ ] After login, you should land **directly** on the ACTIVE round you're assigned to (RoundView for SCORING, MedalRoundView for MEDAL). The sidebar "My Judging" entry is visible (gavel icon).
- [ ] Manually type `/my-judging` in the URL bar → **Expected:** same forward — you end up at the same active-round view.
- [ ] Manually type a different round URL the judge ISN'T assigned to (e.g. a sibling scoring round) → **Expected:** forwarded to root, ends back at the active round or stub.
- [ ] Manually type the URL of a PENDING/READY/COMPLETE round the judge IS assigned to (if any) → **Expected:** same forward (judges only see ACTIVE rounds).

#### 12.9.1 No-active-round stub

- [ ] If the judge has no ACTIVE round (e.g. all their rounds are still PENDING/READY, or all are COMPLETE), the `/my-judging` page shows H2 "My Judging" plus a Span with id `my-judging-empty` reading *"No active round right now. The admin will let you know when judging starts."*
- [ ] To test deliberately: log in as a judge whose round is still PENDING (e.g. before admin clicks Start). Sidebar still shows "My Judging"; click it → the stub renders.

#### 12.9.2 Non-judge user — no sidebar entry

- [ ] Log out, log in as `entrant@example.com` (regular entrant with no judge assignment).
- [ ] **Expected (sidebar):** "My Judging" entry is *not* present (gated by `JudgeAssignmentChecker.hasAnyJudgeAssignment`).
- [ ] Manually navigate to `/my-judging` → **Expected:** MyJudgingView renders with the empty-state stub (no active round). The view itself is `@PermitAll` so it doesn't 403; the sidebar gating is the discoverability cue.

### 12.10 TableView (per-table)

*Back as `judge@example.com`. Per Cycle C, `/my-judging` auto-forwards to the started M1A table — you should already be on RoundView. If not, login again or navigate to `/my-judging`.*

- [ ] **Expected:** URL is `competitions/chip-2026/divisions/amadora/rounds/<roundId>`.
- [ ] **Expected:** Breadcrumb begins with "My Judging" (judge path) or "My Competitions / CHIP 2026 / Amadora / Judging Admin" (admin path).
- [ ] **Expected:** H2 `CHIP 2026 — Amadora — Table: M1A Panel A`.
- [ ] **Expected:** A one-line **explanation** (`round-explanation`) below the header, **role-phrased** like the medal round (§12.12): judges see "Score each entry against the MJP criteria and save every scoresheet…"; admins see the third-person "Judges score each entry against the MJP criteria and save their scoresheets…". The title / info row / explanation have the same vertical breathing room as the medal round (not crammed together).
- [ ] **Expected:** Filter bar with a `Status` Select (options: All, Draft, Submitted; default All) and a `Search` `TextField` (`ValueChangeMode.EAGER`). Placeholder is **"Mead name or entry code"** for admins, **"Entry code"** for judges (anonymity rule — judges can't search by mead name either).
- [ ] **Expected (admin):** A `Grid<Scoresheet>` with columns Entry # *(e.g. "PRO-1", cross-reference back to Entry Admin)*, Code, Mead Name, Status, **Total**, **Advances**, Filled by, Actions.
- [ ] **Expected (judge):** Same grid with **Entry # and Mead Name hidden** (anonymity rule — judges judge to style, not to a brand, and don't see the internal cross-reference either). Visible columns: Code, Status, Total, Advances, Filled by, Actions.
  - **Total column** shows the locked total for SUBMITTED sheets and the live running sum for BLANK/DRAFT/FILLED sheets (judging in progress), or "—" for sheets with no scores entered yet. The running sum is computed live, so admins can see panel progress without opening each scoresheet. (No `*` marker — matches the medal-round grid.)
  - **Advances column** shows ✓ when the judge marked "Advance to medal round", — otherwise.
- [ ] **Status filter** options: `All`, `Blank` (created, no judge touched yet), `Draft` (judge saved at least once), `Submitted`. Apply filter Status = Draft → grid narrows to DRAFT rows only.
- [ ] Type part of a mead name in Search → grid filters client-side; clearing the field restores all rows.

#### 12.10.0 Round-level Finalize / Reopen

- [ ] **Expected:** above the grid an ACTIVE round shows a **Finalize** button (`round-finalize-button`, visible to judge + admin) — **enabled only when every scoresheet on the round is FILLED** (otherwise disabled, with a tooltip to save all scoresheets first). A COMPLETE round instead shows an admin-only **Reopen** button (`round-reopen-button`).
- [ ] (Once all sheets are FILLED — see §12.11) click **Finalize** → confirm dialog states **"N entries advancing to the medal round"**, shows a prominent **zero-advance warning** if none are flagged, and (for admins) an extra "you're finalizing on behalf of the judges" warning. Confirm → all sheets submit (totals computed), round → COMPLETE, and the category's medal-round cascade fires.
- [ ] **(Split-category cascade — verifies the duplicate-key fix.)** For the M1A split (Panel A + Panel B, §12.6.7.1), score + Finalize **both** panels, flagging some entries "Advance to medal round" (§12.11). **Expected:** finalizing the **second** panel (the one that makes *all* M1A scoring rounds COMPLETE) **does not crash** — before the fix it threw `judging_round_entries_entry_id_key`. The cascade auto-creates **`Medal — M1A` (COMPARATIVE)** at READY, and 👁 Open shows the advance-flagged entries as candidates (derived from the scoresheets — the medal round does not own an `entries` set).
- [ ] (Admin, on a COMPLETE round) click **Reopen** → confirm → round → ACTIVE; its SUBMITTED sheets drop back to **FILLED** (editing one demotes it to DRAFT, so the round has to be re-finalized).

#### 12.10.1 Row click + per-row Open → ScoresheetView

- [ ] Click any row.
- [ ] **Expected:** Navigation to `competitions/.../scoresheets/<id>`.
- [ ] **Expected (per-row actions):** both judges and admins see a 👁 **Open** (`open-<sheetId>`) icon that navigates to the scoresheet (coexists with row click). The old per-row 📨 Submit is **gone** — judges Save each sheet (→ FILLED), then the round-level **Finalize** (§12.10.0) submits them all at once. Admins additionally see Revert / Move / Delete (§12.10.2).
- [ ] **Expected (judge):** form opens in edit mode — a single **Save** button (id `save-button`) is visible, fields editable and **auto-saving on blur** (a "Saving…/Draft saved ✓" status shows). No "Save Draft", no per-sheet "Submit".
- [ ] **Expected (admin):** form opens **read-only** (no **Save** button, score fields + comments / language / advance checkbox all marked read-only). Below the read-only form, an **"Edit on behalf of judge"** button (id `admin-edit-scoresheet`) is visible.
- [ ] (As admin) Click "Edit on behalf of judge" → **Expected:** ConfirmDialog *"Edit scoresheet?"* — body warns the action should only be used in exceptional situations (judge left mid-round, correct an obvious typo) and that the admin is not silently overriding the judge's assessment. Buttons: Cancel + "Edit anyway".
- [ ] Cancel → form stays read-only. Re-open the dialog, click "Edit anyway" → form re-renders editable; the **Save** button appears.
- [ ] **Visibility tightening:** a judge who is **not** assigned to the round must not be able to open scoresheets at that round, even by knowing the URL. Try copying a scoresheet URL while logged in as `compadmin@`, log out, log back in as a judge who is NOT on this round (e.g. `judge6@` if you've only assigned `judge3@`/`judge4@`), paste the URL → **Expected:** redirect to root (`/`); ScoresheetView not rendered.

#### 12.10.2 Admin-only actions (Revert, Move)

*Log back in as `compadmin@example.com` and revisit the same TableView URL.*

- [ ] **Expected:** Rows in SUBMITTED show an `arrow-backward` icon (Revert) tooltip "Revert to draft" (or "Cannot revert while medal round is active or complete for this category." when locked).
- [ ] **Expected:** Rows in **DRAFT or FILLED** (pre-submit) show an `exchange` icon (Move) tooltip "Move to another table".
- [ ] **Expected:** Neither button is visible for `judge@example.com` (admin-only).

##### Revert

- [ ] On a SUBMITTED row (you'll need to submit a scoresheet first — see §12.11) click Revert.
- [ ] **Expected:** Confirmation dialog body explains the scoresheet returns to DRAFT, total score is cleared, and if it was the last submitted at the round, round status reopens to ACTIVE.
- [ ] Click Revert.
- [ ] **Expected:** Notification "Reverted scoresheet for {entryCode} to draft."; row Status changes; round Status (visible in JudgingAdmin Rounds grid) returns to ACTIVE if applicable.
- [ ] **Check Mailpit:** the judge who filled that scoresheet receives a "Scoresheet reopened" email, subject "[MEADS] Scoresheet reopened — {entryCode}", heading "A scoresheet needs your attention", CTA button "Log in to MEADS". `JudgingNotificationListener` handles `ScoresheetRevertedEvent`. (No email if the scoresheet was reverted before any judge had filled it.)

##### Move to another table

For this you need a *second* ACTIVE scoring round in the same JUDGING category. Create one via JudgingAdminView → Rounds tab if needed, then Start it (assigning ≥ minJudgesPerRound judges).

- [ ] On a DRAFT row click Move.
- [ ] **Expected:** Dialog with a `Select<JudgingRound>` (target rounds filtered to ACTIVE and same category, excluding current).
- [ ] If no candidate rounds exist: **Expected** the empty-state message "No other ACTIVE rounds cover this category. Add a round first." and a disabled Save button.
- [ ] Pick a target, click Save.
- [ ] **Expected:** Notification "Moved scoresheet to {targetName}."; row disappears from this table's grid (reload to re-render).

##### Delete scoresheet (cleanup before reverting entry status)

Admins occasionally need to revert an entry's status (RECEIVED → SUBMITTED) or
withdraw it after judging has begun (e.g., bottle pulled mid-competition because of
a defect spotted later). The `EntryStatusRevertGuard` in the entry module rejects
those status changes whenever a scoresheet exists for the entry, so admins must
delete the scoresheet from its round first.

- [ ] On any row, click 🗑 **Delete scoresheet** (tooltip "Delete scoresheet" — admin-only).
- [ ] **Expected:** Confirmation dialog *"Delete scoresheet for {entryCode}?"* explaining the scoresheet (and any draft scores/comments) will be permanently removed; the entry stays at its current status.
- [ ] Click **Delete**. **Expected:** notification "Deleted scoresheet for {entryCode}."; row disappears.
- [ ] **Expected:** The delete button is disabled with the tooltip *"Cannot delete the scoresheet while the medal round is active or complete for this category."* once the category's medal round has started — same rule as Revert.
- [ ] (Test the EntryService side) Back on Entry Admin, try to revert the same RECEIVED entry to SUBMITTED (`←` arrow). **Expected:** error notification *"Cannot change the entry's status: a scoresheet already exists on a round…"* if a scoresheet still exists; succeeds after deletion.

##### Late RECEIVED during JUDGING (manual assignment)

When an entry transitions to RECEIVED *during* JUDGING (e.g., the bottle arrived late
and was checked in after the round started), no scoresheet is created automatically.
The admin marks the entry RECEIVED, assigns its final category if needed, then uses
Manage Judging → Rounds tab → **Assign Entries** on the chosen round to add it. That
flow writes `round.entries` and (for ACTIVE scoring rounds) creates the DRAFT
scoresheet via `JudgingService.assignEntryToRound`.

- [ ] On Entry Admin, pick a SUBMITTED entry → click the `→` advance arrow to mark it RECEIVED. **Expected:** status becomes RECEIVED; no scoresheet is created yet.
- [ ] If the entry has no final category, assign one (Final Category column on Entry Admin).
- [ ] Manage Judging → Rounds → **Assign Entries** on the round of your choice → tick the entry → save. **Expected:** the entry now appears in the round's entries; if the round is ACTIVE and SCORING, a DRAFT scoresheet is visible in RoundView's scoresheets grid.

### 12.11 ScoresheetView (judge form)

> **(c) redesign:** "Save Draft" + per-sheet "Submit" are gone. Fields auto-save on
> blur; the **Save** button validates the sheet → FILLED; the round-level **Finalize**
> (on RoundView, §12.10.0) submits all FILLED sheets at once.

*As `judge@example.com`, open any DRAFT scoresheet from `/my-judging` → "Open table" → row click.*

- [ ] **Expected:** URL `competitions/.../divisions/.../scoresheets/<id>`.
- [ ] **Expected:** A **"← Back to round"** anchor (id `scoresheet-back-to-round`) appears at the very top of the view, pointing back at the round's RoundView. One click returns to the scoresheet list.
- [ ] **Expected:** H2 `Scoresheet — {entryCode}`.
- [ ] **Expected (judge view):** A read-only entry card showing **only the declared
  attributes** the judge needs to judge to style — **Initial Category** (code +
  name, what the entrant registered under) and **Final Category** (code + name,
  where the admin placed it for judging; both rendered side-by-side on separate
  lines), sweetness, carbonation, ABV, honey varieties, and (when present) other
  ingredients, wood ageing details, and additional information. **The mead name
  is NOT shown to judges** (anonymity rule — judges judge to style, not to a
  brand). Judges work from poured coded samples, not the labelled bottle, so the
  declared attributes still matter on screen.
- [ ] **Expected (admin view):** If a SYSTEM_ADMIN or division admin opens the
  same URL, the entry card additionally shows the mead name as the first row
  (admins keep the full context for moderation / results review).
- [ ] **Expected:** A "Scores" section with five `NumberField`s, one per MJP field. **Each NumberField is followed by a TextArea with id `score-comment-<fieldName>`** for per-criterion judge comments (placeholder *"Comments on this criterion (optional)"*, `maxLength=2000`). The fields are:
  - `Appearance` (max 12)
  - `Aroma/Bouquet` (max 30)
  - `Flavour and Body` (max 32)
  - `Finish` (max 14)
  - `Overall Impression` (max 12)
  Each `NumberField` has `min=0`, `max=<field max>`, **+/- step buttons visible**, and **auto-saves on blur** (`ValueChangeMode.ON_BLUR`) — a small **save-status** Span (`scoresheet-save-status`) shows "Saving…/Draft saved ✓".
- [ ] **Expected:** A "Current total: N / 100" **H3** (id `scoresheet-total`) below the score fields — sized as `--lumo-font-size-xxl` so it's the loudest thing on the page. Updates as values change.
- [ ] **Expected:** An optional **"Additional comments"** `TextArea` (id `overall-comments`, label "Additional comments (optional)", `maxLength=2000`, **no minimum length** — the old required "Overall comments" is gone). Auto-saves on blur.
- [ ] **Expected:** A "Comment language" `ComboBox` listing all ISO 639-1 languages, sorted by display name in the UI locale. Default: the judge's `JudgeProfile.preferredCommentLanguage` if set, else the judge's `User.preferredLanguage` (UI language) as a sensible fallback, else blank. Judges are free to pick any language — no per-competition restriction.
- [ ] **Expected:** An "Advance to medal round" `Checkbox`.
- [ ] **Expected:** A single **Save** button (id `save-button`, always enabled) next to the save-status Span. There is **no "Save Draft" and no per-sheet "Submit"** — Save validates the sheet and promotes it DRAFT → **FILLED**; the round-level **Finalize** (§12.10.0) does the submitting.

#### 12.11.1 Auto-save (on blur)

- [ ] Enter a score in a field, then tab/click away (blur). **Expected:** the save-status Span flashes "Saving…" then "Saved ✓"; the value persists (refresh to verify) with no button click. The sheet is now `DRAFT`.
- [ ] Enter a per-criterion comment (e.g. *"Bright with a slight haze."* in `score-comment-Appearance`), an Additional-comments value, pick a Comment language, tick Advance to medal round — each auto-saves on blur/change. Refresh: all persist (each comment shows back in its `score-comment-<field>` TextArea).

#### 12.11.2 Save → FILLED (validation)

- [ ] **Comment requirement (server-enforced):** every per-criterion comment ≥ **15** characters, and all 5 fields scored. The old required overall-comment minimum is gone (Additional comments is optional).
- [ ] (Try) Click **Save** with a field unscored or a too-short per-criterion comment → **Expected:** error notification *"Comment on \"{field}\" must be at least 15 characters…"* (key `error.scoresheet.field-comment-too-short`) or *"Scoresheet cannot be submitted: …"* (key `error.scoresheet.incomplete`). The sheet stays `DRAFT`.
- [ ] Fill all 5 scores + a ≥15-char comment per criterion, then click **Save**.
- [ ] **Expected:** Notification *"Scoresheet saved — ready to finalize."*; the sheet is now **FILLED** and you return to the round (RoundView). Re-editing any score/comment demotes it back to DRAFT (re-Save to re-validate).
- [ ] Repeat for every scoresheet on the round (all judges). Once **all** are FILLED, the round's **Finalize** (§12.10.0) enables → Finalize submits them all + computes totals; the TableView Total column then shows each locked total.

#### 12.11.3 Hard COI page-level rejection (judge can't judge own entry)

- [ ] As `compadmin`, ensure an entry exists in Amadora where `entry.userId = judge@example.com`'s user id, with `finalCategoryId` = a JUDGING category the judge is assigned to.
- [ ] As `judge@example.com`, navigate directly to the URL of that entry's scoresheet (you can find the id via DB or by opening a TableView that includes it).
- [ ] **Expected:** Forward to `/my-judging`; no scoresheet form rendered (hard COI block per §3.7).

#### 12.11.4 Authorization rejection (judge not assigned to this table)

- [ ] As `judge@example.com`, navigate to a scoresheet on a *different* table the judge isn't assigned to.
- [ ] **Expected:** Forward to `""`.

### 12.12 MedalRoundView

> **(c) redesign + 2026-05-30 follow-up:** Start, Revert, **Assign Judges and Assign
> Entries** all moved to the unified Rounds grid (§12.6); medal Revert clears the
> round's awards (replacing the old Reset). MedalRoundView keeps only the per-row
> medal actions, **Finalize**, and admin **Reopen**. For a **SCORE_BASED** round
> Finalize is available to the **judge and** admin (judge runs it end-to-end); for
> **COMPARATIVE** it stays admin-only.

The scoring-completion cascade auto-creates a medal `JudgingRound` (type = MEDAL) and marks it READY once every scoring round in the category reaches COMPLETE. From there an admin opens MedalRoundView and clicks **Start** to transition READY → ACTIVE.

*Pre-req: M1A's scoring round (`M1A Panel A` from §12.6) is COMPLETE — finish the scoresheets in §12.10–12.11 first. The cascade will have auto-created a medal `JudgingRound` for M1A with no table assigned and inherited mode `COMPARATIVE` from the category config.*

#### 12.12.0 Open MedalRoundView at READY status

- [ ] As `compadmin@example.com`, navigate to JudgingAdmin → Rounds tab → set Type filter to `Medal` → click Open on the M1A row.
- [ ] **Expected:** URL `competitions/.../divisions/.../medal-rounds/<divisionCategoryId>`.
- [ ] **Expected (header, ACTIVE/read-only):** title, then an info row in this order — **Table** first, a colored **Type badge** (`Medal — Comparative` / `Medal — Score-based`, matching the grid), then **Status** (shown to **admins only**), then a one-line **explanation of the round** (id `round-explanation`). The explanation is **role-phrased**: judges see a second-person instruction ("Score each entry…" / "Compare the entries…"), while admins — who observe rather than score — see a third-person variant ("Judges score each entry…" / "Judges compare the entries…"). At PENDING/READY (admin) the Table + Mode are **editable Selects** plus the status badge (so cascade-auto-created rounds can be configured); once ACTIVE the row is read-only.
- [ ] **Expected:** The header lines (title → table/type/status row → explanation) have a little vertical breathing room between them (not crammed together).
- [ ] **Expected:** Action row: `Finalize` and `Reopen` (admin, enabled only at COMPLETE), with the **bold medal-tally summary right-aligned on the same row** (§12.12.1). **No Assign Judges / Assign Entries here** — those are inline on the unified Rounds grid (§12.6), alongside Start and Revert. For SCORE_BASED, Finalize is disabled with a tooltip until every sheet is FILLED and no tie is open.
- [ ] **Expected:** The entries grid **grows to fit all rows** (no fixed-height internal scrollbar) — a category with many entries expands the grid rather than capping it.
- [ ] **If no table assigned:** assign one via the header **Table** Select (or the grid's ✏ Edit) before starting from the grid — `startRound` requires a physical table.

#### 12.12.0.1 Change mode + table on a cascade-auto-created medal round

- [ ] On the M1A medal round (READY, no PT, mode COMPARATIVE), open the **Table** Select in the header → pick `Table 1` → notification "Table updated" → page reloads → Start button becomes enabled.
- [ ] Open the **Mode** Select → switch to `Score-based` → notification "Medal-round mode updated" → header reflects the change. (Service-side, `JudgingRound.medalMode` is now `SCORE_BASED`; the round will own its scoresheets and auto-populate medals from the FILLED totals — see §12.12.2.)
- [ ] Switch the Mode back to `Comparative` for the rest of the walkthrough.
- [ ] **Expected:** Once you Start the medal round (next sections), the Selects vanish from the header — mode and table are locked beyond READY. Error if you tried to PATCH them anyway: `error.medal-round.mode-locked-after-start` / `error.round.cannot-reassign-physical-table-after-start`.

#### 12.12.0.2 Assign Judges to the medal round (on the Rounds grid)

Medal-round judges are **independent** of scoring-round judges for the same category (redesign decision #5) — could be the same panel, could be different (head judges only). The Profissional M1B medal round is pre-seeded with judges 1+2+6 to demonstrate this — judge6 isn't on any M1B scoring panel. **Assign Judges now lives on the unified Rounds grid**, not inside MedalRoundView.

- [ ] Switch to Profissional → Rounds tab → Type filter `Medal` → click 👥 **Assign Judges** on the M1B row.
- [ ] **Expected:** Dialog "Assign Judges" with a multi-select grid (columns: Name, Meadery, Country). Judges 1, 2, and 6 are pre-checked.
- [ ] Uncheck judge6, check judge3 → Save → notification "Judge assignments updated"; dialog closes; grid refreshes.
- [ ] **Expected:** the grid's 👥 Assign Judges stays available through PENDING → READY → ACTIVE — mid-deliberation panel adjustments are allowed; only locked at COMPLETE. Removing a judge mid-ACTIVE does not undo any medals they already awarded (those carry their own `awardedBy`); it just stops further awards from that judge. The min-judges-per-round check (which applies to scoring rounds) is **skipped for medal rounds** — you can drop a medal-round panel even to zero if needed.

#### 12.12.1 Start medal round — COMPARATIVE mode

- [ ] **Start the medal round from the unified Rounds grid** (§12.6): Rounds tab → ▶ Start on the medal row → confirm → notification "Round started". The MedalRoundView status line flips to `Status: ACTIVE`. (Start is no longer a button inside MedalRoundView.)
- [ ] **Check Mailpit:** each judge with a scoring assignment in this category receives a "Medal round ready" email, subject "[MEADS] Medal round ready — {category}", heading "A medal round is ready", CTA "Log in to MEADS". `JudgingNotificationListener` handles `MedalRoundActivatedEvent`.
- [ ] As `judge@example.com`, navigate via `/my-judging` → Medal Rounds section → "Open medal round →".
- [ ] **Expected:** Entries with a SUBMITTED scoresheet flagged `advancedToMedalRound = true` for this category are listed (eligibility refined per §1.9).
- [ ] **Expected (COMPARATIVE column visibility):** judges award medals by tasting, **independently of the prelim scores**, so on a COMPARATIVE round the grid hides the **Total** column from judges (admins keep it for context) and hides the **Status** column from everyone (the prelim sheets are always SUBMITTED here — the column is noise). A SCORE_BASED round, where the medal round owns the sheets, shows both (§12.6.8.1).
- [ ] **Expected:** Per-row controls — 🥇 · 🥈 · 🥉 · 🗑 Clear, all inline icon buttons (no "More ▾" dropdown). **There is no Withhold action** — an entry with no medal simply isn't awarded one; finalize leaves it without a medal. The 👁 **Open scoresheet** icon is shown only to **admins** on a COMPARATIVE round (the sheet belongs to a prelim scoring round the judge isn't on — judges shouldn't, and can't, open it); on a SCORE_BASED round judges get it too (they own the sheet).
- [ ] Click `🥇` on a row.
- [ ] **Expected:** Notification or live update; the Current medal column shows `🥇 Gold` (the medal icon precedes the label, matching the award buttons).
- [ ] **Expected:** The medal-tally summary "Summary: 1 Gold · 0 Silver · 0 Bronze · {N} no medal" updates live (the last bucket counts every entry without a medal — there's no separate Withhold count anymore). It sits **above the grid**, right-aligned on the **same row as the Finalize button**, in **bold**.
- [ ] Click 🗑 **Clear** on a row with a medal (icon disabled when no award row exists).
- [ ] **Expected:** ConfirmDialog "Delete medal record?" body explains the medal award is deleted and the entry will receive no medal unless awarded again. Footer: Cancel + Delete record.
- [ ] Cancel → no change. Re-open, click Delete record → row reverts to no medal.

#### 12.12.2 SCORE_BASED mode — medals auto-populate as sheets are filled

A SCORE_BASED medal round owns its scoresheets (small-category flow, §12.6.8.1). **Medals are no longer auto-filled at Start** (at Start the sheets are still BLANK). Instead they populate from the **FILLED** totals as judges score.

- [ ] Set up + Start the SCORE_BASED medal round from the Rounds grid (mode chosen at create time, or via the header **Mode** Select while PENDING/READY — §12.12.0.1). At Start, BLANK scoresheets are created; no medals yet.
- [ ] As the judges, score each entry and click **Save** (sheet → FILLED). The grid **Status** column tracks each sheet (`BLANK` → `FILLED` → `SUBMITTED`) and the **Total** column fills in per sheet as you go.
- [ ] **Expected:** Once **every** sheet on the round is FILLED, the top-3 entries (by total, walking gold → silver → bronze, stopping on the first tie within a slot) are auto-populated as MedalAwards. They render with their medal badge.
- [ ] **Expected:** A "tied-slot" banner at the top when ties exist (red text: "{N} slots tied — resolve before finalizing."); tied rows are flagged with a `⚠` marker in the Code column. Resolve a tie by awarding the medal to one tied entry (or clearing awards) until the tie is broken — the view recomputes the cascade live on every action.
- [ ] **Finalize (judge or admin):** when every sheet is FILLED and no tie is open, click **Finalize** (§12.6.8.1) → the sheets are submitted, the medals locked, and the round → COMPLETE in one step. See §12.6.8.1 for the full end-to-end flow.

#### 12.12.3 COMPARATIVE Finalize / Reopen

*(The COMPARATIVE finalize path, where the judges award each medal by hand. **Finalize is judge-or-admin** — the assigned judges who awarded the medals can commit them, and an admin may step in too. **Reopen is admin-only.** The SCORE_BASED judge-driven Finalize is in §12.12.2 / §12.6.8.1.)*

- [ ] On the ACTIVE COMPARATIVE medal round, as **either an assigned judge or `compadmin@example.com`**.
- [ ] **Expected:** A **Finalize** button (visible to judge + admin, enabled while ACTIVE). An admin additionally sees **Reopen** (admin-only, enabled only at COMPLETE). Start + Reset are gone (Start is on the grid; the old Reset is replaced by the grid's ↶ Revert).
- [ ] Award medals to the entries you want (🥇🥈🥉) and **leave the rest with no medal** — there is no requirement to decide every entry (the old undecided-entries guard and the Withhold action are gone).
- [ ] Click **Finalize** → **Expected:** confirm dialog **lists the medals being committed** (Gold / Silver / Bronze counts) **and, in bold, how many entries will receive no medal** (e.g. "3 entries in this category will receive no medal."). Admins additionally see the "you can reopen later" reassurance + the finalize-warning; a judge sees neither (Reopen is admin-only). Click Finalize → notification "Medal round complete"; status → `COMPLETE`; per-row buttons disappear (read-only).
- [ ] Click **Reopen** → confirm → status back to `ACTIVE`; existing MedalAwards preserved (admin can reassign them). For a **SCORE_BASED** medal round (which owns its scoresheets), reopening also drops its **SUBMITTED scoresheets back to FILLED** so the scores can be edited again — without this the medals were reassignable but the sheets stayed locked. (A COMPARATIVE medal round owns no scoresheets, so only the medals are affected.)
- [ ] **To wipe the awards + return the round to READY**, use the unified Rounds grid (§12.6) → ↶ **Revert** on the medal row (confirm body warns the awards are cleared, scoresheets kept). The in-view Reset button is gone.

### 12.13 BosView (dedicated admin form)

*Pre-req: `Judging.phase ∈ {BOS, COMPLETE}` — start BOS from the JudgingAdmin BOS tab after all medal rounds are COMPLETE.*

- [ ] As `compadmin@example.com`, click "Manage placements →" in the JudgingAdmin → BOS tab.
- [ ] **Expected:** URL `competitions/.../divisions/.../bos`.
- [ ] **Expected:** H2 `Best of Show — Amadora`. Header shows `Phase: BOS` and `Places: N` (where N = `Division.bosPlaces`).
- [ ] **Expected:** A placements grid (id `bos-placements-grid`) with N rows (one per slot). Columns: Place, Entry, Mead name, Category, Awarded by, Action.
- [ ] **Expected:** Empty rows show `[+]` Assign button in the Action column. Filled rows show ✏ Reassign and 🗑 Delete.
- [ ] **Expected:** A candidates grid (id `bos-candidates-grid`) listing GOLD MedalAwards across all categories where the entry isn't placed yet. Columns: Entry # (prefixed), Code, Mead name, Category.
- [ ] **Expected:** "← Back to dashboard" anchor at the bottom returning to JudgingAdmin.

#### 12.13.1 Assign

- [ ] Click `[+]` on Place 1.
- [ ] **Expected:** Dialog "Assign place 1" with a `Select<MedalAward>` of unplaced GOLD candidates. Items labelled `{entryCode} · {meadName} · {categoryCode}`. Helper text "Only Gold medal entries are eligible for BOS."
- [ ] Pick a candidate → Save.
- [ ] **Expected:** Notification "Placement 1 recorded."; placements grid row 1 fills in; candidates grid removes that entry.

#### 12.13.2 Reassign

- [ ] Click ✏ on a filled row.
- [ ] **Expected:** Dialog with the current entry preselected in the `Select<MedalAward>`. Available candidates = unplaced GOLDs + current entry.
- [ ] Pick a different entry → Save.
- [ ] **Expected:** Notification "Placement N updated."; row updates; previous entry returns to candidates list.

#### 12.13.3 Delete

- [ ] Click 🗑 on a filled row.
- [ ] **Expected:** Dialog body "Remove {entryCode} from place N?".
- [ ] Click Delete.
- [ ] **Expected:** Notification "Placement N removed."; row returns to empty state; candidate returns to the candidates grid.

#### 12.13.4 Empty BOS allowed

- [ ] Leave at least one place empty and Finalize BOS (from JudgingAdmin BOS tab).
- [ ] **Expected:** Phase flips to `COMPLETE` without error. Per §2.D D11, empty BOS slots are allowed.

#### 12.13.5 Read-only when COMPLETE

- [ ] Re-open `/competitions/.../bos` after Finalize.
- [ ] **Expected:** A banner Span (id `bos-complete-banner`) reads "BOS is COMPLETE. Reopen on the dashboard to edit."
- [ ] **Expected:** The candidates section is hidden entirely.
- [ ] **Expected:** The placements grid's Action column is absent (no `[+]`/✏/🗑 buttons).
- [ ] Click `← Back to dashboard` → click "Reopen BOS" → confirm.
- [ ] **Expected:** Phase returns to `BOS`; `/bos` becomes editable again.

#### 12.13.6 Authorization

- [ ] As `judge@example.com`, navigate directly to `/competitions/chip-2026/divisions/amadora/bos`.
- [ ] **Expected:** Forward to `""` (BOS is admin-only per §4.A).
- [ ] As `entrant@example.com`, navigate to the same URL.
- [ ] **Expected:** Forward to `""`.

### 12.14 JudgeProfile editor

*From `/profile` as `judge@example.com`.*

- [ ] **Expected:** A "Judge profile" section (visible to any user with at least one JudgeAssignment) with:
  - Certifications `MultiSelectComboBox` (options: `MJP`, `BJCP`, `OTHER`)
  - Qualification details `TextField` (e.g. for WSET specifics if `OTHER` selected)
  - Preferred comment language `ComboBox` (same source as ScoresheetView's language ComboBox)
- [ ] Pick `MJP` + `BJCP`, enter qualification text "Judging since 2018", pick `pt` as preferred → Save.
- [ ] **Expected:** Notification; values persist after refresh.

*From `/users` as `admin@example.com` → edit `judge@example.com` user dialog.*

- [ ] **Expected:** A "Judge profile" section in the edit dialog mirroring the self-edit fields.

### 12.15 Cross-module guard — block status revert only when judging is in progress

The `JudgingDivisionStatusRevertGuard` blocks the JUDGING → REGISTRATION_CLOSED revert only when an **ACTIVE or COMPLETE** round exists. Rounds still in PENDING / READY (set up but not started) don't block — admins who advanced to JUDGING and then realised they want to tweak a REG_CLOSED-only setting (BOS places, sharedTables flag, etc.) can revert freely.

- [ ] As `compadmin@example.com`, with Amadora at JUDGING and at least one ACTIVE round, navigate to Amadora division detail.
- [ ] Click "Revert Status" → confirm "Revert from Judging to Registration Closed?".
- [ ] **Expected:** Error notification — *"Cannot revert to REGISTRATION_CLOSED while an active or completed round exists. Revert or reset those rounds first. Rounds still in PENDING or READY don't block the revert — they survive the trip back."* (`error.division.cannot-revert-has-judging`). Status remains `JUDGING`.
- [ ] **Allowed-path check:** Revert the active round back to READY (per §12.6.4.1), then revert the division. **Expected:** revert succeeds; division returns to REGISTRATION_CLOSED with rounds intact (still READY/PENDING) so the admin's setup work is preserved.

### 12.16 StewardView (read-only steward hub)

**Covers:** `StewardViewTest`, `CompetitionServiceTest` (`findCompetitionsBySteward`).

*Pre-req: a user with the STEWARD role in CHIP 2026 — add one via CHIP 2026 →
Participants → Add Participant, role STEWARD (e.g. `steward@example.com`).*

- [ ] Log in as the steward (access code or magic link).
- [ ] **Expected (sidebar):** A "My Stewarding" entry (clipboard icon) appears —
  gated by `StewardChecker.isStewardSomewhere`. Only visible because the user holds
  a STEWARD role somewhere.
- [ ] Click "My Stewarding" (URL `/my-stewarding`).
- [ ] **Expected:** H2 "My Stewarding". For each competition the user stewards, an
  `H3` with the competition name; under it, each JUDGING-or-later division as an
  `H4`, then one card per round.
- [ ] **Expected (per round card):** round name + category (code + name) + status;
  a "Judges: …" line (names, or "—" when none assigned); one `•` line per entry on
  the round showing entry code + mead name.
- [ ] **Expected:** The view is entirely **read-only** — no buttons, no edit actions.
- [ ] **Expected:** A division with no rounds shows "No rounds yet."

#### 12.16.1 Empty-state for a non-steward

- [ ] Log in as a user with no STEWARD role (e.g. `entrant@example.com`).
- [ ] **Expected (sidebar):** "My Stewarding" is *not* present.
- [ ] Navigate directly to `/my-stewarding`.
- [ ] **Expected:** H2 "My Stewarding" + an empty-state message ("You have no
  stewarding assignments yet.").

### 12.17 i18n sanity (judging surfaces only)

*Switch UI language via the language switcher in the user menu (top-right).*

- [ ] For each of `pt`, `es`, `it`, `pl`:
  - Visit JudgingAdminView, TableView, ScoresheetView, MyJudgingView, MedalRoundView, BosView, StewardView.
  - **Expected:** No raw `error.…`, `judging-admin.…`, `medal-round.…` or `steward.…` keys leaking through. Header labels, tab names, column headers, dialog titles, button text, notifications all render in the chosen language.
  - **Expected:** Date/time fields use locale-aware format (DatePicker, NumberField step buttons localized).
  - **Expected:** All score-field labels use canonical English names regardless of UI locale (`Appearance`, `Aroma/Bouquet`, `Flavour and Body`, `Finish`, `Overall Impression`) — these are stored as i18n keys but the canonical English is what's used in `ScoreField.fieldName`.

> The ES/IT/PL translations are draft-quality and intended for native-speaker review. Note any awkward phrasing or terminology disagreements for later correction.

### 12.18 Restore Amadora state (optional cleanup)

- [ ] If you want Amadora to remain testable for entry-side flows, you'll need to remove judging data first:
  - Reset BOS, reset all medal rounds, then delete all scoring rounds (each must be at status `PENDING` with no assignments — uncheck judges via Assign Judges dialog, then delete from the Rounds tab).
  - Once no rounds exist and `Judging.phase = NOT_STARTED`, the `JudgingDivisionStatusRevertGuard` will permit revert.
  - Revert Amadora: JUDGING → REGISTRATION_CLOSED → REGISTRATION_OPEN.
- [ ] **Alternative:** Leave Amadora in JUDGING and use the seeded `Test Competition 2026 > Open` division for further entry-side experiments.

---

## 13. Awards Module

**Covers:** `AwardsServiceImplTest`, `AwardsPublicResultsViewTest`,
`AwardsAdminViewTest`, `MyResultsViewTest`, `AwardsModuleTest`,
`PublicationTest`, `PublicationRepositoryTest`,
`JudgingServiceFreezeGuardTest`, `ScoresheetServiceFreezeGuardTest`,
`ScoresheetPdfServiceTest`.

This section assumes Section 12 left Amadora deep in the judging flow,
with at least one COMPLETE category medal round and ideally one
BosPlacement. If you reset everything at the end of §12 to keep
Amadora reusable, use the seeded `Test Competition 2026 > Open`
division for §13 instead — or re-run a thin slice of §12 first
(start a table, advance the medal round, record one GOLD, finalize the
medal round, place that entry in BOS, complete BOS).

The awards flow advances the division `DELIBERATION → RESULTS_PUBLISHED`.
Steps below are admin-driven unless noted.

### 13.1 Prerequisites — advance to DELIBERATION

*Log in as `compadmin@example.com`.*

- [ ] Navigate to CHIP 2026 → Amadora.
- [ ] **Verify:** Current status is `JUDGING`.

#### 13.1.1 Advance-to-deliberation guard (judging not yet COMPLETE)

- [ ] **Precondition:** §12.13.4 BOS has not been finalized yet (or §12 was reset). `Judging.phase` is `BOS` or earlier.
- [ ] In the division header, click "Advance Status" → confirm
  "Advance from Judging to Deliberation?".
- [ ] **Expected:** Error notification — `JudgingCompleteAdvanceGuard` blocks
  because `Judging.phase != COMPLETE`. Message: *"Cannot move to DELIBERATION
  while judging is in progress. Finalize Best of Show first."* (key
  `error.division.cannot-deliberate-judging-incomplete`).
- [ ] **Expected:** Status stays at `JUDGING`.
- [ ] Complete §12.13.4 (Finalize BOS) — `Judging.phase` flips to `COMPLETE`.

#### 13.1.2 Advance to DELIBERATION (judging COMPLETE)

- [ ] Click "Advance Status" again → confirm.
- [ ] **Expected:** Status badge updates to `DELIBERATION`.
- [ ] Navigate to Manage Judging.
- [ ] **Expected:** A new "Manage Results" button appears in the JudgingAdminView
  header (between the title and the tabs).

#### 13.1.3 Manual advance to RESULTS_PUBLISHED is blocked

- [ ] Back on the Amadora division detail, click "Advance Status" → confirm
  "Advance from Deliberation to Results Published?".
- [ ] **Expected:** Error notification — `BlockManualPublishAdvanceGuard`
  rejects the manual advance. Message: *"Use 'Publish results' in the Results
  admin view instead of the manual status advance"* (key
  `error.division.use-publish-results-instead`).
- [ ] **Expected:** Status stays at `DELIBERATION`. The only way to reach
  `RESULTS_PUBLISHED` is through `AwardsService.publish()` (§13.2) or
  `republish()` (§13.8), both of which create a Publication audit row.

### 13.2 Publish — first publication

*Stay on the Amadora JudgingAdminView header.*

- [ ] Click "Manage Results".
- [ ] **Expected:** Navigates to `/competitions/chip-2026/divisions/amadora/results-admin`
  with breadcrumb `Competitions / CHIP 2026 / Amadora / Results admin`.
- [ ] **Expected:** Page shows "CHIP 2026 — Amadora — Results admin" heading.
- [ ] **Expected:** A single "Publish results" primary button is visible in the
  actions row (no Re-publish / Send announcement / Revert yet — those only
  appear post-publication).
- [ ] **Expected:** A "Publication history" section is rendered below with an
  empty grid (columns: Version, Published at, Published by, Justification).
- [ ] Click "Publish results".
- [ ] **Expected:** Confirmation dialog appears: "Publish results for this
  division?" with body explaining the freeze + advance + no-auto-email.
- [ ] Click "Publish results" in the dialog.
- [ ] **Expected:** Green success notification: "Results published
  successfully."; page reloads.
- [ ] **Expected:** Status of the division is now `RESULTS_PUBLISHED` (verify
  on DivisionDetailView or RootView redirect).
- [ ] **Expected:** Mailpit at `http://localhost:8025` shows **no new mail**
  (publish never sends emails).
- [ ] **Expected:** Publication history grid now has one row: version `1`,
  current timestamp, "compadmin" (or admin's display name), justification
  empty.

### 13.3 Public results page

*Open an incognito / logged-out browser window.*

- [ ] Visit `http://localhost:8080/competitions/chip-2026/divisions/amadora/results`.
- [ ] **Expected:** Page renders without requiring login (`@AnonymousAllowed`).
- [ ] **Expected:** Heading "CHIP 2026 — Amadora — Results".
- [ ] **Expected:** "Best of Show" section visible only when at least one
  BosPlacement exists; columns: Place, Mead, Meadery (no entry IDs).
- [ ] **Expected:** Per-category sections rendered for each category that has
  at least one medal awarded. Within each section, separate blocks for Gold /
  Silver / Bronze, listing `Mead name — Meadery name` only (no entry IDs, no
  entrant names, no category in BOS rows).
- [ ] **Expected:** Entries without a medal are **not** rendered in the medal blocks (only Gold/Silver/Bronze entries appear).
- [ ] Hard refresh the page in a logged-out window for a division still in
  `REGISTRATION_OPEN` (e.g., another division of CHIP 2026 you haven't published):
  `/competitions/chip-2026/divisions/amadora-old/results`.
- [ ] **Expected:** View forwards back to root — no leak of unpublished results.
- [ ] **(Optional, language switch)** Change the navbar language (or open in a
  different browser locale) and reload — labels translate per locale.

### 13.4 Entrant view — banner + results

*Log in as an entrant who has entries in Amadora.* For dev: `entrant1@example.com`
(magic-link login via Mailpit).

- [ ] Navigate to `/competitions/chip-2026/divisions/amadora/my-entries`.
- [ ] **Expected:** A green banner appears at the top: "Results have been
  published. View your results" with the second clause as a link.
- [ ] Click "View your results".
- [ ] **Expected:** Navigates to `/competitions/chip-2026/divisions/amadora/my-results`.
- [ ] **Expected:** Heading "CHIP 2026 — Amadora — Your results".
- [ ] **Expected:** Grid with columns: Entry, Mead, Category, Round 1 total
  (`N / 100` or `—`), Advanced (Yes/No), Medal (Gold / Silver / Bronze / —),
  BOS place (number or —), Action.
- [ ] **Expected:** Entries with no medal render as `—` in the Medal column.
- [ ] **Expected:** "View scoresheet" button is enabled only for rows whose
  scoresheet is SUBMITTED.

### 13.5 Scoresheet drill-in (entrant)

*Stay on MyResultsView.*

- [ ] Click "View scoresheet" on a submitted-scoresheet row.
- [ ] **Expected:** Navigates to
  `/competitions/chip-2026/divisions/amadora/my-entries/{entryId}/scoresheet`.
- [ ] **Expected:** Heading shows entry code + mead name (e.g., `AMA-3 — My
  Wildflower`). Category line below.
- [ ] **Expected:** One card per submitted scoresheet (likely just one in dev),
  headed "Judge 1" — **no judge name or certifications**. Comment language
  line, then 5 score fields rendered as `field: value / max`, then total, then
  overall comments (if any).
- [ ] **Expected:** "Download PDF" anchor is rendered as a download link.
- [ ] Click "Download PDF".
- [ ] **Expected:** PDF downloads. Open it: heading "Anonymized Scoresheet",
  entry/mead/category in a 2-col table, "Judge 1" label (never the real name),
  scores table, total, overall comments. Liberation Sans font (Unicode-safe).
- [ ] **Expected:** Back link "Back to results" returns to MyResultsView.

### 13.6 Freeze guard — judging mutations rejected

*Log back in as `compadmin@example.com`.*

- [ ] Navigate to Amadora → Manage Judging → Rounds tab → set Type filter to `Medal` → click Open on any medal row to land on MedalRoundView.
- [ ] Try to record / change a medal (e.g., click `🥇` on a row).
- [ ] **Expected:** Notification with message:
  *"Results have been published — judging data cannot be modified. Revert
  the publication first."* (i18n key `error.judging.results-published-frozen`)
- [ ] Try the same on the Rounds tab (e.g., add a round, assign a judge,
  start a round).
- [ ] **Expected:** Same frozen notification.
- [ ] Try the same in BOS (record/update/delete a placement).
- [ ] **Expected:** Same frozen notification.
- [ ] Navigate into a table → ScoresheetView (admin entry) and try to edit a
  scoresheet (update a score, revert to draft, set comment language).
- [ ] **Expected:** Same frozen notification on save.

### 13.7 Revert publication

*Navigate to Manage Results.*

- [ ] **Expected:** Two buttons in the actions row: "Send announcement"
  (primary), "Revert publication" (error variant). (At RESULTS_PUBLISHED there
  is no "Re-publish" button — re-publishing requires reverting first so the
  judging data unfreezes for edits.)
- [ ] Click "Revert publication".
- [ ] **Expected:** Dialog with body explaining roll-back to DELIBERATION,
  audit-log preservation. Below the body, a TextField "Type REVERT to confirm".
- [ ] Leave the field empty (or type something else like `revert`) and click
  the in-dialog "Revert publication" button.
- [ ] **Expected:** Notification "Type REVERT exactly to confirm." — the
  publication is NOT reverted.
- [ ] Type `REVERT` (uppercase) and click again.
- [ ] **Expected:** Green success notification "Publication reverted."; page
  reloads. Status reverts to `DELIBERATION`.
- [ ] **Expected:** Publication history grid still shows version 1 — the audit
  record is preserved.
- [ ] Verify the public results page now forwards away (logged-out): visit
  `/competitions/chip-2026/divisions/amadora/results` → redirects to root.
- [ ] Verify the entrant banner is gone on `/my-entries`.

### 13.8 Edit judging data + re-publish

*Stay on Manage Results — status is now `DELIBERATION`.*

- [ ] **Expected:** The actions row now shows a single "Re-publish" primary
  button (because a prior Publication exists, the view shows "Re-publish"
  instead of "Publish results" at DELIBERATION).
- [ ] Navigate to Manage Judging → Rounds tab → set Type filter to `Medal` → click Open on the M1A medal row to land on MedalRoundView.
- [ ] Pick a medal in some category and change its value (e.g., SILVER →
  BRONZE on one entry) by clicking the new medal button on the row.
- [ ] **Expected:** Save succeeds (no frozen notification — data is editable
  again in DELIBERATION).
- [ ] Navigate to Manage Results → click "Re-publish".
- [ ] **Expected:** Dialog with title "Re-publish results" and a TextArea
  labeled "Justification (required)" with helper text about the 20-char
  minimum.
- [ ] Type a short string (e.g., `oops`) and click the in-dialog "Re-publish".
- [ ] **Expected:** Error notification ending with
  `error.awards.justification-too-short` (locale-dependent text); dialog stays
  open.
- [ ] Replace with a real justification: `Corrected silver to bronze in M1A
  after spreadsheet error.` and click "Re-publish".
- [ ] **Expected:** Green success notification "Results re-published
  successfully. You may now send an announcement."; page reloads.
- [ ] **Expected:** Status flips to `RESULTS_PUBLISHED` (republish advances
  the status itself via `CompetitionService.publishDivision` — no separate
  manual advance needed).
- [ ] **Expected:** Publication history now has two rows (version 1 and
  version 2 with the justification populated).
- [ ] **Expected:** No email sent (verify Mailpit empty).

### 13.9 Send announcement — initial template

*Revert your dev state so the latest publication is version 1* (or
work against another division at its first publication).
For this step, the script below assumes you're at version 1 — if you ran
13.8 you'll be at version 2, which is fine but the email type will be the
republish variant. Adjust expectations accordingly.

*From Manage Results, version 1 case:*

- [ ] Click "Send announcement".
- [ ] **Expected:** Dialog "Send results announcement" with a TextArea
  labeled "Optional custom message" + helper text about leaving empty for
  defaults.
- [ ] Leave the message blank and click "Send announcement".
- [ ] **Expected:** Green success notification "Announcement queued for
  delivery."; dialog closes.
- [ ] Open Mailpit at `http://localhost:8025`.
- [ ] **Expected:** One email per entrant in the division (each entrant gets
  a single email — even those without entries don't, since the recipient
  list comes from distinct entry `userId`s).
- [ ] Open one email.
- [ ] **Expected:** Subject "Your CHIP 2026 — Amadora results are available".
- [ ] **Expected:** Body uses the standard "results published" template:
  heading "Results are available" + intro body + a "View results" CTA
  button whose link is a magic-link URL that lands on
  `/competitions/chip-2026/divisions/amadora/my-entries`.
- [ ] **Expected:** Subject + body render in the entrant's `preferredLanguage`
  locale (verify by setting one entrant's preferredLanguage to `pt` and
  re-sending — that recipient gets the PT email).

### 13.10 Send announcement — republish template

*Repeat against a division at version ≥ 2 (use the state from 13.8).*

- [ ] Click "Send announcement" → leave message blank → submit.
- [ ] **Expected:** Mailpit shows one email per entrant.
- [ ] **Expected:** Subject "CHIP 2026 — Amadora results have been updated".
- [ ] **Expected:** Body uses the republish template: heading "Results
  updated", intro line *"The results for CHIP 2026 — Amadora have been
  updated. Reason given by the administrator:"*, followed by the justification
  from the latest publication as a second paragraph (`bodyText2`).
- [ ] **Expected:** CTA still goes to the entrant's My Entries (magic link).

### 13.11 Send announcement — custom message

*From Manage Results.*

- [ ] Click "Send announcement".
- [ ] In the TextArea type: `Thank you for participating! Awards ceremony
  is this Saturday at 19:00.` and click "Send announcement".
- [ ] **Expected:** Green success; Mailpit shows one email per entrant.
- [ ] Open one email.
- [ ] **Expected:** Subject "Update from CHIP 2026 — Amadora".
- [ ] **Expected:** Heading "Announcement"; body is exactly the typed
  message (no justification, no default template body).

### 13.12 Anonymity sanity check

*Log in as `entrant1@example.com` and re-run §13.5 against the current
state.* (After §13.8, that entrant may see a different medal — that's fine.)

- [ ] **Expected:** Scoresheet view still shows "Judge 1" — never the real
  judge name or certifications.
- [ ] Download PDF.
- [ ] **Expected:** PDF body shows "Judge 1" only.

*Note on the admin path:* admin-side scoresheet surfaces (Manage Judging →
Tables → a table → a scoresheet) show real judge names, confirming the
non-anonymized path. There is currently **no admin-facing FULL-mode PDF
download** — `ScoresheetPdfService`'s `AnonymizationLevel.FULL` is implemented
and unit-tested but has no UI caller. The only scoresheet PDF download in the
app is the entrant's ANONYMIZED one (§13.5). Wiring a FULL-mode admin download
is a deferred post-v0.4.0 item.

### 13.13 Cleanup

*If you want Amadora to remain testable for re-runs:*

- [ ] Revert the publication (§13.7 procedure) until status is DELIBERATION.
- [ ] Manually revert further status changes (DELIBERATION → JUDGING) as
  desired. Note: the `JudgingDivisionStatusRevertGuard` may block further
  reverts depending on judging state.

---

## 14. Cross-cutting Concerns

### Mutual exclusivity (end-to-end)

**Covers:** `EntryServiceTest` (shouldRejectAddCreditsWhenMutualExclusivityViolated)

- [ ] Log in as `compadmin@example.com`
- [ ] Navigate to Profissional division entry-admin
- [ ] Click "Add Credits", enter email: `entrant@example.com` (who has Amadora credits), amount: 1
- [ ] Click "Add"
- [ ] **Expected:** Error -- mutual exclusivity violation

### Idempotency (restart app)

**Covers:** `DevDataInitializerTest` (shouldBeIdempotent)

- [ ] Stop the application (Ctrl+C)
- [ ] Run `mvn spring-boot:run -Dspring-boot.run.profiles=dev` again
- [ ] Navigate to `/competitions` as admin
- [ ] **Expected:** Still exactly 2 competitions (CHIP 2026, Test Competition 2026) -- no duplicates
- [ ] Navigate to CHIP 2026 > Amadora entries
- [ ] **Expected:** Same entries as before restart -- no duplicates

### Authorization boundaries

**Covers:** `MyEntriesViewTest` (shouldRedirectUnauthorizedEntrant), `DivisionEntryAdminViewTest`,
`DivisionDetailViewTest` (shouldRedirectUnauthorizedUser)

- [ ] Log in as `user@example.com` (entrant)
- [ ] Navigate directly to `/competitions/chip-2026/divisions/amadora/entry-admin`
- [ ] **Expected:** Redirected to `/` (entrants cannot access admin view)
- [ ] Log in as `judge@example.com`
- [ ] Navigate directly to `/competitions/chip-2026/divisions/amadora/entry-admin`
- [ ] **Expected:** Redirected to `/` (judges cannot access admin view)
- [ ] Navigate directly to `/competitions/chip-2026/divisions/amadora/my-entries`
- [ ] **Expected:** Redirected to `/` (judge has no credits in this division)

### Status workflow -- category lock

**Covers:** `CompetitionServiceTest` (category modification restrictions)

- [ ] Log in as `compadmin@example.com`
- [ ] Navigate to Test Competition 2026 > Open division detail (via My Competitions)
- [ ] Verify the Open division is in DRAFT status
- [ ] **Expected:** "Add Category" button is enabled, "Remove" buttons are enabled
- [ ] Advance status to REGISTRATION_OPEN
- [ ] **Expected:** "Add Category" button is still enabled (REGISTRATION_OPEN allows modification)
- [ ] Advance status to REGISTRATION_CLOSED
- [ ] **Expected:** "Add Category" button is disabled
- [ ] **Expected:** "Remove" buttons are disabled
- [ ] **Expected:** Categories are locked for the rest of the workflow

### DivisionStatus full progression

- [ ] Continue advancing the Open division: REGISTRATION_CLOSED > JUDGING > DELIBERATION > RESULTS_PUBLISHED
- [ ] **Expected:** Each advance shows confirmation dialog with correct from/to statuses
- [ ] **Expected:** After RESULTS_PUBLISHED, "Advance Status" button is hidden or disabled
- [ ] Revert from RESULTS_PUBLISHED back one step at a time to DRAFT
- [ ] **Expected:** Each revert shows confirmation dialog with correct from/to statuses
- [ ] **Expected:** After DRAFT, "Revert Status" button is hidden

---

## 15. Multi-Role & Cross-Competition Edge Cases

**Goal:** Test combinations of roles across competitions and identify gaps in
credential management and authorization. Some of these are exploratory — note
the actual behavior and decide whether it needs to change.

### Same competition: multiple roles

- [x] Log in as `compadmin@example.com`
- [x] Navigate to CHIP 2026 > Participants tab
- [x] Add `judge@example.com` as ENTRANT (judge is already a JUDGE in CHIP)
- [x] **Observe:** JUDGE + ENTRANT is allowed. All other role combinations are rejected with a validation error.
- [x] **Decision:** Only JUDGE + ENTRANT combination is valid in the same competition. Enforced at service level (`CompetitionService.validateRoleCombination`).
- [x] Participant grid shows one row per participant with comma-separated roles. Edit button (pencil icon) opens role checkboxes + user fields dialog.
- [x] Remove button removes the entire participant (all roles). Role removal via edit dialog (uncheck a role).
- [x] Clean up: remove the ENTRANT role from `judge@example.com` if needed

### Cross-competition: entrant becomes competition admin

This is the most important edge case. A user who is an ENTRANT in one competition
may be invited as a competition ADMIN for a different competition.

- [x] Log in as `admin@example.com` (SYSTEM_ADMIN)
- [x] Create a new competition (e.g., "Regional 2026")
- [x] Add `entrant@example.com` as ADMIN of "Regional 2026"
- [x] Log out
- [x] **Test:** `entrant@example.com` receives a password setup email when added as ADMIN.
- [x] **Decision:** Competition admins must have a password. Admin views (MyCompetitionsView, CompetitionDetailView, DivisionDetailView, DivisionEntryAdminView) check for password and block access with a notification if not set.
- [x] **Decision:** RootView checks if comp admin has a password before redirecting to `/my-competitions` — prevents redirect loop for passwordless comp admins (they fall through to `/my-entries` instead).
- [x] **Test navigation:** After setting password, `entrant@example.com` sees "My Competitions" in sidebar and can still access entries in CHIP 2026 Amadora.

### Cross-competition: competition admin is also entrant/judge elsewhere

- [x] Log in as `admin@example.com` (SYSTEM_ADMIN)
- [x] Navigate to a different competition's division entry-admin (e.g., Regional 2026 or Test Competition 2026)
- [x] Add credits for `compadmin@example.com` in that division (makes them an ENTRANT in a different competition)
- [x] Log out, log back in as `compadmin@example.com`
- [x] **Test:** Can access both "My Competitions" (as ADMIN of CHIP 2026) and My Entries (as ENTRANT in the other competition). Works correctly.
- [x] **Tested role conflict:** Adding credits to a user with an incompatible role (e.g., STEWARD) triggers role validation. WebhookService and EntryService both check `hasIncompatibleRolesForEntrant()` before awarding credits. Orders/line items marked NEEDS_REVIEW with reason visible in Orders grid (Review Reason column with tooltip).
- [x] Clean up if needed

### Login mechanism with mixed credentials

- [x] A user with both a password and a magic link should be able to use either — password users requesting magic link get a credentials reminder email (intended behavior)
- [x] A user with an access code (JUDGE/STEWARD) and a password (competition ADMIN in another competition) should be able to use either — confirmed working
- [x] **Test:** `judge@example.com` can still log in with access code after being made ADMIN of another competition
- [x] **Decision:** Access codes authenticate user identity (full account access), not per-competition session. Password requirement on admin views prevents access code users from reaching admin features without a password. Per-competition scoping deferred — acceptable for now, revisit when multiple competitions exist.

### Summary of decisions made

1. **Multiple roles in same competition** — Only JUDGE + ENTRANT combination allowed. All others rejected at service level. Enforced in participant management, webhook processing, and admin credit assignment.
2. **Credential setup for new competition admins** — Password setup email sent automatically. Admin views require password (check in `beforeEnter()`). RootView prevents redirect loop for passwordless comp admins.
3. **Navigation clarity** — Participant grid shows one row per participant with comma-separated roles. Edit dialog with role checkboxes + user info fields. Remove button removes entire participant.
4. **Access code scope** — Per-user identity authentication (not per-competition session). Password gate on admin views provides sufficient separation. Per-competition scoping deferred to when multiple competitions exist.

---

## 16. Security Testing

**Goal:** Verify the application is resilient to common web attacks (OWASP Top 10)
across all input surfaces. Use browser dev tools, Mailpit, and direct HTTP requests.

*Log in as `admin@example.com` for most tests (SYSTEM_ADMIN has broadest access).*

### XSS — Stored (text fields rendered in grids and dialogs)

These fields accept free text that is later rendered in grids, dialogs, breadcrumbs,
and email templates. Vaadin's server-side rendering should escape HTML by default,
but verify each surface.

**Payload:** `<script>alert('xss')</script>` and `<img src=x onerror=alert(1)>`

- [ ] Navigate to `/users`, create a user with name: `<script>alert('xss')</script>`
- [ ] **Expected:** Name appears as literal text in the grid, no script execution
- [ ] Edit the user — verify the dialog shows the literal text, not rendered HTML
- [ ] Delete the test user

- [ ] Navigate to `/competitions`, create a competition with name: `<img src=x onerror=alert(1)>`
- [ ] **Expected:** Name appears as literal text in the grid and header
- [ ] Navigate to the competition detail — verify breadcrumb, header, and tabs show literal text
- [ ] Delete the competition

- [ ] As entrant, navigate to My Entries and create an entry with:
  - Mead name: `<script>alert('xss')</script>`
  - Honey varieties: `"><img src=x onerror=alert(1)>`
  - Other ingredients: `<svg onload=alert(1)>`
  - Additional info: `javascript:alert(1)`
- [ ] **Expected:** All fields render as literal text in the entry grid and view dialog
- [ ] As admin, view the entry in DivisionEntryAdminView — verify literal text in admin grid
- [ ] Delete or withdraw the entry

- [ ] Create a competition with contact email: `"><script>alert(1)</script>@evil.com`
- [ ] **Expected:** Vaadin's `EmailField` rejects the input as invalid — XSS payload cannot be stored

### XSS — Reflected (URL parameters)

- [ ] Navigate to `http://localhost:8080/login?error=<script>alert(1)</script>`
- [ ] **Expected:** Error notification shows generic "Invalid email or password" text, not the parameter value
- [ ] Navigate to `http://localhost:8080/set-password?token=<script>alert(1)</script>`
- [ ] **Expected:** "Invalid or expired token" error notification, no form rendered, no script execution

### XSS — Route parameters

- [ ] Navigate to `http://localhost:8080/competitions/<script>alert(1)</script>`
- [ ] **Expected:** "Access denied" — Spring Security's `StrictHttpFirewall` blocks URLs with `<` and `>`. No script execution.
- [ ] Navigate to `http://localhost:8080/competitions/chip-2026/divisions/<img%20src=x%20onerror=alert(1)>`
- [ ] **Expected:** Division not found — redirected. No script execution.

### SQL Injection — Text fields

**Payload:** `' OR '1'='1` and `'; DROP TABLE users; --`

- [ ] Navigate to `/users`, click "Create User"
- [ ] Enter email: `test@example.com`, name: `' OR '1'='1`
- [ ] Click "Save"
- [ ] **Expected:** User created with the literal name (Spring Data JPA uses parameterized queries)
- [ ] Verify the grid shows exactly: `' OR '1'='1` — no extra rows, no error
- [ ] Delete the test user

- [ ] Create an entry with mead name: `'; DROP TABLE entries; --`
- [ ] **Expected:** Entry created normally, mead name stored as literal text
- [ ] Verify in admin grid — no database error, literal text displayed

### SQL Injection — Route parameters

- [ ] Navigate to `http://localhost:8080/competitions/' OR '1'='1`
- [ ] **Expected:** Competition not found — redirect. No SQL error exposed.
- [ ] Navigate to `http://localhost:8080/competitions/chip-2026/divisions/' UNION SELECT * FROM users --`
- [ ] **Expected:** Division not found — redirect. No data leakage.

### SQL Injection — Webhook endpoint

```bash
curl -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: invalid" \
  -d '{"order": {"id": "1 OR 1=1"}}'
```

- [ ] **Expected:** 401 Unauthorized (HMAC check fails before any DB access)

### JWT Token Manipulation

- [ ] Navigate to `http://localhost:8080/login/magic?token=expired.token.here`
- [ ] **Expected:** Redirected to `/login?error` — expired/invalid tokens rejected
- [ ] Navigate to `http://localhost:8080/set-password?token=` (empty token)
- [ ] **Expected:** Redirected to `/login` (empty token handled gracefully)
- [ ] Navigate to `http://localhost:8080/set-password` (no token parameter)
- [ ] **Expected:** Redirected to `/login`
- [ ] Navigate to `http://localhost:8080/set-password?token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBleGFtcGxlLmNvbSIsImV4cCI6OTk5OTk5OTk5OX0.invalidsignature`
- [ ] **Expected:** "Invalid or expired token" error notification — forged tokens rejected, no form rendered

### Authorization Bypass — Direct URL access

- [ ] Log in as `user@example.com` (regular USER, not admin)
- [ ] Navigate directly to `http://localhost:8080/users`
- [ ] **Expected:** Redirected away (not authorized for admin page)
- [ ] Navigate directly to `http://localhost:8080/competitions`
- [ ] **Expected:** Redirected away (SYSTEM_ADMIN only)
- [ ] Navigate to `http://localhost:8080/competitions/chip-2026`
- [ ] **Expected:** Redirected away (not a competition ADMIN)
- [ ] Navigate to `http://localhost:8080/competitions/chip-2026/divisions/amadora/entry-admin`
- [ ] **Expected:** Redirected away (not authorized for admin view)

- [ ] Log in as `judge@example.com` (JUDGE in CHIP, but not ADMIN)
- [ ] Navigate directly to `http://localhost:8080/competitions/chip-2026`
- [ ] **Expected:** Redirected to root (judge is not competition ADMIN)
- [ ] Navigate to entry admin URL for Amadora
- [ ] **Expected:** Redirected (not authorized)

- [ ] Log out completely
- [ ] Navigate to `http://localhost:8080/competitions/chip-2026`
- [ ] **Expected:** Redirected to `/login` (unauthenticated)

### Authorization Bypass — Webhook without signature

```bash
curl -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -d '{"order": {"id": "99999", "status": "Paid", "customer": {"email": "evil@example.com"}, "products": []}}'
```

- [ ] **Expected:** 401 Unauthorized (missing HMAC header)

```bash
curl -X POST http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -H "Jumpseller-Hmac-Sha256: tampered-signature" \
  -d '{"order": {"id": "99999", "status": "Paid", "customer": {"email": "evil@example.com"}, "products": []}}'
```

- [ ] **Expected:** 401 Unauthorized (invalid HMAC signature)

### CSRF Protection

Vaadin uses its own CSRF mechanism via sync tokens in the UIDL protocol (not traditional
`_csrf` form fields). The login POST is exempted by `VaadinSecurityConfigurer` (standard
Vaadin behavior). The webhook endpoint has a dedicated `SecurityFilterChain` with CSRF
disabled (stateless API, authenticated via HMAC).

- [ ] Open browser dev tools, inspect any Vaadin UIDL POST (e.g., click a button)
- [ ] **Expected:** Request includes `csrfToken` in the Vaadin communication protocol
- [ ] The login `POST /login` does not include a `_csrf` field — this is expected (Vaadin exempts it)
- [ ] **Expected:** CSRF protection is active for all Vaadin UI interactions

### Email Enumeration Prevention

- [ ] Navigate to `/login`
- [ ] Enter email: `user@example.com` (registered), click "Get Login Link"
- [ ] **Expected:** Notification: "If this email is registered, a login link has been sent."
- [ ] Enter email: `nonexistent@example.com` (not registered), click "Get Login Link"
- [ ] **Expected:** Same notification: "If this email is registered, a login link has been sent."
- [ ] **Expected:** Response time is similar for both (no timing side-channel)
- [ ] Repeat with "Forgot password?" — verify same notification for both cases

### Path Traversal — Route parameters

- [ ] Navigate to `http://localhost:8080/competitions/../../users` (as non-admin)
- [ ] **Expected:** Redirected away — authorization prevents access regardless of path tricks
- [ ] Navigate to `http://localhost:8080/competitions/chip-2026/divisions/../../` (as non-admin)
- [ ] **Expected:** Redirected — the Vaadin router treats the full path segment as a route parameter,
  so `../../` is treated as a short name, not a traversal. Authorization layer blocks access.

### File Upload Validation

- [ ] Navigate to competition Settings tab
- [ ] Attempt to upload a file larger than 2.5 MB
- [ ] **Expected:** Upload rejected with error notification (client-side check)
- [ ] Attempt to upload a `.gif` or `.svg` file (wrong MIME type)
- [ ] **Expected:** Upload rejected (accepted types: `image/png`, `image/jpeg` only)
- [ ] Attempt to upload an HTML file renamed to `.png`
- [ ] **Expected:** Upload may succeed client-side, but logo is stored as binary and served as
  base64 data URI with the declared content type — no HTML execution risk

### Input Length / Boundary Testing

All text fields have `setMaxLength()` matching their DB column sizes. All email fields
have `maxLength(255)`, all password fields have `maxLength(128)`.

- [ ] Attempt to type 500+ characters in a competition name field
- [ ] **Expected:** Input is blocked at 255 characters (client-side `maxLength`)
- [ ] Attempt to type 10,000+ characters in an entry TextArea field
- [ ] **Expected:** Input is blocked at field limit (500 for most, 1000 for additional info)
- [ ] Attempt to paste an extremely long email (500+ characters) in the login field
- [ ] **Expected:** Input is blocked at 255 characters (client-side `maxLength`)

### IDOR — Accessing other users' entries

- [ ] Log in as `user@example.com`, note a division and entry they own
- [ ] Log in as `entrant@example.com`
- [ ] **Observe:** Can `entrant@example.com` see entries belonging to `user@example.com`?
- [ ] **Expected:** MyEntriesView only shows entries for the logged-in user
- [ ] **Expected:** No way to edit or view another user's entry details through the UI

### HTTP Method Tampering — Webhook

```bash
curl -X GET http://localhost:8080/api/webhooks/jumpseller/order-paid
```

- [ ] **Expected:** 405 Method Not Allowed (only POST accepted)

```bash
curl -X PUT http://localhost:8080/api/webhooks/jumpseller/order-paid \
  -H "Content-Type: application/json" \
  -d '{}'
```

- [ ] **Expected:** 405 Method Not Allowed

### Error Message Information Leakage

- [ ] Trigger various errors and verify no stack traces, SQL queries, or internal paths are shown:
  - Invalid login credentials → generic "Invalid email or password"
  - Invalid JWT token → "Invalid or expired token"
  - Competition not found → redirect (no error details)
  - Authorization failure → redirect (no "you don't have permission" leaking resource existence)
- [ ] Check server logs for any sensitive data exposure (passwords, tokens in log messages)
- [ ] **Expected:** Passwords are never logged (dev user passwords removed from log output).
  JWT tokens never appear in logs. Access codes appear in participant grids only.

---

## Appendix: Coverage Mapping

| Walkthrough Section | Automated Tests |
|---|---|
| 2. Authentication | `LoginViewTest`, `SetPasswordViewTest`, `AdminPasswordAuthenticationTest`, `JwtMagicLinkAuthenticationTest`, `RootUrlRedirectTest`, `LogoutFlowTest`, `UserActivationListenerTest`, `SecurityConfigTest`, `AccessCodeAwareAuthenticationProviderTest`, `DevUserInitializerTest` |
| 3. Navigation & Layout | `MainLayoutTest`, `RootUrlRedirectTest`, `MyCompetitionsViewTest`, `ProfileViewTest` |
| 4. User Management | `UserListViewTest`, `UserServiceTest`, `UserServiceValidationTest`, `UserTest`, `AdminInitializerTest` |
| 5. Competition Management | `CompetitionListViewTest`, `CompetitionServiceTest`, `CompetitionTest` |
| 6. Competition Detail | `CompetitionDetailViewTest`, `CompetitionServiceTest`, `DivisionTest`, `ParticipantTest`, `ParticipantRoleTest` |
| 7. Division Detail | `DivisionDetailViewTest`, `CompetitionServiceTest`, `DivisionCategoryRepositoryTest`, `CategoryRepositoryTest`, `DivisionStatusTest`, `EntryDivisionRevertGuardTest` |
| 8. Entry Admin | `DivisionEntryAdminViewTest`, `EntryServiceTest`, `ProductMappingRepositoryTest`, `JumpsellerOrderRepositoryTest` |
| 9. Webhook | `JumpsellerWebhookControllerTest`, `WebhookServiceTest`, `JumpsellerOrderTest`, `JumpsellerOrderLineItemTest` |
| 10–11. My Entries | `MyEntriesViewTest`, `EntryServiceTest`, `EntryTest`, `EntryCreditRepositoryTest`, `EntryRepositoryTest` |
| 12. Judging Module | `JudgingAdminViewTest`, `RoundViewTest`, `ScoresheetViewTest`, `MyJudgingViewTest`, `BosViewTest`, `JudgingServiceTest`, `ScoresheetServiceTest`, `JudgeProfileServiceTest`, `MeaderyNameNormalizerTest`, `CoiCheckServiceTest`, `JudgingDivisionStatusRevertGuardTest`, `JudgingMinJudgesLockGuardTest`, `JudgingErrorKeyCoverageTest`, `JudgingNotificationListenerTest`, `JudgingRepositoryTest`, `JudgingRoundRepositoryTest`, `CategoryJudgingConfigRepositoryTest`, `ScoresheetRepositoryTest`, `MedalAwardRepositoryTest`, `BosPlacementRepositoryTest`, `JudgeProfileRepositoryTest` |
| 13. Awards Module | `AwardsServiceImplTest`, `AwardsPublicResultsViewTest`, `AwardsAdminViewTest`, `MyResultsViewTest`, `AwardsModuleTest`, `PublicationTest`, `PublicationRepositoryTest`, `JudgingServiceFreezeGuardTest`, `ScoresheetServiceFreezeGuardTest`, `ScoresheetPdfServiceTest` |
| 14. Cross-cutting | `EntryServiceTest`, `DevDataInitializerTest`, `EntryModuleTest`, `CompetitionModuleTest`, `ModulithStructureTest` |
| 15. Multi-Role | (exploratory; no dedicated automated tests — covered indirectly by service-level role-combination tests in `CompetitionServiceTest` and `EntryServiceTest`) |
| 16. Security | `SecurityConfigTest`, `JumpsellerWebhookControllerTest`, `SmtpEmailServiceTest`, `JwtMagicLinkServiceTest`, `LoginViewTest` |

### Tests without direct manual coverage

These tests cover internal behavior not directly visible in the UI:

- `JwtMagicLinkServiceTest` -- token generation/validation internals
- `SmtpEmailServiceTest` -- email sending (SMTP + Thymeleaf template rendering)
- `DatabaseUserDetailsServiceTest` -- UserDetailsService internals
- `AdminInitializerIntegrationTest` -- startup initialization
- `UserRepositoryTest`, `CompetitionRepositoryTest`, `DivisionRepositoryTest`, `ParticipantRepositoryTest`, `ParticipantRoleRepositoryTest` -- persistence layer
- `JumpsellerOrderLineItemRepositoryTest` -- persistence layer
- `RegistrationClosedListenerTest` -- event listener skeleton
- `CompetitionAccessCodeValidatorTest` -- access code validation internals
- `MeadsApplicationTest` -- context loading
