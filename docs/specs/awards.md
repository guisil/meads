# Module: awards

**Status:** Planned (not yet implemented)
**Depends on:** competition, entry, judging, identity
**Reference:** `docs/reference/chip-competition-rules.md`

> This is a preliminary spec using post-rework naming conventions. It will be
> refined during the design phase before implementation begins. Migration numbers
> are placeholders — actual numbers will depend on the highest version at
> implementation time (currently V15).

## Purpose
Aggregates scores from submitted scoresheets, computes rankings per category,
and determines awards (gold, silver, bronze, best of show). Primarily a read-model
that reacts to judging events.

## Entities

### CategoryResult (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, self-generated |
| divisionId | UUID | NOT NULL (references Division in competition module) |
| divisionCategoryId | UUID | NOT NULL (references DivisionCategory in competition module) |
| entryId | UUID | NOT NULL (references Entry in entry module) |
| averageScore | BigDecimal | NOT NULL (average of all scoresheet totals for this entry) |
| rank | Integer | NOT NULL |
| award | Award | nullable, enum: GOLD, SILVER, BRONZE, HONORABLE_MENTION |
| computedAt | Instant | NOT NULL, @PrePersist |

**Invariants:**
- Only computed from SUBMITTED scoresheets
- Rank is unique per category (ties broken by rules TBD)
- Award assignment may be threshold-based (e.g., gold requires avg >= 38)
- Medals can be withheld at judge discretion (not purely score-threshold-based)

### BestOfShow (entity)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, self-generated |
| divisionId | UUID | NOT NULL |
| entryId | UUID | NOT NULL |
| place | Integer | NOT NULL (1st, 2nd, 3rd — variable per division) |
| score | BigDecimal | NOT NULL |
| determinedAt | Instant | NOT NULL, @PrePersist |

UNIQUE(divisionId, place). BOS is per division, not per competition.
Variable number of BOS places per division (CHIP: 3 for Amadora, 1 for Profissional).

## Service API

### AwardsService
- `computeResults(divisionId)` — aggregates all submitted scoresheets, computes rankings and awards
- `getResultsByCategory(divisionId, divisionCategoryId)` — ranked entries for a category
- `getResultsByDivision(divisionId)` — all category results
- `determineBestOfShow(divisionId)` — selects best entries across all categories (Gold winners only)
- `publishResults(divisionId)` — makes results visible to entrants

## Events Published
- `ResultsPublishedEvent(UUID divisionId)` — results are available to entrants

## Events Consumed
- `ScoresheetSubmittedEvent` — incrementally updates entry score aggregation
- `SessionCompletedEvent` — triggers result recomputation for affected categories

## Views

| Route | View Class | Purpose | Access |
|-------|-----------|---------|--------|
| /divisions/{divisionId}/results | ResultsView | View rankings and awards | SYSTEM_ADMIN, division ADMIN (before publish), all (after publish) |
| /divisions/{divisionId}/my-results | MyResultsView | Entrant sees own scores and awards | Authenticated entrant (after publish) |

## Security Rules
- Only SYSTEM_ADMIN and competition ADMINs can trigger result computation
- Results are visible to admins immediately after computation
- Results are visible to entrants only after explicit publication
- Individual scoresheet details may or may not be visible to entrants (TBD)

## Open Questions
- Award thresholds: fixed per scoring system (e.g., MJP gold >= 38) or configurable per division?
- Should entrants see individual judge scores or only the average?
- Tie-breaking rules: higher individual score? Head judge score? Count of higher-scoring fields?
- Head Judge tie-breaking authority for BOS
