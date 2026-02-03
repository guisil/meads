# MEADS Database Schema

## Context

MEADS manages mead competitions. An **Event** (e.g., "MeadFest 2026") contains one or more **Competitions** (e.g., "Commercial" and "Home"). The application supports the full lifecycle from registration through judging and results.

### Key Flows

1. **Credit Purchase**: Entrants purchase credits externally (Jumpseller) → webhook creates Registration with N credits → email sent with magic link
2. **Mead Registration**: Entrants use credits to register mead entries before deadline → entries assigned codes on submission
3. **Judging Setup**: Admins create categories, tables, assign judges to competition pool, then to specific tables
4. **Judging Rounds**: 
   - CATEGORY round: entries judged by category, top entries advance
   - MEDAL round: best entries compete for 1st/2nd/3rd in category (may be merged with CATEGORY for small categories)
   - BOS round: category winners compete for Best of Show
5. **Results**: After competition is published, mead makers can view their entries' scores and feedback

### User Roles

| Role | Scope | Read | Write |
|------|-------|------|-------|
| System Admin | Global | Everything | Everything |
| Competition Admin | Competition | Everything in competition | Everything in competition, role assignments |
| Steward | Competition | Tables, judges, categories, entries (all tables) | Nothing |
| Judge | Table assignment | Entries at assigned table | Scores for entries at assigned table (or consensus score if scribe) |
| Mead Maker | Own registrations | Own entries, own scores (after PUBLISHED) | Create entries (within credit limit) |

### Authentication

- Passwordless authentication via magic links (email-based)
- Different token types for different purposes (general login, judging sessions)
- Sessions are long-lived after initial authentication

---

## Entities

### User

Core identity. Created when webhook fires or when someone is added as admin/judge.

**Fields:**
- id: UUID, PK
- email: String, unique, not null
- displayName: String, nullable
- displayCountry: String, nullable
- isSystemAdmin: Boolean, not null, default false
- createdAt: Instant, not null
- updatedAt: Instant, not null

**Notes:**
- Email is the natural identifier; users authenticate via magic links
- displayName and displayCountry may come from webhook or be provided later
- isSystemAdmin is a simple flag; only 1-2 users will have this

---

### AccessToken

Magic link tokens for authentication.

**Fields:**
- id: UUID, PK
- tokenHash: String, unique, not null (store hash, not raw token)
- purpose: Enum(LOGIN, JUDGING_SESSION), not null
- email: String, not null (denormalized for lookup before user exists)
- expiresAt: Instant, not null
- used: Boolean, not null, default false
- createdAt: Instant, not null

**Relationships:**
- user: ManyToOne → User, nullable (null if user doesn't exist yet)
- competition: ManyToOne → Competition, nullable (for JUDGING_SESSION tokens)

**Notes:**
- Token is single-use (marked used after successful auth)
- For JUDGING_SESSION, competition scopes what the token grants access to
- Raw token is sent in email; only hash is stored
- LOGIN tokens: short-lived (15-30 minutes)
- JUDGING_SESSION tokens: longer-lived (12-18 hours)

---

### Event

A mead event containing one or more competitions.

**Fields:**
- id: UUID, PK
- name: String, not null
- slug: String, unique, not null (for URLs, e.g., "meadfest-2026")
- timezone: String, not null (e.g., "Europe/Lisbon", "America/Denver")
- startsAt: LocalDate, nullable
- endsAt: LocalDate, nullable
- createdAt: Instant, not null

**Relationships:**
- competitions: OneToMany → Competition

**Notes:**
- timezone helps display registration deadlines and judging times correctly
- startsAt/endsAt are dates (not instants) interpreted in the event's timezone

---

### ScoringSystem

Defines a scoring methodology (e.g., MJP, BJCP) with its components. Can be reused across competitions. Supports versioning.

**Fields:**
- id: UUID, PK
- name: String, not null (e.g., "MJP", "BJCP")
- version: String, not null (e.g., "2024", "1.0", "v2")
- description: String, nullable
- maxTotalPoints: Integer, not null (e.g., 50 for MJP)
- isDefault: Boolean, not null, default false
- enabled: Boolean, not null, default true
- locked: Boolean, not null, default false
- createdAt: Instant, not null

**Relationships:**
- components: OneToMany → ScoringComponent (ordered by displayOrder)
- categoryTemplates: ManyToMany → CategoryTemplate

**Constraints:**
- Unique(name, version)

**Notes:**
- locked becomes true once any competition uses this scoring system
- Locked scoring systems cannot be edited
- When updating a locked system: set enabled=false, create new version with enabled=true
- Only enabled scoring systems can be assigned to new competitions
- isDefault marks the default for new competitions

**Example - MJP 2024:**
```
name: "MJP"
version: "2024"
maxTotalPoints: 50
components:
  - Appearance: 3 points
  - Aroma: 12 points
  - Flavor: 20 points
  - Overall Impression: 15 points
```

---

### ScoringComponent

A sub-component of a scoring system.

**Fields:**
- id: UUID, PK
- name: String, not null (e.g., "Aroma", "Flavor")
- description: String, nullable (guidance for judges)
- maxPoints: Integer, not null (e.g., 12 for Aroma in MJP)
- displayOrder: Integer, not null

**Relationships:**
- scoringSystem: ManyToOne → ScoringSystem, not null

**Constraints:**
- Unique(scoringSystem, name)
- Unique(scoringSystem, displayOrder)
- Sum of all component maxPoints should equal scoringSystem.maxTotalPoints (application-level validation)

---

### CategoryTemplate

Template categories that can be used when creating a competition. Global, linked to scoring systems.

**Fields:**
- id: UUID, PK
- code: String, not null (e.g., "M1A", "M2B")
- name: String, not null (e.g., "Traditional Mead - Dry")
- description: String, nullable
- displayOrder: Integer, not null, default 0

**Relationships:**
- scoringSystems: ManyToMany → ScoringSystem

**Constraints:**
- Unique(code)

**Notes:**
- When creating a competition with a scoring system, templates linked to that system are shown
- Same template can be linked to multiple scoring systems (e.g., M1A works with both MJP and BJCP)
- Admin selects which templates to include; they're copied to competition-specific Category records
- Custom categories can also be created directly on the competition

---

### Competition

A single competition within an event.

**Fields:**
- id: UUID, PK
- name: String, not null
- type: Enum(COMMERCIAL, HOME), not null
- registrationOpensAt: Instant, nullable
- registrationClosesAt: Instant, nullable
- status: Enum(DRAFT, REGISTRATION_OPEN, REGISTRATION_CLOSED, JUDGING, COMPLETED, PUBLISHED), not null
- judgingMode: Enum(INDIVIDUAL, CONSENSUS), not null, default INDIVIDUAL
- maxScoreDifference: Integer, nullable (required for INDIVIDUAL mode)
- entryCodePrefix: String, nullable (e.g., "C" for commercial, "H" for home)
- entryCodeFormat: Enum(SEQUENTIAL, CATEGORY_PREFIXED), not null, default SEQUENTIAL
- nextEntryNumber: Integer, not null, default 1
- bosEntriesPerCategory: Integer, not null, default 1 (how many top entries per category go to BOS)
- createdAt: Instant, not null

**Relationships:**
- event: ManyToOne → Event, not null
- scoringSystem: ManyToOne → ScoringSystem, not null
- categories: OneToMany → Category
- judgingDays: OneToMany → JudgingDay
- judgingTables: OneToMany → JudgingTable
- admins: OneToMany → CompetitionAdmin
- stewards: OneToMany → CompetitionSteward
- judgeAssignments: OneToMany → JudgeCompetitionAssignment
- registrations: OneToMany → Registration

**Notes:**
- registrationOpensAt/ClosesAt are Instants (absolute point in time)
- UI displays these in the event's timezone
- SEQUENTIAL format: "001", "002", ... or with prefix "C-001", "C-002"
- CATEGORY_PREFIXED format: "M1A-001", "M1A-002", "M2B-001", ...
- nextEntryNumber requires proper locking for concurrency
- INDIVIDUAL mode: each judge scores separately, scores must be within maxScoreDifference
- CONSENSUS mode: judges agree on one score, one scribe writes the feedback
- Status COMPLETED means judging finished; PUBLISHED means scores visible to mead makers

---

### JudgingDay

A day on which judging occurs for a competition. Supports multi-day competitions.

**Fields:**
- id: UUID, PK
- date: LocalDate, not null
- description: String, nullable (e.g., "Day 1 - Traditional Meads", "Finals")
- displayOrder: Integer, not null, default 0

**Relationships:**
- competition: ManyToOne → Competition, not null
- tableAssignments: OneToMany → JudgeTableAssignment

**Constraints:**
- Unique(competition, date)

---

### CompetitionAdmin

Grants a user administrative access to a specific competition.

**Fields:**
- id: UUID, PK
- assignedAt: Instant, not null

**Relationships:**
- competition: ManyToOne → Competition, not null
- user: ManyToOne → User, not null
- assignedBy: ManyToOne → User, nullable (who granted the access)

**Constraints:**
- Unique(competition, user)

---

### CompetitionSteward

Grants a user read-only steward access to a competition.

**Fields:**
- id: UUID, PK
- assignedAt: Instant, not null

**Relationships:**
- competition: ManyToOne → Competition, not null
- user: ManyToOne → User, not null
- assignedBy: ManyToOne → User, nullable

**Constraints:**
- Unique(competition, user)

**Notes:**
- Stewards have read access to all tables in the competition

---

### Category

A mead category within a specific competition. Copied from CategoryTemplate or created custom.

**Fields:**
- id: UUID, PK
- code: String, not null (e.g., "M1A", "M2B")
- name: String, not null
- description: String, nullable
- displayOrder: Integer, not null, default 0

**Relationships:**
- competition: ManyToOne → Competition, not null
- sourceTemplate: ManyToOne → CategoryTemplate, nullable (tracks which template it came from)
- entries: OneToMany → MeadEntry

**Constraints:**
- Unique(competition, code)

**Notes:**
- Copied from CategoryTemplate on competition creation, then fully customizable
- sourceTemplate is informational; category is fully independent after creation
- Custom categories have sourceTemplate = null

---

### JudgingTable

A physical or logical table where judging happens.

**Fields:**
- id: UUID, PK
- name: String, not null (e.g., "Table 1", "Table A")
- location: String, nullable (e.g., "Main Hall", "Room 201")
- round: Enum(CATEGORY, MEDAL, BOS), not null, default CATEGORY
- isMedalRound: Boolean, not null, default false (if true, this table determines medals directly)
- minAdvancing: Integer, not null, default 1 (entries that advance to next round)
- maxAdvancing: Integer, not null, default 3
- displayOrder: Integer, not null, default 0

**Relationships:**
- competition: ManyToOne → Competition, not null
- judgeAssignments: OneToMany → JudgeTableAssignment
- entryAssignments: OneToMany → EntryTableAssignment

**Constraints:**
- Unique(competition, name)

**Notes:**
- round indicates which judging round this table is for
- isMedalRound=true means CATEGORY and MEDAL rounds are merged; medals decided directly
- minAdvancing/maxAdvancing only relevant when isMedalRound=false and round=CATEGORY
- At any given table, all entries will be of the same category
- A category may span multiple tables if there are many entries
- For BOS round, entries from different categories compete together

---

### JudgeCompetitionAssignment

Indicates a user is in the judge pool for a specific competition.

**Fields:**
- id: UUID, PK
- assignedAt: Instant, not null
- notes: String, nullable (e.g., "Experienced with melomels", "First-time judge")

**Relationships:**
- competition: ManyToOne → Competition, not null
- user: ManyToOne → User, not null
- assignedBy: ManyToOne → User, nullable
- tableAssignments: OneToMany → JudgeTableAssignment

**Constraints:**
- Unique(competition, user)

---

### JudgeTableAssignment

Assigns a judge to a specific table for a time period.

**Fields:**
- id: UUID, PK
- isTableLeadJudge: Boolean, not null, default false
- scheduledStart: Instant, nullable (null = "whenever", for ad-hoc)
- scheduledEnd: Instant, nullable
- status: Enum(SCHEDULED, ACTIVE, COMPLETED, CANCELLED), not null
- assignedAt: Instant, not null

**Relationships:**
- table: ManyToOne → JudgingTable, not null
- judge: ManyToOne → JudgeCompetitionAssignment, not null
- judgingDay: ManyToOne → JudgingDay, nullable

**Constraints:**
- At most one isTableLeadJudge=true per table (application-level)

**Notes:**
- A judge can have multiple assignments (sequential, different tables/days)
- isTableLeadJudge role differs by judging mode:
  - INDIVIDUAL mode: submits/finalizes scores when all judges' scores are within tolerance
  - CONSENSUS mode: writes the shared feedback AND submits the agreed score

---

### Registration

Created by webhook when someone purchases entry credits.

**Fields:**
- id: UUID, PK
- externalOrderId: String, nullable (Jumpseller order ID)
- totalCredits: Integer, not null
- accessToken: String, unique, not null
- accessTokenExpiresAt: Instant, not null
- createdAt: Instant, not null

**Relationships:**
- competition: ManyToOne → Competition, not null
- user: ManyToOne → User, not null (created eagerly from webhook)
- entries: OneToMany → MeadEntry

**Notes:**
- User is created immediately from webhook data if they don't exist
- If user exists (same email), registration links to existing user
- accessToken is specific to this registration flow
- A user can have multiple registrations in the same competition
- Available credits = sum of totalCredits across registrations minus total entries

---

### MeadEntry

A single mead entered into a competition.

**Fields:**
- id: UUID, PK
- entryCode: String, not null (assigned on creation, e.g., "047", "M1A-003")
- name: String, not null
- description: String, nullable
- honeyVarieties: String, not null (e.g., "Orange Blossom, Wildflower")
- otherIngredients: String, nullable (e.g., "Raspberries, Vanilla")
- status: Enum(SUBMITTED, CHECKED_IN, JUDGING, JUDGED, DISQUALIFIED), not null
- currentRound: Enum(CATEGORY, MEDAL, BOS), nullable (null before judging starts)
- passedToMedalRound: Boolean, not null, default false
- medalPosition: Integer, nullable (1, 2, or 3 for medal winners)
- passedToBos: Boolean, not null, default false
- bosPosition: Integer, nullable (1 for BOS winner, could track top 3)
- createdAt: Instant, not null
- updatedAt: Instant, not null

**Relationships:**
- registration: ManyToOne → Registration, not null
- category: ManyToOne → Category, not null
- tableAssignments: OneToMany → EntryTableAssignment
- scores: OneToMany → Score

**Constraints:**
- Unique(competition, entryCode) — derived via registration.competition

**Notes:**
- entryCode is generated on creation
- Format depends on competition.entryCodeFormat
- Status progression: SUBMITTED → CHECKED_IN → JUDGING → JUDGED
- DISQUALIFIED can happen at any point after SUBMITTED
- currentRound tracks where the entry is in the judging process
- An entry can have multiple table assignments (one per round it participates in)
- medalPosition: 1=gold, 2=silver, 3=bronze within category
- bosPosition: 1=Best of Show winner (could extend to track runner-ups)

---

### EntryTableAssignment

Links a mead entry to a judging table for a specific round.

**Fields:**
- id: UUID, PK
- round: Enum(CATEGORY, MEDAL, BOS), not null
- assignedAt: Instant, not null

**Relationships:**
- entry: ManyToOne → MeadEntry, not null
- table: ManyToOne → JudgingTable, not null
- assignedBy: ManyToOne → User, nullable

**Constraints:**
- Unique(entry, round) — an entry is assigned to one table per round

**Notes:**
- Replaces direct table reference on MeadEntry
- Allows entry to be at different tables in different rounds
- All entries at a table (for CATEGORY/MEDAL rounds) should be same category
- For BOS round, entries from different categories can be at the same table

---

### Score

A score for a mead entry. In INDIVIDUAL mode, one per judge. In CONSENSUS mode, one shared score.

**Fields:**
- id: UUID, PK
- totalPoints: Integer, not null
- feedback: String, nullable (written feedback to mead maker - visible to them)
- judgeNotes: String, nullable (internal notes - not shown to mead maker)
- language: String, not null, default "en" (ISO 639-1 code, e.g., "en", "pt", "es")
- round: Enum(CATEGORY, MEDAL, BOS), not null (which round this score is for)
- createdAt: Instant, not null
- updatedAt: Instant, not null

**Relationships:**
- entry: ManyToOne → MeadEntry, not null
- writtenBy: ManyToOne → User, not null (the judge who wrote/entered this score)
- componentScores: OneToMany → ScoreComponent
- judges: OneToMany → ScoreJudge (all judges who contributed, used in CONSENSUS mode)

**Constraints:**
- In INDIVIDUAL mode: Unique(entry, writtenBy, round)
- In CONSENSUS mode: Unique(entry, round) — only one score per entry per round

**Notes:**
- writtenBy is always populated (the table lead judge in CONSENSUS, the individual judge in INDIVIDUAL)
- In INDIVIDUAL mode: one Score per judge, judges list contains only writtenBy
- In CONSENSUS mode: one Score total, judges list contains all participating judges
- language helps mead makers know if they need translation
- Application validates that totalPoints equals sum of component scores
- Mead makers can see totalPoints, feedback, language, and all componentScores after PUBLISHED

---

### ScoreJudge

Links judges to a score. In INDIVIDUAL mode, just the one judge. In CONSENSUS mode, all participating judges.

**Fields:**
- id: UUID, PK

**Relationships:**
- score: ManyToOne → Score, not null
- judge: ManyToOne → User, not null

**Constraints:**
- Unique(score, judge)

**Notes:**
- In INDIVIDUAL mode: one ScoreJudge per Score (same as writtenBy)
- In CONSENSUS mode: multiple ScoreJudge per Score (all judges at the table)
- Provides clear record of which judges contributed to each score

---

### ScoreComponent

Individual component score within a score.

**Fields:**
- id: UUID, PK
- points: Integer, not null
- notes: String, nullable (judge's notes for this specific component)

**Relationships:**
- score: ManyToOne → Score, not null
- component: ManyToOne → ScoringComponent, not null

**Constraints:**
- Unique(score, component)
- points must be between 0 and component.maxPoints (application-level validation)

**Notes:**
- Each Score should have one ScoreComponent per ScoringComponent in the competition's scoring system

---

## Entity Relationship Diagram

```
User (id, email, displayName, displayCountry, isSystemAdmin)
 │
 ├──< AccessToken (purpose, tokenHash, expiresAt, used)
 │      └──? Competition (for JUDGING_SESSION)
 │
 ├──< CompetitionAdmin ──> Competition
 ├──< CompetitionSteward ──> Competition
 │
 ├──< JudgeCompetitionAssignment ──> Competition
 │      └──< JudgeTableAssignment ──> JudgingTable
 │             │                      JudgingDay (optional)
 │             └── isTableLeadJudge
 │
 ├──< Registration ──> Competition
 │      └──< MeadEntry (entryCode, name, honeyVarieties, status, round tracking)
 │             ├──> Category
 │             ├──< EntryTableAssignment ──> JudgingTable (per round)
 │             └──< Score (per round)
 │                    ├── writtenBy ──> User
 │                    ├──< ScoreJudge ──> User (all contributing judges)
 │                    └──< ScoreComponent ──> ScoringComponent
 │
 └──< ScoreJudge (as participating judge)


Event (id, name, slug, timezone, startsAt, endsAt)
 └──< Competition (id, name, type, status, judgingMode, maxScoreDifference, bosEntriesPerCategory)
        ├──> ScoringSystem
        ├──< JudgingDay (date, description)
        ├──< Category (code, name) ←─? CategoryTemplate
        ├──< JudgingTable (name, location, round, isMedalRound, minAdvancing, maxAdvancing)
        ├──< CompetitionAdmin
        ├──< CompetitionSteward
        ├──< JudgeCompetitionAssignment
        └──< Registration


ScoringSystem (id, name, version, maxTotalPoints, enabled, locked)
 ├──< ScoringComponent (name, maxPoints, displayOrder)
 └──<> CategoryTemplate (ManyToMany)


CategoryTemplate (id, code, name, description)
 └──<> ScoringSystem (ManyToMany)
```

---

## Business Rules

### Registration & Entries

1. A user's available credits = sum of totalCredits across all their Registrations for that competition, minus count of their MeadEntries.

2. MeadEntries can only be created while competition status = REGISTRATION_OPEN and before registrationClosesAt.

3. Each MeadEntry must have exactly one Category from the same competition.

4. Entry codes are unique within a competition and assigned when the entry is created.

### Judging Modes

5. **INDIVIDUAL mode**: 
   - Each judge at the table creates their own Score
   - All scores for an entry must be within maxScoreDifference points
   - Table lead judge cannot submit until scores are within tolerance
   - If scores exceed tolerance, judges must adjust before submission

6. **CONSENSUS mode**:
   - One Score per entry, agreed upon by all judges
   - The table lead judge (isTableLeadJudge=true) writes the feedback and submits
   - All judges at the table are recorded in ScoreJudge

### Judging Rounds

7. **CATEGORY round**: 
   - Entries judged by category (all entries at a table are same category)
   - Top minAdvancing to maxAdvancing entries advance to MEDAL round
   - If isMedalRound=true, medals are decided directly (no separate MEDAL round)

8. **MEDAL round**: 
   - Best entries from CATEGORY round compete for 1st/2nd/3rd in their category
   - May be merged with CATEGORY round for small categories (isMedalRound=true)
   - Top bosEntriesPerCategory entries advance to BOS round

9. **BOS round**:
   - Category winners compete for Best of Show
   - One BOS per competition (commercial and home have separate BOS)
   - bosPosition=1 indicates the winner

10. A user can be both a mead maker and a judge in the same competition, as long as they judge different categories than they entered.

### Scoring Systems & Categories

11. A ScoringSystem becomes locked once any competition references it; it cannot be edited after that.

12. When updating a locked ScoringSystem: set enabled=false, create new version with same name but different version string.

13. Only enabled ScoringSystem records can be assigned to new competitions.

14. CategoryTemplates are linked to ScoringSystems. When creating a competition, only templates linked to the selected scoring system are shown.

15. Categories are copied from templates to competition; after copying, they're independent and can be customized.

### Access Control

16. Mead makers can only view their scores after competition status = PUBLISHED.

17. Stewards have read-only access to all tables within their assigned competition.

18. Judges can only score entries assigned to tables they're assigned to.

---

## Enums

### TokenPurpose
- LOGIN
- JUDGING_SESSION

### CompetitionType
- COMMERCIAL
- HOME

### CompetitionStatus
- DRAFT
- REGISTRATION_OPEN
- REGISTRATION_CLOSED
- JUDGING
- COMPLETED
- PUBLISHED

### JudgingMode
- INDIVIDUAL
- CONSENSUS

### EntryCodeFormat
- SEQUENTIAL
- CATEGORY_PREFIXED

### EntryStatus
- SUBMITTED
- CHECKED_IN
- JUDGING
- JUDGED
- DISQUALIFIED

### JudgingRound
- CATEGORY
- MEDAL
- BOS

### JudgeTableAssignmentStatus
- SCHEDULED
- ACTIVE
- COMPLETED
- CANCELLED

---

## Score Language Codes

Use ISO 639-1 two-letter codes for the language field on Score:
- en: English
- pt: Portuguese
- es: Spanish
- de: German
- fr: French
- it: Italian
- nl: Dutch
- pl: Polish
- (etc.)

The UI should default to the current user's interface language when creating a score.
