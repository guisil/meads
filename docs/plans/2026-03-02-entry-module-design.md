# Entry Module — Design Document

**Date:** 2026-03-02
**Branch:** `competition-module`
**Status:** Design complete, awaiting implementation

---

## Problem

The application has the `identity` and `competition` modules. The next module is `entry`, which
handles:
- Webhook processing from Jumpseller (external shop) when products are purchased
- Entry credits (1 credit = 1 mead entry)
- Mead entry registration by entrants
- Admin management of credits, entries, product mappings, and flagged orders

---

## Requirements

### Webhook Flow (Jumpseller → MEADS)

1. Jumpseller sends **Order Paid** POST with HMAC-SHA256 signature
2. Store the raw order + individual line items in DB (idempotent via order ID)
3. Map each line item's product to a competition via **admin-managed mapping table**
4. **Mutual exclusivity check** (per event): if user already has credits for a different
   competition in the same event, process the valid credits and flag the conflicting ones
   as NEEDS_REVIEW
5. If no user exists for the email, create a PENDING user
6. Create credits for valid line items, mark order lines as PROCESSED
7. Send magic link to entrant (deferred — log for now)
8. Log notification for admin on invalid/flagged orders (email deferred)

Non-mapped products (t-shirts, conference tickets, etc.) are silently ignored — only products
with a mapping in the `product_mappings` table are stored alongside the order.

### Credit Model

- 1 credit = 1 entry, always
- Stored as append-only ledger per user per competition (balance = SUM of amounts)
- Admin can add, remove, or view credit balances
- No automatic entry-to-credit conversion; admin withdraws entry + manually adds credit

### Mutual Exclusivity

A user cannot register entries in both competitions of the same event (e.g., PRO and HOME are
mutually exclusive). Checked at credit time — process valid credits first, flag conflicting
ones as NEEDS_REVIEW.

### Entry Data Model

- **Entry number**: sequential per competition (1, 2, 3...)
- **Entry code**: 6-char random alphanumeric (for judges, opaque)
- **Fields**: mead name, initial category/subcategory, sweetness (enum), strength (enum),
  ABV (decimal %), carbonation (enum), honey varieties (text), other ingredients (text),
  wood aged (boolean + text details), additional information (text)
- **Final category/subcategory**: separate fields, set by admin later (initially null)
- **Meadery name**: on User entity (optional, for PRO entrants, entered once)

### Entry Workflow

- Entrant adds entries one by one (all required fields filled = saved as DRAFT)
- Drafts can be edited/deleted
- **Batch submit**: "Submit All" converts all DRAFTs to SUBMITTED
- After submission: read-only for entrant, visible through to results
- **Statuses**: DRAFT → SUBMITTED → RECEIVED → (judging statuses later) + WITHDRAWN (admin-set)
- Unsubmitted drafts discarded when registration closes (deferred — listener skeleton in place)

### Entrant View

- Route: `/competitions/:competitionId/my-entries`
- After magic link login, land directly here
- Shows: credit balance, list of entries with status, "Add Entry" button (if credits remaining)
- Meadery name field for PRO competitions
- After submission: read-only list with statuses

### Admin Views

- **Credit management**: grid of entrants (email, credit count, entry count), add/remove credits
- **Entry management**: view all entries, modify details, change status
- **Product mapping**: map Jumpseller product IDs/SKUs to this competition
- **Invalid orders**: flagged order lines needing review
- Both SYSTEM_ADMIN and COMPETITION_ADMIN can manage

### Module Boundaries

- `entry` module, `allowedDependencies = {"competition", "identity"}`
- Uses `CompetitionService` for competitions, categories, participants
- Uses `UserService` and `JwtMagicLinkService` for user creation and magic links
- Dependency direction: `entry → competition → identity → root`

---

## Package Layout

```
app.meads.entry                                ← Module root (public API)
├── package-info.java                          ← @ApplicationModule(allowedDependencies = {"competition", "identity"})
├── ProductMapping.java                        ← JPA entity (product-to-competition mapping)
├── JumpsellerOrder.java                       ← JPA entity (webhook order storage, idempotency)
├── JumpsellerOrderLineItem.java               ← JPA entity (individual line items)
├── EntryCredit.java                           ← JPA entity (credits per user per competition)
├── Entry.java                                 ← JPA entity / aggregate root
├── EntryStatus.java                           ← Enum: DRAFT, SUBMITTED, RECEIVED, WITHDRAWN
├── Sweetness.java                             ← Enum: DRY, MEDIUM, SWEET
├── Strength.java                              ← Enum: HYDROMEL, STANDARD, SACK
├── Carbonation.java                           ← Enum: STILL, PETILLANT, SPARKLING
├── OrderStatus.java                           ← Enum: PROCESSED, PARTIALLY_PROCESSED, NEEDS_REVIEW, UNPROCESSED
├── LineItemStatus.java                        ← Enum: PROCESSED, NEEDS_REVIEW, IGNORED, UNPROCESSED
├── EntryService.java                          ← Application service (public API)
├── WebhookService.java                        ← Application service (webhook processing, public API)
├── CreditsAwardedEvent.java                   ← Spring application event (record)
├── EntriesSubmittedEvent.java                 ← Spring application event (record)
└── internal/                                  ← Module-private
    ├── ProductMappingRepository.java          ← JpaRepository
    ├── JumpsellerOrderRepository.java         ← JpaRepository
    ├── JumpsellerOrderLineItemRepository.java ← JpaRepository
    ├── EntryCreditRepository.java             ← JpaRepository
    ├── EntryRepository.java                   ← JpaRepository
    ├── JumpsellerWebhookController.java       ← @RestController (REST endpoint)
    ├── MyEntriesView.java                     ← Entrant-facing view
    └── CompetitionEntryAdminView.java         ← Admin view (separate route, not CompetitionDetailView tab)
```

---

## Entities

### ProductMapping

**Table:** `product_mappings`

| Field | Type | DB Column | Constraints | Notes |
|-------|------|-----------|-------------|-------|
| `id` | `UUID` | `id` | PK, self-generated | |
| `competitionId` | `UUID` | `competition_id` | NOT NULL, FK → competitions | |
| `jumpsellerProductId` | `String` | `jumpseller_product_id` | NOT NULL | Jumpseller's product ID |
| `jumpsellerSku` | `String` | `jumpseller_sku` | nullable | Optional SKU for display |
| `productName` | `String` | `product_name` | NOT NULL | Human-readable name |
| `creditsPerUnit` | `int` | `credits_per_unit` | NOT NULL, default 1 | Credits per quantity unit |
| `createdAt` | `Instant` | `created_at` | NOT NULL, @PrePersist | |

**Unique constraint:** `(competition_id, jumpseller_product_id)`

**Domain methods:**
- Constructor: `ProductMapping(UUID competitionId, String jumpsellerProductId, String jumpsellerSku, String productName, int creditsPerUnit)`
- `updateDetails(String productName, int creditsPerUnit)`

### JumpsellerOrder

**Table:** `jumpseller_orders`

| Field | Type | DB Column | Constraints | Notes |
|-------|------|-----------|-------------|-------|
| `id` | `UUID` | `id` | PK, self-generated | |
| `jumpsellerOrderId` | `String` | `jumpseller_order_id` | NOT NULL, UNIQUE | Idempotency key |
| `customerEmail` | `String` | `customer_email` | NOT NULL | |
| `customerName` | `String` | `customer_name` | NOT NULL | |
| `rawPayload` | `String` | `raw_payload` | NOT NULL, TEXT | Full JSON for debugging |
| `status` | `OrderStatus` | `status` | NOT NULL, STRING | |
| `adminNote` | `String` | `admin_note` | nullable, TEXT | Admin notes on flagged orders |
| `createdAt` | `Instant` | `created_at` | NOT NULL, @PrePersist | |
| `processedAt` | `Instant` | `processed_at` | nullable | When processing completed |

**Domain methods:**
- Constructor: `JumpsellerOrder(String jumpsellerOrderId, String customerEmail, String customerName, String rawPayload)` — sets status = UNPROCESSED
- `markProcessed()` — sets status = PROCESSED, processedAt = now
- `markPartiallyProcessed()` — sets status = PARTIALLY_PROCESSED, processedAt = now
- `markNeedsReview()` — sets status = NEEDS_REVIEW
- `addAdminNote(String note)`

### JumpsellerOrderLineItem

**Table:** `jumpseller_order_line_items`

| Field | Type | DB Column | Constraints | Notes |
|-------|------|-----------|-------------|-------|
| `id` | `UUID` | `id` | PK, self-generated | |
| `orderId` | `UUID` | `order_id` | NOT NULL, FK → jumpseller_orders | |
| `jumpsellerProductId` | `String` | `jumpseller_product_id` | NOT NULL | |
| `jumpsellerSku` | `String` | `jumpseller_sku` | nullable | |
| `productName` | `String` | `product_name` | NOT NULL | |
| `quantity` | `int` | `quantity` | NOT NULL | |
| `status` | `LineItemStatus` | `status` | NOT NULL, STRING | |
| `competitionId` | `UUID` | `competition_id` | nullable, FK → competitions | Set when mapped |
| `creditsAwarded` | `int` | `credits_awarded` | NOT NULL, default 0 | |
| `reviewReason` | `String` | `review_reason` | nullable | Why it needs review |
| `createdAt` | `Instant` | `created_at` | NOT NULL, @PrePersist | |

**Domain methods:**
- Constructor: `JumpsellerOrderLineItem(UUID orderId, String jumpsellerProductId, String jumpsellerSku, String productName, int quantity)` — sets status = UNPROCESSED, creditsAwarded = 0
- `markProcessed(UUID competitionId, int creditsAwarded)`
- `markNeedsReview(String reason)`
- `markIgnored()` — non-mapped product

### EntryCredit

**Table:** `entry_credits`

**Design:** Append-only ledger. Balance = `SUM(amount)` grouped by `(competition_id, user_id)`.
No unique constraint on `(competition_id, user_id)` — multiple records per pair.

| Field | Type | DB Column | Constraints | Notes |
|-------|------|-----------|-------------|-------|
| `id` | `UUID` | `id` | PK, self-generated | |
| `competitionId` | `UUID` | `competition_id` | NOT NULL, FK → competitions | |
| `userId` | `UUID` | `user_id` | NOT NULL, FK → users | |
| `amount` | `int` | `amount` | NOT NULL | Can be negative for removals |
| `sourceType` | `String` | `source_type` | NOT NULL | "WEBHOOK" or "ADMIN" |
| `sourceReference` | `String` | `source_reference` | nullable | Order line item ID or admin email |
| `createdAt` | `Instant` | `created_at` | NOT NULL, @PrePersist | |

**Rationale:** Append-only ledger for audit trail, race condition avoidance, and no history
loss on adjustments.

**Domain methods:**
- Constructor: `EntryCredit(UUID competitionId, UUID userId, int amount, String sourceType, String sourceReference)` — validates amount != 0

### Entry

**Table:** `entries`

| Field | Type | DB Column | Constraints | Notes |
|-------|------|-----------|-------------|-------|
| `id` | `UUID` | `id` | PK, self-generated | |
| `competitionId` | `UUID` | `competition_id` | NOT NULL, FK → competitions | |
| `userId` | `UUID` | `user_id` | NOT NULL, FK → users | |
| `entryNumber` | `int` | `entry_number` | NOT NULL | Sequential per competition |
| `entryCode` | `String` | `entry_code` | NOT NULL, CHAR(6) | Random alphanumeric for judges |
| `meadName` | `String` | `mead_name` | NOT NULL | |
| `initialCategoryId` | `UUID` | `initial_category_id` | NOT NULL, FK → competition_categories | |
| `finalCategoryId` | `UUID` | `final_category_id` | nullable, FK → competition_categories | Set by admin |
| `sweetness` | `Sweetness` | `sweetness` | NOT NULL, STRING | |
| `strength` | `Strength` | `strength` | NOT NULL, STRING | |
| `abv` | `BigDecimal` | `abv` | NOT NULL, DECIMAL(4,1) | e.g., 12.5 |
| `carbonation` | `Carbonation` | `carbonation` | NOT NULL, STRING | |
| `honeyVarieties` | `String` | `honey_varieties` | NOT NULL, TEXT | |
| `otherIngredients` | `String` | `other_ingredients` | nullable, TEXT | |
| `woodAged` | `boolean` | `wood_aged` | NOT NULL | |
| `woodAgeingDetails` | `String` | `wood_ageing_details` | nullable, TEXT | Only if woodAged |
| `additionalInformation` | `String` | `additional_information` | nullable, TEXT | |
| `status` | `EntryStatus` | `status` | NOT NULL, STRING | |
| `createdAt` | `Instant` | `created_at` | NOT NULL, @PrePersist | |
| `updatedAt` | `Instant` | `updated_at` | nullable, @PreUpdate | |

**Unique constraints:** `(competition_id, entry_number)`, `(competition_id, entry_code)`

**Entry code:** 6-char, uppercase, character set `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`
(same as access codes — excludes 0, O, 1, I to avoid confusion).

**Entry number:** `MAX(entry_number) + 1` per competition, assigned in a transaction.

**Domain methods:**
- Constructor with all required fields — sets status = DRAFT, validates woodAged/woodAgeingDetails consistency
- `updateDetails(...)` — only allowed when status == DRAFT
- `submit()` — DRAFT → SUBMITTED
- `markReceived()` — SUBMITTED → RECEIVED
- `withdraw()` — any active status → WITHDRAWN
- `assignFinalCategory(UUID finalCategoryId)` — allowed in any non-WITHDRAWN status
- `getEffectiveCategoryId()` — returns finalCategoryId if set, otherwise initialCategoryId

---

## Enums

### EntryStatus

```java
@Getter
@RequiredArgsConstructor
public enum EntryStatus {
    DRAFT("Draft", "badge-draft"),
    SUBMITTED("Submitted", "badge-submitted"),
    RECEIVED("Received", "badge-received"),
    WITHDRAWN("Withdrawn", "badge-withdrawn");

    private final String displayName;
    private final String badgeCssClass;
}
```

**Transitions:**
- DRAFT → SUBMITTED (entrant batch submit)
- SUBMITTED → RECEIVED (admin marks receipt)
- {DRAFT, SUBMITTED, RECEIVED} → WITHDRAWN (admin only)

### Sweetness

```java
@Getter @RequiredArgsConstructor
public enum Sweetness {
    DRY("Dry"), MEDIUM("Medium"), SWEET("Sweet");
    private final String displayName;
}
```

### Strength

```java
@Getter @RequiredArgsConstructor
public enum Strength {
    HYDROMEL("Hydromel"), STANDARD("Standard"), SACK("Sack");
    private final String displayName;
}
```

### Carbonation

```java
@Getter @RequiredArgsConstructor
public enum Carbonation {
    STILL("Still"), PETILLANT("Petillant"), SPARKLING("Sparkling");
    private final String displayName;
}
```

### OrderStatus

```java
public enum OrderStatus {
    PROCESSED, PARTIALLY_PROCESSED, NEEDS_REVIEW, UNPROCESSED
}
```

### LineItemStatus

```java
public enum LineItemStatus {
    PROCESSED, NEEDS_REVIEW, IGNORED, UNPROCESSED
}
```

---

## Database Migrations (V9–V14)

### V9 — `V9__create_product_mappings_table.sql`

```sql
CREATE TABLE product_mappings (
    id              UUID            PRIMARY KEY,
    competition_id  UUID            NOT NULL REFERENCES competitions(id),
    jumpseller_product_id VARCHAR(255) NOT NULL,
    jumpseller_sku  VARCHAR(255),
    product_name    VARCHAR(255)    NOT NULL,
    credits_per_unit INT            NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_product_mappings_competition_product
        UNIQUE (competition_id, jumpseller_product_id)
);

CREATE INDEX idx_product_mappings_competition_id ON product_mappings(competition_id);
CREATE INDEX idx_product_mappings_jumpseller_product_id ON product_mappings(jumpseller_product_id);
```

### V10 — `V10__create_jumpseller_orders_table.sql`

```sql
CREATE TABLE jumpseller_orders (
    id                  UUID            PRIMARY KEY,
    jumpseller_order_id VARCHAR(255)    NOT NULL UNIQUE,
    customer_email      VARCHAR(255)    NOT NULL,
    customer_name       VARCHAR(255)    NOT NULL,
    raw_payload         TEXT            NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    admin_note          TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_jumpseller_orders_status ON jumpseller_orders(status);
```

### V11 — `V11__create_jumpseller_order_line_items_table.sql`

```sql
CREATE TABLE jumpseller_order_line_items (
    id                      UUID            PRIMARY KEY,
    order_id                UUID            NOT NULL REFERENCES jumpseller_orders(id),
    jumpseller_product_id   VARCHAR(255)    NOT NULL,
    jumpseller_sku          VARCHAR(255),
    product_name            VARCHAR(255)    NOT NULL,
    quantity                INT             NOT NULL,
    status                  VARCHAR(50)     NOT NULL,
    competition_id          UUID            REFERENCES competitions(id),
    credits_awarded         INT             NOT NULL DEFAULT 0,
    review_reason           TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_jumpseller_order_line_items_order_id ON jumpseller_order_line_items(order_id);
```

### V12 — `V12__create_entry_credits_table.sql`

```sql
CREATE TABLE entry_credits (
    id                UUID            PRIMARY KEY,
    competition_id    UUID            NOT NULL REFERENCES competitions(id),
    user_id           UUID            NOT NULL REFERENCES users(id),
    amount            INT             NOT NULL,
    source_type       VARCHAR(50)     NOT NULL,
    source_reference  VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_entry_credits_competition_user ON entry_credits(competition_id, user_id);
```

### V13 — `V13__create_entries_table.sql`

```sql
CREATE TABLE entries (
    id                      UUID            PRIMARY KEY,
    competition_id          UUID            NOT NULL REFERENCES competitions(id),
    user_id                 UUID            NOT NULL REFERENCES users(id),
    entry_number            INT             NOT NULL,
    entry_code              CHAR(6)         NOT NULL,
    mead_name               VARCHAR(255)    NOT NULL,
    initial_category_id     UUID            NOT NULL REFERENCES competition_categories(id),
    final_category_id       UUID            REFERENCES competition_categories(id),
    sweetness               VARCHAR(50)     NOT NULL,
    strength                VARCHAR(50)     NOT NULL,
    abv                     DECIMAL(4,1)    NOT NULL,
    carbonation             VARCHAR(50)     NOT NULL,
    honey_varieties         TEXT            NOT NULL,
    other_ingredients       TEXT,
    wood_aged               BOOLEAN         NOT NULL,
    wood_ageing_details     TEXT,
    additional_information  TEXT,
    status                  VARCHAR(50)     NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_entries_competition_number ON entries(competition_id, entry_number);
CREATE UNIQUE INDEX uq_entries_competition_code ON entries(competition_id, entry_code);
CREATE INDEX idx_entries_competition_user ON entries(competition_id, user_id);
```

### V14 — `V14__add_meadery_name_to_users.sql`

```sql
ALTER TABLE users ADD COLUMN meadery_name VARCHAR(255);
```

---

## Services

### EntryService

`@Service @Transactional @Validated` — public API in `app.meads.entry`

**Product Mappings:**
- `createProductMapping(competitionId, jumpsellerProductId, jumpsellerSku, productName, creditsPerUnit, requestingUserId)` — validates authorization, rejects duplicates
- `updateProductMapping(mappingId, productName, creditsPerUnit, requestingUserId)`
- `removeProductMapping(mappingId, requestingUserId)`
- `findProductMappings(competitionId)`
- `findProductMappingsByProductId(jumpsellerProductId)`

**Credits:**
- `getCreditBalance(competitionId, userId)` — SUM(amount) query
- `addCredits(competitionId, userEmail, amount, requestingUserId)` — validates authorization,
  mutual exclusivity, adds ENTRANT participant if needed, publishes CreditsAwardedEvent
- `removeCredits(competitionId, userId, amount, requestingUserId)` — creates negative record,
  validates balance >= 0
- `findEntrantCreditSummaries(competitionId)` — returns list of
  `EntrantCreditSummary(userId, email, name, creditBalance, entryCount)`
- `hasCreditsInOtherCompetition(eventId, competitionId, userId)` — mutual exclusivity check

**Entries:**
- `createEntry(competitionId, userId, meadName, initialCategoryId, sweetness, strength, abv, carbonation, honeyVarieties, otherIngredients, woodAged, woodAgeingDetails, additionalInformation)` — validates credits > active entries, competition REGISTRATION_OPEN, generates entry_number and entry_code
- `updateEntry(entryId, userId, ...)` — only owning user, only DRAFT
- `deleteEntry(entryId, userId)` — only DRAFT, frees credit
- `submitAllDrafts(competitionId, userId)` — batch DRAFT → SUBMITTED, publishes EntriesSubmittedEvent
- `markReceived(entryId, requestingUserId)` — admin only
- `withdrawEntry(entryId, requestingUserId)` — admin only
- `adminUpdateEntry(entryId, ..., requestingUserId)` — can modify any field, any non-WITHDRAWN status
- `findEntriesByCompetition(competitionId)`
- `findEntriesByCompetitionAndUser(competitionId, userId)`
- `findEntryById(entryId)`
- `countActiveEntries(competitionId, userId)` — non-WITHDRAWN

**DTOs:**
- `EntrantCreditSummary` — `record EntrantCreditSummary(UUID userId, String email, String name, int creditBalance, long entryCount)`

### WebhookService

`@Service @Transactional @Validated` — public API in `app.meads.entry`

Separated from EntryService because webhook processing has distinct concerns (HMAC, payload
parsing, idempotency, Jumpseller-specific logic).

- `verifySignature(payload, signature)` — HMAC-SHA256 with `app.jumpseller.hooks-token` property
- `processOrderPaid(rawPayload)` — parse, check idempotency, for each line item: look up
  mapping → IGNORED if none, check exclusivity → create credits or NEEDS_REVIEW, determine
  order status, create user if not exists, add ENTRANT participant
- `findOrders(status)` — optionally filtered by status
- `findLineItems(orderId)`
- `resolveLineItem(lineItemId, competitionId, requestingUserId)` — admin resolves flagged item

---

## REST Endpoint

### JumpsellerWebhookController

`app.meads.entry.internal` — `@RestController @RequestMapping("/api/webhooks/jumpseller")`

```java
@PostMapping("/order-paid")
public ResponseEntity<Void> handleOrderPaid(
        @RequestHeader("Jumpseller-Hmac-Sha256") String signature,
        @RequestBody String rawPayload) {

    if (!webhookService.verifySignature(rawPayload, signature)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    webhookService.processOrderPaid(rawPayload);
    return ResponseEntity.ok().build();
}
```

**Key behavior:**
- Returns 401 if signature invalid
- Returns 200 always after signature is valid (even on internal errors — prevents Jumpseller
  retries for business logic failures)
- Internal errors captured in order/line item statuses

**Security:** Endpoint must be excluded from Vaadin security filter — requires change to
`SecurityConfig` to add `.requestMatchers("/api/webhooks/**").permitAll()`.

---

## Views

### MyEntriesView

**Route:** `/competitions/:competitionId/my-entries`
**Access:** `@PermitAll` + `beforeEnter` — verify current user is ENTRANT for this competition,
competition status >= REGISTRATION_OPEN

**Layout:**
1. **Header:** Competition name, credit balance ("Credits: X remaining (Y total, Z used)"),
   meadery name TextField for PRO competitions
2. **Entries Grid:** columns — Entry #, Mead Name, Category, Status, Actions (Edit/Delete for
   DRAFT only)
3. **Action buttons:** "Add Entry" (when credits > active entries and REGISTRATION_OPEN),
   "Submit All" (when at least one DRAFT exists)

**Add/Edit Entry Dialog:**
- meadName (TextField)
- initialCategoryId (Select — competition categories)
- sweetness (Select — enum values)
- strength (Select — enum values)
- abv (NumberField, step 0.1, suffix %)
- carbonation (Select — enum values)
- honeyVarieties (TextArea)
- otherIngredients (TextArea)
- woodAged (Checkbox)
- woodAgeingDetails (TextArea, visible only when woodAged checked)
- additionalInformation (TextArea)

**Submit All confirmation:** "Submit X entries? This cannot be undone."

### CompetitionEntryAdminView

**Route:** `/competitions/:competitionId/entry-admin`
**Access:** `@PermitAll` + `beforeEnter` — verify COMPETITION_ADMIN or SYSTEM_ADMIN

**Why separate view:** Spring Modulith forbids circular dependencies. Adding tabs to
`CompetitionDetailView` (competition module) from the entry module would create a circular
dependency. Instead, `CompetitionDetailView` adds a string-based navigation link
("Manage Entries") — no import from entry module required.

**Layout:** Breadcrumb, back link, TabSheet with 4 tabs:

**Tab 1 — Credits:**
- Grid: Email, Name, Credit Balance, Entry Count, Actions
- "Add Credits" button → dialog (email, amount)
- "Remove Credits" per row

**Tab 2 — Entries:**
- Grid: Entry #, Entry Code, Mead Name, Category, Entrant, Status, Actions
- View/Edit dialog (all fields editable for admin)
- Mark Received, Withdraw, Assign Final Category actions

**Tab 3 — Products:**
- Grid: Product ID, SKU, Product Name, Credits Per Unit, Actions
- "Add Mapping" button → dialog
- Edit/Remove per row

**Tab 4 — Orders:**
- Grid: Order ID, Customer, Date, Status, Items
- Filter: NEEDS_REVIEW / PARTIALLY_PROCESSED
- Expandable rows with line items
- "Resolve" action on flagged items

---

## Spring Application Events

### CreditsAwardedEvent

```java
public record CreditsAwardedEvent(UUID competitionId, UUID userId, int amount, String source) {}
```

Published by `addCredits()` and `processOrderPaid()`. No listeners yet — future: notification
email to entrant.

### EntriesSubmittedEvent

```java
public record EntriesSubmittedEvent(UUID competitionId, UUID userId, int entryCount) {}
```

Published by `submitAllDrafts()`. Future: confirmation email.

### CompetitionStatusAdvancedEvent listener

Entry module listens for REGISTRATION_CLOSED — future: discard unsubmitted drafts, send
notifications. Skeleton listener in place.

---

## Changes to Existing Modules

### Identity Module

- **`User.java`:** Add `meaderyName` field (optional) + `updateMeaderyName(String)` domain method
- **`UserService.java`:** Add `updateMeaderyName(UUID userId, String meaderyName)` method
- **`SecurityConfig.java`:** Add `.requestMatchers("/api/webhooks/**").permitAll()` before Vaadin
  security configuration
- **`application.properties`:** Add `app.jumpseller.hooks-token=your-jumpseller-hooks-token-here`

### Competition Module

- **`CompetitionDetailView.java`:** Add "Manage Entries" button/link using string-based
  navigation (`UI.getCurrent().navigate("competitions/" + competitionId + "/entry-admin")`) —
  no import from entry module
- **`CompetitionService.java`:** Add `findCompetitionsByEvent(UUID eventId)` method (unfiltered
  list for mutual exclusivity checks by the entry module)

---

## Design Decisions

### 1. Credit Ledger vs Balance Field

Append-only ledger for audit trail, race condition avoidance, and no history loss on
adjustments. Balance computed as `SUM(amount)` grouped by `(competition_id, user_id)`.

### 2. Mutual Exclusivity at Credit Time

Catches conflicts early (at purchase, not at entry creation), allows admin resolution before
entrant sees issues, aligns with the business rule that a user cannot compete in both
competitions of the same event.

### 3. Webhook Returns 200 After Signature Verification

Always returns 200 to prevent Jumpseller retries on business logic failures. Errors are
captured in order/line item statuses for admin review.

### 4. Separate WebhookService and EntryService

Different concerns (external payload parsing vs entry management), independently testable,
different authorization requirements (webhook is anonymous, entry service requires authentication).

### 5. Entry Admin as Separate View

Spring Modulith forbids circular dependencies. The entry module depends on competition, so
competition cannot depend back on entry. `CompetitionDetailView` uses string-based navigation
to link to the entry admin view without importing any entry module classes.

### 6. Entry Code vs Entry Number

`entryNumber` (sequential, per competition) for logistics and tracking. `entryCode` (random
6-char alphanumeric) for blind judging — judges see only the code, never the entrant name.

---

## Implementation Sequence (TDD Order)

Each numbered phase contains multiple RED-GREEN-REFACTOR cycles.

### Phase 0 — Module Skeleton
- `package-info.java` with `@ApplicationModule(allowedDependencies = {"competition", "identity"})`
- `ModulithStructureTest` passes
- `EntryModuleTest` bootstrap (`@ApplicationModuleTest`)

### Phase 1 — Enums
- All 6 enums (fast cycle, created alongside first test that needs them)

### Phase 2 — Product Mapping (4 cycles)
1. Unit test: create product mapping, reject duplicate
2. Unit test: update product mapping details
3. Unit test: find product mappings by competition
4. Repository test → drives V9 migration

### Phase 3 — Webhook Order Storage (4 cycles)
1. Unit test: JumpsellerOrder domain methods (markProcessed, markNeedsReview, etc.)
2. Unit test: JumpsellerOrderLineItem domain methods
3. Repository test for JumpsellerOrder → drives V10 migration
4. Repository test for JumpsellerOrderLineItem → drives V11 migration

### Phase 4 — Credits (14 cycles)
1. Unit test: HMAC signature verification
2. Unit test: processOrderPaid — valid order, single competition
3. Unit test: processOrderPaid — ignored (non-mapped) products
4. Unit test: processOrderPaid — mutual exclusivity (valid + flagged)
5. Unit test: processOrderPaid — user creation for unknown email
6. Unit test: processOrderPaid — all items invalid
7. Unit test: processOrderPaid — idempotency (duplicate order ID)
8. Repository test for EntryCredit → drives V12 migration
9. Unit test: getCreditBalance
10. Unit test: addCredits — manual, with mutual exclusivity check
11. Unit test: addCredits — creates ENTRANT participant if needed
12. Unit test: removeCredits — validates balance >= 0
13. Unit test: findEntrantCreditSummaries
14. Unit test: hasCreditsInOtherCompetition

### Phase 5 — Entry Entity (9 cycles)
1. Unit test: create entry (constructor, DRAFT status)
2. Unit test: submit() — DRAFT → SUBMITTED
3. Unit test: submit() rejects non-DRAFT
4. Unit test: markReceived() — SUBMITTED → RECEIVED
5. Unit test: withdraw() from various statuses
6. Unit test: updateDetails() — only DRAFT
7. Unit test: assignFinalCategory()
8. Unit test: getEffectiveCategoryId()
9. Repository test → drives V13 migration

### Phase 6 — Entry Service (14 cycles)
1. Unit test: createEntry — validates credits > active entries
2. Unit test: createEntry — rejects if competition not REGISTRATION_OPEN
3. Unit test: createEntry — sequential entry number
4. Unit test: createEntry — unique entry code
5. Unit test: updateEntry — only owning user, only DRAFT
6. Unit test: deleteEntry — only DRAFT
7. Unit test: submitAllDrafts — batch submit + event publication
8. Unit test: submitAllDrafts — no drafts = no-op
9. Unit test: markReceived — admin only
10. Unit test: withdrawEntry — admin only
11. Unit test: adminUpdateEntry — any non-WITHDRAWN
12. Unit test: findEntriesByCompetitionAndUser
13. Unit test: countActiveEntries
14. Unit test: deleteEntry frees credit slot

### Phase 7 — User.meaderyName (3 cycles)
1. Unit test: User.updateMeaderyName() domain method
2. Unit test: UserService.updateMeaderyName()
3. Repository test → drives V14 migration

### Phase 8 — Webhook REST Controller (2 cycles)
1. MockMvc test: valid signature → 200
2. MockMvc test: invalid signature → 401
3. SecurityConfig change (permitAll for /api/webhooks/**)

### Phase 9 — Module Integration Test (1 cycle)
- Full context, real DB, credit → entry workflow

### Phase 10 — Event Listener (1 cycle)
- Async test for CompetitionStatusAdvancedEvent (REGISTRATION_CLOSED) — skeleton listener

### Phase 11 — Views (4 cycles)
1. Karibu test: MyEntriesView — renders, shows credits, entry grid, add entry dialog
2. Karibu test: CompetitionEntryAdminView — tabs render, credit/entry/product/order grids
3. Karibu test: "Manage Entries" link in CompetitionDetailView
4. Karibu test: "My Entries" navigation for entrant role

---

## Open Questions / Deferred Items

| Item | Status |
|------|--------|
| Magic link email to entrants after credit assignment | Deferred — log for now |
| Notification for lingering drafts near registration close | Deferred |
| Automatic discard of unsubmitted drafts when registration closes | Deferred — listener skeleton |
| PRO competition detection (show meadery name field) | Implementation detail |
| Per-competition entry limits (max entries per entrant) | Not in requirements |
| Webhook retry handling beyond idempotency | Not needed |
| Entry credit refunds | Admin removes credits manually |
| Per-category custom fields (e.g., "malt used" for braggot) | Future — TODO |
