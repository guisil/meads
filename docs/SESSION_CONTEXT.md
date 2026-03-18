# Session Context — MEADS Project

## What this file is

Standalone context for resuming work on the MEADS project. Contains everything
needed to continue even without memory files or prior conversation history.

---

## Project Overview

**MEADS (Mead Evaluation and Awards Data System)** — Spring Boot 4 + Vaadin 25
(Java Flow) + PostgreSQL 18 web app for managing mead competitions. Uses Spring
Modulith for modular DDD architecture, Flyway for migrations, Testcontainers +
Karibu Testing for tests. Full conventions in `CLAUDE.md` at project root.

**Branch:** `main`
**Tests:** 679 passing (`mvn test -Dsurefire.useFile=false`) — verified 2026-03-18
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password, optional meaderyName, optional country)
- JWT magic link authentication + admin password login + access code login
- UserService (public API) — includes `updateProfile()` with ISO 3166-1 alpha-2 country validation
- SecurityConfig, UserListView (admin CRUD with meadery name + country fields)
- ProfileView (`/profile`) — self-edit for name, meadery name, country
- Password setup & reset: `SetPasswordView`, `setPasswordByToken()`, `generatePasswordSetupLink()`,
  `hasPassword()`, triggers on admin role assignment, "Forgot password?" on login, admin "Password Reset"
- EmailService (public API) — `SmtpEmailService` (internal) with `JavaMailSender` + Thymeleaf HTML templates.
  Sends magic link, password reset, password setup, credentials reminder, order review alert, submission confirmation, and credit notification emails. SMTP failure logged with fallback link (no crash).
  Per-user rate limiting (5-min cooldown per email type) on user-triggered emails (magic link, password reset, credentials reminder). Daily email counter with WARN at threshold (50).
  Mailpit for dev (port 1025 SMTP, port 8025 web UI). Resend SMTP for prod. 7-day token validity.
- **Status:** Complete

### competition module (`app.meads.competition`)
- **Depends on:** identity
- **Status:** Complete (fully implemented + code reviewed + scope rework done)

#### Entities (public API)
| Entity | Table | Description |
|--------|-------|-------------|
| `Competition` | `competitions` | Top-level: name, shortName (unique), dates, location, logo, contactEmail, shippingAddress, phoneNumber, website |
| `Division` | `divisions` | Sub-level: competitionId, name, shortName (unique per competition), scoringSystem, status, entry limits (per subcategory, per main category, total), entryPrefix, meaderyNameRequired, registrationDeadline, registrationDeadlineTimezone |
| `Participant` | `participants` | Competition-scoped: userId, accessCode |
| `ParticipantRole` | `participant_roles` | Role per participant: JUDGE, STEWARD, ENTRANT, ADMIN |
| `Category` | `categories` | Read-only catalog: code, name, scoringSystem |
| `DivisionCategory` | `division_categories` | Per-division category with optional parent |
| `CompetitionDocument` | `competition_documents` | Competition-scoped document (PDF upload or external link) |

#### Key enums
- `DivisionStatus`: DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION → RESULTS_PUBLISHED
- `CompetitionRole`: JUDGE, STEWARD, ENTRANT, ADMIN
- `ScoringSystem`: MJP
- `DocumentType`: PDF, LINK

#### Service — `CompetitionService` (public API)
- Competition CRUD, Division CRUD, Participant management (add/remove participant, add/remove individual role, role combination validation), Category management
- Document management: `addDocument`, `removeDocument`, `updateDocumentName`, `reorderDocuments`, `getDocuments`, `getDocument`
- Authorization: `isAuthorizedForCompetition()`, `isAuthorizedForDivision()`
- `findCompetitionsByAdmin(userId)` — finds competitions where user has ADMIN participant role
- `findAdminEmailsByCompetitionId(competitionId)` — returns email addresses of all ADMIN participants
- `updateDivisionDeadline()` — updates registration deadline (DRAFT or REGISTRATION_OPEN only)
- `updateCompetitionContactEmail()` — updates competition contact email (shown in participant emails)
- `revertDivisionStatus()` — one-step-back revert with guard interface pattern
- Entry limits (per subcategory, per main category, total) — DRAFT-only, enforced by EntryService
- Events: `DivisionStatusAdvancedEvent`

#### Views
- `CompetitionListView` (`/competitions`) — SYSTEM_ADMIN only, all competitions grid with CRUD
- `CompetitionDetailView` (`/competitions/:shortName`) — tabs: Divisions, Participants, Settings, Documents (add/edit/delete/reorder PDF and link documents)
- `DivisionDetailView` (`/competitions/:compShortName/divisions/:divShortName`) — header: competition logo + "Competition — Division", tabs: Categories, Settings + "Manage Entries" button + "Advance/Revert Status" buttons
- `MyCompetitionsView` (`/my-competitions`) — `@PermitAll`, shows competitions where user is ADMIN

#### Migrations: V3–V8, V14

### entry module (`app.meads.entry`) — COMPLETE

- **Depends on:** competition, identity
- **Status:** All 11 phases complete
- **Design:** `docs/plans/2026-03-02-entry-module-design.md`

#### Entities (public API)
| Entity | Table | Migration | Description |
|--------|-------|-----------|-------------|
| `ProductMapping` | `product_mappings` | V9 | Jumpseller product → division mapping |
| `JumpsellerOrder` | `jumpseller_orders` | V10 | Webhook order storage, idempotency, customerCountry |
| `JumpsellerOrderLineItem` | `jumpseller_order_line_items` | V11 | Per-product line items |
| `EntryCredit` | `entry_credits` | V12 | Append-only credit ledger |
| `Entry` | `entries` | V13 | Mead entry aggregate root |

#### Enums
- `EntryStatus`: DRAFT, SUBMITTED, RECEIVED, WITHDRAWN
- `Sweetness`: DRY, MEDIUM, SWEET
- `Strength`: HYDROMEL, STANDARD, SACK
- `Carbonation`: STILL, PETILLANT, SPARKLING
- `OrderStatus`: PROCESSED, PARTIALLY_PROCESSED, NEEDS_REVIEW, UNPROCESSED
- `LineItemStatus`: PROCESSED, NEEDS_REVIEW, IGNORED, UNPROCESSED

#### Services
- **EntryService** — Product mapping CRUD, credit management, entry CRUD, submission, limits enforcement (total, subcategory, main category)
- **WebhookService** — HMAC signature verification, `processOrderPaid` (JSON parsing, idempotency, mutual exclusivity, credit creation, country enrichment from shipping/billing address, publishes `OrderRequiresReviewEvent` for NEEDS_REVIEW/PARTIALLY_PROCESSED orders)
- **LabelPdfService** — PDF label generation (OpenPDF + ZXing QR codes). Single entry or batch. A4 landscape, 2-line instruction header (line 1: print/attach instructions, line 2: shipping address if set), 3 identical labels per page. Labels include: competition/division name, entry ID, mead name (2-line fixed height), category code, characteristics with field names (Sweetness/Strength/Carbonation), ingredients (Honey/Other/Wood, 2-line fixed height each — text wraps then clips), QR code (left) + notes area (right), "FREE SAMPLES. NOT FOR RESALE." disclaimer. Public API for cross-module access.

#### Events
- `CreditsAwardedEvent(divisionId, userId, amount, source)`
- `EntriesSubmittedEvent(divisionId, userId, List<EntryDetail> entryDetails)`
- `OrderRequiresReviewEvent(orderId, jumpsellerOrderId, customerName, customerEmail, affectedCompetitionIds, affectedDivisionNames, status)`

#### DTOs
- `EntryDetail(entryNumber, meadName, categoryCode, categoryName)` — DTO for submission event payload
- `EntrantCreditSummary(userId, email, name, creditBalance, entryCount)`

#### Views
- `EntrantOverviewView` (`/my-entries`) — cross-competition entrant hub, shows all divisions with credits/entries, auto-redirects to single division
- `MyEntriesView` (`/competitions/:compShortName/divisions/:divShortName/my-entries`) — header: competition logo + "Competition — Division — My Entries", entrant-facing, competition documents list, credits + limits display, process info box, registration deadline display, category guidance hints, entry grid with status badges/Final Category/Actions (view/edit/submit/download label)/filtering/sorting, add/edit dialog (full-width fields, per-field validation, prefixed entry IDs), "Submit All Drafts" button, "Download all labels" batch button (disabled until all entries submitted), meadery name required warning + submit blocking
- `DivisionEntryAdminView` (`/competitions/:compShortName/divisions/:divShortName/entry-admin`) — header: competition logo + "Competition — Division — Entry Admin", admin tabs: Credits, Entries (with Meadery/Country/Final Category columns + view/edit/delete/withdraw actions + individual label download + batch "Download all labels" with confirmation dialog), Products, Orders. View dialog shows all entry fields read-only. Edit has confirmation gate then full edit dialog (all fields, per-field validation, works for any status except WITHDRAWN).

#### REST
- `JumpsellerWebhookController` — `POST /api/webhooks/jumpseller/order-paid` (HMAC-verified)

#### Guards
- `EntryDivisionRevertGuard` — blocks REGISTRATION_OPEN → DRAFT revert when entries exist

#### Event Listeners
- `RegistrationClosedListener` — skeleton for `DivisionStatusAdvancedEvent` (REGISTRATION_CLOSED)
- `OrderReviewNotificationListener` — sends admin alert emails when `OrderRequiresReviewEvent` is published, includes competition name and affected division(s)
- `SubmissionConfirmationListener` — sends entrant confirmation email with entry summary when `EntriesSubmittedEvent` is published (conditional: only when all credits used and no drafts remain). CTA is a magic link (7-day validity via JwtMagicLinkService). Entry lines passed as `List<String>` to template.
- `CreditNotificationListener` — sends entrant credit notification email when `CreditsAwardedEvent` is published (both webhook and admin grants). CTA is a magic link (7-day validity via JwtMagicLinkService).

#### Changes to other modules
- `SecurityConfig` — separate `SecurityFilterChain` with `@Order(1)` for webhook API (CSRF disabled, permitAll)
- `User.java` — added `meaderyName` and `country` fields (now in V2)
- `Division.java` — added `maxEntriesPerSubcategory`, `maxEntriesPerMainCategory`, `maxEntriesTotal`, `entryPrefix`, `meaderyNameRequired`, `registrationDeadline`, `registrationDeadlineTimezone`
- `DivisionDetailView` — "Manage Entries" button, entry prefix (DRAFT-only) + entry limits in Settings tab (DRAFT-only for limits and prefix), meaderyNameRequired checkbox (DRAFT-only), registration deadline fields (DRAFT/REGISTRATION_OPEN)
- `MainLayout` — "My Profile" as submenu item in user dropdown menu (navigates to `/profile`), app version display at bottom of sidebar drawer (from `BuildProperties`)
- `application.properties` — added `app.jumpseller.hooks-token`

#### Migrations: V9–V13

### Cross-cutting

- **Comprehensive logging** added across all 3 modules (INFO for actions, DEBUG for queries/settings, WARN for blocked operations, ERROR for failures)

---

## Documentation Structure

```
docs/
├── SESSION_CONTEXT.md          ← This file (primary context for resuming work)
├── examples/                   ← Test & domain model examples (referenced by CLAUDE.md)
├── plans/
│   ├── 2026-03-02-entry-module-design.md  ← Retained as reference for future module designs
│   ├── 2026-03-10-profile-meadery-country-design.md  ← Design reference for profile/meadery/country
│   ├── 2026-03-10-email-sending-design.md  ← Email sending design (implemented)
│   ├── 2026-03-10-i18n-design.md          ← i18n design (implementation deferred)
│   ├── 2026-03-10-deployment-design.md    ← Deployment options evaluation (decision: DO App Platform)
│   └── deployment-checklist.md           ← Deployment reference: setup, release process, redeployment, rollback
├── reference/
│   └── chip-competition-rules.md          ← CHIP competition rules (active reference)
├── specs/
│   ├── _template.md                       ← Template for new module specs
│   ├── judging.md                         ← Preliminary spec (post-rework naming)
│   └── awards.md                          ← Preliminary spec (post-rework naming)
└── walkthrough/
    ├── manual-test.md                  ← Dev environment test plan (seeded data, comprehensive)
    └── post-deployment-test.md         ← Production test plan (clean database, end-to-end workflow)
```

---

## What's Next

### Priority 1: Manual walkthrough — COMPLETE
All 14 sections completed with fixes along the way.

**Changes made during Section 12–13 walkthrough:**
- **Participant grid refactoring** — One row per participant with comma-separated roles column, edit button (pencil icon) with role checkboxes + name/meadery/country fields, remove button removes entire participant
- **Role combination validation** — Only JUDGE + ENTRANT allowed in same competition. Enforced in `CompetitionService.validateRoleCombination()`, `ensureEntrantParticipant()`, `WebhookService`, and `EntryService.addCredits()`
- **Password requirement for comp admins** — `beforeEnter()` checks in MyCompetitionsView, CompetitionDetailView, DivisionDetailView, DivisionEntryAdminView. RootView prevents redirect loop for passwordless comp admins.
- **Role conflict checks in credit paths** — `hasIncompatibleRolesForEntrant()` check in WebhookService (marks NEEDS_REVIEW) and EntryService (throws exception)
- **Orders grid improvements** — Review Reason column + tooltips on Customer email and Review Reason columns
- **New service methods** — `removeParticipantRole()`, `findRolesForParticipant()`, `validateRoleCombination()`, `hasIncompatibleRolesForEntrant()`
- **Bug fix** — `removeParticipant()` now also deletes the Participant entity (was only deleting roles)
- **Access code scoping** — Deferred. Current behavior (identity auth, not per-competition) is acceptable with password gate on admin views.

**Changes made during Section 14 (Security Testing) walkthrough:**
- **SetPasswordView eager token validation** — Validates JWT on page load in `beforeEnter()`. Invalid/expired tokens show error notification without rendering the form. Empty tokens redirect to `/login`.
- **Webhook missing HMAC header** — `@RequestHeader` changed to `required = false`, returns 401 for missing header (was returning HTML)
- **Webhook HTTP method tampering** — Added explicit `@RequestMapping` for GET/PUT/DELETE/PATCH returning 405 (was falling through to Vaadin)
- **Webhook email length validation** — Rejects orders with customer email > 255 chars
- **Field length limits** — Added `setMaxLength(255)` to all email fields (LoginView, UserListView), `setMaxLength(128)` to all password fields (LoginView, SetPasswordView)
- **Dev password logging** — Removed plaintext password from `DevUserInitializer` log output
- **Mead name tooltip** — Added tooltip on mead name column in both MyEntriesView and DivisionEntryAdminView grids
- **Contact email on My Entries** — Shows "Questions or need help? Contact: {email}" as mailto link, opposite the registration deadline
- **Settings field widths** — Widened Name, Location, Contact Email fields in Competition Settings and Name in Division Settings to 400px

### Priority 1: Post-i18n comprehensive walkthrough and hardening — COMPLETE

Full manual walkthrough completed 2026-03-17. All 4 parts done.

**Changes made during walkthrough:**
- **Part A — Error notification fix:** DivisionDetailView settings save caught wrong exception
  type (`IllegalArgumentException` instead of `IllegalStateException`). Fixed. Admin views
  now force `Locale.ENGLISH` in all `getTranslation()` calls for `BusinessRuleException`,
  so error messages are consistently English regardless of user's locale preference.
- **Part C — Double-click protection:** Added `setDisableOnClick(true)` (Vaadin built-in API)
  to all dialog save/confirm/delete buttons across all views (40+ buttons). Re-enables on
  validation failure and in catch blocks. LoginView: magic link and forgot password buttons
  left unprotected (rate limiting handles abuse, and mistyped emails need easy retry);
  login button is protected (form POST navigates away).
- **Add Credits validation:** Empty email/amount now shows field-level errors instead of
  silently returning.
- **Meadery name confirmation banner:** MyEntriesView now shows green confirmation banner
  with meadery name when set (divisions that require it), instead of just hiding the warning.
- **Admin error locale consistency:** All admin view `BusinessRuleException` catch blocks use
  `getTranslation(key, Locale.ENGLISH, params)` to force English errors, avoiding the
  mixed-language issue where some errors were translated and others fell back to English.

### Priority 2: Date display review
- **Date display** — review how dates are displayed across all supported locales, ensure
  locale-appropriate formatting

### Priority 3: Deletion guards and cascade testing
Comprehensive review and hardening of all deletion operations across the application.
Two parts: (a) implement guards to block unsafe deletions, (b) test all existing
deletion paths for correct behavior.

**Guards to implement:**
- Participant with entries → block removal
- Participant with credits (no entries) → block removal
- Associated orders → handle constraint violations (remove or reassign)
- User deletion → check entries/credits across all competitions
- Cross-module: use guard interface pattern (like `DivisionRevertGuard`)
- UI: show error notification (not exception) when blocked

**Deletion paths to test (with and without dependent data):**
- Delete competition → what happens to divisions, participants, entries, credits, orders, documents?
- Delete division → what happens to entries, credits, product mappings, categories?
- Delete participant → what happens to entries, credits, orders linked to that user?
- Delete user → what happens to entries, credits, participant records?
- Delete product mapping → what happens to existing orders/line items referencing it?
- Delete category from division → what happens to entries using that category?
- Remove ENTRANT role from participant → what happens to their entries/credits?

**Approach:** Start with a design pass to map all FK constraints and cascade behavior.
Then TDD each guard and deletion path. Needs thorough testing — multiple edge cases
across modules.

### Priority 4: Admin view i18n
Translate all admin views to support the same language switching as entrant views.
~270 hardcoded English strings across 8 views to extract to message keys and translate
to PT. Mechanical work — no new patterns or dependencies, `getTranslation()` infrastructure
already in place. Biggest files: CompetitionDetailView (~82 strings), DivisionEntryAdminView
(~90 strings). Also include LoginView and SetPasswordView — entrants with passwords use
these views too, not just admins.

### Priority 5: Post-registration actions audit
Review what actions should be allowed/blocked after a division moves past REGISTRATION_OPEN.
Currently some actions remain available that may need restricting or scoping. Design pass
needed to decide per-status rules:
- **Add credits** — currently allowed in any status. Should it be blocked after registration closes?
- **Admin edit entries** — should remain possible to some extent (e.g., setting final category,
  correcting admin fields), but perhaps not full edits (mead name, category change)
- **Entrant edit entries** — currently blocked after submit. Should view-only access persist?
- **Add/remove participants** — should participant management be locked at some point?
- **Product mappings** — should these be locked after registration?
Do this audit after the admin i18n work (priority 4) since translated views may surface
additional UX considerations.

### Priority 6: MFA for system admins
Evaluate and implement multi-factor authentication for SYSTEM_ADMIN accounts.
Password-only login for privileged accounts is a security risk post-deployment.

### Priority 7: Auto-close + deadline reminders (deferred)
- **Auto-close** — automatically advance division from REGISTRATION_OPEN → REGISTRATION_CLOSED
  when registration deadline passes (scheduled task)
- **Entrant deadline reminder** — notify entrants who have DRAFT entries when the registration
  deadline is approaching (e.g., 7 days, 3 days, 1 day before deadline)
- Other potential: entry received confirmation (when admin marks entry as RECEIVED), results published notification

### Priority 8: Judging module
Design and implementation. Reference: `docs/reference/chip-competition-rules.md`.

### Priority 9: Awards module

Design and implementation, after judging module. Reference: `docs/reference/chip-competition-rules.md`.

### Priority 10: Full category constraint system (low priority — future competition)
Full field locking/validation based on category selection. Design doc: `docs/plans/2026-03-11-category-hints-design.md` (appendix).
Includes: sweetness locking (M1A→Dry, M1B→Medium, M1C→Sweet), ingredient restrictions (M1/M4E),
strength locking (M4S→Hydromel), ABV caps (M4S→7.5%), ABV→Strength derivation (universal),
carbonation locking (custom categories), and admin-configurable constraints for custom categories.
Requires: DB migration, admin UI for constraint config, cross-module data flow, server-side validation.

### Completed priorities
- **i18n review + plural resolution + Strength auto-calculation** — Completed 2026-03-18. Fixed PL grammar (locative case, honey validation, capitalization), IT formal "Lei" consistency, ES swapped limit texts, fruit examples alignment. Added CLDR-based `PluralRules` utility (EN/PT/ES/IT: one/other; PL: one/few/many) with `MeadsI18NProvider.getPlural()`. Converted email credit unit, credits remaining, submit-all confirm/success to plural-aware keys. Added `Strength.fromAbv()` (Hydromel <= 7.5, Standard <= 14, Sack > 14) — Strength auto-derived from ABV at domain level; removed from entrant dialog, read-only in admin edit dialog (updates live with ABV), PDF labels unchanged. 679 tests.
- **Internationalization (i18n)** — Implementation completed 2026-03-16. 5 languages active: EN, ES, IT, PL, PT. Infrastructure: Vaadin I18NProvider + Spring MessageSource, BusinessRuleException across all services, entrant-facing view string extraction, email i18n with Locale, PDF label instructions i18n, MJP category translations, locale-aware date/timezone formatting, language switcher in navbar, User.preferredLanguage (V16 migration). PDF labels use embedded Liberation Sans font (metrically identical to Helvetica, full Unicode support including Polish diacritics). 586 tests. Design: `docs/plans/2026-03-10-i18n-design.md`.
- **Post-deployment walkthrough** — Completed 2026-03-16. `docs/walkthrough/post-deployment-test.md` retained as reference for re-running after major changes.
- **PR #4 code review & merge** — Merged `competition-module` into `main` (2026-03-14). Code review found 5 bugs (missing auth check, missing access code on role promotion, HMAC timing attack, hasCreditConflict inconsistency, entryPrefix DRAFT guard) and 4 convention fixes (ProfileView auth context, @Setter removal, setter→domain method renames, Category constructor). Deferred: test naming (313 methods) and cross-module test imports.
- **Manual walkthrough (Sections 1–14)** — All sections completed. Security testing (Section 14) produced 7 fixes: SetPasswordView eager token validation, webhook missing HMAC header (401), webhook HTTP method tampering (405), webhook email length validation, field length limits on email/password fields, dev password logging cleanup, and UX improvements (contact email on My Entries, mead name tooltips, settings field widths).
- **Configuration audit** — Properties reorganized, secrets in profile-specific files.
- **Email sending** — SMTP with Thymeleaf templates, Mailpit dev, Resend prod.
- **Entry labels (PDF)** — OpenPDF + ZXing, LabelPdfService, individual + batch download. QR code fix: ZXing TYPE_BYTE_BINARY → TYPE_INT_RGB conversion + nested PdfPTable for cell embedding.
- **Competition documents** — PDF upload + external links, admin Documents tab, entrant list.
- **Category code display** — Grid columns show code (e.g. M1A) with tooltip for full name in both MyEntriesView and DivisionEntryAdminView. View entry dialog shows "code — name" format. Entry creation filtered to subcategories only.
- **Category guidance hints** — Informational hint text below category dropdown in entry dialog. All 16 MJP subcategories have style-specific guidance (ingredients, sweetness, ABV). No field locking or validation.
- **Registration deadline** — `registrationDeadline` (LocalDateTime) + `registrationDeadlineTimezone` fields on Division. Displayed in entrant view, editable in DRAFT/REGISTRATION_OPEN. V4 migration modified in-place.
- **Admin order alert emails** — `OrderRequiresReviewEvent` published by WebhookService, `OrderReviewNotificationListener` sends alert to all competition admins.
- **Entry submission confirmation emails** — `SubmissionConfirmationListener` sends confirmation to entrant when entries submitted, with entry summary and link to MyEntriesView. Conditional: only fires when all credits used AND no drafts remain.
- **Credit notification emails** — `CreditNotificationListener` sends email to entrant when credits are awarded (webhook or admin). `WebhookService` now publishes `CreditsAwardedEvent`.
- **Submission email redesign** — `EntriesSubmittedEvent` now carries `List<EntryDetail>` instead of `int entryCount`. Event published only when credits fully used and all entries submitted. Email includes per-entry summary (number, name, category). "Submit All" renamed to "Submit All Drafts". Process info box added to MyEntriesView.
- **Email rate limiting + credentials reminder + set password info** — Per-user 5-min cooldown on user-triggered emails (magic link, password reset, credentials reminder). Daily email counter with WARN at 50. Credentials reminder email sent to password users who request magic links. Set Password page shows info message about login links being disabled after password is set.
- **Entry labels layout redesign** — Characteristics with field names (Sweetness/Strength/Carbonation), fixed 2-line height for mead name and ingredients (Honey/Other/Wood), QR code (left) + notes area (right) in 45/55 split, "FREE SAMPLES. NOT FOR RESALE." disclaimer.
- **Email CTA magic links** — Credit notification and submission confirmation emails now use magic links (7-day validity via JwtMagicLinkService) for the CTA button, so recipients can log in directly.
- **Entry admin UX fixes** — Download dialog auto-closes on click, product mapping validation with field-level errors.
- **Order review email improvements** — Added competition name and affected division(s) to admin alert email. Refactored all email detail content from inline HTML strings (`detailHtml`/`th:utext`) to Thymeleaf template variables (`th:text`/`th:each`) for proper escaping and separation of concerns.
- **Field length limits** — Added `setMaxLength()` to all text input fields across all 7 views to match DB column sizes (VARCHAR) or set reasonable limits (TEXT). TextArea fields capped at 500–1000 chars.
- **Label PDF fixed height** — Changed from `setMinimumHeight` to `setFixedHeight` for mead name and ingredient fields so labels don't expand with long text. Text wraps within 2 lines then clips.
- **Entry dialog improvements** — Per-field validation errors instead of generic notification. Category pre-populates correctly on edit (searches Select items, not full category list). View/submit dialogs use prefixed entry ID (e.g. AMA-1). "Download all labels" disabled until all entries submitted.
- **Dev data** — Profissional division now has `meaderyNameRequired = true`.
- **Admin entry view/edit** — Added view button (eye icon) to admin entries grid with read-only dialog showing all fields + status + entrant. Added Final Category column to grid. Expanded edit dialog from mead-name-only to all entry fields with per-field validation and confirmation gate. Edit works for any status except WITHDRAWN.
- **Competition logo + name in division views** — DivisionDetailView, MyEntriesView, and DivisionEntryAdminView now show the competition logo (64px) and include the competition name in the header title (e.g. "CHIP 2026 — Amadora — My Entries").
- **Credit removal guard** — `removeCredits()` now rejects adjustments that would drop balance below active entry count.
- **Admin entries status filter** — DivisionEntryAdminView Entries tab now has a status dropdown filter alongside the text filter.
- **Participant action icon reorder** — Edit (pencil) now appears before Send Login Link (envelope) in participants grid, consistent with Users grid.
- **Full regression walkthrough (Sections 1–14)** — Completed 2026-03-14. All sections passed. No regressions found. Minor improvements made during walkthrough: competition logo/name in division views, participant icon reorder, credit removal guard, admin entries status filter.
- **MEADS logo branding** — Replaced "MEADS" text with SVG logo in navbar (44px, left-aligned) and PNG logo as CID inline image in email header. Logo files at `META-INF/resources/images/meads-logo.{svg,png}`. SecurityConfig permits `/images/**`. Navbar height increased to 60px.
- **Deployment (2026-03-14)** — Deployed to DigitalOcean App Platform (Amsterdam) + Managed PostgreSQL 18. Resend for email (free tier). Domain `meads.app` (Namecheap) with Let's Encrypt SSL. Full reference: `docs/plans/deployment-checklist.md`.
- **Image-based deploys (2026-03-15)** — Switched from source-based (DO builds from `main`) to image-based (CI builds Docker image from tagged commit, pushes to GHCR, updates DO app spec). Fixes SNAPSHOT version deploy bug. JVM tuned (`-Xmx400m -XX:MaxMetaspaceSize=150m -XX:+UseSerialGC`) for 1GB instance. Version display moved from user dropdown to drawer bottom. New secret: `GHCR_REGISTRY_CREDENTIALS`.

---

## Design decisions
- **Any user can set a password via "Forgot password?"** — even users without a role that
  requires one (e.g., regular entrants who only need magic links). This is allowed by design:
  it's the user's choice, introduces no security issue, and once set, magic links are blocked
  for them (defense in depth). No restriction needed.
- **Entry limits changeable only in DRAFT** — once a division advances past DRAFT,
  entry limits are locked. This prevents unfairness from mid-registration limit changes.
- **Flyway migrations modified in-place** — since the app is pre-deployment, existing
  migrations are edited rather than creating new ones. This keeps migration numbering clean.
- **Country field on User** — ISO 3166-1 alpha-2 code, validated in `UserService.updateProfile()`.
  ComboBox with `Locale.getISOCountries()` in UI. Webhook enrichment from shipping/billing address.
- **Meadery name stays on User profile only** — no per-entry override needed.
- **`meaderyNameRequired` on Division** — boolean flag, changeable only in DRAFT status.
- **Email SMTP failure resilience** — catch and log with fallback link, never crash UI actions.
- **Email rate limiting** — in-memory `ConcurrentHashMap<String, Instant>` keyed by `email:type`, 5-min cooldown (configurable via `app.email.rate-limit-minutes`). Only user-triggered emails are rate-limited. Daily counter logs WARN at threshold (`app.email.daily-warning-threshold=50`). Resets on date change.
- **Token validity (7 days)** — private constant in `SmtpEmailService`, not mentioned in email body.
- **Competition `contactEmail`** — optional field, shown in password setup and credit notification
  emails as visible footer contact. Saved via `CompetitionService.updateCompetitionContactEmail()`.
- **DevUserInitializer uses EmailService** — sends magic link emails via `EmailService.sendMagicLink()`
  at startup. Emails are captured by Mailpit in dev. Password users (admin, compadmin) still log to console.
- **`spring.thymeleaf.check-template-location=false`** — prevents Thymeleaf view resolver conflict
  with Vaadin (Thymeleaf used only for template rendering, not view resolution).
  MyEntriesView shows warning banner and blocks submit (all + individual) when required but missing.
- **Submission email is conditional** — `EntriesSubmittedEvent` only published when `creditBalance - activeEntries == 0`
  AND no DRAFT entries remain. Prevents email spam when entrant submits entries one by one.
- **Email template detail blocks** — `email-base.html` uses Thymeleaf-driven blocks for structured content:
  `orderReviewCompetition`/`orderReviewDivisions` (plain strings via `th:text`) for order alerts, and
  `entryLines` (`List<String>` via `th:each` + `th:text`) for submission summaries. No inline HTML or
  `th:utext` — all content is auto-escaped by Thymeleaf.

- **Role combination restriction** — Only JUDGE + ENTRANT combination is allowed in the same
  competition. All other multi-role combinations are rejected. Enforced at three levels:
  `CompetitionService.validateRoleCombination()` (participant management),
  `CompetitionService.ensureEntrantParticipant()` (webhook auto-assignment),
  `EntryService.addCredits()` and `WebhookService.processOrderPaid()` (credit paths).
- **Password requirement for competition admins** — Non-SYSTEM_ADMIN users with competition ADMIN
  role must have a password set. Admin views check in `beforeEnter()` and block access with a
  notification if no password. RootView skips `/my-competitions` redirect for passwordless comp
  admins to prevent redirect loops.
- **Access code scoping** — Access codes authenticate user identity (full account access), not
  per-competition sessions. Password gate on admin views provides sufficient separation. Per-competition
  scoping deferred to when multiple competitions exist.

### Known UX items (deferred)
- After failed credentials login, page reloads at `/login?error` and shows error notification,
  but password field is cleared (expected browser behavior for form POST). Not blocking.
- Competition dates allow past values — no validation prevents creating or editing a competition
  with start/end dates in the past. Add date validation (start date >= today on create,
  end date >= start date already enforced).
- Withdrawn entries have no way to revert status — consider adding an "undo withdraw" action
  (e.g., revert to SUBMITTED or DRAFT) for admin use.
- Credits can be added beyond the division's total entry limit — no validation prevents granting
  more credits than the entrant could ever use. Low priority since it's an admin action and
  unlikely in practice.

### Configuration
- **Properties reorganized** — `application.properties` contains only non-sensitive,
  environment-agnostic defaults (4 properties). Secrets and env-specific values live in
  profile-specific files (`application-dev.properties`, `application-prod.properties`).
  Test overrides in `src/test/resources/application.properties`.
- **Deployment env vars checklist** in `docs/plans/2026-03-10-deployment-design.md`.

---

## All Test Files (competition module — documents)

### Unit tests
- `CompetitionDocumentTest.java` — entity factory methods, validation (size, content type, name, URL), domain methods
- `CompetitionServiceTest.java` — document CRUD methods (addDocument, removeDocument, updateDocumentName, reorderDocuments, getDocuments, getDocument, deleteCompetition cleanup)

### Repository tests
- `CompetitionDocumentRepositoryTest.java` — save, find ordered, count, exists by name

### UI tests
- `CompetitionDetailViewTest.java` — Documents tab rendering, document grid display
- `MyEntriesViewTest.java` — competition documents section in entrant view

---

## All Test Files (entry module)

### Unit tests
- `EntryServiceTest.java` — product mapping CRUD + credit methods + entry CRUD + submission + limits (subcategory, main category, total)
- `WebhookServiceTest.java` — HMAC verification + processOrderPaid variants + CreditsAwardedEvent publication
- `LabelPdfServiceTest.java` — single/batch PDF generation, missing fields, QR code format, entry prefix handling
- `JumpsellerOrderTest.java` — entity domain methods
- `JumpsellerOrderLineItemTest.java` — entity domain methods
- `EntryTest.java` — entry entity domain methods (constructor, submit, markReceived, withdraw, updateDetails, assignFinalCategory, getEffectiveCategoryId)
- `RegistrationClosedListenerTest.java` — event listener unit tests
- `OrderReviewNotificationListenerTest.java` — sends admin alert emails on order review event
- `SubmissionConfirmationListenerTest.java` — sends entrant confirmation on submission event
- `CreditNotificationListenerTest.java` — sends entrant credit notification on credits awarded event
- `EntryDivisionRevertGuardTest.java` — blocks revert to DRAFT when entries exist

### Repository tests
- `ProductMappingRepositoryTest.java`
- `JumpsellerOrderRepositoryTest.java`
- `JumpsellerOrderLineItemRepositoryTest.java`
- `EntryCreditRepositoryTest.java`
- `EntryRepositoryTest.java`

### Controller test
- `JumpsellerWebhookControllerTest.java` — standalone MockMvc (valid signature → 200, invalid → 401)

### Module integration test
- `EntryModuleTest.java` — bootstrap + full credit → entry → submit workflow

### UI tests
- `MyEntriesViewTest.java` — credits display, entry grid, authorization redirect, meadery name warning + submit blocking, download all labels button, download label for submitted entries, competition documents display, process info box, "Submit All Drafts" button
- `DivisionEntryAdminViewTest.java` — admin tabs rendering, meadery name + country columns, download all labels button

---

## Key Technical Notes

- Karibu TabSheet: content is lazy-loaded. Must call `tabSheet.setSelectedIndex(N)` before finding components
- Karibu component columns: buttons inside Grid `addComponentColumn` are not found by `_find(Button.class)`
- `Category` has a public constructor (code, name, description, scoringSystem, parentCode) and a protected no-arg for JPA
- `Select.setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — must handle null
- Service constructors are package-private (convention)
- `@DirtiesContext` required on UI tests that modify security context strategy
- `EntryCredit` is append-only ledger — balance computed as `SUM(amount)` via JPQL
- `WebhookService` constructor takes `@Value("${app.jumpseller.hooks-token}")` — property must exist
- Mutual exclusivity: user cannot have credits in two different divisions of same competition
- `@WebMvcTest` doesn't work in this Vaadin project — use `MockMvcBuilders.standaloneSetup(controller)` with `@ExtendWith(MockitoExtension.class)` instead
- String-based `Anchor` navigation for cross-module links (avoids Spring Modulith circular dependencies)
- Comprehensive logging: `@Slf4j` on all services, controllers, filters, listeners, guards
