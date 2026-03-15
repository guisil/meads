# Manual Walkthrough / Test

Comprehensive manual test plan for MEADS. Covers every user-facing behavior and API
endpoint across identity, competition, and entry modules. Organized by workflow area
with checkboxes for progress tracking.

**Date:** 2026-03-10
**Seeded data:** Dev profile (`spring.profiles.active=dev`)

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

### Hard delete (confirmation dialog)

- [ ] Find `newuser@test.com` (now INACTIVE)
- [ ] **Expected:** Trash icon button with tooltip "Delete"
- [ ] Click the trash icon button
- [ ] **Expected:** Confirmation dialog: "Are you sure you want to permanently delete user newuser@test.com? This action cannot be undone."
- [ ] Click "Confirm"
- [ ] **Expected:** Notification "User deleted successfully" (green)
- [ ] **Expected:** User removed from grid

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

### Delete division

- [ ] Click the trash icon button on `Test Division`
- [ ] **Expected:** Confirmation dialog with warning about removing categories
- [ ] Click "Delete"
- [ ] **Expected:** Notification "Division deleted successfully" (green)
- [ ] **Expected:** Division removed from grid

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
- [ ] **Expected:** Dialog with Name, Type (PDF/Link selector defaulting to PDF), Upload component
- [ ] Change Type to "Link"
- [ ] **Expected:** Upload component hides, URL field appears
- [ ] Enter Name: `MJP Guidelines`, URL: `https://meadjudging.com/guidelines`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Document added successfully" (green)
- [ ] **Expected:** Document appears in grid with Name "MJP Guidelines", Type badge "LINK"

#### Add PDF document

- [ ] Click "Add Document"
- [ ] **Expected:** Type defaults to PDF, Upload component visible
- [ ] Enter Name: `Competition Rules`
- [ ] Upload a small test PDF (any PDF under 10 MB)
- [ ] Click "Save"
- [ ] **Expected:** Notification "Document added successfully" (green)
- [ ] **Expected:** Two documents in grid

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
- [ ] **Expected:** Grid with columns: Entry # (with AMA prefix, e.g. "AMA-1"), Code, Mead Name, Category (code with tooltip for full name), Final Category (code with tooltip, or "—" if not set), Entrant, Meadery, Country, Status, Actions (view/edit/delete/withdraw icons)
- [ ] **Expected:** Meadery column shows user's meadery name (or empty if not set)
- [ ] **Expected:** Country column shows display name (e.g. "Portugal") based on user's ISO country code
- [ ] **Expected:** 4 entries total (3 from user@example.com, 1 from entrant@example.com), sorted by entry number
- [ ] **Expected:** Wildflower Traditional and Blueberry Bliss -- Status: SUBMITTED
- [ ] **Expected:** Oak-Aged Bochet and Lavender Metheglin -- Status: DRAFT
- [ ] **Expected:** Columns are sortable
- [ ] **Expected:** Delete button (trash) only enabled for DRAFT entries
- [ ] **Expected:** Withdraw button (ban) disabled for WITHDRAWN entries
- [ ] **Expected:** View button (eye) opens read-only dialog showing all entry fields, status, and entrant email
- [ ] **Expected:** Edit button opens confirmation dialog ("Are you sure you want to edit this entry's data?"), then full edit dialog with all fields (mead name, category, sweetness, strength, ABV, carbonation, honey, other ingredients, wood aged, wood ageing details, additional info)
- [ ] **Expected:** Edit works for entries in any status except WITHDRAWN
- [ ] **Expected:** Delete button opens confirmation dialog
- [ ] **Expected:** Withdraw button opens confirmation dialog

### Entry labels -- individual download (admin)

- [ ] **Expected:** SUBMITTED and RECEIVED entries have a download icon (download-alt) in the Actions column
- [ ] **Expected:** DRAFT and WITHDRAWN entries do NOT have a download icon
- [ ] Click the download icon on a SUBMITTED entry
- [ ] **Expected:** Browser downloads a PDF file named `label-AMA-{N}.pdf`
- [ ] **Expected:** PDF is A4 landscape with 2-line instruction header (line 1: print/attach, line 2: shipping address + phone if set) and 3 identical labels
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
echo -n '<payload>' | openssl dgst -sha256 -hmac 'dev-jumpseller-hooks-token' | awk '{print $2}'
```

Or as a reusable shell function:

```bash
sign() { echo -n "$1" | openssl dgst -sha256 -hmac 'dev-jumpseller-hooks-token' | sed 's/.*= //'; }
```

#### Alternative: Postman

1. Create a Postman environment variable `hooks_token` = `dev-jumpseller-hooks-token`
2. Add a **Pre-request Script** to automatically compute the signature:
   ```javascript
   const payload = pm.request.body.raw;
   const token = pm.environment.get("hooks_token");
   const signature = CryptoJS.HmacSHA256(payload, token).toString(CryptoJS.enc.Hex);
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
PAYLOAD='{"id":"WH-001","customer":{"email":"webhooktest@example.com","full_name":"Webhook Tester"},"products":[{"product_id":"1001","sku":"CHIP-AMA","name":"CHIP Amadora Entry","qty":3}],"shipping_address":{"country_code":"PT"}}'
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
- [ ] **Check Mailpit:** credit notification email sent to `webhooktest@example.com`, subject "[MEADS] Entry credits received — Amadora", body says "3 entry credits", CTA button "View My Entries" (magic link URL)

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
PAYLOAD2='{"id":"WH-002","customer":{"email":"webhooktest@example.com","full_name":"Webhook Tester"},"products":[{"product_id":"9876","sku":"TSHIRT","name":"Conference T-Shirt","qty":1}]}'
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
  -d '{"id":"WH-003","customer":{"email":"test@example.com","full_name":"Test"},"products":[]}'
```

- [ ] **Expected:** HTTP 401 (Unauthorized)
- [ ] Verify: No order `WH-003` in the Orders tab

### Mutual exclusivity conflict

`webhooktest@example.com` already has credits in Amadora. An order for Profissional
(product ID `1002`) in the same competition (CHIP 2026) should be flagged.

```bash
PAYLOAD3='{"id":"WH-004","customer":{"email":"webhooktest@example.com","full_name":"Webhook Tester"},"products":[{"product_id":"1002","sku":"CHIP-PRO","name":"CHIP Profissional Entry","qty":1}]}'
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
PAYLOAD4='{"id":"WH-005","customer":{"email":"newbuyer@example.com","full_name":"New Buyer"},"products":[{"product_id":"1001","sku":"CHIP-AMA","name":"CHIP Amadora Entry","qty":2},{"product_id":"1002","sku":"CHIP-PRO","name":"CHIP Profissional Entry","qty":1}]}'
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
- [ ] **Expected:** "MJP Category Guide" appears as a clickable link (opens in new tab)
- [ ] If no documents were added, this section should not appear

### Credit balance display

- [ ] **Expected:** Credit info shows: "Credits: N remaining (M total, K used)"
- [ ] **Expected:** Total should be 7 (5 original + 2 added in section 8), used should be 3
- [ ] **Expected:** Limits info shows: "Limits: 10 total, 5 per main category, 3 per subcategory"

### Process info box

- [ ] **Expected:** Blue info box below credits: "Use your entry credits to add meads, then submit them when ready. Submitted entries cannot be edited. Once all credits are used and all entries are submitted, you'll receive a confirmation email with a summary of your entries."

### Registration deadline display

- [ ] **Expected:** "Registration closes: [date] [timezone]" shown below credit info
- [ ] **Expected:** Date format is like "30 Jun 2026, 23:59 Europe/Lisbon"

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
- [ ] **Expected:** Dialog (600px wide) with full-width fields: Mead Name, Category (subcategories only, shows "code — name"), category hint (initially hidden), Sweetness, Strength, ABV (%), Carbonation, Honey Varieties, Other Ingredients, Wood Aged checkbox, Additional Information
- [ ] Select Category: M1B
- [ ] **Expected:** Category hint appears below the dropdown: "Traditional mead: only honey (and optionally wood). Expected sweetness: Medium."
- [ ] Change Category to M2A
- [ ] **Expected:** Hint updates to: "Pome fruit melomel: apples, pears, quince."
- [ ] Fill in: Mead Name: `Spring Blossom`, Category: M1B, Sweetness: Medium, Strength: Standard, ABV: 12.0, Carbonation: Still, Honey: `Orange blossom`
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
- [ ] **Expected:** Read-only dialog with title "Entry AMA-N — Blueberry Bliss" showing all entry fields (Mead Name, Category (code — name format), Sweetness, Strength, ABV, Carbonation, Honey Varieties, Status, etc.). Entry code is NOT shown (only visible to admins).
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

## 12. Cross-cutting Concerns

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

## 13. Multi-Role & Cross-Competition Edge Cases

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

## 14. Security Testing

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
| 10. My Entries | `MyEntriesViewTest`, `EntryServiceTest`, `EntryTest`, `EntryCreditRepositoryTest`, `EntryRepositoryTest` |
| 11. Cross-cutting | `EntryServiceTest`, `DevDataInitializerTest`, `EntryModuleTest`, `CompetitionModuleTest`, `ModulithStructureTest` |
| 14. Security | `SecurityConfigTest`, `JumpsellerWebhookControllerTest`, `SmtpEmailServiceTest`, `JwtMagicLinkServiceTest`, `LoginViewTest` |

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
