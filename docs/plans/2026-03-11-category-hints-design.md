# Category Guidance Hints — Design

**Date:** 2026-03-11
**Status:** Approved
**Scope:** Lightweight — UI hints only, no locking/validation/schema changes

---

## Summary

Show brief guidance text below the category dropdown in the entry creation/edit dialog.
When an entrant selects a category, a hint appears describing what's expected for that
style. No field locking, no server-side validation — purely informational.

---

## Hint Mappings

All MJP subcategories get a hint. Main categories (M1–M4) are not selectable in the
dialog (filtered to subcategories only), so they need no mapping.

| Code | Hint Text |
|------|-----------|
| M1A | Traditional mead: only honey (and optionally wood). Expected sweetness: Dry. |
| M1B | Traditional mead: only honey (and optionally wood). Expected sweetness: Medium. |
| M1C | Traditional mead: only honey (and optionally wood). Expected sweetness: Sweet. |
| M2A | Pome fruit melomel: apples, pears, quince. |
| M2B | Pyment: made with grapes. |
| M2C | Berry melomel: berry fruits. |
| M2D | Stone fruit melomel: peaches, cherries, plums, etc. |
| M2E | Other melomel: other fruits or fruit combinations. |
| M3A | Fruit and spice mead: one or more fruits combined with one or more spices. |
| M3B | Metheglin: herbs, spices, or vegetables. |
| M3C | Other metheglin: coffee, chocolate, chili, nuts, or seeds. |
| M4A | Braggot: mead with malt / beer-style honey beverage. |
| M4B | Historical mead: made using historical methods or recipes. |
| M4C | Experimental mead: novel ingredients or processes. |
| M4D | Honey alcoholic beverage: distillates, tinctures, liqueurs. |
| M4E | Bochet: caramelized honey should be a significant character. May include other ingredients. |
| M4S | Session mead: ABV should be under 7.5%, strength should be Hydromel. |

Custom categories (no catalog link or unrecognized code): no hint shown.

---

## Implementation

### Changes

**Single file:** `MyEntriesView.java` (entry dialog)

1. Add a `Span` below the category `Select` in the dialog, initially hidden.
2. Add a value change listener on the category `Select`:
   - Look up the `DivisionCategory` code in the hint map.
   - If a hint exists, set text and show the `Span`.
   - If no hint, hide the `Span`.
3. Style with Lumo `text-secondary` + `text-s` for subtle appearance.
4. Private static helper method: `getCategoryHint(String code)` returning `Optional<String>`.

### What it doesn't do

- No field locking or disabling
- No server-side validation of category-characteristic consistency
- No admin configuration for custom category hints
- No migration, no service changes, no new entities

### Testing

One Karibu UI test in `MyEntriesViewTest`:
- Select M1A in dialog → verify hint text appears with expected content.
- Change to a non-hinted or different category → verify hint updates.

---

## Future: Full Category Constraint System (Low Priority)

A full-fledged implementation would lock/disable fields and enforce constraints server-side.
This is documented here for reference when planning a future competition that needs it.

### Constraint Types Identified

| Constraint | Example | Affected Field |
|------------|---------|----------------|
| Lock sweetness | M1A → DRY | Sweetness dropdown |
| Disable other ingredients | M1A/B/C → honey/wood only | Other Ingredients textarea |
| Lock strength | M4S → HYDROMEL | Strength dropdown |
| Cap ABV | M4S → max 7.5% | ABV number field |
| Derive strength from ABV | <7.5% → Hydromel, 7.5–14% → Standard, >14% → Sack | Strength dropdown (universal) |
| Lock carbonation | (no current MJP category, but possible for custom) | Carbonation dropdown |

### Design Considerations

**ABV → Strength derivation** is universal (applies to all entries, not category-specific).
Key decisions needed:
- Is Strength ever independent of ABV? If not, it becomes a derived display field, not an input.
- Are ABV thresholds fixed (MJP standard) or configurable per division?
- What happens when a category lock (M4S → HYDROMEL) and ABV derivation conflict?

**Custom categories** require admin-configurable constraints. Two approaches:

- **Option A: Metadata columns on `division_categories`** — `locked_sweetness`, `locked_strength`,
  `locked_carbonation`, `max_abv`, `ingredients_restricted`. Pros: simple schema. Cons: rigid,
  new column per constraint type.
- **Option B: Constraint rules table** — separate `category_constraints` table with type/value pairs.
  Pros: extensible. Cons: more complex queries and admin UI.

**Implementation scope for full version:**
- DB migration (new columns or table)
- Admin UI for constraint configuration in DivisionDetailView category editor
- Cross-module data flow: Entry module reads constraints from Competition module
- Entrant UI: dynamic field locking/unlocking with value change listeners
- Server-side validation in EntryService (can't trust UI alone)
- Comprehensive testing: unit, repository, UI tests for all constraint combinations

**Recommendation:** Only pursue this if competition organizers report frequent data quality
issues from category-characteristic mismatches. The admin `finalCategoryId` override is the
existing safety net.
