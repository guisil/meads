# Module: judging

**Status:** Planned (not yet implemented)
**Depends on:** competition, entry, identity
**Reference:** `docs/reference/chip-competition-rules.md`

> This is a preliminary spec using post-rework naming conventions. It will be
> refined during the design phase before implementation begins. Migration numbers
> are placeholders — actual numbers will depend on the highest version at
> implementation time (currently V15).

## Purpose
Manages judging sessions, table assignments, and scoresheets. Judges are assigned to
tables, categories are assigned to tables for a given session, and judges score the
entries in their assigned categories using the scoring system's field definitions.

## Entities

### JudgingSession (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, self-generated |
| divisionId | UUID | NOT NULL (references Division in competition module) |
| name | String | NOT NULL (e.g., "Round 1", "Finals") |
| date | LocalDate | NOT NULL |
| status | SessionStatus | NOT NULL, enum: PLANNED, ACTIVE, COMPLETED |
| createdAt | Instant | NOT NULL, @PrePersist |

**Invariants:**
- Cannot activate before division enters JUDGING status
- Cannot complete until all scoresheets for the session are submitted

### JudgingTable (child of JudgingSession)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, self-generated |
| sessionId | UUID | FK to JudgingSession, NOT NULL |
| name | String | NOT NULL (e.g., "Table 1", "Table A") |
| divisionCategoryId | UUID | NOT NULL (references DivisionCategory in competition module) |

A table is assigned one category per session. Multiple tables may judge the same
category (for large categories with many entries).

### JudgeAssignment (child of JudgingTable)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, self-generated |
| tableId | UUID | FK to JudgingTable, NOT NULL |
| judgeId | UUID | NOT NULL (references User in identity module) |
| assignedAt | Instant | NOT NULL, @PrePersist |

UNIQUE(tableId, judgeId). A judge sits at one table per session. The table's category
determines which entries the judge can score.

### Scoresheet (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, self-generated |
| assignmentId | UUID | FK to JudgeAssignment, NOT NULL |
| entryId | UUID | NOT NULL (references Entry in entry module) |
| status | ScoresheetStatus | NOT NULL, enum: DRAFT, SUBMITTED |
| totalScore | Integer | nullable (computed on submit) |
| comments | String | nullable (overall comments) |
| submittedAt | Instant | nullable |
| createdAt | Instant | NOT NULL, @PrePersist |

### ScoreField (child of Scoresheet)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, self-generated |
| scoresheetId | UUID | FK to Scoresheet, NOT NULL |
| fieldName | String | NOT NULL (e.g., "Aroma", "Flavor") |
| maxValue | Integer | NOT NULL (copied from ScoreFieldDefinition) |
| value | Integer | nullable (filled by judge) |
| comment | String | nullable (per-field comment) |

ScoreFields are created when a Scoresheet is initialized, based on the division's
scoring system's ScoreFieldDefinitions. The `maxValue` is copied at creation time
(denormalized) so scoresheets remain valid even if the scoring system is later modified.

**Invariants:**
- `value` must be between 0 and `maxValue` (inclusive)
- Cannot submit scoresheet unless all ScoreFields have a value
- `totalScore` = sum of all ScoreField values
- Judge can only score entries in their assigned table's category

## Service API

### JudgingService
- `createSession(divisionId, name, date)` — creates PLANNED session
- `createTable(sessionId, name, divisionCategoryId)` — adds table to session
- `assignJudge(tableId, judgeId)` — assigns judge to table
- `activateSession(sessionId)` — PLANNED → ACTIVE (creates scoresheets for all entries in assigned categories)
- `completeSession(sessionId)` — ACTIVE → COMPLETED (validates all scoresheets submitted)

### ScoresheetService
- `getScoresheet(scoresheetId)` — returns scoresheet with all score fields
- `updateScoreField(scoresheetId, fieldName, value, comment)` — updates a single field
- `submitScoresheet(scoresheetId)` — validates completeness, computes total, DRAFT → SUBMITTED
- `findByJudge(judgeId, sessionId)` — all scoresheets for a judge in a session

## Events Published
- `ScoresheetSubmittedEvent(UUID scoresheetId, UUID entryId, UUID divisionId, int totalScore)` — feeds into awards
- `SessionCompletedEvent(UUID sessionId, UUID divisionId)` — all judging for session done

## Events Consumed
- `DivisionStatusAdvancedEvent` — enables session activation when division reaches JUDGING status

## Views

| Route | View Class | Purpose | Access |
|-------|-----------|---------|--------|
| /divisions/{divisionId}/judging | JudgingSessionListView | Manage sessions and tables | SYSTEM_ADMIN or division ADMIN |
| /divisions/{divisionId}/my-assignments | MyAssignmentsView | Judge sees assigned entries and fills scoresheets | Authenticated judge |
| /scoresheets/{id} | ScoresheetView | Fill in scores for one entry | Assigned judge only |

## Security Rules
- SYSTEM_ADMIN and competition ADMINs manage sessions, tables, and judge assignments
- Judges can only view and edit scoresheets for entries at their assigned table
- Scoresheets are read-only after submission
- Judges cannot see other judges' scores until the session is completed

## Open Questions
- Can a judge be assigned to multiple tables in the same session (different categories)?
- Should scoresheet creation happen eagerly (when session activates) or lazily (when judge opens the entry)?
- Do we need a "head judge" concept for tie-breaking or quality review?
- Conflict of interest: judges can't evaluate own entries/company (enforcement mechanism TBD)
