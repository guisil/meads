# Post-Deployment Walkthrough

End-to-end test plan for the MEADS application after deployment to production.
Starts from a clean database (no seeded data) and walks through the full workflow:
admin setup, competition creation, entrant registration, entry submission, and labels.

**Date:** 2026-03-12
**Environment:** Production (no dev profile, no seeded data)
**Initial state:** Only the bootstrapped SYSTEM_ADMIN exists (from `AdminInitializer`)

---

## 1. Prerequisites

### Verify the application is running

- [ ] Navigate to the application URL (e.g., `https://meads.example.com`)
- [ ] **Expected:** Redirected to `/login`
- [ ] **Expected:** Login page shows: email field, "Get Login Link" button, collapsible "Login with credentials" section

### Verify email delivery

- [ ] Confirm SMTP is configured and working (Resend or other provider)
- [ ] Have access to at least 3 real email addresses for testing:
  - **Admin email** — for the system admin account
  - **Competition admin email** — for competition management
  - **Entrant email** — for entry submission

### Note the admin credentials

The initial SYSTEM_ADMIN is created by `AdminInitializer` on first startup.
Check the environment variables for the admin email and password
(`ADMIN_EMAIL` / `ADMIN_PASSWORD`, or defaults from `application.properties`).

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

## 4. Competition Setup

*Log in as the SYSTEM_ADMIN for this section.*

### Create a competition

- [ ] Navigate to `/competitions`
- [ ] Click "Create Competition"
- [ ] Fill in: Name, Short Name, Start Date, End Date, Location
- [ ] Optionally upload a logo (PNG/JPEG, max 2.5 MB)
- [ ] Click "Save"
- [ ] **Expected:** Notification "Competition created successfully" (green)
- [ ] **Expected:** Competition appears in grid

### Open the competition detail

- [ ] Click the competition row in the grid
- [ ] **Expected:** Competition detail page with tabs: Divisions, Participants, Settings, Documents
- [ ] **Expected:** Breadcrumb: "Competitions / {Competition Name}"

### Add the competition admin as participant

- [ ] Click the "Participants" tab
- [ ] Click "Add Participant"
- [ ] Enter the competition admin email, select role: Admin
- [ ] Click "Add"
- [ ] **Expected:** Notification "Participant added successfully" (green)
- [ ] **Expected:** Notification "Password setup email sent to {email}" (green) — if user has a password, a password setup email is sent
- [ ] **Expected:** Participant appears in grid with role "Admin"

### Set competition contact email (optional)

- [ ] Click the "Settings" tab
- [ ] Enter a contact email in the "Contact Email" field
- [ ] Click "Save"
- [ ] **Expected:** Notification "Competition updated successfully" (green)

### Set shipping address and phone (optional)

- [ ] In the Settings tab, enter a shipping address and phone number
- [ ] Click "Save"
- [ ] **Expected:** These will appear on entry labels (instruction header)

---

## 5. Division Setup

*Log in as the competition admin for this section (or continue as SYSTEM_ADMIN).*

### Create a division

- [ ] Navigate to the competition detail (via "My Competitions" or `/competitions`)
- [ ] On the "Divisions" tab, click "Create Division"
- [ ] Fill in: Name, Short Name, Scoring System (MJP)
- [ ] Set entry limits if desired (per subcategory, per main category, total)
- [ ] Set a registration deadline and timezone
- [ ] Click "Save"
- [ ] **Expected:** Notification "Division created successfully" (green)
- [ ] **Expected:** Division appears in grid with status "Draft"

### Configure division settings

- [ ] Click the division row to open the detail view
- [ ] Click the "Settings" tab
- [ ] Set Entry Prefix (e.g., "AMA") — only editable in DRAFT status
- [ ] Set "Meadery Name Required" if needed — only editable in DRAFT status
- [ ] Verify entry limit fields show the values set during creation
- [ ] Click "Save"
- [ ] **Expected:** Notification "Settings saved successfully" (green)

### Add categories

- [ ] Click the "Categories" tab
- [ ] Click "Add Category"
- [ ] On "From Catalog" tab, select a main category (e.g., M1 — Traditional Mead)
- [ ] Click "Add"
- [ ] **Expected:** Main category and its subcategories appear in the TreeGrid
- [ ] Repeat for other desired categories (M2, M3, M4, etc.)
- [ ] Optionally remove unwanted subcategories (click X icon on the row)
- [ ] Optionally add custom categories via the "Custom" tab

### Set up product mappings (for webhook integration)

- [ ] Click "Manage Entries" in the division detail header
- [ ] **Expected:** Entry admin view with tabs: Credits, Entries, Products, Orders
- [ ] Click the "Products" tab
- [ ] Click "Add Mapping"
- [ ] Enter the Jumpseller Product ID, SKU (optional), Product Name, Credits Per Unit
- [ ] Click "Add"
- [ ] **Expected:** Notification "Product mapping added" (green)

### Advance division to Registration Open

- [ ] Navigate back to the competition detail > Divisions tab
- [ ] Click the forward icon (tooltip: "Advance Status") on the division
- [ ] **Expected:** Confirmation dialog: "Advance division '{name}' from Draft to Registration Open?"
- [ ] Click "Advance"
- [ ] **Expected:** Status badge changes to "Registration Open"
- [ ] **Expected:** Division is now accepting entries

### Add documents (optional)

- [ ] Click the "Documents" tab on the competition detail
- [ ] Add any relevant documents (rules PDF, external links)
- [ ] These will be visible to entrants on their My Entries page

---

## 6. Entrant Onboarding

### Grant entry credits manually

*Log in as the competition admin.*

- [ ] Navigate to the division's entry admin (via division detail > "Manage Entries")
- [ ] On the "Credits" tab, click "Add Credits"
- [ ] Enter the entrant's email address and the number of credits
- [ ] Click "Add"
- [ ] **Expected:** Notification "Credits added" (green)
- [ ] **Expected:** If the user doesn't exist, they are NOT created — the email must match an existing user
- [ ] **Expected:** If the user exists, they appear in the Credits grid
- [ ] **Expected:** Credit notification email sent to the entrant with subject "[MEADS] Entry credits received — {division}"
- [ ] **Expected:** Email contains a magic link "View My Entries" button

### Grant entry credits via webhook (alternative)

If Jumpseller integration is configured:

- [ ] A customer purchases an entry product on Jumpseller
- [ ] **Expected:** Webhook `POST /api/webhooks/jumpseller/order-paid` fires
- [ ] **Expected:** Credits are automatically awarded to the customer's email
- [ ] **Expected:** If the user doesn't exist, a PENDING user is created automatically
- [ ] **Expected:** Credit notification email sent to the customer
- [ ] Verify in the entry admin Orders tab that the order appears with status PROCESSED
- [ ] Verify in the Credits tab that the customer has the correct credit balance

### Entrant first login

- [ ] The entrant receives the credit notification email
- [ ] Click the "View My Entries" magic link in the email
- [ ] **Expected:** Authenticated and redirected to My Entries page for the division
- [ ] **Expected:** If user was PENDING, status is now ACTIVE (activated on first login)

---

## 7. Entry Submission (Entrant View)

*Logged in as the entrant.*

### My Entries page

- [ ] **Expected:** Page title "{Division Name} -- My Entries"
- [ ] **Expected:** Breadcrumb: "My Entries / {Competition Name} / {Division Name}"
- [ ] **Expected:** Credit info: "Credits: N remaining (N total, 0 used)"
- [ ] **Expected:** Limits info shows configured limits (if any)
- [ ] **Expected:** Process info box explaining the workflow
- [ ] **Expected:** Registration deadline displayed (if set)
- [ ] **Expected:** Competition documents listed (if any were added)
- [ ] **Expected:** Empty entries grid

### Update profile (if meadery name required)

- [ ] If the division requires a meadery name and the entrant hasn't set one:
- [ ] **Expected:** Warning banner: "This division requires a meadery name..." with "My Profile" link
- [ ] **Expected:** Submit buttons are disabled
- [ ] Click "My Profile", set meadery name and country, save
- [ ] Navigate back to My Entries
- [ ] **Expected:** Warning is gone, submit buttons enabled

### Add entries

- [ ] Click "Add Entry"
- [ ] **Expected:** Dialog with fields: Mead Name, Category (subcategories only), Sweetness, Strength, ABV (%), Carbonation, Honey Varieties, Other Ingredients, Wood Aged checkbox, Additional Information
- [ ] **Expected:** Category hints appear when selecting certain categories
- [ ] Fill in all required fields, click "Save"
- [ ] **Expected:** Notification "Entry created" (green)
- [ ] **Expected:** Entry appears in grid with status DRAFT
- [ ] **Expected:** Credits used count increases
- [ ] Repeat to add more entries (up to available credits)

### Edit a draft entry

- [ ] Click the edit (pencil) icon on a DRAFT entry
- [ ] **Expected:** Dialog pre-populated with entry data
- [ ] Make changes, click "Save"
- [ ] **Expected:** Notification "Entry updated" (green)

### View entry details

- [ ] Click the view (eye) icon on any entry
- [ ] **Expected:** Read-only dialog showing all entry fields

### Submit entries

- [ ] Click "Submit All Drafts"
- [ ] **Expected:** Confirmation dialog: "Submit N draft entries? Submitted entries can no longer be edited."
- [ ] Click "Submit"
- [ ] **Expected:** Notification "N entries submitted" (green)
- [ ] **Expected:** All entries now show status SUBMITTED
- [ ] **Expected:** Edit and submit buttons disabled for SUBMITTED entries
- [ ] **Expected:** If all credits used and no drafts remain: confirmation email sent with entry summary

### Download labels

- [ ] **Expected:** "Download all labels" button is enabled (no drafts remain)
- [ ] Click "Download all labels"
- [ ] **Expected:** PDF downloads with one page per entry
- [ ] **Expected:** Each page has instruction header (with shipping address if set) and 3 identical labels
- [ ] **Expected:** Labels show: competition name, division name, entry ID (with prefix), mead name, category, characteristics, ingredients, QR code, disclaimer
- [ ] Click the download icon on an individual SUBMITTED entry
- [ ] **Expected:** Single-entry label PDF downloads

---

## 8. Admin Entry Management

*Log in as the competition admin.*

### View entries

- [ ] Navigate to division entry admin > "Entries" tab
- [ ] **Expected:** Grid shows all entries with columns: Entry #, Code, Mead Name, Category, Final Category ("—"), Entrant, Meadery, Country, Status, Actions
- [ ] Click the view (eye) icon on an entry
- [ ] **Expected:** Read-only dialog with all fields, status, and entrant email

### Edit an entry (admin)

- [ ] Click the edit (pencil) icon on an entry
- [ ] **Expected:** Confirmation dialog: "Are you sure you want to edit this entry's data? This should only be done to correct mistakes."
- [ ] Click "Proceed"
- [ ] **Expected:** Full edit dialog with all fields (works for any status except WITHDRAWN)
- [ ] Make a correction, click "Save"
- [ ] **Expected:** Notification "Entry updated" (green)

### Withdraw an entry

- [ ] Click the withdraw (ban) icon on an entry
- [ ] **Expected:** Confirmation dialog
- [ ] Click "Withdraw"
- [ ] **Expected:** Entry status changes to WITHDRAWN

### Download labels (admin)

- [ ] Click "Download all labels" in the Entries tab toolbar
- [ ] **Expected:** Confirmation dialog with entry count
- [ ] Click "Download"
- [ ] **Expected:** PDF with labels for all SUBMITTED + RECEIVED entries

### Review orders (if webhook used)

- [ ] Click the "Orders" tab
- [ ] **Expected:** Grid shows webhook orders with status, credits awarded/pending
- [ ] If any orders have status NEEDS_REVIEW or PARTIALLY_PROCESSED:
  - Click the edit icon to review
  - Update status and add an admin note if needed

---

## 9. Status Workflow

*Log in as the competition admin.*

### Close registration

- [ ] Navigate to competition detail > Divisions tab
- [ ] Click the forward icon on the division (currently REGISTRATION_OPEN)
- [ ] **Expected:** Confirmation: "Advance from Registration Open to Registration Closed?"
- [ ] Click "Advance"
- [ ] **Expected:** Status changes to "Registration Closed"
- [ ] **Expected:** Entrants can no longer add new entries (verify by logging in as entrant — "Add Entry" should be disabled)

### Continue through the workflow

- [ ] Advance through remaining statuses as needed:
  - REGISTRATION_CLOSED → JUDGING
  - JUDGING → DELIBERATION
  - DELIBERATION → RESULTS_PUBLISHED
- [ ] **Expected:** Each advance shows confirmation dialog with correct statuses
- [ ] **Expected:** At RESULTS_PUBLISHED, "Advance Status" is hidden/disabled

### Revert status (if needed)

- [ ] Click "Revert Status" to go back one step
- [ ] **Expected:** Confirmation dialog with correct statuses
- [ ] **Note:** Reverting to DRAFT is blocked if entries exist (entry guard)

---

## 10. Security Checks

### Authorization boundaries

- [ ] As entrant, navigate directly to `/competitions/{shortName}` — **Expected:** Redirected (not admin)
- [ ] As entrant, navigate to entry admin URL — **Expected:** Redirected (not admin)
- [ ] Log out, navigate to any protected URL — **Expected:** Redirected to `/login`

### XSS prevention

- [ ] Create an entry with mead name: `<script>alert('xss')</script>`
- [ ] **Expected:** Name appears as literal text everywhere (grid, dialog, labels)

### Webhook security

- [ ] Send a request to the webhook endpoint without HMAC signature
- [ ] **Expected:** 401 Unauthorized
- [ ] Send a request with an invalid signature
- [ ] **Expected:** 401 Unauthorized

### Email enumeration prevention

- [ ] On `/login`, request a magic link for a non-existent email
- [ ] **Expected:** Same generic message as for existing emails — no enumeration

---

## 11. Cleanup / Verification

### Final checks

- [ ] Verify all emails were delivered correctly (check email provider dashboard)
- [ ] Verify labels PDF renders correctly in different PDF viewers
- [ ] Verify the application responds under normal load
- [ ] Check application logs for any unexpected errors or warnings

### State summary

After completing this walkthrough, the application should have:

- 1 SYSTEM_ADMIN user
- 1 competition admin user (with password)
- 1+ entrant users (magic link)
- 1 competition with at least 1 division
- Categories configured per division
- Product mappings (if using webhooks)
- Entries submitted by entrants
- Labels generated and downloaded
