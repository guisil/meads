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

**Branch:** `competition-module`
**Tests:** 481 passing (`mvn test -Dsurefire.useFile=false`)
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
  Sends magic link, password reset, and password setup emails. SMTP failure logged with fallback link (no crash).
  Mailpit for dev (port 1025 SMTP, port 8025 web UI). Resend SMTP for prod. 7-day token validity.
- **Status:** Complete

### competition module (`app.meads.competition`)
- **Depends on:** identity
- **Status:** Complete (fully implemented + code reviewed + scope rework done)

#### Entities (public API)
| Entity | Table | Description |
|--------|-------|-------------|
| `Competition` | `competitions` | Top-level: name, shortName (unique), dates, location, logo, contactEmail, shippingAddress, phoneNumber |
| `Division` | `divisions` | Sub-level: competitionId, name, shortName (unique per competition), scoringSystem, status, entry limits (per subcategory, per main category, total), entryPrefix, meaderyNameRequired |
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
- Competition CRUD, Division CRUD, Participant management, Category management
- Document management: `addDocument`, `removeDocument`, `updateDocumentName`, `reorderDocuments`, `getDocuments`, `getDocument`
- Authorization: `isAuthorizedForCompetition()`, `isAuthorizedForDivision()`
- `findCompetitionsByAdmin(userId)` — finds competitions where user has ADMIN participant role
- `updateCompetitionContactEmail()` — updates competition contact email (shown in participant emails)
- `revertDivisionStatus()` — one-step-back revert with guard interface pattern
- Entry limits (per subcategory, per main category, total) — DRAFT-only, enforced by EntryService
- Events: `DivisionStatusAdvancedEvent`

#### Views
- `CompetitionListView` (`/competitions`) — SYSTEM_ADMIN only, all competitions grid with CRUD
- `CompetitionDetailView` (`/competitions/:shortName`) — tabs: Divisions, Participants, Settings, Documents (add/edit/delete/reorder PDF and link documents)
- `DivisionDetailView` (`/competitions/:compShortName/divisions/:divShortName`) — tabs: Categories, Settings + "Manage Entries" button + "Advance/Revert Status" buttons
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
- **WebhookService** — HMAC signature verification, `processOrderPaid` (JSON parsing, idempotency, mutual exclusivity, credit creation, country enrichment from shipping/billing address)
- **LabelPdfService** — PDF label generation (OpenPDF + ZXing QR codes). Single entry or batch. A4 landscape, instruction header, 3 identical labels per page. Public API for cross-module access.

#### Events
- `CreditsAwardedEvent(divisionId, userId, amount, source)`
- `EntriesSubmittedEvent(divisionId, userId, entryCount)`

#### DTOs
- `EntrantCreditSummary(userId, email, name, creditBalance, entryCount)`

#### Views
- `EntrantOverviewView` (`/my-entries`) — cross-competition entrant hub, shows all divisions with credits/entries, auto-redirects to single division
- `MyEntriesView` (`/competitions/:compShortName/divisions/:divShortName/my-entries`) — entrant-facing, competition documents list, credits + limits display, entry grid with status badges/Final Category/Actions (view/edit/submit/download label)/filtering/sorting, add/edit dialog (full-width fields), submit all, "Download all labels" batch button, meadery name required warning + submit blocking
- `DivisionEntryAdminView` (`/competitions/:compShortName/divisions/:divShortName/entry-admin`) — admin tabs: Credits, Entries (with Meadery/Country columns + individual label download + batch "Download all labels" with confirmation dialog), Products, Orders

#### REST
- `JumpsellerWebhookController` — `POST /api/webhooks/jumpseller/order-paid` (HMAC-verified)

#### Guards
- `EntryDivisionRevertGuard` — blocks REGISTRATION_OPEN → DRAFT revert when entries exist

#### Event Listener
- `RegistrationClosedListener` — skeleton for `DivisionStatusAdvancedEvent` (REGISTRATION_CLOSED)

#### Changes to other modules
- `SecurityConfig` — separate `SecurityFilterChain` with `@Order(1)` for webhook API (CSRF disabled, permitAll)
- `User.java` — added `meaderyName` and `country` fields (now in V2)
- `Division.java` — added `maxEntriesPerSubcategory`, `maxEntriesPerMainCategory`, `maxEntriesTotal`, `entryPrefix`, `meaderyNameRequired`
- `DivisionDetailView` — "Manage Entries" button, entry prefix + entry limits in Settings tab (DRAFT-only for limits), meaderyNameRequired checkbox (DRAFT-only)
- `MainLayout` — "My Profile" nav item for all authenticated users
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
│   └── 2026-03-10-deployment-design.md    ← Deployment options + config checklist (decision deferred)
├── reference/
│   └── chip-competition-rules.md          ← CHIP competition rules (active reference)
├── specs/
│   ├── _template.md                       ← Template for new module specs
│   ├── judging.md                         ← Preliminary spec (post-rework naming)
│   └── awards.md                          ← Preliminary spec (post-rework naming)
└── walkthrough/
    └── manual-test.md                  ← Living test plan (UI + API, must be updated with every change)
```

---

## What's Next

### Priority 1: Internationalization (i18n)
**Design complete** — see `docs/plans/2026-03-10-i18n-design.md`. Implementation deferred.
Summary: Vaadin I18NProvider + Spring MessageSource, resource bundles, browser locale +
UI switcher (cookie/localStorage), entrant-facing views only (6 views), MJP category
translations via bundles keyed by code. ~100-120 strings to extract. No DB changes needed.

### Priority 2: Manual walkthrough (full redo)
Redo the **entire** manual-test walkthrough from Section 1. Previous partial run covered
Sections 2–9; this is a fresh pass through all sections (1–13) to validate the current
state end-to-end, including recent entry limits UI, logging, and all accumulated changes.
May produce bug fixes or UX improvements.

### Priority 3: Deployment planning
**Investigation complete** — see `docs/plans/2026-03-10-deployment-design.md`. Decision deferred.
Summary: Evaluated Railway, DigitalOcean (App Platform, Droplet), AWS (Lightsail, EB+RDS).
Recommendation: DigitalOcean App Platform + Managed PostgreSQL (~$20/mo) — best balance of
cost, automatic backups (daily + PITR), and zero ops. Needs Dockerfile, DNS setup, email
provider (Resend), and prod config before deploying.

### Priority 4: Configuration audit
**Complete.** Properties reorganized: `application.properties` has only non-sensitive
environment-agnostic defaults. Secrets and env-specific values moved to `application-dev.properties`.
Created `application-prod.properties` (minimal). Test properties in `src/test/resources/application.properties`.
Deployment configuration checklist in `docs/plans/2026-03-10-deployment-design.md`.

### Priority 5: Email sending implementation
**Complete.** `spring-boot-starter-mail` + `spring-boot-starter-thymeleaf` for SMTP email
delivery with HTML templates. `EmailService` interface in identity module public API,
`SmtpEmailService` in `internal/`. Three email types: magic link, password reset, password
setup (with competition context + contact email). Thymeleaf HTML template (`email/email-base.html`)
with table-based layout, CTA button, fallback URL, conditional contact footer. SMTP failures
caught and logged with fallback link (no UI crash). Mailpit for dev, Resend SMTP for prod.
Competition `contactEmail` field in Settings tab, saved to DB, shown in password setup emails.
7-day token validity as private constant. DevUserInitializer unchanged (uses JwtMagicLinkService
directly). 7 unit tests for SmtpEmailService.

### Priority 6: Entry submission labels (PDF)
**Complete.** OpenPDF + ZXing for PDF label generation. `LabelPdfService` generates A4 landscape
PDFs with instruction header (shipping address + phone) and 3 identical labels per page (competition
name, division name, entry ID, mead name, category code, sweetness/strength/carbonation, ingredients,
QR code, notes area, disclaimer). Entrants: individual download for SUBMITTED entries + batch
"Download all labels" (direct). Admins: individual download for SUBMITTED/RECEIVED entries + batch
"Download all labels" with confirmation dialog. Competition entity gained `shippingAddress` (TEXT) and
`phoneNumber` (VARCHAR) fields. Settings tab in CompetitionDetailView has shipping address + phone fields.
Design: `docs/plans/2026-03-10-entry-labels-design.md`. Plan: `docs/plans/2026-03-10-entry-labels-plan.md`.

### Priority 7: Competition documents
**Complete.** `CompetitionDocument` entity (PDF upload or external link) with `DocumentType` enum.
Admin "Documents" tab in `CompetitionDetailView` for CRUD + reordering. Entrant document list
in `MyEntriesView`. PDF stored in DB (max 10 MB), links stored as URLs. Unique name per
competition, admin-managed display order. V14 migration. 26 new tests.

### After these priorities: Resume planned work
7. **Judging module** — design and implementation
8. **Awards module** — design and implementation

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
- **Token validity (7 days)** — private constant in `SmtpEmailService`, not mentioned in email body.
- **Competition `contactEmail`** — optional field, shown in password setup emails as reply-to
  and visible footer contact. Saved via `CompetitionService.updateCompetitionContactEmail()`.
- **DevUserInitializer unchanged** — uses `JwtMagicLinkService` directly (runs at startup before
  Mailpit is ready, uses 30-day tokens, dev-only).
- **`spring.thymeleaf.check-template-location=false`** — prevents Thymeleaf view resolver conflict
  with Vaadin (Thymeleaf used only for template rendering, not view resolution).
  MyEntriesView shows warning banner and blocks submit (all + individual) when required but missing.

### Known UX items (deferred)
- After failed credentials login, page reloads at `/login?error` and shows error notification,
  but password field is cleared (expected browser behavior for form POST). Not blocking.

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
- `WebhookServiceTest.java` — HMAC verification + processOrderPaid variants
- `LabelPdfServiceTest.java` — single/batch PDF generation, missing fields, QR code format, entry prefix handling
- `JumpsellerOrderTest.java` — entity domain methods
- `JumpsellerOrderLineItemTest.java` — entity domain methods
- `EntryTest.java` — entry entity domain methods (constructor, submit, markReceived, withdraw, updateDetails, assignFinalCategory, getEffectiveCategoryId)
- `RegistrationClosedListenerTest.java` — event listener unit tests
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
- `MyEntriesViewTest.java` — credits display, entry grid, authorization redirect, meadery name warning + submit blocking, download all labels button, download label for submitted entries, competition documents display
- `DivisionEntryAdminViewTest.java` — admin tabs rendering, meadery name + country columns, download all labels button

---

## Key Technical Notes

- Karibu TabSheet: content is lazy-loaded. Must call `tabSheet.setSelectedIndex(N)` before finding components
- Karibu component columns: buttons inside Grid `addComponentColumn` are not found by `_find(Button.class)`
- `Category` has only protected no-arg constructor — use `Mockito.mock()` in unit tests
- `Select.setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — must handle null
- Service constructors are package-private (convention)
- `@DirtiesContext` required on UI tests that modify security context strategy
- `EntryCredit` is append-only ledger — balance computed as `SUM(amount)` via JPQL
- `WebhookService` constructor takes `@Value("${app.jumpseller.hooks-token}")` — property must exist
- Mutual exclusivity: user cannot have credits in two different divisions of same competition
- `@WebMvcTest` doesn't work in this Vaadin project — use `MockMvcBuilders.standaloneSetup(controller)` with `@ExtendWith(MockitoExtension.class)` instead
- String-based `Anchor` navigation for cross-module links (avoids Spring Modulith circular dependencies)
- Comprehensive logging: `@Slf4j` on all services, controllers, filters, listeners, guards
