# Module: awards

## Purpose
Aggregates scores from submitted scoresheets, computes rankings per category,
and determines awards (gold, silver, bronze, best of show). Primarily a read-model
that reacts to judging events.

## Entities

### CategoryResult (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| competitionId | UUID | NOT NULL |
| categoryId | UUID | NOT NULL |
| entryId | UUID | NOT NULL |
| averageScore | BigDecimal | NOT NULL (average of all scoresheet totals for this entry) |
| rank | Integer | NOT NULL |
| award | Award | nullable, enum: GOLD, SILVER, BRONZE, HONORABLE_MENTION |
| computedAt | LocalDateTime | NOT NULL, auto |

**Invariants:**
- Only computed from SUBMITTED scoresheets
- Rank is unique per category (ties broken by rules TBD)
- Award assignment may be threshold-based (e.g., gold requires avg >= 38)

### BestOfShow (entity)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| competitionId | UUID | NOT NULL, UNIQUE |
| entryId | UUID | NOT NULL |
| score | BigDecimal | NOT NULL |
| determinedAt | LocalDateTime | NOT NULL, auto |

## Service API

### AwardsService
- `computeResults(competitionId)` — aggregates all submitted scoresheets, computes rankings and awards
- `getResultsByCategory(competitionId, categoryId)` — ranked entries for a category
- `getResultsByCompetition(competitionId)` — all category results
- `determineBestOfShow(competitionId)` — selects best entry across all categories
- `publishResults(competitionId)` — makes results visible to entrants

## Events Published
- `ResultsPublishedEvent(UUID competitionId)` — results are available to entrants

## Events Consumed
- `ScoresheetSubmittedEvent` — incrementally updates entry score aggregation
- `SessionCompletedEvent` — triggers result recomputation for affected categories
- `CompetitionClosedEvent` — triggers final result computation

## Views

| Route | View Class | Purpose | Access |
|-------|-----------|---------|--------|
| /competitions/{id}/results | ResultsView | View rankings and awards | SYSTEM_ADMIN, competition admin (before publish), all (after publish) |
| /my-results | MyResultsView | Entrant sees own scores and awards | Authenticated entrant (after publish) |

## Security Rules
- Only SYSTEM_ADMIN and competition admins can trigger result computation
- Results are visible to admins immediately after computation
- Results are visible to entrants only after explicit publication
- Individual scoresheet details may or may not be visible to entrants (TBD)

## Flyway Migrations Needed
- `V18__create_category_results_table.sql` — id, competition_id, category_id, entry_id, average_score, rank, award, computed_at
- `V19__create_best_of_show_table.sql` — id, competition_id, entry_id, score, determined_at

## Open Questions
- Award thresholds: fixed per scoring system (e.g., BJCP gold >= 38) or configurable per competition?
- Should entrants see individual judge scores or only the average?
- Tie-breaking rules: higher individual score? Head judge score? Count of higher-scoring fields?
