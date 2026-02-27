# Module: competition

## Purpose
Defines and manages mead events, competitions, scoring systems, and categories.
An Event (e.g., "Mead Madness Cup") groups one or more Competitions (e.g., "MMC Home",
"MMC Pro"). Each Competition uses a scoring system (MJP, BJCP) which provides default
categories that can be customized per competition.

## Entities

### Event (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| name | String | NOT NULL |
| description | String | nullable |
| createdAt | LocalDateTime | NOT NULL, auto |
| updatedAt | LocalDateTime | auto |

**Invariants:**
- Must have at least one Competition before it can be published

### Competition (child entity of Event)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| event | Event | FK, NOT NULL |
| name | String | NOT NULL |
| description | String | nullable |
| scoringSystem | ScoringSystem | FK, NOT NULL |
| status | CompetitionStatus | NOT NULL, enum: DRAFT, OPEN, JUDGING, CLOSED |
| entryDeadline | LocalDate | nullable (set when OPEN) |
| judgingDate | LocalDate | nullable |
| createdAt | LocalDateTime | NOT NULL, auto |
| updatedAt | LocalDateTime | auto |

**Invariants:**
- Cannot transition to OPEN without at least one category
- Cannot transition to JUDGING before entryDeadline passes
- Cannot reopen a CLOSED competition

### ScoringSystem (reference entity)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| name | String | NOT NULL, UNIQUE (e.g., "MJP", "BJCP") |
| description | String | nullable |

### ScoreFieldDefinition (child of ScoringSystem)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| scoringSystem | ScoringSystem | FK, NOT NULL |
| name | String | NOT NULL (e.g., "Aroma", "Flavor") |
| maxValue | Integer | NOT NULL (e.g., 12, 20) |
| displayOrder | Integer | NOT NULL |

### DefaultCategory (child of ScoringSystem)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| scoringSystem | ScoringSystem | FK, NOT NULL |
| name | String | NOT NULL |
| description | String | nullable |

### CompetitionCategory (child of Competition)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| competition | Competition | FK, NOT NULL |
| name | String | NOT NULL |
| description | String | nullable |

Categories are initialized from the scoring system's defaults when a competition is
created, then independently customized (merge, split, rename, add new, remove).
No FK back to DefaultCategory — once copied, they are independent.

### CompetitionAdmin (join entity)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| competition | Competition | FK, NOT NULL |
| userId | UUID | NOT NULL (references identity module) |
| grantedAt | LocalDateTime | NOT NULL, auto |

UNIQUE(competition, userId). An Event admin is someone who is a CompetitionAdmin for
ALL competitions in that event. This is a query-level concept, not a separate entity —
avoids dual maintenance. To make someone an event admin, assign them to all competitions.

## Service API

### CompetitionService
- `createEvent(name, description)` — creates an Event
- `createCompetition(eventId, name, scoringSystemId)` — creates DRAFT Competition under Event, copies default categories from scoring system
- `addCategory(competitionId, name, description)` — adds custom category
- `removeCategory(competitionId, categoryId)` — removes a category
- `openCompetition(competitionId)` — DRAFT → OPEN (validates categories exist)
- `startJudging(competitionId)` — OPEN → JUDGING
- `closeCompetition(competitionId)` — JUDGING → CLOSED
- `assignAdmin(competitionId, userId)` — grants competition admin role
- `removeAdmin(competitionId, userId)` — revokes competition admin role
- `findEventById(UUID id)` — returns event with competitions
- `findCompetitionById(UUID id)` — returns competition with categories

## Events Published
- `CompetitionOpenedEvent(UUID competitionId)` — when competition opens for entries
- `JudgingPhaseStartedEvent(UUID competitionId)` — when judging begins
- `CompetitionClosedEvent(UUID competitionId)` — when competition is finalized

## Events Consumed
- None initially (self-contained)

## Views

| Route | View Class | Purpose | Access |
|-------|-----------|---------|--------|
| /events | EventListView | List/manage events | SYSTEM_ADMIN or COMPETITION_ADMIN |
| /events/{id} | EventDetailView | Manage competitions under an event | SYSTEM_ADMIN or competition admin |
| /competitions/{id} | CompetitionDetailView | Categories, settings, admin assignment | SYSTEM_ADMIN or competition admin |

## Security Rules
- SYSTEM_ADMIN can manage all events, competitions, and admins
- COMPETITION_ADMIN can manage their assigned competitions (categories, settings)
- COMPETITION_ADMIN cannot assign other admins (only SYSTEM_ADMIN can)
- Future: entrants may view open competitions (read-only)

## Flyway Migrations Needed
- `V4__create_events_table.sql` — id, name, description, timestamps
- `V5__create_scoring_systems_table.sql` — id, name, description
- `V6__create_score_field_definitions_table.sql` — id, scoring_system_id, name, max_value, display_order
- `V7__create_default_categories_table.sql` — id, scoring_system_id, name, description
- `V8__create_competitions_table.sql` — id, event_id, name, description, scoring_system_id, status, dates, timestamps
- `V9__create_competition_categories_table.sql` — id, competition_id, name, description
- `V10__create_competition_admins_table.sql` — id, competition_id, user_id, granted_at, UNIQUE(competition_id, user_id)

## Open Questions
- Should scoring systems and their defaults be seeded via Flyway migration (reference data) or managed via UI?
- Should COMPETITION_ADMIN be a new Role enum value in the identity module, or is it purely a CompetitionAdmin record? (Lean: keep Role as USER/SYSTEM_ADMIN and check CompetitionAdmin records for authorization)
