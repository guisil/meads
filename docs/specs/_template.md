# Module: {module_name}

## Purpose
{One-sentence description of what this module does.}

## Entities

### {AggregateName} (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| ... | ... | ... |

**Invariants:**
- {Business rule the entity enforces}

### {ValueObject} (if any)
- {field}: {type} — {description}

## Service API

### {ModuleName}Service
- `create(...)` — {what it does}
- `update(...)` — {what it does}
- `findById(UUID id)` — {what it returns}

## Events Published
- `{ModuleName}CreatedEvent(UUID id, ...)` — {when published}
- `{ModuleName}UpdatedEvent(UUID id, ...)` — {when published}

## Events Consumed
- `{SourceModule}Event` — {what this module does in response}

## Views

| Route | View Class | Purpose | Access |
|-------|-----------|---------|--------|
| /{route} | {ViewName} | {description} | @RolesAllowed("...") |

## Security Rules
- {Who can do what}

## Flyway Migrations Needed
- `V{N}__create_{table}_table.sql` — {columns}
