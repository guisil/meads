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

**Branch:** `feature/judging-module` (Phase 6 views — Tables tab fully done with per-row actions + scoresheets count; remaining: View drill-in, Medal Rounds tab, BOS tab, Judge views)
**Tests:** 928 passing (`mvn test -Dsurefire.useFile=false`) — verified 2026-05-10 (Phase 6.3: `ScoresheetService.countByTableIdAndStatus` + Tables tab Scoresheets column wiring; +1 unit test)
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password, optional meaderyName, optional country, optional totpSecret, mfaEnabled)
- JWT magic link authentication + admin password login + access code login
- **TOTP-based MFA for SYSTEM_ADMIN**: `TotpService` (HMAC-SHA1, Base32, ±1 window); `UserService` MFA methods (`setupMfa`, `confirmMfa`, `verifyMfaCode`, `disableMfa`); `MfaAuthenticationSuccessHandler` redirects MFA-enabled admins to `/mfa` after login; `MfaVerifyView` (`/mfa`, `@AnonymousAllowed`); MFA setup/disable section in `ProfileView` (SYSTEM_ADMIN only). V19 migration adds `totp_secret` and `mfa_enabled` columns.
- UserService (public API) — includes `updateProfile()` with ISO 3166-1 alpha-2 country validation
- SecurityConfig, UserListView (admin CRUD with meadery name + country fields)
- ProfileView (`/profile`) — self-edit for name, meadery name, country + MFA section (SYSTEM_ADMIN only)
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
| `CompetitionDocument` | `competition_documents` | Competition-scoped document (PDF upload or external link), optional language filter |

#### Key enums
- `DivisionStatus`: DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION → RESULTS_PUBLISHED
- `CompetitionRole`: JUDGE, STEWARD, ENTRANT, ADMIN
- `ScoringSystem`: MJP
- `DocumentType`: PDF, LINK

#### Service — `CompetitionService` (public API)
- Competition CRUD, Division CRUD, Participant management (add/remove participant, add/remove individual role, role combination validation), Category management
- Document management: `addDocument` (with optional language), `removeDocument`, `updateDocumentName`, `reorderDocuments`, `getDocuments`, `getDocumentsForLocale`, `getDocument`
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

#### Migrations: V3–V8, V14, V17, V18

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
- **EntryService** — Product mapping CRUD, credit management, entry CRUD, submission, limits enforcement (total, subcategory, main category). `advanceEntryStatus()` calls `publishSubmissionEventIfComplete()` when the transition is DRAFT→SUBMITTED, keeping admin-triggered submissions consistent with entrant-triggered ones. `assignFinalCategory(entryId, finalCategoryId, userId)` — standalone method to set/clear final category; validates `finalCategoryId` is a JUDGING-scoped category when judging categories exist (falls back to any category when none exist yet).
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
- `DivisionEntryAdminView` (`/competitions/:compShortName/divisions/:divShortName/entry-admin`) — header: competition logo + "Competition — Division — Entry Admin", admin tabs: Credits, Entries (with Meadery/Country/Final Category columns + view/edit/←/→/withdraw/delete/download-label actions + "Add Entry" button always enabled for admins + batch "Download all labels" with confirmation dialog + summary line showing credits balance and full per-status entry count: "Total entries: N (Draft: X, Submitted: Y, Received: Z, Withdrawn: W)"; credits balance auto-refreshes after add/adjust credits), Products, Orders. View dialog: read-only. Edit: confirmation gate then full dialog (all fields). "Add Entry": two-step — first confirmation dialog warning about bypassing credits, then full entry form with entrant email + all mead fields. `←`/`→` advance/revert status with confirmation. **Credits tab** and **Products tab** buttons disabled with tooltip when past REGISTRATION_OPEN (tooltip via Span wrapper — works on disabled buttons). Credits balance auto-refreshes after add/adjust operations.

#### REST
- `JumpsellerWebhookController` — `POST /api/webhooks/jumpseller/order-paid` (HMAC-verified)

#### Guards
- `EntryDivisionRevertGuard` — blocks REGISTRATION_OPEN → DRAFT revert when entries exist
- `EntryJudgingCategoryDeletionGuard` — blocks deletion of JUDGING categories when any `entry.finalCategoryId` references them

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
│   ├── 2026-05-05-judging-module-design.md ← Judging module design (in progress, multi-session)
│   └── deployment-checklist.md           ← Deployment reference: setup, release process, redeployment, rollback
├── reference/
│   ├── chip-competition-rules.md          ← CHIP competition rules (active reference)
│   ├── Short-version-of-MJP-scoring-sheet-V3.0.pdf ← Official MJP scoresheet (5 fields, max 100)
│   └── MEAD-GUIDELINES-2023.pdf           ← Full MJP mead guidelines (categories, styles)
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

### Priority 2: Deletion guards and cascade testing — COMPLETE
Comprehensive review and hardening of all deletion operations across the application.

**Guards implemented:**
- **Division deletion guard** — `DivisionDeletionGuard` interface (competition module) + `EntryDivisionDeletionGuard` impl (entry module). Blocks deletion when entries, credits, or product mappings exist.
- **User hard-delete guard** — `UserDeletionGuard` interface (identity module) + `CompetitionUserDeletionGuard` impl (competition module). Blocks hard delete when user has participant records.
- **Last-role removal fix** — `removeParticipantRole()` now invokes `ParticipantRemovalCleanup` interface when removing the last role (was bypassing cleanup, causing orphaned entries/credits).
- **Competition deletion cleanup** — `deleteCompetition()` now cleans up participants and their roles before deleting the competition.

**Assessed and deferred:**
- Category removal — DB FK constraint already blocks deletion when entries reference the category. View catches `DataIntegrityViolationException` with a user-friendly message. No code change needed.

**Deletion path safety summary:**
| Path | Guard | Status |
|------|-------|--------|
| Delete competition | Blocked if divisions exist; participants cleaned up | ✓ |
| Delete division | `DivisionDeletionGuard` blocks if entries/credits/products exist | ✓ |
| Delete participant | `ParticipantRemovalCleanup` cleans entries/credits | ✓ |
| Remove last role | Now invokes cleanup (was missing) | ✓ Fixed |
| Delete user (soft) | Deactivates, no cascade needed | ✓ |
| Delete user (hard) | `UserDeletionGuard` blocks if participant records exist | ✓ |
| Delete product mapping | No dependent data | ✓ |
| Delete entry | Only DRAFT status allowed | ✓ |
| Delete category | DB FK constraint blocks if entries reference it | ✓ |
| Delete document | No dependent data | ✓ |

### Priority 3: Entry status management redesign — COMPLETE

Replaced the dedicated "Mark as Received" button with `←` / `→` arrow buttons covering the
full DRAFT → SUBMITTED → RECEIVED flow. WITHDRAWN entries revert to DRAFT via `←`.
Both buttons show a confirmation dialog before acting. Button order: `[eye] [pencil] [←] [→] [ban] [trash]`.

New domain methods on `Entry`: `advanceStatus()` and `revertStatus()`.
New service methods on `EntryService`: `advanceEntryStatus()`, `revertEntryStatus()`, and
`getTotalCreditBalance(divisionId)` (single aggregate query, replaces N+1 participant loop).
`EntryCreditRepository.sumAmountByDivisionId()` added for the aggregate.
`markReceived()` kept on entity/service for backwards compatibility.
Summary row: credits label ("Credits balance: N") + entries label ("Total entries: N (Draft: X, Submitted: Y, Received: Z, Withdrawn: W)"). Span IDs added for testability.
`advanceStatus()` delegates to `submit()`/`markReceived()` to avoid logic duplication.
Authorization rejection tests added for both advance/revert; advance-from-RECEIVED rejection test added.
Dialog handlers catch `IllegalStateException` for stale-state concurrent-edit edge case.
Tooltip switch arms made exhaustive (no `default` fallthrough).

### Priority 4: Admin view i18n — COMPLETE

Extracted ~270 hardcoded English strings from 8 admin views into `getTranslation()` calls,
added all keys to `messages.properties`, and translated to Portuguese in `messages_pt.properties`.
Views updated: LoginView, SetPasswordView, UserListView, CompetitionListView, MyCompetitionsView,
CompetitionDetailView, DivisionDetailView, DivisionEntryAdminView. ES/IT/PL fall back to EN.
Fixed `ComboBox<>` type inference compilation errors introduced by Java 21 stricter inference
(needed explicit `new ComboBox<String>(...)` where label arg comes from `getTranslation()`).

### Priority 1: Post-registration actions audit — COMPLETE

**Rules implemented:**
- **Add credits / adjust credits** — blocked after REGISTRATION_OPEN. Allowed in DRAFT + REGISTRATION_OPEN. `EntryService.addCredits()` and `removeCredits()` throw `BusinessRuleException("error.credits.registration-closed")`. DivisionEntryAdminView Credits tab: "Add Credits" button and adjust icon both disabled with "Registration is closed" tooltip.
- **Product mappings** — blocked after REGISTRATION_OPEN. `createProductMapping()`, `updateProductMapping()`, `removeProductMapping()` all throw `BusinessRuleException("error.product.registration-closed")`. DivisionEntryAdminView Products tab: "Add Mapping", edit, and delete buttons all disabled with "Registration is closed" tooltip.
- **Entrant edit entries** — blocked after REGISTRATION_OPEN. `updateEntry()` throws `BusinessRuleException("error.entry.division-not-open")` if division status is not REGISTRATION_OPEN.
- **Add/remove participants** — deferred. No change.
- **Admin edit entries** — no change. Admins can still edit entries (set final category, correct fields) in any status via the admin view.
- **`DivisionStatus.allowsRegistrationActions()`** — new helper on enum (DRAFT || REGISTRATION_OPEN) used by credits and product mapping guards.
- **6 new tests** in `EntryServiceTest`: `shouldRejectCreateProductMappingWhenRegistrationClosed`, `shouldRejectUpdateProductMappingWhenRegistrationClosed`, `shouldRejectRemoveProductMappingWhenRegistrationClosed`, `shouldRejectAddCreditsWhenRegistrationClosed`, `shouldRejectRemoveCreditsWhenRegistrationClosed`, `shouldRejectUpdateEntryWhenDivisionNotOpen`.

### Priority 1 (NEXT): Judging category management

**Problem:** After REGISTRATION_CLOSED, the admin needs to reorganize categories for judging
(e.g. combine thin categories, create new groupings, rename). Currently, `allowsCategoryModification()`
blocks all category changes after REGISTRATION_OPEN, and even if that were lifted, deleting a
category referenced by `entry.initialCategoryId` would violate the FK constraint.

**Design: `scope` field on `DivisionCategory`**

Add a `scope` enum (`REGISTRATION` / `JUDGING`) to `DivisionCategory`:

| Scope | Created during | Deletable? | Referenced by |
|-------|----------------|-----------|---------------|
| `REGISTRATION` | DRAFT / REGISTRATION_OPEN only | Blocked if any `initialCategoryId` references it | `entry.initialCategoryId` |
| `JUDGING` | REGISTRATION_CLOSED or later | Blocked if any `finalCategoryId` references it | `entry.finalCategoryId` |

**Confirmed design decisions:**
- JUDGING categories require a description (same as REGISTRATION — no nullable change)
- `finalCategoryId` is clearable (nullable) from admin UI — Select includes empty option
- Standalone `EntryService.assignFinalCategory(entryId, finalCategoryId, userId)` method (not bundled into `adminUpdateEntry`)
- Final category picker falls back to ALL categories if no JUDGING categories exist yet
- Judging category management allowed for any status >= REGISTRATION_CLOSED (through JUDGING, DELIBERATION, RESULTS_PUBLISHED)
- "Initialize judging categories" is part of this feature; full judging module workflows come later
- Unique constraint changes to `UNIQUE(division_id, code, scope)` — same code can exist in both scopes

**Key rules:**
- REGISTRATION categories: fully managed via existing flow; once REGISTRATION_CLOSED, they become
  read-only in the UI (visible but no add/edit/delete — they are historic record)
- JUDGING categories: only appear after REGISTRATION_CLOSED; admin can freely add/edit/delete them
  (guarded against deletion when referenced by `finalCategoryId`)
- "Initialize judging categories" button clones all REGISTRATION categories into JUDGING ones
  (same codes, names, descriptions, hierarchy; `catalogCategoryId = null` on clones) — admin can then diverge freely
- When setting `finalCategoryId` on an entry (in DivisionEntryAdminView), the category picker shows
  only JUDGING categories (if any exist) or falls back to all division categories
- Service validates `finalCategoryId` references a JUDGING category when JUDGING categories exist

**New files:**
- `app.meads.competition.CategoryScope.java` — public enum (`REGISTRATION`, `JUDGING`)
- `app.meads.competition.JudgingCategoryDeletionGuard.java` — public guard interface
- `app.meads.entry.internal.EntryJudgingCategoryDeletionGuard.java` — guard impl

**Migration:** V18
- Add `scope VARCHAR(20) NOT NULL DEFAULT 'REGISTRATION'` to `division_categories`
- Drop `UNIQUE(division_id, code)` constraint
- Add `UNIQUE(division_id, code, scope)` constraint

**New service methods (CompetitionService):**
- `initializeJudgingCategories(divisionId, adminUserId)` — clones REGISTRATION → JUDGING; throws if JUDGING categories already exist
- `addJudgingCategory(divisionId, code, name, description, parentJudgingCategoryId, adminUserId)`
- `updateJudgingCategory(divisionId, categoryId, code, name, description, adminUserId)`
- `removeJudgingCategory(divisionId, categoryId, adminUserId)` — guarded by `JudgingCategoryDeletionGuard`
- `findJudgingCategories(divisionId)` — returns only JUDGING scope

**New service method (EntryService):**
- `assignFinalCategory(entryId, finalCategoryId, requestingUserId)` — nullable `finalCategoryId` to clear; validates category is JUDGING scope (when JUDGING categories exist)

**New repository methods:**
- `DivisionCategoryRepository.findByDivisionIdAndScopeOrderByCode(divisionId, scope)`
- `DivisionCategoryRepository.existsByDivisionIdAndCodeAndScope(divisionId, code, scope)`
- `EntryRepository.existsByFinalCategoryId(categoryId)`

**New status helper on `DivisionStatus`:**
- `allowsJudgingCategoryManagement()` → `ordinal() >= REGISTRATION_CLOSED.ordinal()`

**UI changes:**
- `DivisionDetailView` Categories tab: split into "Registration Categories" (read-only after REGISTRATION_CLOSED) and "Judging Categories" section (only visible when `allowsJudgingCategoryManagement()`)
- "Initialize from Registration Categories" button (only shown when no judging categories exist yet)
- `DivisionEntryAdminView` admin edit dialog: add Final Category Select field (clearable); shows JUDGING categories or falls back to all categories

**Sequencing (TDD cycles):**
1. ✅ Unit test: `CompetitionServiceJudgingCategoryTest` — `initializeJudgingCategories`, `addJudgingCategory`, `updateJudgingCategory`, `removeJudgingCategory` (11 tests). Also: `CategoryScope` enum, `JudgingCategoryDeletionGuard` interface, `DivisionStatus.allowsJudgingCategoryManagement()`, V18 migration, backward-compat 7-arg `DivisionCategory` constructor.
2. ✅ Repository test: `DivisionCategoryRepositoryTest` — scope-based queries; `EntryRepository.existsByFinalCategoryId()` (3 new tests). Note: must use returned entity from `save()` when re-saving in `@Transactional` tests — `@PrePersist` fires on managed copy, not original Java object.
3. ✅ Unit test: `EntryServiceTest` — `assignFinalCategory` (6 tests: sets, clears, fallback when no judging categories, validates JUDGING scope, entry not found, unauthorized)
4. ✅ Module integration test: `CompetitionModuleTest` — 4 new tests: initialize judging categories, full lifecycle (add/update/find/remove), status rejection (DRAFT/REGISTRATION_OPEN), duplicate initialization rejection
5. ✅ UI test: `DivisionDetailViewTest` — 5 new tests: Add Category disabled after REGISTRATION_CLOSED, Initialize Judging Categories button appears, absent before REGISTRATION_CLOSED, judging grid shown when categories exist, Add Judging Category button shown when categories exist. `DivisionDetailView` updated: judging categories section below registration grid (Initialize button when empty, grid + Add button when populated)
6. ✅ UI test + guard: `DivisionEntryAdminViewTest` — `shouldRenderEntriesGridWhenEntryHasFinalCategoryAssigned` (grid renders without NPE when entry has finalCategoryId). `EntryModuleTest` — `shouldPreventDeletionOfJudgingCategoryReferencedByFinalCategoryId`. Created `EntryJudgingCategoryDeletionGuard`. Wired `assignFinalCategory` in admin edit Save handler with clearable JUDGING category Select.

### Priority 1: Manual test of judging category management — COMPLETE
Manual testing completed 2026-05-03. Issues found and fixed during walkthrough:
- **Judging Categories moved to own tab** — previously embedded in the Categories tab; now a
  separate "Judging Categories" tab (only visible when `allowsJudgingCategoryManagement()`).
  Default tab is Judging Categories when status ≥ REGISTRATION_CLOSED.
- **i18n for judging section** — all hardcoded English strings (button labels, dialog titles,
  column headers, field labels, notifications) now use `getTranslation()`. Keys added under
  `division-detail.judging.*` in both `messages.properties` and `messages_pt.properties`.
- **Missing error keys added** — `error.category.judging-has-entries`,
  `error.category.judging-not-allowed-status`, `error.category.judging-already-initialized`
  added to both EN and PT. Previously showed raw key string instead of message.
- **Admin error messages now locale-aware** — removed forced `Locale.ENGLISH` from all 40
  `BusinessRuleException` catch blocks across 5 admin views (UserListView, CompetitionListView,
  CompetitionDetailView, DivisionDetailView, DivisionEntryAdminView). Errors now render in the
  user's preferred language, consistent with all other translated strings.
- **Add Judging Category dialog** — fields now laid out vertically (Code → Name → Description
  → Parent Category), matching the custom category dialog style. Parent select added.

### Priority 2: Bitwarden compatibility on login page — INVESTIGATED, DEFERRED
Bitwarden shows "This page is interfering with the Bitwarden experience." on the login page.
Root cause confirmed (2026-05-03): Shadow DOM's effect on Bitwarden's `document.elementFromPoint()`
visibility check — not fixable from the page side without structural changes.
- Added `autocomplete="email"` and `autocomplete="current-password"` to the fields (kept for
  general autofill correctness, but did not affect the Bitwarden warning).
- The only structural fix would be replacing the custom form with Vaadin's `LoginForm`, which
  would change the login page appearance. User chose not to pursue this.
- Deferred: only affects admins using password login; functionality is unaffected by the warning.

### Priority 3: MFA for system admins — COMPLETE (2026-05-03)
TOTP-based 2FA for SYSTEM_ADMIN accounts. No external library — HMAC-SHA1 + Base32 implemented
from scratch using standard Java APIs. Full implementation:
- `TotpService` (internal): `generateSecret()`, `generateQrUri()`, `verifyCode()` (±1 timestep window)
- `User` entity: `totp_secret` + `mfa_enabled` fields (V19 migration); `storePendingMfaSecret()`, `enableMfa()`, `disableMfa()`
- `UserService`: `setupMfa()`, `confirmMfa()`, `verifyMfaCode()`, `disableMfa()` — full setup/confirm/verify/disable flow
- `MfaAuthenticationSuccessHandler`: after form login, redirects MFA-enabled users to `/mfa` (clears SecurityContext, sets `MFA_PENDING_EMAIL` session attribute)
- `MfaVerifyView` (`/mfa`, `@AnonymousAllowed`): TOTP code entry; on success re-authenticates via `UserDetailsService`, saves to HTTP session, redirects to `/`
- `ProfileView` MFA section (SYSTEM_ADMIN only): "Set Up 2FA" (dialog with secret key + code field) or "Disable 2FA" button
- i18n: `mfa.verify.*` + `profile.mfa.*` keys in EN and PT. `error.mfa.invalid-code` error key.
23 new tests (7 TotpService + 3 UserRepository + 5 UserService unit + 5 MFA integration + 3 ProfileView UI). 777 total.

### Priority 4: Full manual walkthrough
Run the full `docs/walkthrough/manual-test.md` end-to-end (all 14 sections) before
starting the judging module implementation.

### Priority 5: Judging module
Design and implementation. **Design in progress (multi-session):** see
`docs/plans/2026-05-05-judging-module-design.md` for current state, decisions made,
open questions, and the "Next Session: Start Here" marker. Reference:
`docs/reference/chip-competition-rules.md` and `docs/specs/judging.md`.

**Phase 4 ✅ COMPLETE (2026-05-09, post branch-reconciliation).** All 10
items + §Q15 closed in a single session. §Q16 (per-entry tasting-label
PDF) and §Q17 (mobile / touch UX review) deferred. **Phase 5
(implementation) is next.**

**Branch reconciliation (2026-05-09):** the abandoned
`origin/judging-module` branch had two pushed commits with overlapping
Phase 4.A/4.B work that diverged from main's first 3 Phase 4 commits.
The user opted to discard the branch after a side-by-side comparison;
each strategic difference was resolved decision-by-decision, and the
better elements of both designs were merged into main:
- Admin dashboard placement: **main wins** (new top-level view, three
  tabs)
- Save semantics: **main wins** (explicit Save Draft only)
- URL convention: **new shared decision** — fully scoped under
  division (`/competitions/:c/divisions/:d/...`) for scoresheets,
  tables, medal-rounds, and BOS form
- Per-table view: **main wins** (unified `TableView` with role-aware
  columns/actions; admin gets revert + move actions; judges don't)
- Medal round per-row: **hybrid** — button row primary
  `[🥇][🥈][🥉]` + "More ▾" dropdown with Withhold/Clear
- Tier 2 retreat actions: **main wins** (in form header, admin-only)
- COMPARATIVE eligibility: **branch wins** (filter to
  `advancedToMedalRound = true`); SCORE_BASED uses score regardless
- BOS form: **branch wins** (dedicated `/bos` form with drag-and-drop
  primary + [+] dialog fallback); dashboard tab summarizes and links
- Resume next draft: **main wins** (prominent button on hub)
- Branch touches kept: explicit blind-judging policy on scoresheet,
  Notes-deferred-to-v2 note on medal round, service param rename
  `judgeUserId → adminUserId` on BOS methods, sidebar visibility
  gated by `hasAnyJudgeAssignment`
- Polish from branch kept: filters (Status + Search) on `TableView`,
  helpful empty-state CTAs on `MyJudgingView`, live summary line on
  `MedalRoundView` (G/S/B/Withhold/unset counts)
- **Project-wide policy refinement (new):** judges see no COI
  indicators during scoring (admin vets at table-assignment time);
  soft-COI warnings are admin-only. Hard-COI blocks remain at all
  levels (defense in depth: view authorization + service-side
  rejection).
- §Q17 raised: mobile / touch UX review across judging surfaces.

**Phase 4.A–4.C (2026-05-09) — design decisions:**
- 4.A: §Q15 resolved — **admin-only BOS for v1**. SYSTEM_ADMIN +
  competition ADMIN can record/edit/delete `BosPlacement`. No data-
  model changes (no HEAD_JUDGE role, no JudgeAssignment.isHeadJudge,
  no per-division designation table). Recordkeeping via
  `BosPlacement.awardedBy`. §3.7 authorization table updated.
- 4.B: admin division-level judging dashboard. New top-level view
  `JudgingAdminView` at `/competitions/:c/divisions/:d/judging-admin`,
  linked from DivisionDetailView via "Manage Judging" button (visible
  when `status >= JUDGING`). TabSheet with three tabs: **Tables**
  (CRUD + judge assignment with COI chips + start), **Medal Rounds**
  (per-category mode + status + Tier 2 retreat actions), **BOS**
  (header phase indicator + GOLD candidates list + placements grid;
  disabled until `Judging.phase != NOT_STARTED`). Lazy-loaded
  `Judging` row created on `beforeEnter()` if missing. Inline i18n
  keys recorded; full inventory deferred to Item 10.
- 4.C: judge scoresheet form. Full-page route `ScoresheetView` at
  `/my-judging/scoresheets/:scoresheetId`. Three-tier authorization
  (SYSTEM_ADMIN / competition ADMIN / assigned judge); hard COI page-
  level rejection if `entry.userId == judge.userId`; soft COI banner
  via `MeaderyNameNormalizer.areSimilar`. Layout: read-only entry
  header card → 5 score-field cards (NumberField min=0/max=maxValue
  with inline tier-label hints under each) → overall comments
  TextArea → comment-language ComboBox (sourced from
  `competition.commentLanguages ∪ judge.preferredCommentLanguage`,
  sorted by display name) → advance-to-medal-round Checkbox →
  Save Draft + Submit + Cancel buttons. Submit only enabled when all
  5 score values non-null; confirmation Dialog. Read-only mode for
  SUBMITTED scoresheets. Live total preview "Current total: N / 100".
  Inline i18n keys recorded.
- 4.D: judge hub + table drill-in. `MyJudgingView` at `/my-judging`
  (cross-competition hub, parallel to `/my-entries`) and
  `JudgeTableView` at `/my-judging/tables/:tableId`. Hub layout:
  prominent "▶ Resume next draft scoresheet" button (jumps to oldest
  DRAFT across assigned tables) → tables grouped by competition
  (status badge, scheduled date, scoresheet progress) → Medal Rounds
  section (only when CategoryJudgingConfig is ACTIVE for a category
  covered by judge's tables). JudgeTableView: per-table scoresheet
  grid; row click → ScoresheetView. Soft COI warning icon on rows
  with similar meaderies. New service helpers:
  `findTablesByJudgeUserId`, `hasAnyJudgeAssignment`,
  `findActiveCategoryConfigsForJudge`, `findNextDraftForJudge`. Link
  added to MainLayout sidebar (visible to any user with at least one
  JudgeAssignment).
- 4.E: medal round forms. Single shared route
  `/competitions/:c/divisions/:d/medal-rounds/:divisionCategoryId` →
  `MedalRoundView` (used by both judges and admins). Header: status +
  mode + admin-only Reset/Reopen/Finalize buttons. COMPARATIVE: grid
  of all entries with `[🥇][🥈][🥉][✗ Withhold][⊘ Clear]` action
  buttons per row. SCORE_BASED: pre-populated MedalAward rows with
  "(auto)" caption; tied-slot banner at top + warning-bg highlighting
  on tied rows + inline resolver. `MedalAward.medal=null` = explicit
  withhold (per D11). Hard COI on self-entries; soft COI tooltip.
  Read-only mode when status=COMPLETE. Service-side: caller must be
  admin OR judge with at least one JudgeAssignment for a table
  covering this category. New helper:
  `JudgingService.recomputeScorePreview` for SCORE_BASED tied-slot
  read-side projection. Inline i18n keys recorded.
- 4.F: admin Settings extensions. (1) `Competition.commentLanguages`
  — `MultiSelectComboBox<String>` on `CompetitionDetailView` Settings
  tab in a new "Judging" sub-section, sourced from
  `MeadsI18NProvider.getSupportedLanguageCodes()`, sorted by display
  name; editable any DivisionStatus. (2) `Division.bosPlaces` —
  `IntegerField` (min 1) on `DivisionDetailView` Settings tab,
  editable in DRAFT/REGISTRATION_OPEN, locked beyond with tooltip.
  (3) `Division.minJudgesPerTable` — `IntegerField` (min 1, default 2)
  on same tab, editable through REGISTRATION_CLOSED but locked once
  any JudgingTable in the division has `status != NOT_STARTED`
  (cross-module check via `MinJudgesPerTableLockGuard`). New
  CompetitionService methods (Phase 5):
  `updateCommentLanguages`, `updateDivisionBosPlaces`,
  `updateDivisionMinJudgesPerTable`, `isMinJudgesPerTableLocked`. No
  new migration — schema already in V20 per §2.G/§2.H. Inline i18n
  keys recorded.

**Phase 2 ✅ COMPLETE (2026-05-08).** All design questions resolved
(§Q1, §Q7, §Q8, §Q10, §Q11, §Q12, §Q13).

**Phase 2.A–2.F (2026-05-07) — design decisions:**
- 2.A: three-tier state model — division (`Judging.phase: NOT_STARTED → ACTIVE
  → BOS → COMPLETE`), per-table (`JudgingTable.status: NOT_STARTED → ROUND_1
  → COMPLETE`), per-category medal round (`CategoryJudgingConfig.medalRoundStatus:
  PENDING → READY → ACTIVE → COMPLETE`). Seven independent aggregates
  (Judging, JudgingTable, CategoryJudgingConfig, Scoresheet, MedalAward,
  BosPlacement, JudgeProfile) with UUID FKs only. SCORE_BASED mode requires
  manual judge intervention on tied scores.
- 2.B: retreat semantics. Tier 0 per-scoresheet revert (admin-only) is the
  foundation; Tier 1 per-table retreat is implicit. Tier 2 medal round:
  preserve on COMPLETE → ACTIVE, wipe on ACTIVE → READY. Tier 3 division:
  preserve on COMPLETE → BOS, require empty BosPlacements on BOS → ACTIVE.
  Compensating retreat events paired with every advance event. Judging
  module registers a `DivisionStatusRevertGuard`. §Q11 + §Q13 resolved.
- 2.C: §2.1 trigger re-framed to per-table; sync rule unchanged.
- 2.D: start triggers — per-table hard-blocks on judges < `Division.minJudgesPerTable`
  (new field, NOT NULL DEFAULT 2). SCORE_BASED auto-population walks
  gold→silver→bronze, stops cascade on first tie. Empty BOS allowed via UX
  info message. §Q12 resolved.
- 2.E: COI similarity — cross-country gate (skip if both countries set and
  differ) + country-aware suffix-stripping normalization + Levenshtein
  distance ≤ 2 + exact-match-on-normalized. Soft warning only. Initial
  suffix lists cover EN/PT/ES/IT/PL/FR/DE/NL/global. §Q7 resolved.
- 2.F: `JudgeProfile` aggregate (judging module) — `userId` UNIQUE,
  `certifications: Set<Certification>` (enum: MJP, BJCP, OTHER),
  `qualificationDetails: String` (free-text; also specifies what `OTHER`
  is, e.g. WSET). v1 scoresheet PDF stays anonymized (privacy-safe;
  per-jurisdiction template config deferred). §Q10 resolved.

**Phase 2.G (2026-05-08) — field-level entity finalization:**
- Field-by-field types, JPA annotations, nullability, column lengths, and
  invariants for all 7 aggregates and the 2 within-aggregate children
  (`JudgeAssignment`, `ScoreField`). Domain methods enumerated for each
  aggregate root (state-machine transitions, mutation gates).
- New competition-module fields: `Division.bosPlaces` (int NOT NULL
  DEFAULT 1, locked past REGISTRATION_OPEN — §1.6) and
  `Division.minJudgesPerTable` (int NOT NULL DEFAULT 2, locked once any
  table starts via cross-module `MinJudgesPerTableLockGuard` — §2.D).
- V20 schema produced: `judgings`, `judging_tables`, `judge_assignments`,
  `category_judging_configs`, `scoresheets`, `score_fields`,
  `medal_awards`, `bos_placements`, `judge_profiles`,
  `judge_profile_certifications` + the two `divisions` columns.
- ScoreField names stored as canonical English (i18n keys); tier
  descriptions UI-only via `MeadsI18NProvider`. MJP field constants
  (`Appearance` 12, `Aroma/Bouquet` 30, `Flavour and Body` 32, `Finish`
  14, `Overall Impression` 12) live in a `MjpScoringFieldDefinition`
  in-code constant in v1.

**Phase 2.H (2026-05-08) — scoresheet PDF locale + comment-language tagging
(closes Phase 2; resolves §Q14):**
- Scoresheet PDF renders in printer's UI locale (locale-aware), same
  mechanism as entry-side label PDFs.
- New `Scoresheet.commentLanguage` (`VARCHAR(5)`, NOT NULL at SUBMIT;
  ISO 639-1 / BCP 47) records the language of judge prose. Frozen at
  SUBMIT. Default-resolution chain: `JudgeProfile.preferredCommentLanguage`
  → `User.preferredLanguage` (UI locale).
- New `JudgeProfile.preferredCommentLanguage` (`VARCHAR(5)`, nullable)
  holds the sticky preference. Updated whenever the judge changes the
  language on any scoresheet. Lifecycle adjusted: `JudgeProfile` row
  auto-created on first `JudgeAssignment`.
- New `Competition.commentLanguages` (Set<String>, `@ElementCollection`
  → `competition_comment_languages` join table) is the admin-curated
  per-competition dropdown source. Seeded with the 5 UI codes (`en`,
  `es`, `it`, `pl`, `pt`) at competition creation; admin edits in
  `CompetitionDetailView` Settings tab. Dropdown shown to judges =
  union of `competition.commentLanguages` and the judge's current
  `preferredCommentLanguage` (so a sticky value outside the list still
  shows).
- PDF: each comment block carries a "Comments — written in &lt;Language&gt;"
  subheader (display name in printer's locale).
- V20 SQL extended with `competition_comment_languages` table, plus
  `comment_language` and `preferred_comment_language` columns on
  `scoresheets` and `judge_profiles`.

**Phase 3 (2026-05-08) — service contracts, events, authorization, COI
mechanism, cross-module guards (docs-only sketch; Java skeleton deferred
to Phase 5):**
- Module skeleton plan: `app.meads.judging` + `package-info.java` with
  `@ApplicationModule(allowedDependencies = {"competition", "entry", "identity"})`.
- Three judging-module services with full method signatures:
  `JudgingService` (table CRUD, judge assignment, table/medal-round/BOS
  state transitions, medal awards, BOS placements), `ScoresheetService`
  (eager creation, edits, status transitions, comment language),
  `JudgeProfileService` (ensure-on-assignment, CRUD, sticky language helper).
- One competition-module extension: `CompetitionService.updateCommentLanguages`.
- 13 event records (advance/retreat pairs across all 3 retreat tiers
  from §2.B), denormalized to carry routing fields (`divisionId`,
  `divisionCategoryId`, `totalScore`, etc.) so listeners don't reload.
- Authorization rules table covering SYSTEM_ADMIN, competition ADMIN,
  assigned judge, other judge, entrant. Hard COI block enforced
  service-side; soft COI warning UI-only.
- COI implementation contract: `MeaderyNameNormalizer` utility (with
  full per-country suffix maps as compile-time constants per §2.E) +
  `CoiCheckService` interface returning `CoiResult(hardBlock, softWarningKey)`.
- Cross-module guards: `JudgingDivisionStatusRevertGuard` (judging
  impl of existing competition interface) blocks JUDGING →
  REGISTRATION_CLOSED retreat once data exists; new
  `MinJudgesPerTableLockGuard` interface in competition module +
  `JudgingMinJudgesLockGuard` impl.
- Open: §Q15 (head-judge designation for BOS authorization) — deferred
  to Phase 4 view design; default leaning is admin-only for v1.

**Phase 4 ✅ COMPLETE (single session, 2026-05-09).** All 10 items + §Q15
closed (see Phase 4.A–4.K above). §Q16 (per-entry tasting-label PDF
variant for wine-glass tags) and §Q17 (mobile / touch UX review across
judging surfaces) opened — both deferred. Phase 5 (implementation) is
next; design doc has the recommended TDD order from module skeleton →
V20 migration → entities → services → guards → events → views →
PDF service → i18n keys → integration tests.

### Priority 5b: Judging module Phase 5 (implementation)

All design pinned in `docs/plans/2026-05-05-judging-module-design.md`.
Implementation translates mechanically from Phases 2–4 (entities + V20
schema in §2.G/§2.H, services in §3.2–§3.5, events in §3.6, COI
mechanism in §3.8, cross-module guards in §3.9, views in §4.B–§4.J,
i18n in §4.K).

When Phase 5 starts, switch to a feature branch (e.g.
`feature/judging-module`) for code work — the design doc is on `main`
and complete.

**Phase 5 (impl, IN PROGRESS on `feature/judging-module`):** module
skeleton → V20 migration → entities → services (TDD, repository tests
first) → events + listeners → views → integration tests. Java
skeleton from Phase 3 translates mechanically.

**Phase 5 progress (2026-05-09):**
- ✅ Module skeleton: `app.meads.judging` package + `package-info.java`
  with `@ApplicationModule(allowedDependencies = {"competition",
  "entry", "identity"})`. `internal/` sub-package created.
- ✅ V20 migration started: `V20__create_judgings_table.sql`. Per the
  established pattern (V9–V13 split entry-module schema across 5
  migrations, one per table), V20+ for judging tables will likewise
  split per table — V20 for `judgings`, V21 for `judging_tables` +
  `judge_assignments`, etc. The single-V20 sketch in §2.G of the
  design doc is treated as a logical schema, not a literal migration
  plan.
- ✅ TDD Cycle 1 — `Judging` aggregate (judging-module aggregate root):
  `Judging` entity (UUID self-gen, `phase` enum, `@PrePersist`
  `createdAt`, `@PreUpdate` `updatedAt`, all 5 state-machine domain
  methods per §2.G); `JudgingPhase` enum (NOT_STARTED / ACTIVE / BOS
  / COMPLETE); `JudgingRepository` with `findByDivisionId`. Test:
  `JudgingRepositoryTest#shouldSaveAndFindJudgingByDivisionId`.
- ✅ TDD Cycle 2 — `JudgingTable` aggregate + `JudgeAssignment` child:
  `JudgingTable` entity in public package with `@OneToMany` +
  `@JoinColumn(name="judging_table_id")` + `orphanRemoval = true` over
  `JudgeAssignment`; `JudgingTableStatus` enum (NOT_STARTED / ROUND_1
  / COMPLETE); `JudgeAssignment` `@Entity` in `internal/` (no FK
  field on the child — JPA-managed via parent's `@JoinColumn` to
  avoid duplicate mapping); 6 domain methods (`updateName`,
  `updateScheduledDate`, `assignJudge` idempotent, `removeJudge`,
  `startRound1` / `markComplete` / `reopenToRound1` state machine);
  `JudgingTableRepository` with `findByJudgingId` and JPQL-based
  `findByJudgeUserId` (joins to assignments). 5 tests (save w/
  assignments, find-by-judging-id, orphan-removal of assignment,
  cross-table find-by-judge, empty result). V21 migration:
  `judging_tables` + `judge_assignments` (with FK cascade DELETE).
- ✅ TDD Cycle 3 — `CategoryJudgingConfig` aggregate: UUID self-gen,
  `divisionCategoryId` UNIQUE FK, `MedalRoundMode` enum (COMPARATIVE
  default / SCORE_BASED), `MedalRoundStatus` enum (PENDING / READY /
  ACTIVE / COMPLETE state machine). 7 domain methods per §2.G:
  `updateMode` (gated to PENDING ∨ READY), `markReady`, `markPending`,
  `startMedalRound`, `completeMedalRound`, `reopenMedalRound`,
  `resetMedalRound`. `CategoryJudgingConfigRepository` with
  `findByDivisionCategoryId`. V22 migration. 2 repository tests.
- ✅ TDD Cycle 4 — `Scoresheet` aggregate + `ScoreField` child:
  `Scoresheet` (UUID self-gen, `tableId` mutable while DRAFT,
  `entryId` UNIQUE FK, `filledByJudgeUserId`, `ScoresheetStatus`
  enum DRAFT/SUBMITTED, `totalScore` computed at submit, free-text
  `overallComments`, `advancedToMedalRound` flag, `submittedAt`,
  `commentLanguage`); 8 domain methods (`updateScore`,
  `updateOverallComments`, `setFilledBy`, `setAdvancedToMedalRound`,
  `submit` with all-fields-filled validation, `revertToDraft`,
  `moveToTable`, `setCommentLanguage`); 5 `ScoreField` children
  auto-created at construction from `MjpScoringFieldDefinition`
  constants (Appearance 12, Aroma/Bouquet 30, Flavour and Body 32,
  Finish 14, Overall Impression 12 = total max 100); `ScoreField`
  validates `0 <= value <= maxValue`. `ScoresheetRepository` with
  `findByEntryId` (UNIQUE) + `findByTableId`. V23 migration:
  `scoresheets` + `score_fields` (FK cascade DELETE on score_fields,
  UNIQUE (scoresheet_id, field_name)). 4 repository tests.
- ✅ TDD Cycle 5 — `MedalAward` aggregate: UUID self-gen, `entryId`
  UNIQUE FK, `divisionId` (denormalized for query), `finalCategoryId`,
  nullable `medal` (per D11 — null = explicit withhold; absence of row
  = not a candidate), `awardedAt` `@PrePersist`, `awardedBy`,
  `updatedAt` `@PreUpdate`. `Medal` enum (GOLD / SILVER / BRONZE).
  `updateMedal(newValue, awardedBy)` mutator (service-level
  guard: `medalRoundStatus = ACTIVE` enforced later by
  `JudgingService`). `MedalAwardRepository`: `findByEntryId`,
  `findByDivisionId`, `findByFinalCategoryId`. V24 migration. 4
  repository tests.
- ✅ TDD Cycle 6 — `BosPlacement` aggregate: UUID self-gen,
  `divisionId`, `entryId`, `place` int (entity guard `>= 1`),
  `awardedAt` `@PrePersist`, `awardedBy`, `updatedAt` `@PreUpdate`,
  `UNIQUE(division_id, place)` + `UNIQUE(division_id, entry_id)`.
  `updatePlace(newPlace, awardedBy)` mutator. `BosPlacementRepository`:
  `findByDivisionIdOrderByPlace`, `findByEntryId`. V25 migration. 6
  repository tests.
- ✅ TDD Cycle 7 — `JudgeProfile` aggregate: UUID self-gen, `userId`
  UNIQUE FK, `Set<Certification>` via `@ElementCollection` +
  `@CollectionTable(name = "judge_profile_certifications")`,
  `qualificationDetails` VARCHAR(200), `preferredCommentLanguage`
  VARCHAR(5) (sticky preference per §2.H). `Certification` enum
  (MJP/BJCP/OTHER). 3 domain methods: `updateCertifications` (full
  set replacement), `updateQualificationDetails` (trim + null on
  blank), `updatePreferredCommentLanguage` (null clears). Eager fetch
  on the certifications collection. `JudgeProfileRepository.findByUserId`.
  V26 migration. 5 repository tests.
- 🎉 All 7 judging-module aggregates implemented (Judging,
  JudgingTable+JudgeAssignment, CategoryJudgingConfig,
  Scoresheet+ScoreField, MedalAward, BosPlacement, JudgeProfile).
- ✅ TDD Cycle 8 — Competition + Division judging fields:
  `Competition.commentLanguages` (`Set<String>` via
  `@ElementCollection` → `competition_comment_languages` join table,
  eager fetch); `updateCommentLanguages(Set<String>)` validates each
  code against `^[a-z]{2}(-[A-Za-z0-9]+)?$` BCP-47 pattern; getter
  returns unmodifiable set. `Division.bosPlaces` (default 1, locked
  past REGISTRATION_OPEN) and `Division.minJudgesPerTable` (default
  2, locked past REGISTRATION_CLOSED at entity level — additional
  cross-module lock via `MinJudgesPerTableLockGuard` interface in
  competition module, to be implemented by judging module). Both
  domain methods reject `< 1`. V27 migration:
  `competition_comment_languages` join table + two ALTER TABLE
  columns on `divisions` with `NOT NULL DEFAULT 1` / `2`
  (backward-compatible). 14 new unit tests across DivisionTest +
  CompetitionTest.
- 🎉 Entity layer COMPLETE. All judging-module aggregates +
  competition-module additions implemented and persisted.
- ✅ TDD Cycle 9 — 13 event records (§3.6): `ScoresheetSubmittedEvent`,
  `ScoresheetRevertedEvent`, `TableStartedEvent`, `TableCompletedEvent`,
  `TableReopenedEvent`, `MedalRoundActivatedEvent`,
  `MedalRoundCompletedEvent`, `MedalRoundReopenedEvent`,
  `MedalRoundResetEvent`, `BosStartedEvent`, `BosCompletedEvent`,
  `BosReopenedEvent`, `BosResetEvent`. All denormalized to carry
  routing fields (divisionId, divisionCategoryId, totalScore, etc.).
- ✅ TDD Cycle 10 — `JudgeProfileService` (§3.4): public interface +
  `JudgeProfileServiceImpl` in `internal/`. 5 methods:
  `ensureProfileForJudge` (idempotent), `createOrUpdate` (auth: self
  or SYSTEM_ADMIN), `findByUserId`, `updatePreferredCommentLanguage`
  (internal helper, creates profile if absent), `delete` (admin-only;
  rejects if any JudgeAssignment references user via new
  `JudgingTableRepository.existsAssignmentByJudgeUserId`). 14 unit tests.
- ✅ TDD Cycle 11 — COI mechanism (§2.E + §3.8):
  `MeaderyNameNormalizer` utility (compile-time per-country suffix
  maps for GLOBAL/PT/BR/ES/MX/AR/IT/PL/FR/DE/AT/CH/NL/BE) + `normalize`
  + `areSimilar` (cross-country gate, exact-or-Levenshtein-≤2).
  `CoiCheckService` public interface + `CoiCheckServiceImpl` returning
  `CoiResult(hardBlock, softWarningKey)`. Hard block when
  `entry.userId == judge.userId`; soft warning when meadery names
  similar. 13 normalizer tests + 4 service tests.
- ✅ TDD Cycle 12 — `CompetitionService.updateCommentLanguages`
  (§3.5): SYSTEM_ADMIN or competition ADMIN auth, validates each code
  matches BCP-47 pattern (entity throws `IllegalArgumentException`,
  service translates to `BusinessRuleException`). 4 unit tests.
- ✅ TDD Cycle 13 — `CompetitionService.updateDivisionBosPlaces` +
  `updateDivisionMinJudgesPerTable` + `isMinJudgesPerTableLocked`.
  Constructor now takes `List<MinJudgesPerTableLockGuard>`; the
  `updateDivisionMinJudgesPerTable` method consults all registered
  guards via `isMinJudgesPerTableLocked()` before delegating to entity.
  6 new unit tests. **Note:** The existing
  `CompetitionServiceJudgingCategoryTest` constructor was updated to
  pass empty list for the new guard parameter.
- ✅ TDD Cycle 14 — `JudgingService` table CRUD + judge assignment
  (§3.2): public interface + `JudgingServiceImpl` in `internal/`.
  Methods: `ensureJudgingExists` (lazy), `createTable`,
  `updateTableName`, `updateTableScheduledDate`, `deleteTable` (only
  NOT_STARTED + no assignments), `findTablesByJudgingId`,
  `findTablesByJudgeUserId`, `hasAnyJudgeAssignment`, `assignJudge`
  (idempotent + calls `ensureProfileForJudge`), `removeJudge` (rejects
  if would drop below `Division.minJudgesPerTable` while ROUND_1).
  Authorization: `competitionService.isAuthorizedForDivision`. 15 tests.
  Added `existsAssignmentByJudgeUserId` to JudgingTableRepository.
- ✅ TDD Cycle 15 — `JudgingService` table state + medal-round
  transitions: `startTable` (guards minJudgesPerTable, marks
  judging.markActive() if first table, ensures CategoryJudgingConfig,
  delegates to `ScoresheetService.createScoresheetsForTable`, publishes
  `TableStartedEvent`), `configureCategoryMedalRound` (idempotent
  create-or-update), `startMedalRound` (with SCORE_BASED auto-population
  per §2.D D10 — walks gold→silver→bronze, stops cascade on first tie),
  `completeMedalRound`, `reopenMedalRound` (guards Judging.phase=ACTIVE),
  `resetMedalRound` (deletes MedalAward rows in tx). All publish Tier
  1+2 events. 8 tests. Added `findDivisionCategoryById` to
  CompetitionService for division-from-category lookup.
- ✅ TDD Cycle 16 — `JudgingService` medals + BOS:
  `recordMedal`/`updateMedal`/`deleteMedalAward` (COI hard block on
  recordMedal/updateMedal; medalRoundStatus=ACTIVE guard;
  authorization: admin OR judge with assignment to a table covering
  this category), `startBos` (guards every CategoryJudgingConfig is
  COMPLETE), `completeBos`, `reopenBos`, `resetBos` (rejects if any
  BosPlacement exists per §2.B Tier 3),
  `recordBosPlacement`/`updateBosPlacement`/`deleteBosPlacement`
  (admin-only per §Q15; place ∈ [1, Division.bosPlaces]; entry must
  have MedalAward.medal=GOLD). All publish Tier 3 events. 14 tests.
- ✅ TDD Cycle 17 — `ScoresheetService` (§3.3): public interface +
  `ScoresheetServiceImpl` in `internal/`. 9 methods:
  `createScoresheetsForTable` (eager, idempotent — uses new
  `EntryService.findEntriesByFinalCategoryId` + new
  `EntryRepository.findByFinalCategoryId`), `ensureScoresheetForEntry`
  (sync rule), `updateScore`/`updateOverallComments`/
  `setAdvancedToMedalRound` (COI hard block, sets
  filledByJudgeUserId on first edit), `setCommentLanguage` (validates
  against `competition.commentLanguages ∪ judge.preferredCommentLanguage`,
  atomically updates JudgeProfile preference), `submit` (DRAFT→SUBMITTED;
  validates all 5 fields; computes total; resolves default
  commentLanguage from JudgeProfile; cascades to TableComplete +
  CategoryJudgingConfig.markReady() if all sheets at table SUBMITTED
  AND all tables for category COMPLETE; publishes
  `ScoresheetSubmittedEvent` + `TableCompletedEvent`),
  `revertToDraft` (Tier 0 admin-only; cascades table reopen +
  CategoryJudgingConfig.markPending() if applicable; publishes
  `ScoresheetRevertedEvent` + `TableReopenedEvent`), `moveToTable`
  (admin-only; validates newTable.divisionCategoryId ==
  entry.finalCategoryId). 12 tests.
- ✅ TDD Cycle 18 — Cross-module guards (§3.9):
  `JudgingDivisionStatusRevertGuard` (impl of competition-module
  `DivisionRevertGuard`) blocks JUDGING → REGISTRATION_CLOSED revert
  if `Judging.phase != NOT_STARTED` OR any JudgingTable exists for
  the judging row. `JudgingMinJudgesLockGuard` (impl of competition-
  module `MinJudgesPerTableLockGuard`) returns true if any
  JudgingTable for the division has status != NOT_STARTED. New
  repo queries: `JudgingTableRepository.existsByJudgingId` +
  `existsStartedByJudgingId`. Both impl classes are `public` (Java
  package-private is per-package, not per-module — same convention as
  `UserRepository`). 5 + 3 = 8 unit tests. Spring auto-discovers and
  wires them into `CompetitionService` via the `List<...Guard>`
  constructor parameters.
- 🎉 Phase 5 services layer COMPLETE. 916 tests (+98 from Phase 5
  services + guards alone). Spring wiring verified end-to-end via
  full suite + ModulithStructureTest. **Note:** Listeners for the 13
  events are NOT yet implemented — they're published, but no
  `@ApplicationModuleListener` consumers exist yet (deferred to a
  later cycle, will mostly be email notifications + future awards
  module integration).
- 🟡 Next: Phase 6 (views) — `JudgingAdminView`, `MyJudgingView`,
  `ScoresheetView`, `MedalRoundView`, `JudgeTableView`, BOS form.
  Per design doc §4.B–§4.J. Also pending: i18n keys for all the new
  `error.*` and `coi.warning.*` keys introduced in Phase 5 services
  (currently fall back to English raw key).

**Phase 6 progress (2026-05-10) — JudgingAdminView skeleton + Tables tab basic:**
- ✅ Cycle 1 — `JudgingAdminView` skeleton at
  `/competitions/:c/divisions/:d/judging-admin` (judging.internal).
  `@PermitAll` + `beforeEnter()` auth (SYSTEM_ADMIN OR
  isAuthorizedForDivision; password gate; status >= JUDGING gate;
  forwards to `""` or division detail when blocked).
  Lazy-loaded `Judging` row via `ensureJudgingExists` on entry.
  Header: competition logo + "Competition — Division — Judging Admin"
  H2 + breadcrumb. TabSheet with three tabs: Tables / Medal Rounds /
  Best of Show (latter two empty placeholders for now). i18n keys:
  `judging-admin.nav.judging-admin`, `judging-admin.tab.{tables,
  medal-rounds, bos}` in EN + PT.
- ✅ Cycle 2 — "Manage Judging" button on `DivisionDetailView`,
  visible only when `division.status.ordinal() >= JUDGING.ordinal()`,
  navigates to the new view via string-based ui.navigate (no
  cross-module import). i18n: `division-detail.manage-judging` in
  EN + PT.
- ✅ Cycle 3 — Tables tab grid + Add Table dialog. Grid columns
  (Name, Category "code — name", Status, Judges count, Scheduled,
  Scoresheets, Actions). "+ Add Table" header button opens Dialog
  (TextField name + `Select<DivisionCategory>` filtered to JUDGING
  scope via `competitionService.findJudgingCategories` + DatePicker
  scheduledDate). Save calls `JudgingService.createTable`. Per-field
  validation; `BusinessRuleException` translated via
  `getTranslation(messageKey, params)`. Scoresheets column shows "—"
  placeholder (real DRAFT/SUBMITTED counts deferred — needs
  `ScoresheetService.countByTableIdAndStatus` helper). Actions column
  empty for now (per-row buttons in next cycle).
- ✅ Pre-existing bug surfaced + fixed: `JudgingServiceImpl`,
  `ScoresheetServiceImpl`, `JudgeProfileServiceImpl` had `@NotNull`
  / `@NotBlank` on impl method parameters but the public-API
  interfaces had none. Hibernate Validator's HV000151 (LSP
  enforcement) fires when these `@Validated` services are bean-
  proxied. Bug never surfaced because all existing Spring tests for
  these services use unit-test impl-construction (no proxy). The
  view is the first Spring-wired consumer. Fix: moved all
  `@NotNull` / `@NotBlank` annotations from impls to interfaces (the
  contract is the right place for Bean Validation).
- ✅ Prerequisite cycle (2026-05-10): added
  `CompetitionService.findUsersByRoleInCompetition(competitionId, role)`
  returning `List<User>`. Composes `participantRepository.findByCompetitionId`
  + `participantRoleRepository.existsByParticipantIdAndRole` +
  `userService.findAllByIds` (no new repo query). Needed by the
  Assign Judges dialog in the next Tables-actions cycle. 1 unit test.
- ✅ Phase 6.2 cycle (2026-05-10): Tables tab per-row actions on
  `JudgingAdminView`. Action buttons in column order: ✏ Edit (always
  enabled), ▶ Start (NOT_STARTED only), 👥 Assign Judges, 🗑 Delete
  (NOT_STARTED + no assignments). Each opens a Dialog via
  `openEditTableDialog` / `openStartTableDialog` /
  `openAssignJudgesDialog` / `openDeleteTableDialog` (public for
  test access). Start dialog shows different body text based on
  whether `entryService.findEntriesByFinalCategoryId` returns any
  entries. Assign Judges dialog: `Grid<User>` (multi-select)
  pre-selected with currently-assigned judges; columns Name /
  Meadery / Country / COI; per-row COI chips computed via
  `coiCheckService.check(judgeId, entryId)` against entries in the
  table's category (hard-block badge for self-entries, soft-warning
  badge for similar meadery). Save diffs selection vs current and
  calls `assignJudge` / `removeJudge` per delta. Service errors
  caught and shown as ERROR notifications. New repo query:
  `JudgingTableRepository.countAssignmentsByTableId` (used by
  test, exposed for any future query needs). Added i18n keys for
  all action labels, dialog titles/bodies, and a first batch of
  judging-table service error keys (`error.judging-table.*`,
  `error.judging.not-found`) in EN + PT (broader Phase 5 service
  error key cleanup still deferred). 5 new view tests.
- ✅ Phase 6.3 cycle (2026-05-10): added
  `ScoresheetService.countByTableIdAndStatus(tableId, status)` (and
  `ScoresheetRepository` derived query). Tables tab Scoresheets
  column now renders "DRAFT N · SUBMITTED M" (or "—" when both 0).
  i18n key `judging-admin.tables.scoresheets.format` in EN + PT.
  1 new unit test.
- 🟡 Next cycles for Phase 6: table drill-in (👁 View action,
  per-table scoresheet admin), Medal Rounds tab grid + actions,
  BOS tab content, then judge-side views (`MyJudgingView`,
  `JudgeTableView`, `ScoresheetView`, `MedalRoundView`, BOS form).
  Per design doc §4.B–§4.J.

### Priority 6: Awards module
Design and implementation, after judging module. Reference: `docs/reference/chip-competition-rules.md` and `docs/specs/awards.md`.

### Priority 7: Auto-close + deadline reminders (deferred)
- **Auto-close** — automatically advance division from REGISTRATION_OPEN → REGISTRATION_CLOSED
  when registration deadline passes (scheduled task)
- **Entrant deadline reminder** — notify entrants who have DRAFT entries when the registration
  deadline is approaching (e.g., 7 days, 3 days, 1 day before deadline)
- Other potential: entry received confirmation (when admin marks entry as RECEIVED), results published notification

### Priority 8: Full category constraint system (low priority — future competition)
Full field locking/validation based on category selection. Design doc: `docs/plans/2026-03-11-category-hints-design.md` (appendix).
Includes: sweetness locking (M1A→Dry, M1B→Medium, M1C→Sweet), ingredient restrictions (M1/M4E),
strength locking (M4S→Hydromel), ABV caps (M4S→7.5%), ABV→Strength derivation (universal),
carbonation locking (custom categories), and admin-configurable constraints for custom categories.
Requires: DB migration, admin UI for constraint config, cross-module data flow, server-side validation.

### Priority 9: Bitwarden compatibility on login page (deferred, low priority)
Bitwarden shows "This page is interfering with the Bitwarden experience. The Bitwarden inline
menu has been temporarily disabled as a safety measure."

**Real root cause (re-investigated 2026-05-03):** the previous Shadow-DOM/`elementFromPoint`
theory was wrong. Trigger is `bitwarden/clients` PR #17400 (merged Nov 2025), shipped in
Bitwarden browser extension late-2025/early-2026. The check fires when:
- **Top-layer hijack**: page has other top-layer items (popovers/dialogs) and Bitwarden's own
  inline-menu popover gets bumped, forcing it to call `hidePopover()` + `showPopover()` to
  reclaim position. **5 refreshes in 5 s → warning** (only `window.alert()` in entire browser source).
- **Popover attribute mutation**: page modifies `popover` attribute on Bitwarden's own button/list
  away from `"manual"`. **10 mutations in 5 s → warning.**
- **Page opacity check**: `<html>` or `<body>` computed opacity ≤ 0.6 → inline menu closes.

**Why our login page hits the top-layer threshold:** Vaadin's overlay/notification/tooltip system
moved to the native `popover` API in 24.5+ — well before our 25.1.3 upgrade. Any Vaadin field
with eager validation (we have `ValueChangeMode.EAGER` on email + password) and any tooltip/notification
churn produces a stream of popover open/close events. With Bitwarden's autofill button popover
also competing for the top layer, the 5-in-5s threshold is easy to hit.

**Why now:** not a Vaadin regression. The detection was added on the **Bitwarden side** in
Nov 2025; users started seeing it as their Bitwarden extension auto-updated. Any Vaadin 24.5+ app
shows this on form pages.

**autocomplete attributes** (`email`, `current-password`) already added to fields — kept for
correctness but they have no effect on this warning.

**Mitigations** (none done yet, all deferred):
1. Replace custom `LoginView` with Vaadin's `LoginForm` / `LoginOverlay`. Officially designed for
   password-manager compatibility (light-DOM inputs, attaches to body). Would change the page's
   visual layout though — user previously chose not to pursue.
2. Switch email/password fields to `ValueChangeMode.ON_BLUR` to cut validation-tooltip churn.
   Cheapest experiment; may or may not help depending on whether tooltips are the real source.
3. Drop the `Details` collapsible — render password row directly. Reduces DOM churn on first paint.
4. User-side workaround: add `meads.app` to Bitwarden Settings → Autofill → Blocked Domains.

**Status:** Functionality unaffected (autofill via Bitwarden popup/keyboard shortcut still works;
only the inline button is suppressed). Only affects admins using password login. Revisit if we
move to `LoginForm` for other reasons or if Bitwarden softens the threshold.

**References:**
- bitwarden/clients PR #17400 — `apps/browser/src/autofill/overlay/inline-menu/content/autofill-inline-menu-content.service.ts`
- community.bitwarden.com thread 92519 ("This page is interfering with the Bitwarden experience")

### Completed priorities
- **Branch `feature/judging-module` + version bump to 0.4.0-SNAPSHOT** — 2026-05-09. Phase 4 design closed on `main` (4 commits pushed). Created `feature/judging-module` for Phase 5 implementation work; bumped version to 0.4.0-SNAPSHOT to clearly separate the in-progress judging module from `main` (which stays at 0.3.0-SNAPSHOT until merge).
- **Version bump to 0.3.0-SNAPSHOT** — 2026-05-02. Bumped from 0.2.9-SNAPSHOT to 0.3.0-SNAPSHOT ahead of judging category management and the judging module. 723 tests.
- **Post-registration guards + admin add entry** — Completed 2026-05-02. Credits (add/adjust), product mappings (add/edit/delete), and entrant entry edits all blocked after REGISTRATION_OPEN. `DivisionStatus.allowsRegistrationActions()`. Disabled-button tooltips via Span wrapper. Credits balance auto-refreshes after credit operations. Submit All Drafts disabled when not REGISTRATION_OPEN. MyEntriesView shows "Registration is closed" in red when past REGISTRATION_OPEN. Admin "Add Entry" in Entries tab (two-step: warning confirmation → entry form with entrant email). `EntryService.adminCreateEntry()` skips credit check and status check. 8 new unit tests. 723 tests.
- **v0.2.8 release** — Released 2026-05-02. Includes entry status redesign, expanded entries summary row (per-status breakdown), admin view i18n (PT translations), dependency upgrades, and PT translation fixes (pre-AO orthography, wood-aged terminology).
- **Entry status management redesign** — Completed 2026-04-30. Replaced "Mark as Received" button with `←`/`→` arrow buttons for the full DRAFT → SUBMITTED → RECEIVED flow. WITHDRAWN entries revert to DRAFT. New domain methods `advanceStatus()`/`revertStatus()` on `Entry`; new service methods `advanceEntryStatus()`/`revertEntryStatus()` on `EntryService`. `advanceEntryStatus()` calls `publishSubmissionEventIfComplete()` (consistent with entrant-triggered path). `getTotalCreditBalance(divisionId)` replaces N+1 participant loop. Summary row: "Credits balance: N | Total entries: N (Draft: X, Submitted: Y, Received: Z, Withdrawn: W)". 715 tests.
- **Dependency upgrades + entry admin summary row** — Completed 2026-04-29. Bumped Spring Boot 4.0.2→4.0.6, Vaadin 25.0.7→25.1.3, Spring Modulith 2.0.4→2.0.6, Testcontainers 2.0.4→2.0.5. Added summary row to DivisionEntryAdminView Entries tab (credits balance + full per-status entry breakdown). 696 tests.
- **Admin view i18n** — Completed 2026-04-29. Extracted ~270 hardcoded strings from 8 admin views (LoginView, SetPasswordView, UserListView, CompetitionListView, MyCompetitionsView, CompetitionDetailView, DivisionDetailView, DivisionEntryAdminView). Added keys to `messages.properties`, Portuguese translations to `messages_pt.properties`. ES/IT/PL fall back to EN. Fixed `ComboBox<>` type inference issue under Java 21 (explicit `new ComboBox<String>(...)`).
- **Italian informal language + dependency upgrades** — Completed 2026-03-23. Switched all Italian UI text from formal "Lei" to informal "tu" (UI, emails, PDF instructions, error messages). Upgraded dependencies: Vaadin 25.0.5→25.0.7, Spring Modulith 2.0.2→2.0.4, OpenPDF 2.0.3→3.0.3 (package rename com.lowagie→org.openpdf), BouncyCastle 1.80→1.83, Karibu Testing 2.6.2→2.7.0, Testcontainers 2.0.3→2.0.4. Added Vaadin-generated frontend files to .gitignore. 695 tests.
- **Document language filtering** — Completed 2026-03-19. Added optional `language` field (VARCHAR(5)) to `CompetitionDocument` (V17 migration). `null` = visible to all languages, a language code (e.g. "pt") = visible only to that locale. Admin add-document dialog has Language dropdown (from `MeadsI18NProvider.getSupportedLanguageCodes()`). Admin grid shows Language column. Entrant view (`MyEntriesView`) filters via `getDocumentsForLocale()`. 685 tests.
- **Date display** — Completed 2026-03-18. Registration deadline in entrant view now uses locale-aware `DateTimeFormatter.ofLocalizedDate(SHORT)` + `ofLocalizedTime(SHORT)` instead of hardcoded pattern. Timezone display removed.
- **Logo update** — Completed 2026-03-18. Switched to new logo files: `meads-logo-white` for navbar and emails, `meads-logo-dark-grey` for README light mode.
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
- **MEADS logo branding** — Replaced "MEADS" text with SVG logo in navbar (44px, left-aligned) and PNG logo as CID inline image in email header. Logo files at `META-INF/resources/images/meads-logo-white.{svg,png}` (app + email) and `meads-logo-dark-grey.{svg,png}` (README light mode). SecurityConfig permits `/images/**`. Navbar height increased to 60px.
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
- `EntryTest.java` — entry entity domain methods (constructor, submit, markReceived, withdraw, updateDetails, assignFinalCategory, getEffectiveCategoryId, advanceStatus, revertStatus)
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
