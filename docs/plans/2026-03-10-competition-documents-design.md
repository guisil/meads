# Competition Documents — Design

## Summary

Allow competition admins to attach multiple named documents to a competition, viewable
by authenticated users. Two document types: **PDF upload** (stored in DB) and **external link**.
Documents are ordered by admin-controlled display order.

---

## Data Model

New entity `CompetitionDocument` in the `competition` module (public API).

| Field | Type | DB Column | Description |
|-------|------|-----------|-------------|
| `id` | UUID | PK | Self-generated |
| `competitionId` | UUID | FK → competitions | Owning competition |
| `name` | String | NOT NULL | Display name (e.g., "Competition Rules") |
| `type` | DocumentType | NOT NULL | `PDF` or `LINK` |
| `data` | byte[] | LAZY, nullable | PDF bytes (null for LINK) |
| `contentType` | String | nullable | MIME type, e.g., `application/pdf` (null for LINK) |
| `url` | String | nullable | External URL (null for PDF) |
| `displayOrder` | int | NOT NULL | Position in list (0-based) |
| `createdAt` | Instant | NOT NULL | Auto-set @PrePersist |
| `updatedAt` | Instant | nullable | Auto-set @PreUpdate |

Constraints:
- Unique: `(competition_id, name)`
- Max PDF size: 10 MB
- PDF type requires `data` + `contentType`; LINK type requires `url`

Enum `DocumentType`: `PDF`, `LINK` — in competition module public API.

Migration: `V14__create_competition_documents_table.sql`

---

## Service Layer

New methods on `CompetitionService` (no new service class):

- `addDocument(competitionId, name, DocumentType, byte[] data, String contentType, String url)` — validates, assigns next display order
- `removeDocument(documentId)` — deletes, recompacts ordering
- `updateDocumentName(documentId, name)` — rename (unique per competition)
- `reorderDocuments(competitionId, List<UUID> orderedIds)` — sets display order from list position
- `getDocuments(competitionId)` — ordered by `displayOrder`
- `getDocument(documentId)` — single document (for PDF download)

Validation:
- Name: `@NotBlank`, unique per competition (IllegalArgumentException if duplicate)
- PDF: max 10 MB, content type must be `application/pdf`
- Link: `@NotBlank` URL string
- Type-specific: PDF requires data + contentType; LINK requires url

No events — CRUD within competition module, no cross-module consumers.

---

## UI

### Admin: "Documents" tab in CompetitionDetailView

New tab alongside Divisions, Participants, Settings.

- Grid columns: Name, Type (badge), Actions
- Actions: Download/Open (PDF downloads, Link opens new tab), Edit name, Move up/down, Delete
- "Add Document" button → dialog:
  - RadioButtonGroup: PDF / Link
  - Name field (required)
  - Conditional: Upload component (PDF) or URL TextField (Link)
- Reordering via up/down buttons in grid rows

### Entrant: document list in MyEntriesView

- Section above or below the entries grid showing competition documents
- Simple list: document name as clickable link
  - PDF: triggers download (served from a StreamResource or similar)
  - Link: opens in new tab
- Ordered by `displayOrder`

---

## Repository

`CompetitionDocumentRepository` (internal):
- `findByCompetitionIdOrderByDisplayOrder(UUID competitionId)`
- `countByCompetitionId(UUID competitionId)` — for assigning next display order
- `existsByCompetitionIdAndName(UUID competitionId, String name)` — uniqueness check

---

## Scope Exclusions

- No rich text / markdown document type (PDF + Link covers the need)
- No document versioning (admin deletes and re-uploads)
- No per-division documents (competition-level only)
- No public/anonymous access (authenticated users only, same as competition views)
