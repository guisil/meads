# Phase B: Data Model Redesign

## Status: Implemented (B1-B7 complete, B6.4 done in Phase C)

## Overview

Redesign competition participant model to introduce event-level participants,
competition admin authorization, and fix the N+1 query problem in the participant grid.

---

## New Data Model

### EventParticipant (new entity)

```
EventParticipant (id, eventId, userId, accessCode, status, createdAt)
```

- One per user per event
- Holds event-scoped access code (login once, see all competitions in that event)
- Status: ACTIVE, WITHDRAWN

### CompetitionParticipant (redesigned)

```
CompetitionParticipant (id, competitionId, eventParticipantId, role, createdAt)
```

- Links an event participant to a competition with a specific role
- A user CAN have multiple roles in the same competition (JUDGE + ENTRANT)
- Unique constraint: `(competitionId, eventParticipantId, role)`
- Conflict of interest checks (judge not assigned to categories they entered) deferred to judging module

---

## Authorization Model

- **System admin**: create events, create competitions, add first competition admin
- **Competition admin**: add other competition admins (not remove), add/edit participants,
  manage competition settings, advance status
- Adding participants auto-creates user if email doesn't exist

---

## Participant Flow Changes

- "Add to all competitions in event" replaces "copy participants"
- Single EventParticipant + multiple CompetitionParticipants
- Access code validation: query EventParticipant by code (not CompetitionParticipant)

---

## N+1 Fix

Current problem: participant grid calls `userService.findById()` per cell render
(Name column and Email column each call it independently).

With EventParticipant model, user info can be fetched once per event participant.
Alternatively, batch-load users in `refreshParticipantsGrid()` and pass a `Map<UUID, User>`
to the column value providers.

---

## Migration Strategy

Since the app is not yet deployed to production, migrations can be rewritten.
However, if we want to keep Phase A migrations (V3-V6) stable:

- V7: `event_participants` table
- V8: alter `competition_participants` to reference `event_participants` instead of `users` directly
- V9: data migration (create event_participants from existing competition_participants)

Or consolidate V3-V8 if we're still comfortable rewriting pre-deploy.
