# Manual Walkthrough / Test

Comprehensive manual test plan for MEADS. Covers every user-facing behavior and API
endpoint across identity, competition, and entry modules. Organized by workflow area
with checkboxes for progress tracking.

**Date:** 2026-05-12
**Seeded data:** Dev profile (`spring.profiles.active=dev`)

> Section 12 (Judging Module) drives Amadora through `REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING`. Section 12.17 explains how to clean up afterwards if you want Amadora to remain testable for entry-side flows; alternatively, run §12 against Amadora last or use the seeded `Test Competition 2026 > Open` division for further entry-side experiments.

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
| `steward@example.com` | Dev Steward | USER | ACTIVE | Magic link (see Mailpit) |
| `entrant@example.com` | Dev Entrant | USER | ACTIVE | Magic link (see Mailpit) |

### Seeded competition data (CHIP 2026)

- **Competition:** CHIP 2026 (June 11-14, 2026, Amarante, Portugal)
- **Divisions:** Amadora (Amateur) and Profissional (Commercial) -- both REGISTRATION_OPEN, MJP scoring
- **Entry limits:** 3 per subcategory, 5 per main category (both divisions)
- **Entry prefixes:** Amadora = "AMA", Profissional = "PRO"
- **Categories:** Full MJP catalog minus M4B and M4D
- **Participants:**
  - `compadmin@example.com` -- ADMIN
  - `judge@example.com` -- JUDGE (has access code)
  - `steward@example.com` -- STEWARD (has access code)
  - `user@example.com` -- ENTRANT (5 credits in Amadora)
  - `entrant@example.com` -- ENTRANT (3 credits in Amadora)
  - `buyer1@example.com` -- ENTRANT (2 credits in Amadora, added via webhook)
  - `buyer2@example.com` -- ENTRANT (3 credits in Profissional, added via webhook)
- **Product mappings:** CHIP-AMA (Amadora, product ID 1001), CHIP-PRO (Profissional, product ID 1002)
- **Entries for `user@example.com`:** Wildflower Traditional (SUBMITTED, M1A), Blueberry Bliss (SUBMITTED, M2C), Oak-Aged Bochet (DRAFT, M1A)
- **Entries for `entrant@example.com`:** Lavender Metheglin (DRAFT, M3B)
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
- [ ] **Expected:** Grid with columns: Name (sortable), Email (sortable), Meadery (sortable), Country (sortable, full name), Role (sortable), Status (sortable), Actions (icon buttons)
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

- [ ] Find `Test Division` (status: Draft)
- [ ] Click the forward icon button (tooltip: "Advance Status")
- [ ] **Expected:** Confirmation dialog: "Advance division 'Test Division' from Draft to Registration Open?"
- [ ] Click "Advance"
- [ ] **Expected:** Notification "Status advanced successfully" (green)
- [ ] **Expected:** Status badge changes to "Registration Open"

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
- [ ] **Expected:** Form with: Name, Short Name, Start Date, End Date, Location, Contact Email, Logo label ("Logo") above upload field (max 2.5 MB, PNG/JPEG), Save button
- [ ] **Expected:** Fields pre-populated with CHIP 2026 data
- [ ] **Expected:** Contact Email field with helper text "Shown in emails sent to competition participants" and clear button
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
- [ ] **Expected:** Country column shows display name (e.g. "Portugal") based on user's ISO country code
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

**Main category limit (5 per main category):**

- [ ] Create entries across multiple M1 subcategories (M1A, M1B, M1C) to total 5
- [ ] Attempt to create a 6th entry under any M1 subcategory
- [ ] **Expected:** Error "Entry limit reached for this main category (max 5)"

---

## 12. Judging Module

**Covers:** `JudgingAdminViewTest`, `TableViewTest`, `ScoresheetViewTest`,
`MyJudgingViewTest`, `BosViewTest`, `JudgingServiceTest`, `ScoresheetServiceTest`,
`JudgeProfileServiceTest`, `MeaderyNameNormalizerTest`, `CoiCheckServiceTest`,
`JudgingDivisionStatusRevertGuardTest`, `JudgingMinJudgesLockGuardTest`,
`JudgingErrorKeyCoverageTest`. Plus the seven aggregate repository tests
(`JudgingRepositoryTest`, `JudgingTableRepositoryTest`,
`CategoryJudgingConfigRepositoryTest`, `ScoresheetRepositoryTest`,
`MedalAwardRepositoryTest`, `BosPlacementRepositoryTest`,
`JudgeProfileRepositoryTest`).

This section assumes Amadora has been walked through Sections 6–11 already.
The judging flow advances Amadora to JUDGING for the duration of this section
and can be reverted afterwards if you want the entry-side flows to remain
testable. Steps below are admin-driven unless noted.

### 12.1 Prerequisites — advance Amadora to JUDGING

*Log in as `compadmin@example.com`.*

- [ ] Navigate to CHIP 2026 → Amadora division detail.
- [ ] **Verify:** Current status is `REGISTRATION_OPEN`.
- [ ] Click "Advance Status" → confirm "Advance from Registration Open to Registration Closed?".
- [ ] **Expected:** Status badge updates to `REGISTRATION_CLOSED`.
- [ ] Click "Advance Status" → confirm "Advance from Registration Closed to Judging?".
- [ ] **Expected:** Status badge updates to `JUDGING`.
- [ ] **Expected:** A new "Judging Categories" tab appears between "Categories" and "Settings".
- [ ] **Expected:** A "Manage Judging" button now shows in the division header (alongside "Manage Entries").

### 12.2 Division Settings — judging fields

*Stay on Amadora division detail, Settings tab.*

- [ ] **Expected:** A "Judging" sub-section appears at the bottom of Settings with two `IntegerField`s:
  - **BOS places** (defaults to 1, helper text "Number of Best of Show placements awarded for this division.")
  - **Minimum judges per table** (defaults to 2, helper text "Hard minimum enforced when starting a judging table.")
- [ ] **Expected:** "BOS places" is `setReadOnly(true)` (locked because status is past REGISTRATION_OPEN). Tooltip on hover explains the lock.
- [ ] **Expected:** "Minimum judges per table" is editable (not locked yet — no table started). The cross-module `MinJudgesPerTableLockGuard` only locks it once a table has `status != NOT_STARTED`.
- [ ] Change "Minimum judges per table" from 2 to 3, click "Save".
- [ ] **Expected:** Notification "Settings saved successfully".
- [ ] Refresh — value persists at 3.
- [ ] Change back to 2 and save.

### 12.3 Competition Settings — comment languages

*Navigate to CHIP 2026 detail → Settings tab.*

- [ ] **Expected:** A "Judging" sub-section with a `MultiSelectComboBox<String>` labelled "Comment languages for scoresheets".
- [ ] **Expected:** The list contains all supported language codes (`en`, `es`, `it`, `pl`, `pt`) sorted by display name in the current UI locale.
- [ ] Select `en` and `pt`, click "Save".
- [ ] **Expected:** Notification "Competition updated successfully".
- [ ] Refresh — selection persists.

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
- [ ] **Try:** Add a judging category with the same code as an existing one (e.g. `M1A`).
- [ ] **Expected:** Allowed — `UNIQUE(division_id, code, scope)` permits the same code in different scopes.

### 12.5 Assign final categories to entries

For meaningful judging tests, a few entries must have `finalCategoryId` set to a JUDGING-scope category.

*Navigate to Amadora → Entry Admin → Entries tab.*

- [ ] Pick at least 2 entries in SUBMITTED or RECEIVED state.
- [ ] Click the Edit (pencil) icon → confirm in the warning dialog.
- [ ] **Expected:** The edit dialog now includes a "Final Category" Select (clearable, populated from JUDGING-scope categories).
- [ ] Pick a category (e.g. `M1A — Traditional Mead (Dry)`), Save.
- [ ] **Expected:** Notification "Entry updated"; Final Category column shows the chosen value.

### 12.6 JudgingAdminView — Tables tab

*Click "Manage Judging" on Amadora division detail.*

- [ ] **Expected:** URL is `/competitions/chip-2026/divisions/amadora/judging-admin`.
- [ ] **Expected:** Breadcrumb: `My Competitions / CHIP 2026 / Amadora / Judging Admin`.
- [ ] **Expected:** H2 header `CHIP 2026 — Amadora — Judging Admin` with competition logo.
- [ ] **Expected:** TabSheet with three tabs: `Tables`, `Medal Rounds`, `Best of Show`. Default = Tables.

#### 12.6.1 Add a table

- [ ] Click "+ Add Table".
- [ ] **Expected:** Dialog with `Name` text field, `Category` Select (filtered to JUDGING-scope categories), `Scheduled` date picker.
- [ ] Leave Name blank → click Save.
- [ ] **Expected:** Inline error "Name is required" on the field.
- [ ] Enter Name = `M1A Panel`, leave Category empty → click Save.
- [ ] **Expected:** Inline error "Category is required".
- [ ] Pick Category = `M1A — Traditional Mead (Dry)`, set Scheduled = today + 7 days → Save.
- [ ] **Expected:** Notification "Table added"; row appears in the grid.
- [ ] **Expected:** Grid columns: Name, Category (code — name), Status, Judges (count), Scheduled (locale-aware date), Scoresheets (— when empty), Actions.

#### 12.6.2 Edit table

- [ ] Click ✏ Edit on the new row.
- [ ] **Expected:** Dialog with Name and Scheduled (Category is not editable after creation).
- [ ] Change name to `M1A Panel A`, Save.
- [ ] **Expected:** Notification "Table updated"; grid reflects new name.

#### 12.6.3 Assign judges (with COI badges)

For COI badges to appear, `judge@example.com` should already exist as a JUDGE in CHIP 2026 (seeded). Optionally add a second judge so you can see multi-selection.

- [ ] As `admin@example.com` (SYSTEM_ADMIN), navigate to Users and create `judge2@example.com` (USER, ACTIVE).
- [ ] As `compadmin@example.com`, navigate to CHIP 2026 → Participants → "Add Participant" → email `judge2@example.com`, role JUDGE.
- [ ] **Optional:** To exercise the soft-COI warning, set `judge@example.com`'s meadery name (via Profile) to match one of the entrants' meadery names (e.g. set both to "Hiveheart Meadery").
- [ ] **Optional:** To exercise the hard-COI warning, add credits to `judge@example.com` in Amadora, then submit an entry as `judge@example.com` and assign its final category to `M1A`.
- [ ] Back on Judging Admin → Tables tab → click 👥 Assign Judges on the row.
- [ ] **Expected:** Dialog with a multi-select `Grid<User>` titled `assign-judges-grid`. Columns: Name, Meadery, Country, Conflict of Interest.
- [ ] **Expected:** If `judge@example.com` has a submitted entry in the table's category, a red "Self-entry — cannot judge" badge appears.
- [ ] **Expected:** If `judge@example.com`'s meadery matches an entry's entrant meadery, an orange "Similar meadery to entry #N" badge appears.
- [ ] Select both judges, click Save.
- [ ] **Expected:** Notification "Judge assignments updated"; row's `Judges` count shows `2`.
- [ ] **Try:** Open dialog again and uncheck both judges → Save.
- [ ] **Expected:** Notification; count goes back to 0.
- [ ] Re-select both judges before continuing.

#### 12.6.4 Start table

- [ ] Click ▶ Start on the row.
- [ ] **Expected:** Confirmation dialog: if entries with `finalCategoryId = M1A` exist, body says "All assigned judges will be notified. This creates one scoresheet per entry assigned to this category."; if no entries, body says "This table has no entries yet. Start anyway?".
- [ ] Click Start.
- [ ] **Expected:** Notification "Table started"; Status column changes from `NOT_STARTED` to `ROUND_1`; Scoresheets column now reads `DRAFT N · SUBMITTED 0` for some N ≥ 0.
- [ ] **Expected:** ▶ Start button becomes disabled (already started).
- [ ] **Expected:** 🗑 Delete button is disabled with tooltip "Cannot delete a started table or one with assigned judges".

#### 12.6.5 minJudgesPerTable lock — verify settings tab

- [ ] Navigate back to Amadora division detail → Settings.
- [ ] **Expected:** "Minimum judges per table" is now `setReadOnly(true)` — locked because a table has `status != NOT_STARTED`.
- [ ] **Expected:** Tooltip on the field explains the lock.

#### 12.6.6 Try to remove a judge below the minimum

*From Tables tab → 👥 Assign Judges on the started table.*

- [ ] Uncheck both judges, click Save.
- [ ] **Expected:** Error "Removing this judge would drop the table below the required minimum of 2 judges."
- [ ] Close — both judges remain assigned.

#### 12.6.7 Delete a not-started, no-judges table (negative + positive)

- [ ] Add a second table (`Throwaway`, category `M2A — Pome Fruit Melomel`, no Scheduled).
- [ ] **Expected:** New row, NOT_STARTED.
- [ ] Click 🗑 Delete → confirm.
- [ ] **Expected:** Notification "Table deleted"; row removed.

### 12.7 JudgingAdminView — Medal Rounds tab

*Click "Medal Rounds" tab.*

- [ ] **Expected:** Empty-state message if no JUDGING-scope categories exist, otherwise a `Grid<CategoryJudgingConfig>` with columns: Category, Mode, Status, Tables, Awards, Actions.
- [ ] **Expected:** Each JUDGING category appears with a lazily-created default config: Mode = `COMPARATIVE`, Status = `PENDING`, Tables = `0 / 1 COMPLETE` for M1A, Awards = `G:0 S:0 B:0 W:0`.

#### 12.7.1 Start medal round (cannot start until tables complete)

- [ ] Find the row for `M1A` (the category whose table is in ROUND_1).
- [ ] **Expected:** ▶ Start button is disabled with tooltip explaining the medal round can only start when status = `READY`.
- [ ] (You'll fill scoresheets first via §12.10 to flip the table to COMPLETE, which marks the config READY automatically.)

#### 12.7.2 RESET strong-confirm

- [ ] Pick a row with Status `READY` or `ACTIVE` (use SCORE_BASED mode for easier setup if needed).
- [ ] Click ⟲ Reset.
- [ ] **Expected:** Dialog body explains "This wipes N MedalAward rows. Type RESET to confirm."
- [ ] Click Reset without typing.
- [ ] **Expected:** Inline error "Type RESET exactly to confirm". No service call.
- [ ] Type `RESET` exactly, click Reset.
- [ ] **Expected:** Notification "Medal round reset"; Status flips to READY; Awards counts zero out.

### 12.8 JudgingAdminView — Best of Show tab

*Click "Best of Show" tab.*

- [ ] **Expected (Judging.phase = ACTIVE):** Phase badge `Phase: Active`, configured BOS places line, "Manage placements →" anchor, and three sections: header, GOLD candidates (empty until medal rounds complete), placements (1 empty row).
- [ ] **Expected:** "Start BOS" button is disabled with tooltip "All medal rounds must be COMPLETE before BOS can start." until every JUDGING category's config is `medalRoundStatus = COMPLETE`.
- [ ] After all medal rounds COMPLETE, click "Start BOS" → confirm.
- [ ] **Expected:** Notification "BOS started"; phase badge updates to `Phase: BOS`; "Finalize BOS" and "Reset BOS" appear.

### 12.9 MyJudgingView (judge hub)

*Log out as compadmin, log in as `judge@example.com` (use the magic link from Mailpit; access code also works).*

- [ ] **Expected (sidebar):** A new "My Judging" entry (gavel icon) appears in the drawer. Only visible because the judge has at least one `JudgeAssignment`.
- [ ] Click "My Judging".
- [ ] **Expected:** URL `/my-judging`. H2 header "My Judging".
- [ ] **Expected:** Each competition the judge has tables in is shown as an `H3` with the competition name; under it, one block per assigned table showing the division name (Span), table name (Span), and an "Open table →" anchor.
- [ ] If at least one DRAFT scoresheet exists across assigned tables: **Expected** a prominent "▶ Resume next draft scoresheet" anchor near the top, pointing at `competitions/.../scoresheets/<oldest-DRAFT-id>`.
- [ ] If a Medal Round is ACTIVE for a category the judge has a table for: **Expected** a "Medal Rounds" section listing each active config with an "Open medal round →" anchor.

#### 12.9.1 Empty-state for a non-judge user

- [ ] Log out, log in as `entrant@example.com` (regular entrant).
- [ ] **Expected (sidebar):** "My Judging" entry is *not* present (gated by `JudgeAssignmentChecker.hasAnyJudgeAssignment`).
- [ ] Navigate directly to `/my-judging` (manually type the URL).
- [ ] **Expected:** H2 "My Judging" renders, plus an empty-state message ("You have no judging assignments yet…") and two CTA anchors: "Edit your judge profile →" (to `/profile`) and "Browse competitions →" (to `/competitions` or `/my-competitions` depending on role).

### 12.10 TableView (per-table)

*Back as `judge@example.com`, on `/my-judging`, click "Open table →" for the started M1A table.*

- [ ] **Expected:** URL is `competitions/chip-2026/divisions/amadora/tables/<tableId>`.
- [ ] **Expected:** Breadcrumb begins with "My Judging" (judge path) or "My Competitions / CHIP 2026 / Amadora / Judging Admin" (admin path).
- [ ] **Expected:** H2 `CHIP 2026 — Amadora — Table: M1A Panel A`.
- [ ] **Expected:** Filter bar with a `Status` Select (options: All, Draft, Submitted; default All) and a `Search` `TextField` (placeholder "Mead name or entry code", `ValueChangeMode.EAGER`).
- [ ] **Expected:** A `Grid<Scoresheet>` with columns Entry, Mead name, Status, Total, Filled by, Actions.
- [ ] Apply filter Status = Draft → grid narrows to DRAFT rows only.
- [ ] Type part of a mead name in Search → grid filters client-side; clearing the field restores all rows.

#### 12.10.1 Row click → ScoresheetView

- [ ] Click any row.
- [ ] **Expected:** Navigation to `competitions/.../scoresheets/<id>`.

#### 12.10.2 Admin-only actions (Revert, Move)

*Log back in as `compadmin@example.com` and revisit the same TableView URL.*

- [ ] **Expected:** Rows in SUBMITTED show an `arrow-backward` icon (Revert) tooltip "Revert to draft" (or "Cannot revert while medal round is active or complete for this category." when locked).
- [ ] **Expected:** Rows in DRAFT show an `exchange` icon (Move) tooltip "Move to another table".
- [ ] **Expected:** Neither button is visible for `judge@example.com` (admin-only).

##### Revert

- [ ] On a SUBMITTED row (you'll need to submit a scoresheet first — see §12.11) click Revert.
- [ ] **Expected:** Confirmation dialog body explains the scoresheet returns to DRAFT, total score is cleared, and if it was the last submitted at the table, table status reopens to ROUND_1.
- [ ] Click Revert.
- [ ] **Expected:** Notification "Reverted scoresheet for {entryCode} to draft."; row Status changes; table Status (visible in JudgingAdmin Tables grid) returns to ROUND_1 if applicable.

##### Move to another table

For this you need a *second* ROUND_1 table in the same JUDGING category. Create one via JudgingAdminView if needed, then Start it (assigning ≥ minJudgesPerTable judges).

- [ ] On a DRAFT row click Move.
- [ ] **Expected:** Dialog with a `Select<JudgingTable>` (target tables filtered to ROUND_1 and same category, excluding current).
- [ ] If no candidate tables exist: **Expected** the empty-state message "No other ROUND_1 tables cover this category. Add a table first." and a disabled Save button.
- [ ] Pick a target, click Save.
- [ ] **Expected:** Notification "Moved scoresheet to {targetName}."; row disappears from this table's grid (reload to re-render).

### 12.11 ScoresheetView (judge form)

*As `judge@example.com`, open any DRAFT scoresheet from `/my-judging` → "Open table" → row click.*

- [ ] **Expected:** URL `competitions/.../divisions/.../scoresheets/<id>`.
- [ ] **Expected:** H2 `Scoresheet — {entryCode}`.
- [ ] **Expected:** A read-only entry header card showing the mead name.
- [ ] **Expected:** A "Scores" section with five `NumberField`s, one per MJP field:
  - `Appearance` (max 12)
  - `Aroma/Bouquet` (max 30)
  - `Flavour and Body` (max 32)
  - `Finish` (max 14)
  - `Overall Impression` (max 12)
  Each `NumberField` has `min=0`, `max=<field max>`, and `ValueChangeMode.EAGER`.
- [ ] **Expected:** A "Current total: N / 100" Span (id `scoresheet-total`) below the score fields. It updates live as you change values.
- [ ] **Expected:** An "Overall comments" `TextArea` (`maxLength=2000`).
- [ ] **Expected:** A "Comment language" `ComboBox` sourced from competition.commentLanguages (∪ judge.preferredCommentLanguage if set). Items sorted by display name in the UI locale.
- [ ] **Expected:** An "Advance to medal round" `Checkbox`.
- [ ] **Expected:** Two buttons: "Save Draft" (always enabled) and "Submit" (enabled only when all 5 score fields are non-null).

#### 12.11.1 Save Draft

- [ ] Enter score values for two fields, type a few words in Overall comments, pick a Comment language, tick Advance to medal round.
- [ ] Click "Save Draft".
- [ ] **Expected:** Notification "Scoresheet saved as draft." Scores, comments, language, and advance flag persist (refresh to verify).

#### 12.11.2 Submit (all fields filled)

- [ ] Fill in the remaining three score fields → "Submit" button enables.
- [ ] Click "Submit".
- [ ] **Expected:** Confirmation dialog: "Submit scoresheet for {entryCode}? Once submitted, the scoresheet becomes read-only and the total score is locked. An admin can revert it to draft if the medal round has not started."
- [ ] Click "Submit".
- [ ] **Expected:** Notification "Scoresheet submitted."; the view reloads in read-only mode (all fields `setReadOnly(true)`; Save Draft and Submit buttons hidden).
- [ ] **Expected:** `totalScore` (in TableView Total column) equals the sum of the 5 fields.

#### 12.11.3 Hard COI page-level rejection (judge can't judge own entry)

- [ ] As `compadmin`, ensure an entry exists in Amadora where `entry.userId = judge@example.com`'s user id, with `finalCategoryId` = a JUDGING category the judge is assigned to.
- [ ] As `judge@example.com`, navigate directly to the URL of that entry's scoresheet (you can find the id via DB or by opening a TableView that includes it).
- [ ] **Expected:** Forward to `/my-judging`; no scoresheet form rendered (hard COI block per §3.7).

#### 12.11.4 Authorization rejection (judge not assigned to this table)

- [ ] As `judge@example.com`, navigate to a scoresheet on a *different* table the judge isn't assigned to.
- [ ] **Expected:** Forward to `""`.

### 12.12 MedalRoundView

*Pre-req: at least one CategoryJudgingConfig is ACTIVE for the judge's category. Use JudgingAdminView → Medal Rounds tab to Start the round for `M1A` (after the M1A table is COMPLETE).*

- [ ] As `judge@example.com`, navigate via `/my-judging` → Medal Rounds section → "Open medal round →".
- [ ] **Expected:** URL `competitions/.../divisions/.../medal-rounds/<divisionCategoryId>`.
- [ ] **Expected:** Header shows category code + name, mode badge (`COMPARATIVE` or `SCORE_BASED`), status badge.

#### 12.12.1 COMPARATIVE mode

- [ ] **Expected:** Entries that have a SUBMITTED scoresheet with `advancedToMedalRound = true` for this category are listed (eligibility refined per §1.9).
- [ ] **Expected:** Per-row controls — Gold/Silver/Bronze buttons plus a "More ▾" dropdown (Withhold / Clear).
- [ ] Click `🥇` on a row.
- [ ] **Expected:** Notification or live update; row gets a Gold badge.
- [ ] **Expected:** Bottom summary line "Summary: 1 Gold · 0 Silver · 0 Bronze · 0 Withhold · {N} unset" updates live.
- [ ] Click "Withhold" via the dropdown.
- [ ] **Expected:** Row badge shows "Withheld" (per D11 — `MedalAward.medal = null` distinguishes explicit withhold from no row).
- [ ] Click "Clear".
- [ ] **Expected:** Row reverts to no medal.

#### 12.12.2 SCORE_BASED mode

- [ ] *Pre-req: change a category's mode to SCORE_BASED via JudgingAdminView → Medal Rounds tab → row's mode column (TBD — currently only set at config creation; if no UI for this, edit DB).*
- [ ] On Start, rows are auto-populated walking gold→silver→bronze by total score. Ties stop the cascade.
- [ ] **Expected:** A "tied-slot" banner at the top when ties exist; tied rows highlighted with a warning background; per-row resolver lets you pick which entry gets the slot.

#### 12.12.3 Admin actions (Reset / Reopen / Finalize)

- [ ] As `compadmin@example.com`, navigate to the same medal-round URL.
- [ ] **Expected:** Header includes admin-only buttons `Reset`, `Reopen`, `Finalize` depending on status.
- [ ] Click "Finalize".
- [ ] **Expected:** Confirmation dialog → click Finalize → notification "Medal round complete"; status flips to `COMPLETE`; per-row buttons disappear (read-only mode).
- [ ] Click "Reopen".
- [ ] **Expected:** Confirmation dialog → status flips back to `ACTIVE`; existing MedalAwards preserved (per §2.B Tier 2: preserve on COMPLETE → ACTIVE).
- [ ] Click "Reset" → type RESET → confirm.
- [ ] **Expected:** All MedalAward rows for the category deleted; status flips to `READY`. (Tier 2 wipe on ACTIVE → READY.)

### 12.13 BosView (dedicated admin form)

*Pre-req: `Judging.phase ∈ {BOS, COMPLETE}` — start BOS from the JudgingAdmin BOS tab after all medal rounds are COMPLETE.*

- [ ] As `compadmin@example.com`, click "Manage placements →" in the JudgingAdmin → BOS tab.
- [ ] **Expected:** URL `competitions/.../divisions/.../bos`.
- [ ] **Expected:** H2 `Best of Show — Amadora`. Header shows `Phase: BOS` and `Places: N` (where N = `Division.bosPlaces`).
- [ ] **Expected:** A placements grid (id `bos-placements-grid`) with N rows (one per slot). Columns: Place, Entry, Mead name, Category, Awarded by, Action.
- [ ] **Expected:** Empty rows show `[+]` Assign button in the Action column. Filled rows show ✏ Reassign and 🗑 Delete.
- [ ] **Expected:** A candidates grid (id `bos-candidates-grid`) listing GOLD MedalAwards across all categories where the entry isn't placed yet. Columns: Entry, Mead name, Category.
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

### 12.15 Cross-module guard — block status revert when judging data exists

- [ ] As `compadmin@example.com`, navigate to Amadora division detail.
- [ ] Click "Revert Status" → confirm "Revert from Judging to Registration Closed?".
- [ ] **Expected:** Error notification — `JudgingDivisionStatusRevertGuard` blocks the revert because `Judging.phase != NOT_STARTED` OR any JudgingTable exists. Message renders the `error.division.cannot-revert-has-judging` translation.
- [ ] **Expected:** Status remains `JUDGING`.

### 12.16 i18n sanity (judging surfaces only)

*Switch UI language via the language switcher in the user menu (top-right).*

- [ ] For each of `pt`, `es`, `it`, `pl`:
  - Visit JudgingAdminView, TableView, ScoresheetView, MyJudgingView, MedalRoundView, BosView.
  - **Expected:** No raw `error.…` or `judging-admin.…` keys leaking through. Header labels, tab names, column headers, dialog titles, button text, notifications all render in the chosen language.
  - **Expected:** Date/time fields use locale-aware format (DatePicker, NumberField step buttons localized).
  - **Expected:** All score-field labels use canonical English names regardless of UI locale (`Appearance`, `Aroma/Bouquet`, `Flavour and Body`, `Finish`, `Overall Impression`) — these are stored as i18n keys but the canonical English is what's used in `ScoreField.fieldName`.

> The ES/IT/PL translations added in Phase 6.34 are draft-quality and intended for native-speaker review. Note any awkward phrasing or terminology disagreements for later correction.

### 12.17 Restore Amadora state (optional cleanup)

- [ ] If you want Amadora to remain testable for entry-side flows, you'll need to remove judging data first:
  - Reset BOS, reset all medal rounds, then delete all tables (each table needs to be NOT_STARTED with no assignments — uncheck judges via Assign Judges dialog, then delete).
  - Once no tables exist and `Judging.phase = NOT_STARTED`, the `JudgingDivisionStatusRevertGuard` will permit revert.
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
- [ ] **Verify:** Current status is `JUDGING`. (If `REGISTRATION_CLOSED`, advance once.)
- [ ] In the division header, click "Advance Status" → confirm
  "Advance from Judging to Deliberation?".
- [ ] **Expected:** Status badge updates to `DELIBERATION`.
- [ ] Navigate to Manage Judging.
- [ ] **Expected:** A new "Manage Results" button appears in the JudgingAdminView
  header (between the title and the tabs).

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
- [ ] **Expected:** Withheld medals (Medal = null) are **not** rendered.
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
- [ ] **Expected:** Withheld medals render as `—` (same as no medal) — entrant
  view does NOT distinguish withheld vs unset.
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

- [ ] Navigate to Amadora → Manage Judging → Medal Rounds tab.
- [ ] Try to record / change a medal (e.g., click the gear or edit icon on a
  category, then change a Medal value).
- [ ] **Expected:** Notification with message:
  *"Results have been published — judging data cannot be modified. Revert
  the publication first."* (i18n key `error.judging.results-published-frozen`)
- [ ] Try the same in the Tables tab (e.g., add a table, assign a judge,
  start a table).
- [ ] **Expected:** Same frozen notification.
- [ ] Try the same in BOS (record/update/delete a placement).
- [ ] **Expected:** Same frozen notification.
- [ ] Navigate into a table → ScoresheetView (admin entry) and try to edit a
  scoresheet (update a score, revert to draft, set comment language).
- [ ] **Expected:** Same frozen notification on save.

### 13.7 Revert publication

*Navigate to Manage Results.*

- [ ] **Expected:** Three buttons in the actions row: "Re-publish",
  "Send announcement" (primary), "Revert publication" (error variant).
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

*Navigate back to Manage Judging → Medal Rounds tab.*

- [ ] Pick a medal in some category and change its value (e.g., SILVER →
  BRONZE on one entry) via the per-category edit dialog.
- [ ] **Expected:** Save succeeds (no frozen notification — data is editable
  again in DELIBERATION).
- [ ] In the division header, click "Advance Status" → confirm "Advance from
  Deliberation to Results Published?". (Note: this DOES NOT create a
  Publication record — the explicit awards flow does that.)
- [ ] **Actually**, instead of advancing manually, the correct flow is to use
  the awards re-publish path which expects the division to already be at
  `RESULTS_PUBLISHED`. So first advance manually back to RESULTS_PUBLISHED via
  the division header. *(Implementation note: an admin who wants a "second
  publication" must re-advance manually; awards.republish requires the status
  to already be RESULTS_PUBLISHED.)*
- [ ] Navigate to Manage Results.
- [ ] Click "Re-publish".
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

*Sanity check on the admin path:* log in as `compadmin@example.com` and
generate a FULL-mode PDF by visiting Manage Judging → Tables → click into a
table → click on a scoresheet — admin-side surfaces still show real judge
names. (The admin "View Scoresheet" path is not part of the awards flow per
se, but it confirms the two anonymization modes are wired correctly.)

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
| 12. Judging Module | `JudgingAdminViewTest`, `TableViewTest`, `ScoresheetViewTest`, `MyJudgingViewTest`, `BosViewTest`, `JudgingServiceTest`, `ScoresheetServiceTest`, `JudgeProfileServiceTest`, `MeaderyNameNormalizerTest`, `CoiCheckServiceTest`, `JudgingDivisionStatusRevertGuardTest`, `JudgingMinJudgesLockGuardTest`, `JudgingErrorKeyCoverageTest`, `JudgingRepositoryTest`, `JudgingTableRepositoryTest`, `CategoryJudgingConfigRepositoryTest`, `ScoresheetRepositoryTest`, `MedalAwardRepositoryTest`, `BosPlacementRepositoryTest`, `JudgeProfileRepositoryTest` |
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
