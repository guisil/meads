# Entry Submission Labels (PDF) — Design

## Overview

Generate printable A4 landscape PDFs with entry labels for mead competition submissions.
Each page has an instruction header (shipping info) and 3 identical labels for the same
entry. Supports individual entry download and batch download (all qualifying entries as
multi-page PDF). Generated on-the-fly, not stored.

## Data Model Changes

### Competition entity — new fields

| Field | Type | DB Column | Nullable | Description |
|-------|------|-----------|----------|-------------|
| `shippingAddress` | String (TEXT) | `shipping_address` | Yes | Free-text multi-line shipping address |
| `phoneNumber` | String (VARCHAR) | `phone_number` | Yes | Contact phone number |

**Migration:** Update existing V3 (pre-deployment) to add columns to `competitions` table.

**Competition entity:** Add fields + domain method `updateShippingDetails(shippingAddress, phoneNumber)`.

**CompetitionService:** Add `updateCompetitionShippingDetails()` method.

**Competition Settings tab (CompetitionDetailView):** Add `shippingAddress` (TextArea) and
`phoneNumber` (TextField) fields, saved on blur/change like existing settings fields.

## PDF Layout (A4 Landscape)

### Instruction Header (top of page)

> "Print the labels and cut along the lines. Attach one label to each bottle using
> elastic bands (do not use sticky tape). Post your bottles to: {shippingAddress},
> Tel. {phoneNumber}"

If shippingAddress or phoneNumber is blank, omit the corresponding part gracefully.

### Labels (3 identical, side-by-side below header)

Each label, top to bottom:

1. **Competition name** (bold)
2. **Division name** (large text)
3. ID: `{entryPrefix}-{entryNumber}` (e.g., HOME-123)
4. Name: `{meadName}`
5. Category: `{categoryCode}` (resolved from `initialCategoryId`)
6. `{sweetness} | {strength} | {carbonation}` (display names, single line)
7. Ingredients section (each on own line, with field names):
   - Honey: `{honeyVarieties}`
   - Other ingredients: `{otherIngredients}` (if present)
   - Wood ageing: `{woodAgeingDetails}` (if wood aged)
8. QR code — content: `{competitionShortName}-{entryPrefix}-{entryNumber}`
9. Empty area: "For official notes. Leave blank."
10. **"FREE SAMPLES. NOT FOR RE-SALE."** (bold)

Line separators between logical sections (exact placement during implementation).

## Service Layer

### LabelPdfService (entry module, public API)

```java
@Service
public class LabelPdfService {

    // Single entry — 1-page PDF with 3 identical labels
    byte[] generateLabel(Entry entry, Competition competition,
                         Division division, DivisionCategory category);

    // Batch — multi-page PDF (1 page per entry, 3 labels each)
    byte[] generateLabels(List<Entry> entries, Competition competition,
                          Division division, Function<UUID, DivisionCategory> categoryResolver);
}
```

Note: Uses `DivisionCategory` (not `Category`) since entries reference division-specific
categories. `DivisionCategory` has the `code` field needed for label display.

Stateless. On-the-fly generation. No storage. Uses OpenPDF for PDF, ZXing for QR codes.

### QR Code Content

Format: `{competitionShortName}-{entryPrefix}-{entryNumber}`

Example: `chip-2026-HOME-123`

## Download Mechanism

**Vaadin StreamResource** attached to Anchor components. No REST controller needed.
Downloads happen via Vaadin's internal streaming mechanism. No extra security config.

## View Integration

### MyEntriesView (entrants)

- **Per-entry:** Download link/icon on each SUBMITTED entry row
- **Batch:** "Download all labels" button — multi-page PDF for all SUBMITTED entries
  in the division. No confirmation dialog.

### DivisionEntryAdminView (admins)

- **Per-entry:** Download link/icon on each SUBMITTED or RECEIVED entry row
- **Batch:** "Download all labels" button with confirmation dialog
  ("This will generate labels for X entries. Continue?") — covers all SUBMITTED + RECEIVED
  entries. Always generates for all qualifying entries regardless of grid filters.

## Dependencies (Maven)

```xml
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
</dependency>
```

Versions managed via `<dependencyManagement>` or direct version tags in pom.xml.

## Entry Status Visibility

| View | DRAFT | SUBMITTED | RECEIVED | WITHDRAWN |
|------|-------|-----------|----------|-----------|
| MyEntriesView (entrant) | - | download | - | - |
| DivisionEntryAdminView (admin) | - | download | download | - |

## Not In Scope

- Customizable label templates or layout configuration
- Storing generated PDFs
- Filter-aware batch download (always all qualifying entries)
- Label generation for DRAFT or WITHDRAWN entries
