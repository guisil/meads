# MVP Flow — First Stage (Entry Registration)

## Goal
Application ready to accept entrants and mead entries.

## Actors & Auth
- **System admin**: password login (exists)
- **Competition admin**: password login (email sent to set password, then ACTIVE)
- **Entrant**: magic link login (triggered after credit purchase)
- **Judge/Steward**: magic link or access code (later stage)

## Flow

### 1. Setup (System Admin)
- Create events, competitions
- Add competition admins (creates user → email to set password → ACTIVE)

### 2. Competition Management (Competition Admin)
- Edit event/competition details (logo, name, dates)
- Customize initial mead categories per competition
- Add/edit/withdraw participants (judges, stewards, entrants — for problem resolution)
- Add/edit/withdraw entries (admin override)

### 3. Credit Purchase (External Webhook)
- **Only REST/HTTP endpoint in the app**
- External website calls webhook with purchase data
- Creates entry credits for the purchaser
- Idempotency: use orderID from external website to prevent duplicate processing
- Invalid order handling:
  - e.g., entrant has HOME entries but purchases PRO credits
  - Store in separate table for admin resolution
- Creates user if not exists, or associates with existing user by email

### 4. Entry Registration (Entrant)
- Credits associated with user → magic link sent
- Entrant logs in → sees available credits
- Adds entry details: initial category + subcategory (from competition's customized categories)
- Submits entries
- After submission: **read-only** for entrant, only competition admins can edit

### 5. Close Registration → Judging (future stage)

## Key Design Notes
- Competition admins CANNOT see platform user list — only manage participants by email
- Competition admins CAN edit entries (admin override)
- Categories are customizable per competition (not just seed data)
- Access code security (admin impersonation risk) — deferred, relevant for judging phase
- Invalid webhook orders need admin-facing UI for resolution
