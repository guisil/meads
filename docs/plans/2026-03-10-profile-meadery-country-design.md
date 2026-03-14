# Design: User Profile Self-Edit, Meadery Name & Country

**Date:** 2026-03-10
**Status:** Approved
**Branch:** competition-module

---

## Overview

Add user profile self-edit capability, a country field on users, and a per-division
`meaderyNameRequired` flag that blocks entry submission when the entrant's meadery name
is not set. Opportunistically capture country from Jumpseller webhook payloads.

---

## Data Model Changes

### User entity (identity module)

- **Add `country`** — `VARCHAR(2)`, nullable, ISO 3166-1 alpha-2 code (e.g., "PT", "BR", "US")
- `meaderyName` already exists — no change
- Add domain method `updateCountry(String country)`
- Add `UserService.updateProfile(UUID userId, String name, String meaderyName, String country)`
  — single method for self-edit, validates ISO country code

### Division entity (competition module)

- **Add `meaderyNameRequired`** — `BOOLEAN NOT NULL DEFAULT FALSE`
- Editable in Division Settings tab (DRAFT-only, same restriction as entry limits)
- Exposed via `CompetitionService` for other modules to query

### JumpsellerOrder entity (entry module)

- **Add `customerCountry`** — `VARCHAR(2)`, nullable
- Extracted from `shipping_address.country_code` (fallback: `billing_address.country_code`)
- For audit/reference only

### Entry entity

- **No changes** — meadery name and country come from User profile at display time

### Flyway migrations (modify existing, pre-deployment)

| Migration | Change |
|-----------|--------|
| V2 | Add `country VARCHAR(2)` to `users` |
| V4 | Add `meadery_name_required BOOLEAN NOT NULL DEFAULT FALSE` to `divisions` |
| V10 | Add `customer_country VARCHAR(2)` to `jumpseller_orders` |

---

## Profile Self-Edit UI

### MainLayout

- Add "My Profile" menu item in navigation drawer, visible to all authenticated users
- Links to `/profile`

### ProfileView (new, identity/internal/)

- Route: `/profile`, `@PermitAll`, layout = `MainLayout`
- Fields:
  - **Email** — read-only display
  - **Name** — TextField (required)
  - **Meadery Name** — TextField (optional)
  - **Country** — ComboBox with ISO countries (optional), searchable/filterable
- "Save" button calls `UserService.updateProfile()`
- Success notification on save

### Country ComboBox

- Source: `java.util.Locale.getISOCountries()` for alpha-2 codes
- Display: `new Locale("", code).getDisplayCountry(Locale.ENGLISH)` (e.g., "Portugal")
- Sorted alphabetically by display name

### Admin UserListView

- Add meadery name and country fields to the existing edit dialog

---

## Meadery Name Enforcement

### Division Settings

- `DivisionDetailView` Settings tab: add `meaderyNameRequired` checkbox
- DRAFT-only (same restriction pattern as entry limits)

### MyEntriesView

- On `beforeEnter`, check if division has `meaderyNameRequired == true` and user's
  `meaderyName` is blank
- **Warning banner** at top of view: "This division requires a meadery name. Please
  update your profile before submitting entries." with link to `/profile`
- **"Submit All" button** disabled while meadery name is missing
- **Per-entry submit buttons** (check icon in actions column) disabled with tooltip:
  "Meadery name required — update your profile"
- Entry creation/editing remains unblocked (drafts are fine)

### DivisionEntryAdminView

- Entries tab grid: add meadery name + country columns (from User profile)

---

## Webhook Enrichment

### WebhookService

- Extract `shipping_address.country_code` from JSON payload (fallback:
  `billing_address.country_code`)
- Store as `customerCountry` on `JumpsellerOrder`
- When creating/finding a User: if user's `country` is null, set it from the webhook
  country code (opportunistic enrichment — never overwrite manual edits)

### Jumpseller limitations

- No structured company field in the Jumpseller payload — meadery name cannot be
  auto-populated from orders. Users must set it manually.

---

## What's NOT Changing

- **Entry.java** — no new fields
- **EntrantOverviewView** — no changes
- Country is display/statistics only — no eligibility enforcement
- Meadery name source is always the User profile (no per-entry override)
