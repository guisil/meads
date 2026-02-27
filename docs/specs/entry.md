# Module: entry

## Purpose
Manages entry credits (purchased externally) and mead registrations. An external payment
site calls a webhook to grant credits; users then consume credits to register their meads.

## Entities

### EntryCredit (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| competitionId | UUID | NOT NULL (references competition module) |
| userEmail | String | NOT NULL (may not yet exist as a User) |
| orderId | String | NOT NULL (from external payment site) |
| totalCredits | Integer | NOT NULL, > 0 |
| usedCredits | Integer | NOT NULL, default 0 |
| createdAt | LocalDateTime | NOT NULL, auto |

UNIQUE(competitionId, orderId) — prevents duplicate credit grants from the same order.

**Invariants:**
- `usedCredits <= totalCredits`
- Cannot consume credits if competition is not OPEN
- Credits are tied to a specific competition (not transferable)

### Entry (aggregate root)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, assigned |
| competitionId | UUID | NOT NULL |
| entrantId | UUID | NOT NULL (references identity module) |
| categoryId | UUID | NOT NULL (references competition module) |
| creditId | UUID | FK to EntryCredit |
| meadName | String | NOT NULL |
| description | String | nullable |
| status | EntryStatus | NOT NULL, enum: REGISTERED, WITHDRAWN |
| registeredAt | LocalDateTime | NOT NULL, auto |

**Invariants:**
- Cannot register without available credits (credit.usedCredits < credit.totalCredits)
- Cannot register to a competition that is not OPEN
- Cannot withdraw after competition enters JUDGING phase
- Withdrawing an entry releases the credit (decrements usedCredits)

## Service API

### EntryCreditService
- `grantCredits(competitionId, userEmail, orderId, quantity)` — creates EntryCredit (idempotent on orderId+competitionId). Publishes event to trigger magic link for new users.
- `findByUserAndCompetition(userEmail, competitionId)` — returns credits with remaining count
- `getAvailableCredits(userEmail, competitionId)` — returns number of unused credits

### EntryService
- `register(competitionId, entrantId, categoryId, meadName, description)` — creates entry, consumes one credit
- `withdraw(entryId, entrantId)` — withdraws own entry, releases credit
- `findByCompetition(UUID competitionId)` — all entries for a competition
- `findByEntrant(UUID entrantId)` — all entries by a user

## REST API (webhook)

### POST /api/entry-credits
External payment site calls this to grant credits.
```json
{
  "competitionId": "uuid",
  "userEmail": "someone@somewhere.com",
  "orderId": "external-order-123",
  "quantity": 3
}
```
Returns 201 on success, 200 if orderId already processed (idempotent), 400 on validation error.

Authentication: API key or shared secret (TBD — not user session-based).

## Events Published
- `EntryCreditGrantedEvent(UUID creditId, UUID competitionId, String userEmail)` — triggers magic link for user
- `EntryRegisteredEvent(UUID entryId, UUID competitionId, UUID categoryId)` — entry available for judging

## Events Consumed
- `CompetitionOpenedEvent` — enables credit consumption and entry registration
- `CompetitionClosedEvent` — prevents new registrations, withdraws any non-finalized entries
- `JudgingPhaseStartedEvent` — prevents withdrawals

## Views

| Route | View Class | Purpose | Access |
|-------|-----------|---------|--------|
| /my-entries | MyEntriesView | Entrant registers meads, sees credits remaining | Authenticated user with credits |
| /competitions/{id}/entries | EntryListView | Admin view of all entries | SYSTEM_ADMIN or competition admin |

## Security Rules
- Webhook endpoint uses API key authentication (not user session)
- Users can only register entries if they have available credits
- Users can only withdraw their own entries
- SYSTEM_ADMIN and competition admins can view all entries
- Entry registration auto-creates User if email doesn't exist (PENDING status)

## Flyway Migrations Needed
- `V11__create_entry_credits_table.sql` — id, competition_id, user_email, order_id, total_credits, used_credits, created_at, UNIQUE(competition_id, order_id)
- `V12__create_entries_table.sql` — id, competition_id, entrant_id, category_id, credit_id, mead_name, description, status, registered_at

## Open Questions
- Should the webhook auto-create the User in the identity module, or just store the email and let the magic link flow handle user creation?
- How should API key authentication work for the webhook? Separate from Spring Security's user auth chain.
