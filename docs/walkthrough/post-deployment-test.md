# Post-Deployment Walkthrough

End-to-end test plan for the MEADS application after deployment to production.
Starts from a clean database (no seeded data) and walks through the full workflow
in two stages: a test competition for verification, then the real competition setup.

**Date:** 2026-03-15
**Environment:** Production (no dev profile, no seeded data)
**Initial state:** Only the bootstrapped SYSTEM_ADMIN exists (from `AdminInitializer`)

---

## 0. Infrastructure Verification

Before testing the application itself, verify that all infrastructure components are
accessible and functioning. Reference: `docs/plans/deployment-checklist.md` (operations
reference section).

### Database

- [ ] Navigate to DO Console → Databases → meads-db
- [ ] **Overview:** Connection details are visible, cluster status is "Online"
- [ ] **Insights:** Active connections from the app are shown
- [ ] **Backups (SnapShooter):** Daily backup job configured with 7-day retention
- [ ] **Settings → Trusted Sources:** App Platform app is listed

### Application

- [ ] Navigate to DO Console → App Platform → meads app
- [ ] **Runtime Logs:** Logs are streaming, no recurring errors
- [ ] **Activity:** Latest deployment shows as "Active"
- [ ] **Insights:** CPU and memory usage are within normal range
- [ ] **Settings → Domains:** `meads.app` shows as "Active" with SSL

### Monitoring

- [ ] **Alert Policies:** CPU and Memory alert policies are configured
- [ ] Verify alert destination email is correct

### Email

- [ ] Navigate to resend.com → Domains
- [ ] **Expected:** `meads.app` shows as "Verified"
- [ ] Check Usage tab for current email counts

### Version

- [ ] Open `https://meads.app`, log in
- [ ] Open sidebar drawer → version number is displayed at the bottom
- [ ] **Expected:** Version matches the latest release tag

---

# Stage 1: Test Competition

Purpose: verify the full workflow end-to-end, including the Jumpseller webhook
integration, before setting up the real competition. The test competition and its
data will be deleted after verification.

---

## 1. Prerequisites

### Verify the application is running

- [ ] Navigate to `https://meads.app`
- [ ] **Expected:** Redirected to `/login`
- [ ] **Expected:** Login page shows: email field, "Get Login Link" button, collapsible "Login with credentials" section

### Verify email delivery

- [ ] Confirm SMTP is configured and working (Resend)
- [ ] Have access to at least 3 real email addresses for testing:
  - **Admin email** — for the system admin account
  - **Competition admin email** — for competition management
  - **Entrant email** — for entry submission

### Note the admin credentials

The initial SYSTEM_ADMIN is created by `AdminInitializer` on first startup.
Check the environment variables for the admin email and password
(`INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD`).

---

## 2. System Admin Login

- [ ] Navigate to `/login`
- [ ] Enter the admin email
- [ ] Expand "Login with credentials"
- [ ] Enter the admin password
- [ ] Click "Login"
- [ ] **Expected:** Redirected to `/competitions` (SYSTEM_ADMIN default landing page)
- [ ] **Expected:** Sidebar shows "Competitions" and "Users"
- [ ] **Expected:** Competitions page is empty (no competitions yet)

---

## 3. User Management

### Create a competition admin user

- [ ] Navigate to `/users`
- [ ] **Expected:** Grid shows only the admin user
- [ ] Click "Create User"
- [ ] Enter email: `<competition-admin-email>`, name: `Competition Admin`
- [ ] Leave role as USER
- [ ] Click "Save"
- [ ] **Expected:** Notification "User created successfully" (green)
- [ ] **Expected:** New user appears in grid with status PENDING

### Set up competition admin password

- [ ] Click the key icon (tooltip: "Password Reset") on the new user
- [ ] **Expected:** Notification "Password reset link sent successfully" (green)
- [ ] **Expected:** Email arrives with subject "Reset your MEADS password", "Set Password" button
- [ ] Open the email, click "Set Password"
- [ ] **Expected:** Set Password page with info message about login links being disabled after setting a password
- [ ] Enter a password (8+ chars), confirm it
- [ ] Click "Set Password"
- [ ] **Expected:** "Password set successfully" notification, redirected to `/login`

### Verify competition admin login

- [ ] Log out
- [ ] Log in with the competition admin email and password
- [ ] **Expected:** Redirected to `/my-competitions` (empty — no competitions yet)
- [ ] Log out

---

## 4. Test Competition Setup

*Log in as the SYSTEM_ADMIN for this section.*

### Create a test competition

- [ ] Navigate to `/competitions`
- [ ] Click "Create Competition"
- [ ] Fill in: Name: "Test Competition", Short Name: "test", Start Date, End Date, Location
- [ ] Optionally upload a logo
- [ ] Click "Save"
- [ ] **Expected:** Notification "Competition created successfully" (green)

### Add the competition admin as participant

- [ ] Open the test competition detail
- [ ] Click the "Participants" tab
- [ ] Click "Add Participant"
- [ ] Enter the competition admin email, select role: Admin
- [ ] Click "Add"
- [ ] **Expected:** Participant appears in grid with role "Admin"

### Set competition settings

- [ ] Click the "Settings" tab
- [ ] Enter a contact email, shipping address, phone number, website
- [ ] Click "Save"

---

## 5. Test Division Setup

### Create a test division

- [ ] On the "Divisions" tab, click "Create Division"
- [ ] Fill in: Name: "Test Division", Short Name: "test", Scoring System: MJP
- [ ] Set entry limits (e.g., 3 per subcategory, 5 per main category, 10 total)
- [ ] Set a registration deadline (in the future) and timezone
- [ ] Click "Save"

### Configure division settings

- [ ] Open the test division detail
- [ ] Click the "Settings" tab
- [ ] Set Entry Prefix (e.g., "TST")
- [ ] Set "Meadery Name Required" to true
- [ ] Click "Save"

### Add categories

- [ ] Click the "Categories" tab
- [ ] Add at least 2 main categories from catalog (e.g., M1 — Traditional Mead, M2 — Fruit Melomel)
- [ ] Verify subcategories appear under each main category

### Set up product mappings

- [ ] Click "Manage Entries" in the division detail header
- [ ] Click the "Products" tab
- [ ] Click "Add Mapping"
- [ ] Enter the Jumpseller Product ID, SKU (optional), Product Name, Credits Per Unit
- [ ] Click "Add"
- [ ] **Expected:** Notification "Product mapping added" (green)

### Advance to Registration Open

- [ ] Navigate back to competition detail > Divisions tab
- [ ] Click the forward icon on the test division
- [ ] Confirm advance from Draft to Registration Open
- [ ] **Expected:** Status changes to "Registration Open"

---

## 6. Jumpseller Webhook Test

### Configure Jumpseller webhook

- [ ] In Jumpseller admin (Config → Notifications / Webhooks), configure a webhook:
  - URL: `https://meads.app/api/webhooks/jumpseller/order-paid`
  - Jumpseller sends all events to this URL — the app processes `order/paid` events
- [ ] Copy the **webhook token** shown on the Jumpseller webhooks page
- [ ] In DO (App Platform → Settings → Environment Variables), set `APP_JUMPSELLER_HOOKS_TOKEN`
  to match the Jumpseller token (this triggers a redeploy)

### Test webhook with a real purchase

- [ ] Make a test purchase on Jumpseller using the entrant email
- [ ] **Expected:** Webhook fires to `/api/webhooks/jumpseller/order-paid`
- [ ] **Expected:** Check app runtime logs for webhook processing messages
- [ ] **Expected:** Credits awarded to the entrant
- [ ] **Expected:** Credit notification email sent to the entrant with magic link

### Verify webhook results

- [ ] Log in as competition admin
- [ ] Navigate to test division entry admin
- [ ] **Orders tab:** order appears with status PROCESSED (or NEEDS_REVIEW if issues)
- [ ] **Credits tab:** entrant appears with correct credit balance
- [ ] If order has NEEDS_REVIEW status, review the reason and resolve

### Test webhook security

- [ ] Send a request without HMAC signature → **Expected:** 401 Unauthorized
- [ ] Send a request with invalid signature → **Expected:** 401 Unauthorized

---

## 7. Entrant Flow (Test)

### Entrant first login

- [ ] The entrant receives the credit notification email
- [ ] Click the "View My Entries" magic link
- [ ] **Expected:** Authenticated and redirected to My Entries page for the test division

### Update profile

- [ ] **Expected:** Warning banner about meadery name required
- [ ] **Expected:** Submit buttons are disabled
- [ ] Click "My Profile", set meadery name and country, save
- [ ] Navigate back to My Entries
- [ ] **Expected:** Warning is gone, submit buttons enabled

### My Entries page verification

- [ ] **Expected:** Header shows "Test Competition — Test Division — My Entries"
- [ ] **Expected:** Credit info, limits, process info box, registration deadline all displayed
- [ ] **Expected:** Competition documents listed (if any)

### Add entries

- [ ] Click "Add Entry"
- [ ] **Expected:** Dialog with all fields, category hints when selecting categories
- [ ] Fill in fields, click "Save"
- [ ] **Expected:** Entry appears in grid with status DRAFT and prefixed ID (TST-1)
- [ ] Add 1-2 more entries

### Edit a draft entry

- [ ] Click the edit icon on a DRAFT entry
- [ ] Make changes, save
- [ ] **Expected:** Entry updated

### Submit entries

- [ ] Click "Submit All Drafts"
- [ ] Confirm submission
- [ ] **Expected:** All entries now show status SUBMITTED
- [ ] **Expected:** If all credits used and no drafts remain: confirmation email sent

### Download labels

- [ ] **Expected:** "Download all labels" button is enabled
- [ ] Click "Download all labels"
- [ ] **Expected:** PDF with instruction header (shipping address, phone, website), 3 labels per page
- [ ] **Expected:** Labels show: competition name, division name, entry ID, mead name, category, characteristics, ingredients, QR code, disclaimer
- [ ] Download an individual entry label — verify it matches

---

## 8. Admin Entry Management (Test)

*Log in as the competition admin.*

### View and edit entries

- [ ] Navigate to test division entry admin > "Entries" tab
- [ ] **Expected:** Grid shows all entries with correct columns
- [ ] View an entry (eye icon) — read-only dialog with all fields
- [ ] Edit an entry (pencil icon) — confirmation gate, then full edit dialog
- [ ] Withdraw an entry (ban icon) — confirm, status changes to WITHDRAWN

### Download labels (admin)

- [ ] Click "Download all labels"
- [ ] **Expected:** PDF includes SUBMITTED + RECEIVED entries (not WITHDRAWN)

### Manual credit grant

- [ ] On the "Credits" tab, click "Add Credits"
- [ ] Enter the entrant's email and a number of credits
- [ ] **Expected:** Credits added, notification email sent to entrant

---

## 9. Status Workflow (Test)

### Close registration

- [ ] Advance division from REGISTRATION_OPEN → REGISTRATION_CLOSED
- [ ] **Expected:** Entrants can no longer add new entries

### Continue through workflow

- [ ] Advance: REGISTRATION_CLOSED → JUDGING → DELIBERATION → RESULTS_PUBLISHED
- [ ] **Expected:** Each advance shows confirmation dialog
- [ ] **Expected:** At RESULTS_PUBLISHED, advance is hidden/disabled

### Revert status

- [ ] Revert one step back
- [ ] **Expected:** Confirmation dialog, status reverts

---

## 10. Security Checks

### Authorization boundaries

- [ ] As entrant, navigate directly to `/competitions/test` → **Expected:** Redirected
- [ ] As entrant, navigate to entry admin URL → **Expected:** Redirected
- [ ] Log out, navigate to any protected URL → **Expected:** Redirected to `/login`

### XSS prevention

- [ ] Create an entry with mead name: `<script>alert('xss')</script>`
- [ ] **Expected:** Name appears as literal text everywhere

### Email enumeration prevention

- [ ] On `/login`, request a magic link for a non-existent email
- [ ] **Expected:** Same generic message as for existing emails

---

## 11. Test Cleanup

Once all tests pass:

- [ ] Log in as SYSTEM_ADMIN
- [ ] Delete the test competition (this removes all divisions, entries, credits, etc.)
- [ ] Optionally remove test users from the Users page (keep the competition admin if
  they'll be used for the real competition)
- [ ] **Do NOT delete the Jumpseller webhook configuration** — it will be used for the
  real competition

### Stage 1 sign-off

- [ ] All infrastructure checks passed
- [ ] User management works (create, password setup, login)
- [ ] Competition/division CRUD works
- [ ] Jumpseller webhook delivers and processes orders correctly
- [ ] Entrant flow works end-to-end (credits → entries → submit → labels)
- [ ] Admin entry management works (view, edit, withdraw, labels)
- [ ] Status workflow advances and reverts correctly
- [ ] Security checks passed
- [ ] Emails delivered correctly (magic link, password reset, credit notification, submission confirmation)

---

# Stage 2: Real Competition Setup

Purpose: set up the actual competition with real data. Only proceed after Stage 1
is fully verified.

---

## 12. Create the Real Competition

*Log in as the SYSTEM_ADMIN.*

- [ ] Navigate to `/competitions`
- [ ] Click "Create Competition"
- [ ] Fill in the real competition details: Name, Short Name, Start Date, End Date, Location
- [ ] Upload the competition logo
- [ ] Click "Save"

### Add participants

- [ ] Add the competition admin(s) as participants with Admin role
- [ ] Add any other known participants (judges, stewards) with appropriate roles

### Configure settings

- [ ] Set contact email
- [ ] Set shipping address, phone number, and website (for entry labels)

### Add documents

- [ ] Upload rules PDF and/or add external links
- [ ] Reorder documents as needed

---

## 13. Create Real Division(s)

- [ ] Create division(s) with real names, short names, and scoring system
- [ ] Configure entry limits, entry prefix, meadery name requirement
- [ ] Set registration deadline and timezone
- [ ] Add categories from catalog (and custom categories if needed)
- [ ] Set up product mappings matching real Jumpseller products
- [ ] Verify all settings are correct

### Advance to Registration Open

- [ ] Advance each division to Registration Open when ready
- [ ] **Expected:** The division is now live and accepting entries

---

## 14. Final Verification

- [ ] Verify the competition admin can log in and see the competition in "My Competitions"
- [ ] Verify the competition admin can access the division detail and entry admin views
- [ ] Make a real test purchase on Jumpseller → verify credits are awarded correctly
- [ ] Verify the entrant flow works end-to-end with the real competition data
- [ ] Check application logs for any errors
- [ ] Check email delivery (Resend dashboard)

### State summary

After completing this walkthrough, the production application should have:

- 1 SYSTEM_ADMIN user
- 1+ competition admin users (with passwords)
- 1 real competition with division(s) in REGISTRATION_OPEN status
- Categories and product mappings configured
- Jumpseller webhook active and verified
- Ready to accept entrant registrations
