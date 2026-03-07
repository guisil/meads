# Manual UI Walkthrough / Test

Comprehensive manual test plan for MEADS. Covers every user-facing behavior across
identity, competition, and entry modules. Organized by workflow area with checkboxes
for progress tracking.

**Date:** 2026-03-07
**Seeded data:** Dev profile (`spring.profiles.active=dev`)

---

## 1. Prerequisites

### Start the application

```bash
docker-compose up -d          # Start PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Wait for startup to complete. The console will show magic links for dev users.

### Dev users

| Email | Name | Role | Status | Credential |
|-------|------|------|--------|------------|
| `admin@example.com` | Dev Admin | SYSTEM_ADMIN | ACTIVE | Password: `admin` |
| `compadmin@example.com` | Competition Admin | USER | ACTIVE | Password: `compadmin` |
| `user@example.com` | Dev User | USER | ACTIVE | Magic link (see logs) |
| `pending@example.com` | Pending User | USER | PENDING | Magic link (see logs) |
| `judge@example.com` | Dev Judge | USER | ACTIVE | Magic link (see logs) |
| `steward@example.com` | Dev Steward | USER | ACTIVE | Magic link (see logs) |
| `entrant@example.com` | Dev Entrant | USER | ACTIVE | Magic link (see logs) |

### Seeded competition data (CHIP 2026)

- **Competition:** CHIP 2026 (June 1-30, 2026, Lisbon, Portugal)
- **Divisions:** Amadora (Amateur) and Profissional (Commercial) -- both REGISTRATION_OPEN, MJP scoring
- **Entry limits:** 3 per subcategory, 5 per main category (both divisions)
- **Categories:** Full MJP catalog minus M4B and M4D
- **Participants:**
  - `compadmin@example.com` -- ADMIN
  - `judge@example.com` -- JUDGE (has access code)
  - `steward@example.com` -- STEWARD (has access code)
  - `user@example.com` -- ENTRANT (5 credits in Amadora)
  - `entrant@example.com` -- ENTRANT (3 credits in Amadora)
- **Product mappings:** CHIP-AMA (Amadora, product ID 1001), CHIP-PRO (Profissional, product ID 1002)
- **Entries for `user@example.com`:** Wildflower Traditional (DRAFT, M1A), Blueberry Bliss (SUBMITTED, M2C), Oak-Aged Bochet (DRAFT, M1A)
- **Entries for `entrant@example.com`:** Lavender Metheglin (DRAFT, M3B)

### Second competition (minimal)

- **Competition:** Test Competition 2026 (September 1-30, 2026, Porto, Portugal)
- **Division:** Open (MJP, REGISTRATION_OPEN, full catalog)
- **Participants:** `compadmin@example.com` -- ADMIN

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
- [ ] **Expected:** Redirected to `/` with "Welcome admin@example.com"

### Password login (competition admin)

- [ ] Log out
- [ ] Enter email: `compadmin@example.com`
- [ ] Expand "Login with credentials" section
- [ ] Enter password: `compadmin`
- [ ] Click "Login"
- [ ] **Expected:** Redirected to `/` with "Welcome compadmin@example.com"

### Magic link login

- [ ] Log out (or open incognito window)
- [ ] Navigate to `/login`
- [ ] Enter email: `user@example.com`
- [ ] Click "Get Login Link"
- [ ] **Expected:** Notification "If this email is registered, a login link has been sent."
- [ ] Copy the magic link URL from the server console (format: `http://localhost:8080/login/magic?token=...`)
- [ ] Paste the URL in the browser
- [ ] **Expected:** Authenticated as `user@example.com`, redirected to `/` with "Welcome user@example.com"

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
- [ ] Check server logs for the password setup link URL
- [ ] **Expected:** Link format `http://localhost:8080/set-password?token=...`

### Forgot password? (non-existent email — no enumeration)

- [ ] Navigate to `/login`
- [ ] Enter email: `nonexistent@example.com`
- [ ] Expand "Login with credentials" section
- [ ] Click "Forgot password?"
- [ ] **Expected:** Same notification "If this email is registered, a password reset link has been sent."
- [ ] **Expected:** No link in server logs (user doesn't exist)

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

- [ ] While logged in, click the "Logout" button in the top navbar
- [ ] **Expected:** Redirected to `/login`
- [ ] Navigate to `http://localhost:8080/`
- [ ] **Expected:** Redirected to `/login` (session ended)

### Set Password via token link

- [ ] Log in as `admin@example.com`, navigate to `/users`
- [ ] Click "Send Magic Link" for any user (e.g., `user@example.com`)
- [ ] Copy the magic link URL from the server logs
- [ ] Replace `/login/magic?token=` with `/set-password?token=` in the URL
- [ ] Open the modified URL in a browser (can be logged out)
- [ ] **Expected:** Set Password page with "Password" and "Confirm Password" fields
- [ ] Enter mismatched passwords → click "Set Password"
- [ ] **Expected:** "Passwords do not match" error on confirm field
- [ ] Enter a matching password shorter than 8 characters → click "Set Password"
- [ ] **Expected:** Error notification with "at least 8 characters"
- [ ] Enter a valid matching password (8+ chars) → click "Set Password"
- [ ] **Expected:** "Password set successfully" notification, redirected to `/login`
- [ ] Log in with the user's email and the new password
- [ ] **Expected:** Successful login

---

## 3. Navigation & Layout

**Covers:** `MainLayoutTest`, `RootUrlRedirectTest`

### Main layout structure

- [ ] Log in as `admin@example.com`
- [ ] **Expected:** Top navbar shows "MEADS" title and "Logout" button
- [ ] **Expected:** Left sidebar (drawer) has: Home, Competitions, Users
- [ ] Click the drawer toggle (hamburger icon)
- [ ] **Expected:** Sidebar collapses/expands

### SYSTEM_ADMIN nav items

- [ ] While logged in as `admin@example.com` (SYSTEM_ADMIN)
- [ ] **Expected:** Side nav shows "Home", "Competitions", "Users"

### Regular user nav items

- [ ] Log out, log in as `compadmin@example.com` (competition admin, regular USER)
- [ ] **Expected:** Side nav shows "Home", "My Competitions" -- no "Competitions" or "Users"
- [ ] Log out, log in as `user@example.com` (regular USER, not competition admin)
- [ ] **Expected:** Side nav shows "Home", "My Competitions" -- no "Competitions" or "Users"

### Home link

- [ ] Navigate to `/users` (as admin)
- [ ] Click "Home" in the sidebar
- [ ] **Expected:** Navigated to `/` with "Welcome admin@example.com"

---

## 4. User Management

**Covers:** `UserListViewTest`, `UserServiceTest`, `UserServiceValidationTest`

*Log in as `admin@example.com` for all steps.*

### User list grid

- [ ] Navigate to `/users`
- [ ] **Expected:** Page title "Users"
- [ ] **Expected:** Grid with columns: Email, Name, Role, Status, (Actions)
- [ ] **Expected:** Grid contains at least 7 dev users (admin, compadmin, user, pending/active, judge, steward, entrant)

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
- [ ] **Expected:** Dialog with pre-populated fields (email read-only, name editable, role, status)
- [ ] Change name to `Updated User`
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
- [ ] **Expected:** Action button shows "Deactivate"
- [ ] Click "Deactivate"
- [ ] **Expected:** Notification "User deactivated successfully" (green)
- [ ] **Expected:** User status changes to INACTIVE in the grid

### Hard delete (confirmation dialog)

- [ ] Find `newuser@test.com` (now INACTIVE)
- [ ] **Expected:** Action button now shows "Delete"
- [ ] Click "Delete"
- [ ] **Expected:** Confirmation dialog: "Are you sure you want to permanently delete user newuser@test.com? This action cannot be undone."
- [ ] Click "Confirm"
- [ ] **Expected:** Notification "User deleted successfully" (green)
- [ ] **Expected:** User removed from grid

### Send magic link

- [ ] Find `user@example.com` in the grid
- [ ] Click "Send Magic Link"
- [ ] **Expected:** Notification "Magic link sent successfully" (green)
- [ ] **Expected:** Magic link URL logged in server console

### Send password reset link

- [ ] Find any user in the grid
- [ ] Click "Password Reset"
- [ ] **Expected:** Notification "Password reset link generated (check server logs)" (green)
- [ ] **Expected:** Password setup link URL logged in server console (format: `.../set-password?token=...`)

### Password setup link on SYSTEM_ADMIN creation

- [ ] Click "Create User"
- [ ] Fill: email `newadmin@test.com`, name `New Admin`, role `SYSTEM_ADMIN`
- [ ] Click "Save"
- [ ] **Expected:** "User created successfully" notification (green)
- [ ] **Expected:** "Password setup link generated (check server logs)" notification
- [ ] **Expected:** Password setup link logged in server console

### Self-delete prevention

- [ ] Find `admin@example.com` (yourself)
- [ ] Click "Deactivate"
- [ ] **Expected:** Error notification (cannot deactivate your own account)

---

## 5. Competition Management

**Covers:** `CompetitionListViewTest`, `CompetitionServiceTest` (create/update/delete)

*Log in as `admin@example.com` for all steps.*

### Competition list grid

- [ ] Navigate to `/competitions`
- [ ] **Expected:** Page title "Competitions"
- [ ] **Expected:** Grid with columns: Name, Start Date, End Date, Location, (Actions)
- [ ] **Expected:** Grid shows "CHIP 2026" and "Test Competition 2026"

### Access denied for regular user

- [ ] Log in as `user@example.com`
- [ ] Navigate to `/competitions`
- [ ] **Expected:** Access denied (redirected away from the page)

### Create competition -- success

- [ ] Log in as `admin@example.com`, navigate to `/competitions`
- [ ] Click "Create Competition"
- [ ] **Expected:** Dialog with fields: Name, Start Date, End Date, Location, Logo upload
- [ ] Enter name: `Test Comp`, start: tomorrow, end: next week, location: `Porto`
- [ ] Click "Create"
- [ ] **Expected:** Notification "Competition created successfully" (green)
- [ ] **Expected:** Grid shows new competition

### Create competition -- blank name

- [ ] Click "Create Competition"
- [ ] Leave name blank, fill in dates
- [ ] Click "Create"
- [ ] **Expected:** Name field shows error "Name is required"

### Create competition -- missing dates

- [ ] Click "Create Competition"
- [ ] Enter name, leave start date blank
- [ ] Click "Create"
- [ ] **Expected:** Start date field shows error "Start date is required"

### Edit competition

- [ ] Click "Edit" on `Test Comp`
- [ ] **Expected:** Dialog with pre-populated fields
- [ ] Change name to `Updated Comp`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Competition updated successfully" (green)

### Delete competition -- success (no divisions)

- [ ] Click "Delete" on `Updated Comp`
- [ ] **Expected:** Confirmation dialog: "Are you sure you want to delete \"Updated Comp\"?"
- [ ] Click "Delete"
- [ ] **Expected:** Notification "Competition deleted successfully" (green)
- [ ] **Expected:** Competition removed from grid

### Delete competition -- blocked (has divisions)

- [ ] Click "Delete" on "CHIP 2026"
- [ ] Click "Delete" in the confirmation dialog
- [ ] **Expected:** Error notification (cannot delete competition with divisions)

### Navigate to competition detail

- [ ] Click the "CHIP 2026" row in the grid
- [ ] **Expected:** Navigated to `/competitions/{id}` (CompetitionDetailView)

---

## 6. Competition Detail (CHIP 2026)

**Covers:** `CompetitionDetailViewTest`, `CompetitionServiceTest` (divisions, participants, settings)

*Log in as `compadmin@example.com` for all steps unless noted (competition admin, not system admin).*

### Header

- [ ] Navigate to CHIP 2026 via "My Competitions" in the sidebar, click CHIP 2026 row
- [ ] **Expected:** Competition name "CHIP 2026" displayed
- [ ] **Expected:** Date range "Jun 1 - 30, 2026" (or similar formatted range)
- [ ] **Expected:** Location "Lisbon, Portugal"

### Divisions tab

- [ ] **Expected:** Default tab is "Divisions"
- [ ] **Expected:** Grid with columns: Name, Status, Scoring, (Actions)
- [ ] **Expected:** "Amadora" row -- Status badge "Registration Open", Scoring "MJP"
- [ ] **Expected:** "Profissional" row -- Status badge "Registration Open", Scoring "MJP"
- [ ] **Expected:** Each row has "View", "Advance", "Delete" buttons

### Create division

- [ ] Click "Create Division"
- [ ] **Expected:** Dialog with fields: Name, Scoring System (default: MJP)
- [ ] Enter name: `Test Division`
- [ ] Click "Create"
- [ ] **Expected:** Notification "Division created successfully" (green)
- [ ] **Expected:** New division appears in grid with status "Draft"

### Advance division status

- [ ] Find `Test Division` (status: Draft)
- [ ] Click "Advance"
- [ ] **Expected:** Confirmation dialog: "Advance division 'Test Division' from Draft to Registration Open?"
- [ ] Click "Advance"
- [ ] **Expected:** Notification "Status advanced successfully" (green)
- [ ] **Expected:** Status badge changes to "Registration Open"

### Delete division

- [ ] Click "Delete" on `Test Division`
- [ ] **Expected:** Confirmation dialog with warning about removing categories
- [ ] Click "Delete"
- [ ] **Expected:** Notification "Division deleted successfully" (green)
- [ ] **Expected:** Division removed from grid

### View division detail

- [ ] Click "View" on "Amadora"
- [ ] **Expected:** Navigated to `/divisions/{id}` (DivisionDetailView)

### Participants tab

- [ ] Click the "Participants" tab
- [ ] **Expected:** Grid with columns: Name, Email, Role, Access Code, (Remove)
- [ ] **Expected:** Rows for compadmin (Admin, no code), judge (Judge, 8-char code), steward (Steward, 8-char code), user (Entrant, no code), entrant (Entrant, no code)

### Add participant

- [ ] Click "Add Participant"
- [ ] **Expected:** Dialog with fields: Email, Role (default: Judge)
- [ ] Enter email: `newjudge@test.com`
- [ ] Click "Add"
- [ ] **Expected:** Notification "Participant added successfully" (green)
- [ ] **Expected:** New participant appears in grid with role "Judge" and an 8-char access code

### Add participant -- blank email

- [ ] Click "Add Participant"
- [ ] Leave email blank, click "Add"
- [ ] **Expected:** Error "Email is required"

### Remove participant

- [ ] Find `newjudge@test.com` in the grid
- [ ] Click "Remove"
- [ ] **Expected:** Notification "Participant removed" (green)
- [ ] **Expected:** Participant removed from grid

### Settings tab

- [ ] Click the "Settings" tab
- [ ] **Expected:** Form with: Name, Start Date, End Date, Location fields, Logo upload, Save button
- [ ] **Expected:** Fields pre-populated with CHIP 2026 data
- [ ] Change location to `Porto, Portugal`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Competition updated successfully" (green)
- [ ] Revert location back to `Lisbon, Portugal` and save

### Authorization -- regular user redirected

- [ ] Log in as `judge@example.com` (not a competition ADMIN)
- [ ] Navigate directly to `/competitions/{chipId}` (use the URL from earlier)
- [ ] **Expected:** Redirected to `/` (root) -- judge is not authorized for competition admin

---

## 7. Division Detail (Amadora)

**Covers:** `DivisionDetailViewTest`, `CompetitionServiceTest` (categories, settings, status)

*Log in as `compadmin@example.com` for all steps unless noted.*

### Header

- [ ] Navigate to Amadora division detail
- [ ] **Expected:** Division name "Amadora" displayed
- [ ] **Expected:** Status badge "Registration Open"
- [ ] **Expected:** Scoring system "MJP"

### Breadcrumb

- [ ] **Expected:** Breadcrumb shows "CHIP 2026 / Amadora" (or similar)
- [ ] Click the "CHIP 2026" link in the breadcrumb
- [ ] **Expected:** Navigated back to CompetitionDetailView

### Categories tab (TreeGrid)

- [ ] Navigate back to Amadora division detail
- [ ] **Expected:** Default tab is "Categories"
- [ ] **Expected:** TreeGrid with columns: Code, Name, Description, (Remove)
- [ ] **Expected:** Main categories expandable: M1 (Traditional Mead), M2 (Fruit Meads), M3 (Spiced Meads), M4 (Specialty Meads)
- [ ] Expand M1
- [ ] **Expected:** Sub-categories: M1A, M1B, M1C, M1V
- [ ] **Expected:** M4B and M4D are NOT present (excluded for CHIP)
- [ ] **Expected:** "Add Category" button is enabled (status is REGISTRATION_OPEN, which allows modification)
- [ ] **Expected:** "Remove" buttons are enabled

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
- [ ] Click "Remove"
- [ ] **Expected:** Notification "Category removed" (green)
- [ ] **Expected:** Category removed from grid
- [ ] Also remove the catalog category added earlier to restore original state

### Settings tab

- [ ] Click the "Settings" tab
- [ ] **Expected:** Fields: Name (editable -- but see note below), Scoring System, Status (read-only)
- [ ] **Note:** Settings are only editable in DRAFT status. Since Amadora is REGISTRATION_OPEN, the Save button and editable fields may be disabled.

### Manage Entries link

- [ ] **Expected:** "Manage Entries" link visible in the header area
- [ ] Click "Manage Entries"
- [ ] **Expected:** Navigated to `/divisions/{id}/entry-admin`
- [ ] Navigate back to division detail

### Advance status from division detail

- [ ] **Expected:** "Advance Status" button visible (since status is not RESULTS_PUBLISHED)
- [ ] **Do NOT click** -- this would advance Amadora beyond REGISTRATION_OPEN, affecting later tests

### Authorization -- unauthorized user redirected

- [ ] Log in as `user@example.com` (regular USER, not competition ADMIN)
- [ ] Navigate directly to `/divisions/{amadoraId}`
- [ ] **Expected:** Page loads (user has credits in this division, so MyEntriesView would be accessible, but DivisionDetailView requires ADMIN)
- [ ] **Note:** Check whether regular entrant can see division detail or is redirected

---

## 8. Entry Admin (Amadora)

**Covers:** `DivisionEntryAdminViewTest`, `EntryServiceTest` (credits, entries, products)

*Log in as `compadmin@example.com` for all steps.*

### Navigate to Entry Admin

- [ ] From Amadora division detail, click "Manage Entries"
- [ ] **Expected:** Page title "Amadora -- Entry Admin"
- [ ] **Expected:** TabSheet with 4 tabs: Credits, Entries, Products, Orders

### Credits tab

- [ ] **Expected:** Default tab is "Credits"
- [ ] **Expected:** Grid with columns: Email, Name, Credits, Entries
- [ ] **Expected:** `user@example.com` -- Credits: 5, Entries: 3
- [ ] **Expected:** `entrant@example.com` -- Credits: 3, Entries: 1

### Add credits

- [ ] Click "Add Credits"
- [ ] **Expected:** Dialog with fields: Entrant Email, Amount (default: 1)
- [ ] Enter email: `user@example.com`, amount: `2`
- [ ] Click "Add"
- [ ] **Expected:** Notification "Credits added" (green)
- [ ] **Expected:** `user@example.com` credits now shows 7

### Mutual exclusivity -- add credits to different division

- [ ] Navigate to Profissional division entry-admin (via CompetitionDetailView > Profissional > View > Manage Entries)
- [ ] Click "Add Credits"
- [ ] Enter email: `user@example.com`, amount: `1`
- [ ] Click "Add"
- [ ] **Expected:** Error notification -- mutual exclusivity violation (user already has credits in Amadora)

### Entries tab

- [ ] Navigate back to Amadora entry-admin
- [ ] Click the "Entries" tab
- [ ] **Expected:** Grid with columns: Entry #, Entry Code, Mead Name, Category, Entrant, Status
- [ ] **Expected:** 4 entries total (3 from user@example.com, 1 from entrant@example.com)
- [ ] **Expected:** Blueberry Bliss -- Status: SUBMITTED
- [ ] **Expected:** Others -- Status: DRAFT

### Products tab

- [ ] Click the "Products" tab
- [ ] **Expected:** Grid with columns: Product ID, SKU, Product Name, Credits/Unit
- [ ] **Expected:** Row: Product ID 1001, SKU "CHIP-AMA", Product Name "CHIP Amadora Entry", Credits/Unit 1

### Add product mapping

- [ ] Click "Add Mapping"
- [ ] **Expected:** Dialog with fields: Jumpseller Product ID, SKU (optional), Product Name, Credits Per Unit (default: 1)
- [ ] Enter product ID: `9999`, product name: `Test Product`, credits: `2`
- [ ] Click "Add"
- [ ] **Expected:** Notification "Product mapping added" (green)
- [ ] **Expected:** New mapping appears in grid

### Orders tab

- [ ] Click the "Orders" tab
- [ ] **Expected:** Grid with columns: Order ID, Customer, Status, Date
- [ ] **Expected:** Grid is empty (orders come only via webhook, not seeded)

---

## 9. My Entries (Amadora -- entrant view)

**Covers:** `MyEntriesViewTest`, `EntryServiceTest` (create/update/delete/submit entries)

### Navigate as entrant

- [ ] Log in as `user@example.com`
- [ ] Navigate to `/divisions/{amadoraId}/my-entries` (use the Amadora division UUID from the URL seen earlier as admin)
- [ ] **Expected:** Page title "Amadora -- My Entries"

### Credit balance display

- [ ] **Expected:** Credit info shows: "Credits: N remaining (M total, K used)"
- [ ] **Expected:** Total should be 7 (5 original + 2 added in section 8), used should be 3

### Entries grid

- [ ] **Expected:** Grid with columns: Entry #, Mead Name, Category, Status
- [ ] **Expected:** 3 entries:
  - Wildflower Traditional -- M1A -- DRAFT
  - Blueberry Bliss -- M2C -- SUBMITTED
  - Oak-Aged Bochet -- M1A -- DRAFT

### Add entry -- success

- [ ] **Expected:** "Add Entry" button is enabled (remaining credits > 0)
- [ ] Click "Add Entry"
- [ ] **Expected:** Dialog (600px wide) with fields: Mead Name, Category, Sweetness, Strength, ABV (%), Carbonation, Honey Varieties, Other Ingredients, Wood Aged checkbox, Additional Information
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

- [ ] Click "Add Entry"
- [ ] Leave mead name blank, fill in other required fields
- [ ] Click "Save"
- [ ] **Expected:** Mead name field shows error "Mead name is required"

### Add entry -- validation (missing required fields)

- [ ] Click "Add Entry"
- [ ] Enter only mead name, leave other required fields empty
- [ ] Click "Save"
- [ ] **Expected:** Notification "Please fill in all required fields"

### Edit draft entry

- [ ] Find "Wildflower Traditional" (DRAFT) in the grid
- [ ] Click to edit (or use the edit action if available)
- [ ] **Expected:** Dialog pre-populated with entry data
- [ ] Change mead name to `Wildflower Traditional (Updated)`
- [ ] Click "Save"
- [ ] **Expected:** Notification "Entry updated" (green)
- [ ] **Expected:** Grid shows updated name

### Delete draft entry

- [ ] Note: This step depends on whether a delete action is exposed in the UI
- [ ] If a delete button/action exists for DRAFT entries, click it
- [ ] **Expected:** Entry removed, credit slot freed

### Submit all drafts

- [ ] **Expected:** "Submit All" button is enabled (there are DRAFT entries)
- [ ] Click "Submit All"
- [ ] **Expected:** Confirmation dialog: "Submit N entries? This cannot be undone."
- [ ] Click "Submit"
- [ ] **Expected:** Notification "N entries submitted" (green)
- [ ] **Expected:** All previously DRAFT entries now show status SUBMITTED
- [ ] **Expected:** "Submit All" button is now disabled (no more drafts)
- [ ] **Expected:** "Add Entry" button may still be enabled if credits remain

### Entry limit enforcement

**Subcategory limit (3 per subcategory):**

- [ ] Create entries to test the subcategory limit (max 3 per subcategory in Amadora)
- [ ] After reaching 3 non-withdrawn entries in M1A (Traditional Mead Dry), attempt to create a 4th
- [ ] **Expected:** Error "Entry limit reached for this subcategory (max 3)"

**Main category limit (5 per main category):**

- [ ] Create entries across multiple M1 subcategories (M1A, M1B, M1C, M1V) to total 5
- [ ] Attempt to create a 6th entry under any M1 subcategory
- [ ] **Expected:** Error "Entry limit reached for this main category (max 5)"

---

## 10. Cross-cutting Concerns

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
- [ ] Navigate directly to `/divisions/{amadoraId}/entry-admin`
- [ ] **Expected:** Redirected to `/` (entrants cannot access admin view)
- [ ] Log in as `judge@example.com`
- [ ] Navigate directly to `/divisions/{amadoraId}/entry-admin`
- [ ] **Expected:** Redirected to `/` (judges cannot access admin view)
- [ ] Navigate directly to `/divisions/{amadoraId}/my-entries`
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

---

## 11. Multi-Role & Cross-Competition Edge Cases

**Goal:** Test combinations of roles across competitions and identify gaps in
credential management and authorization. Some of these are exploratory — note
the actual behavior and decide whether it needs to change.

### Same competition: multiple roles

- [ ] Log in as `compadmin@example.com`
- [ ] Navigate to CHIP 2026 > Participants tab
- [ ] Add `judge@example.com` as ENTRANT (judge is already a JUDGE in CHIP)
- [ ] **Observe:** Does the system allow a user to be both JUDGE and ENTRANT?
- [ ] **Decide:** Should this be allowed? (CHIP rules say judges cannot judge their own entries, but they might enter in a different category/division)
- [ ] If allowed: verify `judge@example.com` can access both My Entries and any future judge views
- [ ] Clean up: remove the ENTRANT role from `judge@example.com` if needed

### Cross-competition: entrant becomes competition admin

This is the most important edge case. A user who is an ENTRANT in one competition
may be invited as a competition ADMIN for a different competition.

- [ ] Log in as `admin@example.com` (SYSTEM_ADMIN)
- [ ] Create a new competition (e.g., "Regional 2026")
- [ ] Add `entrant@example.com` as ADMIN of "Regional 2026"
- [ ] Log out
- [ ] **Test:** How does `entrant@example.com` log in?
  - They have no password (magic link only)
  - They have no access code (access codes are for JUDGE/STEWARD roles)
  - As competition admin, they need reliable access (magic links expire)
- [ ] **Decide:** What's the credential setup flow when a user becomes a competition admin?
  - Option A: SYSTEM_ADMIN sets a password for them via the Users page
  - Option B: Automatic password setup email/link when added as ADMIN
  - Option C: Competition admins can also use magic links (current behavior)
- [ ] **Test navigation:** If `entrant@example.com` logs in via magic link:
  - Do they see "My Competitions" in the sidebar? (Should show "Regional 2026")
  - Can they still access their entries in CHIP 2026 Amadora?

### Cross-competition: competition admin is also entrant/judge elsewhere

- [ ] Log in as `compadmin@example.com`
- [ ] Navigate to CHIP 2026 > Participants tab
- [ ] Add credits for `compadmin@example.com` in Amadora (makes them an ENTRANT)
- [ ] Log out, log back in as `compadmin@example.com`
- [ ] **Test:** Can they access both:
  - "My Competitions" to manage CHIP 2026 (as ADMIN)?
  - `/divisions/{amadoraId}/my-entries` to manage their entries (as ENTRANT)?
- [ ] **Observe:** Is the navigation clear? Can they tell which "hat" they're wearing?
- [ ] Clean up if needed

### Login mechanism with mixed credentials

- [ ] A user with both a password and a magic link should be able to use either
- [ ] A user with an access code (JUDGE/STEWARD) and a password (competition ADMIN in another competition) should be able to use either
- [ ] **Test:** Log in as `judge@example.com` with their access code -- does it still work after they've been made ADMIN of another competition?
- [ ] **Decide:** Should access codes work across competitions or only for the competition that issued them?

### Summary of decisions needed

After running the above tests, document decisions on:

1. **Multiple roles in same competition** — allowed or not? Any restrictions?
2. **Credential setup for new competition admins** — what's the flow?
3. **Navigation clarity** — does the UI make it clear when a user has multiple roles?
4. **Access code scope** — per-competition or per-user?

---

## Appendix: Coverage Mapping

| Walkthrough Section | Automated Tests |
|---|---|
| 2. Authentication | `LoginViewTest`, `SetPasswordViewTest`, `AdminPasswordAuthenticationTest`, `JwtMagicLinkAuthenticationTest`, `RootUrlRedirectTest`, `LogoutFlowTest`, `UserActivationListenerTest`, `SecurityConfigTest`, `AccessCodeAwareAuthenticationProviderTest`, `DevUserInitializerTest` |
| 3. Navigation & Layout | `MainLayoutTest`, `RootUrlRedirectTest`, `MyCompetitionsViewTest` |
| 4. User Management | `UserListViewTest`, `UserServiceTest`, `UserServiceValidationTest`, `UserTest`, `AdminInitializerTest` |
| 5. Competition Management | `CompetitionListViewTest`, `CompetitionServiceTest`, `CompetitionTest` |
| 6. Competition Detail | `CompetitionDetailViewTest`, `CompetitionServiceTest`, `DivisionTest`, `ParticipantTest`, `ParticipantRoleTest` |
| 7. Division Detail | `DivisionDetailViewTest`, `CompetitionServiceTest`, `DivisionCategoryRepositoryTest`, `CategoryRepositoryTest`, `DivisionStatusTest` |
| 8. Entry Admin | `DivisionEntryAdminViewTest`, `EntryServiceTest`, `ProductMappingRepositoryTest`, `JumpsellerOrderRepositoryTest` |
| 9. My Entries | `MyEntriesViewTest`, `EntryServiceTest`, `EntryTest`, `EntryCreditRepositoryTest`, `EntryRepositoryTest` |
| 10. Cross-cutting | `EntryServiceTest`, `DevDataInitializerTest`, `EntryModuleTest`, `CompetitionModuleTest`, `ModulithStructureTest` |

### Tests without direct manual coverage

These tests cover internal behavior not directly visible in the UI:

- `JwtMagicLinkServiceTest` -- token generation/validation internals
- `DatabaseUserDetailsServiceTest` -- UserDetailsService internals
- `AdminInitializerIntegrationTest` -- startup initialization
- `UserRepositoryTest`, `CompetitionRepositoryTest`, `DivisionRepositoryTest`, `ParticipantRepositoryTest`, `ParticipantRoleRepositoryTest` -- persistence layer
- `JumpsellerOrderTest`, `JumpsellerOrderLineItemTest` -- domain model internals
- `JumpsellerOrderLineItemRepositoryTest` -- persistence layer
- `WebhookServiceTest` -- webhook processing (requires external HTTP calls)
- `RegistrationClosedListenerTest` -- event listener skeleton
- `CompetitionAccessCodeValidatorTest` -- access code validation internals
- `MeadsApplicationTest` -- context loading
