# Post-Deployment Walkthrough

End-to-end test plan for the MEADS application after deployment to production.
Starts from a clean database (no seeded data) and walks through the full workflow
in two stages: a test competition for verification, then the real competition setup.

**Date:** 2026-05-25
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
- [ ] **Verify "Shared tables across divisions" checkbox** (defaults to ON for new
  competitions). ON means starting a round at "Table 1" in any division locks
  "Table 1" in every other division of this competition until the round completes.
  Turn OFF only if each division has its own independent physical setup.
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
- [ ] Scroll to the **Judging** sub-section:
  - [ ] Set **BOS places** (default 1) — number of Best of Show placements awarded for this division
  - [ ] Set **Minimum judges per round** (default 2) — hard minimum enforced when starting a round
  - [ ] **Note:** BOS places lock at JUDGING status; minimum judges lock once any round has status != PENDING. Set both now before advancing.
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

## 9. Judging Smoke Test

Minimal end-to-end exercise of the judging surfaces against production. Goal: prove
the judging UI works against the real DB/email, not to test every variant — the dev
walkthrough (`docs/walkthrough/manual-test.md` §12) covers exhaustive cases.

### Close registration

- [ ] Advance division from REGISTRATION_OPEN → REGISTRATION_CLOSED
- [ ] **Expected:** Entrants can no longer add new entries
- [ ] **Expected:** A new **"Judging Categories"** tab appears on Division Detail
- [ ] **Expected:** A **"Manage Judging"** button appears in the Division Detail header (visible from REGISTRATION_CLOSED onwards — judging setup happens here before flipping to JUDGING)

### Initialize judging categories

- [ ] On the Judging Categories tab, click **"Initialize Judging Categories"**
- [ ] **Expected:** All REGISTRATION-scope categories cloned into JUDGING scope; grid appears (Code, Name, Description, Remove)

### Mark entries RECEIVED and assign final categories

- [ ] On Entry Admin → Entries tab, advance at least one SUBMITTED entry to RECEIVED via the `→` arrow
- [ ] Click **"Auto-assign final categories"** → confirm → **Expected:** notification "Assigned N final category/categories." for entries whose initial-category code matches a JUDGING-scope code
- [ ] Manually assign final category for any unmatched entries via the Edit dialog

### Add physical tables

- [ ] Click "Manage Judging" → **Tables** tab
- [ ] **Expected:** "Shared tables is ON" banner (if competition.sharedTables = true) above the **+ Add Table** button
- [ ] Click "+ Add Table" → label "Table 1" → Save
- [ ] Add at least 2 more tables (Table 2, Table 3)

### Add JUDGE participants

- [ ] On Competition Detail → Participants tab, add at least 2 users as JUDGE
- [ ] (Optional) Add a STEWARD too
- [ ] **Note:** JudgeProfile (certifications, preferred comment language) is auto-created on first judge assignment to a round — no manual setup required. Judges can refine their profile via My Profile once assigned.

### Create a scoring round

- [ ] Manage Judging → **Rounds** tab → **"+ Add Round"** → Type SCORING, Name "M1A Panel 1", Category M1A (or any JUDGING category with RECEIVED entries), Table 1, Scheduled date → Save
- [ ] Click 📦 **Assign Entries** → tick the RECEIVED entries with finalCategory = M1A → Save
- [ ] Click 👥 **Assign Judges** → tick at least 2 judges (no COI; expect orange "Similar meadery" badges if a judge's meadery matches an entrant's) → Save
- [ ] **Expected:** Round Status is READY

### Advance to JUDGING

- [ ] Back on Division Detail, click "Advance Status" → REGISTRATION_CLOSED → JUDGING
- [ ] **Expected:** All guards pass; status badge updates to JUDGING
- [ ] **Expected:** Guard rejection if any SUBMITTED/RECEIVED entry still lacks a final category — assign and retry

### Start the round

- [ ] Manage Judging → Rounds tab → click **▶ Start** on the round → confirm
- [ ] **Expected:** Round Status ACTIVE; DRAFT scoresheets created for each assigned entry; each assigned judge receives a "table ready" email

### Fill in a scoresheet (as a judge)

- [ ] Log out, log in as one of the assigned judges (magic link from the table-ready email or the access code shown in the participant grid)
- [ ] Click **"My Judging"** in the sidebar → click "Open table →" on the active round
- [ ] Click into a DRAFT scoresheet → fill MJP fields (Aroma, Appearance, Flavor, Mouthfeel, Overall Impression) → optionally add per-item comments → Submit
- [ ] **Expected:** Status changes to SUBMITTED; total score appears
- [ ] **Expected (anonymity):** `meadName` is hidden from judges (admins still see it)
- [ ] Fill in remaining scoresheets at the table → round transitions to COMPLETE when all are SUBMITTED

### Add and complete a medal round

- [ ] Log back in as competition admin → Manage Judging → Rounds tab → **"+ Add Round"** → Type MEDAL, Category M1A → Save
- [ ] **Expected:** Once the scoring round COMPLETES, the medal round auto-transitions to READY and `medalRound.entries` auto-populates from the eligible set (advance-flag entries in COMPARATIVE mode, all SUBMITTED in SCORE_BASED mode)
- [ ] Click Open on the medal row → MedalRoundView → assign judges → Start → mark medals (Gold/Silver/Bronze/None) → Finalize
- [ ] **Expected:** Medal round Status COMPLETE
- [ ] **Verify the one-medal-per-category backstop:** try adding a second MEDAL round for the same category → **Expected:** error *"A medal round for this category already exists."* (V31 partial unique index)

### Set BOS placements (if BOS places > 0)

- [ ] All medal rounds in the division must be COMPLETE first
- [ ] Best of Show tab → **Start BOS** → assign GOLD candidates to BOS placements → **Finalize BOS**

### Cross-division shared-tables check (if running ≥ 2 divisions)

- [ ] With sharedTables = ON and an ACTIVE round at division A's `Table 1`, switch to division B and try to start any round at its `Table 1`
- [ ] **Expected:** Error *"Table 'Table 1' is already in use by an active round in another division of this competition..."* (key `error.round.physical-table-busy-shared`)
- [ ] Either revert/finish the A round or toggle sharedTables OFF in competition settings, then re-try → **Expected:** success

---

## 10. Awards Smoke Test

### Advance to DELIBERATION

- [ ] On Division Detail, click "Advance Status" → JUDGING → DELIBERATION
- [ ] **Expected:** Status badge updates to DELIBERATION
- [ ] **Expected:** Manage Judging header now shows a **"Manage results"** button

### Publish results

- [ ] Click "Manage results" → AwardsAdminView (`/competitions/.../divisions/.../results-admin`)
- [ ] Click **"Publish results"** → confirm
- [ ] **Expected:** Notification "Results published"; division status advances to RESULTS_PUBLISHED; Publication **v1** appears in the history grid

### Verify public results

- [ ] In a fresh browser/incognito (no auth), navigate to `https://meads.app/competitions/<comp-short>/divisions/<div-short>/results`
- [ ] **Expected:** Anonymized public results page renders (medals + BOS sections, entrant identities anonymized)

### Verify entrant results

- [ ] Log in as the test entrant → **My Entries**
- [ ] **Expected:** A **"Results published"** banner is visible at the top, linking to MyResultsView
- [ ] Click the banner → **Expected:** entrant's per-entry rows with round-1 totals + medal + BOS columns
- [ ] Click "View scoresheet" on a row → **Expected:** MyScoresheetView shows the anonymized scoresheet (judges shown as "Judge 1", "Judge 2"); PDF download works

### Freeze guard — judging mutations rejected

- [ ] Log back in as competition admin → Manage Judging → try to revert any scoresheet, edit a medal, or change BOS
- [ ] **Expected:** Error *"Results have been published. Revert the publication first to make changes."* (key `error.judging.results-published-frozen`)

### Send announcement (initial)

- [ ] On AwardsAdminView, click **"Send announcement"** → leave custom message empty → Send
- [ ] **Expected:** Notification "Announcement sent to N recipient(s)"; the initial-announcement email lands in each entrant's inbox (in their preferred language)

### Re-publish flow (optional — exercise if you want to verify the correction path)

- [ ] On AwardsAdminView, click **"Revert publication"** → confirm
- [ ] **Expected:** Division status returns to DELIBERATION; judging mutations are unfrozen
- [ ] Make a correction (e.g., edit a scoresheet score) → re-publish → enter a 20–1000-char **justification** → Save
- [ ] **Expected:** Publication **v2** logged in the history grid
- [ ] Click "Send announcement" → leave custom message empty → Send → republish-template email goes out
- [ ] Click "Send announcement" again → enter a custom message → Send → custom-message template goes out

---

## 11. Security Checks

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

## 12. Test Cleanup

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

## 13. Create the Real Competition

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
- [ ] Verify the **"Shared tables across divisions"** checkbox is in the right state
  (defaults to ON for new competitions). ON = a round started at "Table 1" in one
  division locks "Table 1" in every other division until that round completes.
  Turn OFF if each division has its own independent physical setup.

### Add documents

- [ ] Upload rules PDF and/or add external links
- [ ] Reorder documents as needed

---

## 14. Create Real Division(s)

- [ ] Create division(s) with real names, short names, and scoring system
- [ ] Configure entry limits, entry prefix, meadery name requirement
- [ ] Set registration deadline and timezone
- [ ] Add categories from catalog (and custom categories if needed)
- [ ] Set up product mappings matching real Jumpseller products
- [ ] On Settings tab, configure the **Judging** sub-section:
  - [ ] **BOS places** (default 1) — number of Best of Show placements awarded
  - [ ] **Minimum judges per round** (default 2) — hard minimum at round start
  - [ ] **Note:** BOS places lock at JUDGING; min judges lock once any round has status != PENDING. Set both now while the division is still in DRAFT/REGISTRATION_OPEN.
- [ ] Verify all settings are correct

### Advance to Registration Open

- [ ] Advance each division to Registration Open when ready
- [ ] **Expected:** The division is now live and accepting entries

---

## 15. Pre-Judging Configuration

*Run this checklist on each division once registration closes (or just before
flipping to REGISTRATION_CLOSED). All operations are available at
REGISTRATION_CLOSED — judging setup happens here before flipping to JUDGING.*

### Close registration

- [ ] Advance each division REGISTRATION_OPEN → REGISTRATION_CLOSED
- [ ] **Expected:** New "Judging Categories" tab on Division Detail; "Manage Judging" button appears in the division header

### Re-verify the new settings (last chance before locks kick in)

- [ ] Competition Detail → Settings → **Shared tables across divisions** flag still correct
- [ ] Each Division → Settings → Judging sub-section → **BOS places** and **Minimum judges per round** still correct (BOS places lock at JUDGING; do not advance until verified)

### Initialize judging categories

- [ ] On each division → Judging Categories tab → click **"Initialize Judging Categories"**
- [ ] Verify the JUDGING-scope grid matches the REGISTRATION-scope (add/remove custom JUDGING categories if needed for split-category scenarios — same code can exist in both REGISTRATION and JUDGING scopes)

### Add physical tables

- [ ] On each division → Manage Judging → **Tables** tab → click **"+ Add Table"** for each physical station planned (label, e.g. "Table 1", "Table 2", ...)
- [ ] Verify the "Shared tables is ON" banner matches the competition setting
- [ ] **Tip:** plan capacity based on number of judges × number of categories — more tables = more rounds running in parallel

### Add JUDGE participants

- [ ] On Competition Detail → Participants tab, add each judge as a JUDGE (one user per judge with an email — magic link or access code login both work)
- [ ] Add STEWARDs as needed (read-only steward hub)
- [ ] **Note:** `JudgeProfile` (certifications, preferred comment language) is auto-created on first judge assignment to a round. Judges can refine via My Profile once assigned.

### Mark entries RECEIVED and assign final categories

- [ ] As bottles arrive, mark entries RECEIVED on Entry Admin (`→` arrow)
- [ ] Click **"Auto-assign final categories"** to bulk-assign by code match
- [ ] Manually assign for any mismatches (split-category demos, custom JUDGING categories) via the Edit dialog → Final Category Select
- [ ] **Note:** Advance to JUDGING is blocked while any SUBMITTED or RECEIVED entry lacks a final category (`EntryFinalCategoryAdvanceGuard`). The "{N} entries have no judging category…" warning on JudgingAdminView is a defense-in-depth indicator.

### Create rounds and assign entries + judges

- [ ] On each division → Manage Judging → **Rounds** tab → create scoring rounds (Type SCORING) covering all RECEIVED entries
- [ ] For each scoring round: 📦 **Assign Entries** + 👥 **Assign Judges** (≥ Minimum judges per round; orange "Similar meadery" badges flag soft-COI, red "Self-entry" badges hard-block assignment)
- [ ] Pre-stage medal rounds (Type MEDAL, one per JUDGING category) — they auto-transition to READY when the matching scoring round COMPLETES, and `medalRound.entries` auto-populates from the eligible set
- [ ] **Backstop:** trying to add a second MEDAL round for the same category is rejected (V31 partial unique index)

### Advance to JUDGING

- [ ] Once setup is done and the deliberation panel is ready to start, on each division → Advance Status → REGISTRATION_CLOSED → JUDGING
- [ ] **Expected:** All guards pass (judging categories initialized, all SUBMITTED/RECEIVED entries have a final category). Status flips to JUDGING. Rounds can now be **started**.

---

## 16. Awards (Post-Judging)

*Run on each division after all scoring rounds + medal rounds + BOS (if applicable) are COMPLETE.*

### Advance to DELIBERATION

- [ ] On each division → Advance Status → JUDGING → DELIBERATION
- [ ] **Expected:** Manage Judging header now shows a **"Manage results"** button

### Publish results

- [ ] Click "Manage results" → AwardsAdminView
- [ ] Click **"Publish results"** → confirm
- [ ] **Expected:** Notification "Results published"; division status advances to RESULTS_PUBLISHED; **Publication v1** logged in the history grid

### Verify entrant + public access

- [ ] Public page at `https://meads.app/competitions/<comp-short>/divisions/<div-short>/results` renders anonymized results (no auth required)
- [ ] Entrants see a "Results published" banner on My Entries → link to MyResultsView → per-entry rows + anonymized scoresheet drill-in

### Send announcement

- [ ] On AwardsAdminView, click **"Send announcement"** — leave custom message empty for the initial-announcement template; entrants receive the email in their preferred language
- [ ] **Expected:** Notification "Announcement sent to N recipient(s)"

### Re-publish corrections (if needed)

- [ ] Click **"Revert publication"** on AwardsAdminView → division returns to DELIBERATION; judging mutations unfreeze
- [ ] Make the correction (scoresheet edit, medal change, BOS adjust)
- [ ] Re-publish → enter a **20–1000-char justification** → Save → Publication **v(n+1)** logged
- [ ] Send announcement again — empty custom message → republish template; with custom message → custom-message template

---

## 17. Final Verification

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
