# Module: judging

## Purpose
Manages judging sessions, table assignments, and scoresheets. Judges are assigned to
tables, categories are assigned to tables for a given session, and judges score the
entries in their assigned categories using the scoring system's field definitions.

## Entities

### JudgingSession (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| competitionId | UUID | NOT NULL |
| name | String | NOT NULL (e.g., "Round 1", "Finals") |
| date | LocalDate | NOT NULL |
| status | SessionStatus | NOT NULL, enum: PLANNED, ACTIVE, COMPLETED |
| createdAt | LocalDateTime | NOT NULL, auto |

**Invariants:**
- Cannot activate before competition enters JUDGING phase
- Cannot complete until all scoresheets for the session are submitted

### JudgingTable (child of JudgingSession)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| session | JudgingSession | FK, NOT NULL |
| name | String | NOT NULL (e.g., "Table 1", "Table A") |
| categoryId | UUID | NOT NULL (references competition category) |

A table is assigned one category per session. Multiple tables may judge the same
category (for large categories with many entries).

### JudgeAssignment (child of JudgingTable)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| table | JudgingTable | FK, NOT NULL |
| judgeId | UUID | NOT NULL (references identity module) |
| assignedAt | LocalDateTime | NOT NULL, auto |

UNIQUE(table, judgeId). A judge sits at one table per session. The table's category
determines which entries the judge can score.

### Scoresheet (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| assignmentId | UUID | FK to JudgeAssignment, NOT NULL |
| entryId | UUID | NOT NULL (references entry module) |
| status | ScoresheetStatus | NOT NULL, enum: DRAFT, SUBMITTED |
| totalScore | Integer | nullable (computed on submit) |
| comments | String | nullable (overall comments) |
| submittedAt | LocalDateTime | nullable |
| createdAt | LocalDateTime | NOT NULL, auto |

### ScoreField (child of Scoresheet)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| scoresheet | Scoresheet | FK, NOT NULL |
| fieldName | String | NOT NULL (e.g., "Aroma", "Flavor") |
| maxValue | Integer | NOT NULL (copied from ScoreFieldDefinition) |
| value | Integer | nullable (filled by judge) |
| comment | String | nullable (per-field comment) |

ScoreFields are created when a Scoresheet is initialized, based on the competition's
scoring system's ScoreFieldDefinitions. The `maxValue` is copied at creation time
(denormalized) so scoresheets remain valid even if the scoring system is later modified.

**Invariants:**
- `value` must be between 0 and `maxValue` (inclusive)
- Cannot submit scoresheet unless all ScoreFields have a value
- `totalScore` = sum of all ScoreField values
- Judge can only score entries in their assigned table's category

## Service API

### JudgingService
- `createSession(competitionId, name, date)` — creates PLANNED session
- `createTable(sessionId, name, categoryId)` — adds table to session
- `assignJudge(tableId, judgeId)` — assigns judge to table
- `activateSession(sessionId)` — PLANNED → ACTIVE (creates scoresheets for all entries in assigned categories)
- `completeSession(sessionId)` — ACTIVE → COMPLETED (validates all scoresheets submitted)

### ScoresheetService
- `getScoresheet(scoresheetId)` — returns scoresheet with all score fields
- `updateScoreField(scoresheetId, fieldName, value, comment)` — updates a single field
- `submitScoresheet(scoresheetId)` — validates completeness, computes total, DRAFT → SUBMITTED
- `findByJudge(judgeId, sessionId)` — all scoresheets for a judge in a session

## Events Published
- `ScoresheetSubmittedEvent(UUID scoresheetId, UUID entryId, UUID competitionId, int totalScore)` — feeds into awards
- `SessionCompletedEvent(UUID sessionId, UUID competitionId)` — all judging for session done

## Events Consumed
- `JudgingPhaseStartedEvent` — enables session activation
- `EntryRegisteredEvent` — entry becomes available for table/scoresheet assignment
- `CompetitionClosedEvent` — prevents new session creation

## Views

| Route | View Class | Purpose | Access |
|-------|-----------|---------|--------|
| /competitions/{id}/judging | JudgingSessionListView | Manage sessions and tables | SYSTEM_ADMIN or competition admin |
| /judging/my-assignments | MyAssignmentsView | Judge sees assigned entries and fills scoresheets | Authenticated judge |
| /judging/scoresheet/{id} | ScoresheetView | Fill in scores for one entry | Assigned judge only |

## Security Rules
- SYSTEM_ADMIN and competition admins manage sessions, tables, and judge assignments
- Judges can only view and edit scoresheets for entries at their assigned table
- Scoresheets are read-only after submission
- Judges cannot see other judges' scores until the session is completed

## Flyway Migrations Needed
- `V13__create_judging_sessions_table.sql` — id, competition_id, name, date, status, created_at
- `V14__create_judging_tables_table.sql` — id, session_id, name, category_id
- `V15__create_judge_assignments_table.sql` — id, table_id, judge_id, assigned_at, UNIQUE(table_id, judge_id)
- `V16__create_scoresheets_table.sql` — id, assignment_id, entry_id, status, total_score, comments, submitted_at, created_at
- `V17__create_score_fields_table.sql` — id, scoresheet_id, field_name, max_value, value, comment

## Open Questions
- Can a judge be assigned to multiple tables in the same session (different categories)?
- Should scoresheet creation happen eagerly (when session activates) or lazily (when judge opens the entry)?
- Do we need a "head judge" concept for tie-breaking or quality review?
