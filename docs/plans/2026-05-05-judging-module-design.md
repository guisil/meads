# Judging Module — Design Document

**Started:** 2026-05-05
**Status:** Phase 1 ✅ complete (2026-05-05). Phase 2 in progress (started 2026-05-06).
Phase 2.A–2.F ✅ complete (2026-05-07) — state machine, retreat semantics,
§2.1 trigger re-frame, start trigger preconditions, COI similarity heuristic,
and judge MJP qualifications storage all decided. Remaining Phase 2:
field-level entity finalization for Phase 3.
**Module dependencies:** competition, entry, identity
**References:**
- `docs/specs/judging.md` — preliminary spec (post-rework naming)
- `docs/specs/awards.md` — sister module (boundary affected)
- `docs/reference/chip-competition-rules.md` — CHIP regulations

---

## How to use this document

This is a multi-session planning artifact. The goal is **session portability**: any future
session (even with cleared context) can read this doc + `docs/SESSION_CONTEXT.md` and pick
up where the last session left off.

Three living sections grow across sessions:
- **Decisions** — chronological log of what's been decided and why
- **Open Questions** — what's still pending, with current state (open / under-discussion / deferred)
- **Next Session: Start Here** — explicit marker for the next session's first action

Once a phase is complete, its open questions should all have decisions or be explicitly deferred.

---

## Planning Phases

| Phase | Goal | Status |
|---|---|---|
| 0 | Frame & set up tracking doc | ✅ Complete |
| 1 | Scope & module boundary decisions | ✅ Complete |
| 2 | Domain model — entity definitions, eager/lazy creation, COI heuristic, MJP qualifications storage, scoresheet locking | 🔄 In progress (2.A–2.F ✅; field finalization pending) |
| 3 | Service + event contracts, authorization, COI mechanism, judging start trigger | ⏳ Pending |
| 4 | View design (admin table mgmt, judge scoresheet UX, results-before-publication) | ⏳ Pending |
| 5 | Implementation sequencing — TDD cycle order, migration plan, MVP slice | ⏳ Pending |

---

## Next Session: Start Here

**Phase 2.A–2.F all complete (2026-05-07).**
- 2.A: three-tier state model with three independent aggregates (§1.5-A).
- 2.B: retreat semantics across three tiers + compensating events +
  `DivisionStatusRevertGuard` (§2.B).
- 2.C: §2.1 trigger re-frame confirmed.
- 2.D: start trigger preconditions and behaviors; `Division.minJudgesPerTable`
  added (§2.D).
- 2.E: COI similarity heuristic — country-aware suffix-stripping +
  Levenshtein ≤ 2 (§2.E).
- 2.F: `JudgeProfile` aggregate added; v1 scoresheet PDF stays anonymized
  (§2.F).

§Q1, §Q7, §Q8, §Q10, §Q11, §Q12, §Q13 fully resolved. **All Phase 2 design
questions closed.**

### What next session must address: field-level entity finalization

Last Phase 2 step. Mechanical pass over all 7 aggregates and child entities
to produce the Phase 3-ready entity definition section. For each aggregate:

- Field-by-field types, JPA annotations, nullability, column lengths.
- `@PrePersist` / `@PreUpdate` for timestamps where applicable.
- Invariants (state machine guards, FK rules, UNIQUE constraints).
- Domain methods on the aggregate root (e.g. `Judging.advancePhase()`,
  `JudgingTable.startRound1()`, `Scoresheet.submit()`,
  `CategoryJudgingConfig.startMedalRound()`).
- No-arg protected constructor for JPA + public constructor with required
  business fields.

**Aggregates to finalize:**
1. `Judging` (§1.5-A)
2. `JudgingTable` + child `JudgeAssignment` (§1.5-A, §1.3, §1.4)
3. `CategoryJudgingConfig` (§1.5-A)
4. `Scoresheet` + child `ScoreField` (§1.3, §1.10)
5. `MedalAward` (§1.7)
6. `BosPlacement` (§1.7)
7. `JudgeProfile` (§2.F)

**Plus competition-module change:**
- `Division.minJudgesPerTable` (§2.D) — Integer NOT NULL DEFAULT 2.

After field finalization, Phase 2 is closed and Phase 3 (service + event
contracts, authorization, COI mechanism, judging start trigger
implementation contracts) can begin.

### Suggested start prompt for next session
> "Read `docs/plans/2026-05-05-judging-module-design.md` and `docs/SESSION_CONTEXT.md`,
> then begin the field-level entity finalization (last Phase 2 step) — start with
> `Judging` and `JudgingTable`."

---

## Decisions

### 2026-05-05 — Phase 1.1: Module boundary

**Decision:** Option B — judging covers Round 1 (scoresheets) + Medal Round + BOS.
Awards module is responsible only for the final arrangement, publication, and
entrant-facing results views.

**Rationale:** All score/medal/BOS decisions are made by judges during the judging
event itself. Awards is a downstream read model.

**Implication:** Awards spec needs revision later — its current design absorbs medal
round and BOS, which now live in judging.

### 2026-05-05 — Phase 1.2: MVP scope

**Decision:** All three rounds (Round 1, Medal Round, BOS) must be functional for
the next CHIP edition. No phased rollout.

### 2026-05-05 — Phase 1.3: Scoresheet cardinality

**Decision:** One scoresheet per entry. One judge per table records the scoresheet
on behalf of the table; other judges taste and discuss but don't have individual
scoresheets in the system.

**Future:** Multi-mode (per-judge scoresheets) may be added later but is out of
scope for v1.

**Implication:** The preliminary spec's `JudgeAssignment → Scoresheet` relationship
changes. Scoresheet relates to a `JudgingTable`, not a `JudgeAssignment`. The
"which judge filled it in" link is informational, not the ownership relation.

### 2026-05-05 — Phase 1.4: Conflict of interest

**Decision (v1):**
- **Hard block** on a judge's own entries (judge.userId == entry.userId).
- **Soft warning** when the entry's entrant has a similar meadery name to any
  meadery name associated with the judge. (Similarity heuristic TBD — start
  simple: case-insensitive equality + Levenshtein-distance threshold, or a
  normalized-string match. Revisit in Phase 2/3.)
- **No COI list** for v1 (admin-managed list of declared conflicts). CHIP first
  edition is small enough to handle "direct professional relationship" manually.

**Future:** Admin-managed COI list (judge → blocked entrants/meaderies) when
the competition scales.

### 2026-05-05 — Phase 1.5: Workflow state machine + per-category Medal Round mode

> **⚠️ State machine portion superseded by Phase 2.A (2026-05-07) — see §1.5-A
> below.** The per-category Medal Round mode (COMPARATIVE / SCORE_BASED) survives,
> with a tie-handling rule added in 2.A. The original linear `JudgingPhase` enum
> is replaced by a three-tier model (division / table / per-category). This entry
> is retained for historical context.

**Decision (state machine — superseded):** Option C. Keep `DivisionStatus` linear
(DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION →
RESULTS_PUBLISHED) unchanged. Introduce a fine-grained `JudgingPhase` enum on a
new `Judging` aggregate (one per division):

```
JudgingPhase: NOT_STARTED → ROUND_1 → MEDAL_ROUND → BOS → COMPLETE
```

`DivisionStatus.JUDGING` covers ROUND_1 → BOS. When `JudgingPhase = COMPLETE`,
admin can advance `DivisionStatus` to DELIBERATION.

**Decision (per-category Medal Round mode):** Each judging-scope `DivisionCategory`
can be configured (during JUDGING phase, before MEDAL_ROUND begins) to use one of
two modes for medal determination:

| Mode | Behavior |
|------|----------|
| **COMPARATIVE** (default) | Separate comparative tasting in MEDAL_ROUND phase. Judges decide medals via direct comparison. No additional scoresheets. |
| **SCORE_BASED** | Medals derived from Round 1 scores (highest = gold, next = silver, next = bronze). Judges may still withhold individual medals. Used when category has too few entries to justify a separate comparative round. |

**Rationale:** small categories make a separate comparative tasting wasteful; the
CHIP organisers want the option to fall back to score-based medals while
preserving judge discretion to withhold.

**Storage:** new entity `CategoryJudgingConfig` in judging module, references
`divisionCategoryId` (must be a JUDGING-scope category). Field: `medalRoundMode`
(enum). Created lazily when admin first configures judging for a category.

**Terminology confirmed (2026-05-05):** user originally said "BOS round" but
meant Medal Round. Decision stands as "per-category Medal Round mode".

### 2026-05-05 — Phase 1.6: BOS configurability

**Decision:** `bosPlaces` integer field on `Division`, NOT NULL DEFAULT 1, set
during DRAFT/REGISTRATION_OPEN. Locked once division advances past
REGISTRATION_OPEN.

CHIP examples: Amadora = 3, Profissional = 1.

**Out of scope for v1:** prize amounts (display/reporting only — would live on
awards/competition module if added).

### 2026-05-05 — Phase 1.7: Medal/award data structure

**Decision:** Two separate entities in the judging module:

**`MedalAward`** — one per (entry, division) when judges decide a medal.
- `entryId` (UUID, NOT NULL)
- `divisionId` (UUID, NOT NULL)
- `finalCategoryId` (UUID, NOT NULL — must be a JUDGING-scope category)
- `medal` (enum: GOLD / SILVER / BRONZE; nullable — null = withheld but explicitly
  recorded; absence of MedalAward row = entry didn't reach medal round)
- `awardedAt` (Instant)
- `awardedBy` (judge userId, NOT NULL — who entered it)

UNIQUE(entryId).

**`BosPlacement`** — one per (division, place 1..bosPlaces) when BOS is decided.
- `divisionId` (UUID, NOT NULL)
- `entryId` (UUID, NOT NULL)
- `place` (Integer, NOT NULL — 1, 2, 3...)
- `awardedAt` (Instant)
- `awardedBy` (judge userId, NOT NULL)

UNIQUE(divisionId, place). UNIQUE(divisionId, entryId) (an entry can't get two BOS places).

**Decision (drop withholding-rationale):** No free-text rationale field for v1.
A `MedalAward` row with `medal=null` is sufficient to mark withheld; judges can
use entry-level comments if explanation is needed.

**Confirmed (2026-05-05):** user confirmed dropping the rationale field.

### 2026-05-05 — Phase 1.8: Session granularity

**Decision:** No top-level `JudgingSession` entity in v1. The aggregates are:

```
Judging (per division)
  └─ JudgingTable (per category being judged at a table)
       ├─ JudgeAssignment (judge ↔ table)
       └─ Scoresheet (one per entry; Scoresheet → Entry; relates to Table, not to a single judge)
```

A "session" in real life = a date grouping of tables. We can add an optional
`JudgingTable.scheduledDate` field for date display/grouping in views without
modeling a session entity. Multi-day, multi-session real-world flow is supported
through table-level dates.

### 2026-05-05 — Phase 1.9: Round 1 → Medal Round advancement

**Decision:** No automatic threshold or top-N rule. Judges decide on the spot
which entries advance to medal round, per category.

**Storage:** boolean flag on `Scoresheet` — `advancedToMedalRound` (default false).
Set when Round 1 scoresheet is finalized; admin/judge can toggle until MEDAL_ROUND
phase begins.

**Alternative considered (rejected):** separate `MedalRoundEntry` join table.
Overkill — the advancement is always entry-level Round 1 decision, the boolean
on the scoresheet captures it cleanly.

**SCORE_BASED categories:** advancement is implicit — top scores are auto-considered.
Judges still need to explicitly mark medal/withhold per entry in MEDAL_ROUND.

### 2026-05-05 — Phase 1.10: MJP scoresheet structure

**Source:** `docs/reference/Short-version-of-MJP-scoring-sheet-V3.0.pdf` (V3.0).

**Score fields (5):**

| Field | Max | Tiers (label : range) |
|-------|-----|------------------------|
| Appearance | 12 | Unacceptable 0–2, Below Average 3–4, Average 5–6, Good 7–8, Very Good 9–10, Perfect 11–12 |
| Aroma/Bouquet | 30 | Unacceptable 0–5, Below Average 6–10, Average 11–15, Good 16–20, Very Good 21–25, Perfect 26–30 *(PDF shows "25–30" — treated as typo)* |
| Flavour and Body | 32 | Unacceptable 0–5, Below Average 6–10, Average 11–15, Good 16–20, Very Good 21–26, Perfect 27–32 |
| Finish | 14 | Unacceptable 0–2, Below Average 3–4, Average 5–6, Good 7–8, Very Good 9–11, Perfect 12–14 |
| Overall Impression | 12 | Unacceptable 0–2, Below Average 3–4, Average 5–6, Good 7–8, Very Good 9–10, Perfect 11–12 |

**Total max:** 100.

**Per-field artifacts:** numeric score (0..max), free-text comments. Tier labels
are guidance (UI hints), not stored values.

**Header data on PDF (already on Entry):** category/subcategory, entry number,
sweetness, strength, carbonation, additional info.

**Header data not yet on Entry / Judge:**
- Judge MJP qualifications (level + certifications: EMMA, AMMA, BJCP, Other) —
  per-judge profile data, not per-scoresheet. **Phase 2 question:** add to User
  entity? Or new Judge profile entity?

**Implementation note:** Score fields per scoresheet are denormalized at creation
(name + max copied from a `ScoringSystemFieldDefinition` table) so historical
scoresheets stay valid if the scoring system definition changes. This matches
the preliminary spec's design.

### 2026-05-06 — Phase 2.1: Eager scoresheet creation + recategorization sync rule

**Decision (§Q8):** Eager scoresheet creation. When admin starts a table
(`JudgingTable.NOT_STARTED → ROUND_1`, per §1.5-A 2026-05-07), the system creates
one empty `Scoresheet` per entry that has `finalCategoryId` set to that table's
category, attached to that `JudgingTable`.

- Entries without `finalCategoryId` when the table starts get no scoresheet —
  admin must categorize them first.
- New entries created (or assigned `finalCategoryId`) **after** the relevant
  table is in ROUND_1 auto-create their scoresheet at that table.

> **Note (2026-05-07):** Original wording said "When admin advances `JudgingPhase`
> to `ROUND_1`" — re-framed to per-table after Phase 2.A redesigned the state
> machine. The sync rule itself is unchanged.

**Decision (recategorization sync rule):** `entry.finalCategoryId` remains
mutable after ROUND_1 starts. When admin reassigns it, sync rule applies based
on the existing scoresheet state:

| Scoresheet state | Behavior on recategorization |
|---|---|
| **No scoresheet** (new entry, or just categorized) | Auto-create empty scoresheet at the new category's table (only if `JudgingPhase >= ROUND_1`). |
| **DRAFT, no fields filled** | Update `tableId` to the new category's table. |
| **DRAFT, partially filled** | Confirmation prompt to admin. On confirm: update `tableId`, preserve scores. UI marks scoresheet "moved from T1" for transparency. |
| **SUBMITTED** | **Block** — admin must explicitly revert scoresheet to DRAFT first (see §Q13). |

**Invariants on `Scoresheet`:**
- `tableId` is mutable (admin-only via service).
- For DRAFT scoresheets: `scoresheet.tableId.divisionCategoryId == entry.finalCategoryId` (service-enforced).
- SUBMITTED scoresheet's `tableId` is effectively immutable — captured at submission.

**Rationale:** Recategorization of single entries is common enough (initial
miscategorization, re-evaluation by judges) that requiring full phase retreat
would be too heavy. The sync rule keeps scoresheet/table coherent without
hidden state.

**Implications recorded for Phase 2 follow-ups:**
- §Q11 phase retreat is now decoupled from per-entry recategorization (retreat
  becomes purely an escape hatch for bigger mistakes).
- §Q13 SUBMITTED revertibility is now load-bearing — the recategorization
  workflow assumes admin can revert SUBMITTED → DRAFT.

### 2026-05-07 — Phase 2.A: Three-tier state machine (supersedes §1.5 state machine)

**Decision (D1+D2+D3 from 2026-05-07 conversation).** The per-division
`JudgingPhase` enum from §1.5 doesn't capture how CHIP judging actually flows:
tables progress independently, Medal Round happens at the category level, and a
single category can be split across multiple tables that move at different
speeds. Replace with a three-tier model — division, table, and per-category
medal-round state — backed by three independent aggregates.

#### Tier 1: Division-level — `Judging.phase : JudgingPhase`

```
NOT_STARTED → ACTIVE → BOS → COMPLETE
```

- **NOT_STARTED**: tables can be configured (categories assigned, judges
  added) but no scoresheets exist. Auto-leaves on first table start.
- **ACTIVE**: Round 1 + Medal Round work happens per-table/per-category.
  This is where most of judging life lives.
- **BOS**: division-wide head-judge round. Gated — only entered when every
  judging-scope category has its medal round COMPLETE.
- **COMPLETE**: BOS finalized. Admin can advance `DivisionStatus` to DELIBERATION.

`DivisionStatus.JUDGING` covers `ACTIVE → BOS`; `DELIBERATION` is reached
once `Judging.phase = COMPLETE`.

#### Tier 2: Per-table — `JudgingTable.status : JudgingTableStatus`

```
NOT_STARTED → ROUND_1 → COMPLETE
```

- **NOT_STARTED**: judge assignments mutable, scoresheets not yet created.
- **ROUND_1**: scoresheets eager-created (per §2.1) on entry into this state.
  Judges fill them in.
- **COMPLETE**: all scoresheets at this table are SUBMITTED.

A table covers one (judging-scope `divisionCategoryId`, physical group)
pairing. Two tables judging the same category are independent until both
COMPLETE — then the category becomes READY for medal round.

#### Tier 3: Per-category medal round — `CategoryJudgingConfig.medalRoundStatus : MedalRoundStatus`

```
PENDING → READY → ACTIVE → COMPLETE
```

- **PENDING**: at least one table covering this category is not yet COMPLETE.
- **READY**: every table covering this category is COMPLETE. Auto-derived;
  visible to admin.
- **ACTIVE**: medal-round work in progress. Behavior depends on `medalRoundMode`:
  - **COMPARATIVE**: judges convene for direct comparative tasting; no scoresheets;
    medals decided by discussion.
  - **SCORE_BASED**: system computes provisional medals from Round 1 totals; judges
    review, **resolve ties manually**, and may withhold individual medals.
- **COMPLETE**: `MedalAward` rows finalized for this category.

#### Medal round modes (refines §1.5)

| Mode | Behavior in ACTIVE |
|---|---|
| **COMPARATIVE** (default) | Separate comparative tasting. Judges decide medals via direct comparison. No additional scoresheets. |
| **SCORE_BASED** | System ranks Round 1 totals and proposes Gold/Silver/Bronze. **Ties at any boundary block finalization** — judges must manually resolve (elevate, demote, or withhold). Judges may also withhold any medal regardless of score. |

The original §1.5 explicit "skip Medal Round entirely" intuition is folded
into SCORE_BASED — the act of "skipping the gathering" is just SCORE_BASED's
auto-population, while the ACTIVE state still exists as the brief review
window. **No third `NONE` mode** is introduced.

`CategoryJudgingConfig` is created lazily when admin first configures a
judging-scope category for medal round (default mode: COMPARATIVE).

#### State transitions: auto vs explicit

| Transition | Trigger | Notes |
|---|---|---|
| Division `NOT_STARTED → ACTIVE` | **Auto** on first table start | No "Start Judging" button needed |
| Table `NOT_STARTED → ROUND_1` | **Explicit** ("Start this table") | Triggers eager scoresheet creation per §2.1 |
| Table `ROUND_1 → COMPLETE` | **Auto** when all scoresheets SUBMITTED | Mechanical |
| Category `PENDING → READY` | **Auto-derived** from table completion | Read-side projection; not a stored transition action |
| Category `READY → ACTIVE` | **Explicit** ("Start Medal Round") | Both modes; UI populates suggested medals for SCORE_BASED |
| Category `ACTIVE → COMPLETE` | **Explicit** ("Finalize medals") | All `MedalAward` decisions recorded; ties resolved if SCORE_BASED |
| Division `ACTIVE → BOS` | **Explicit** ("Start BOS"), gated | Gate: every category COMPLETE |
| Division `BOS → COMPLETE` | **Explicit** ("Finalize BOS") | All `BosPlacement` rows recorded |

Retreat (un-do) transitions are §Q11 territory — to be resolved in Phase 2.B.

#### Aggregate scope (D3 — three independent aggregates)

```
Judging                  (one per division — phase only)
JudgingTable             (one per (judging-category, physical group) — owns JudgeAssignment[])
CategoryJudgingConfig    (one per judging-scope DivisionCategory — mode + medalRoundStatus)
Scoresheet               (one per entry — owns ScoreField[])
MedalAward               (per §1.7 — unchanged)
BosPlacement             (per §1.7 — unchanged)
```

All cross-aggregate references are **UUID FKs only** (no `@OneToMany` between
aggregates) — same convention as `Division`/`Participant`, `Entry`/`EntryCredit`
in the existing modules. `JudgeAssignment` and `ScoreField` are children
within their parent aggregate (small, bounded, no cross-cutting query needs).

**Why three aggregates instead of one fat `Judging`:**
- Independent lifecycles — a table progresses without locking the division
  aggregate or sibling tables.
- Smaller transactional units — submitting a scoresheet doesn't load every
  table in the division.
- Modulith-clean — all six aggregates live in the `app.meads.judging` package;
  modulith verification doesn't see them as boundaries.
- Matches existing codebase convention (Entry/EntryCredit are separate aggregates
  with separate repositories, no parent-owns-children JPA relationship).

`Judging.divisionId` carries a UNIQUE constraint (one Judging per division).
`CategoryJudgingConfig.divisionCategoryId` carries a UNIQUE constraint (one
config per judging-scope category).

#### Implications

- **§Q1** (workflow state machine) — fully resolved by §1.5-A.
- **§Q11** (retreat) — re-asked under three-tier model; queued for Phase 2.B.
- **§Q12** (start trigger) — re-framed as three triggers (per-table,
  per-category, division BOS); queued for Phase 2.D.
- **§2.1** trigger now reads as per-table, not division-level — sync rule
  itself unchanged.
- **§1.8** working sketch superseded by aggregate diagram in §Discussions
  (updated below).

### 2026-05-07 — Phase 2.B: Retreat semantics across the three tiers (resolves §Q11)

**Decision (D4+D5+D6+D7 from 2026-05-07 conversation).** Retreat is layered:
small steps back preserve data; larger steps require explicit wipe. Every
advance has a compensating retreat event so downstream modules can reverse
durable effects.

#### Tier 0: Per-scoresheet revert — the foundation

`Scoresheet.status: SUBMITTED → DRAFT` (admin-only action). **Score values
preserved**; admin can edit the existing draft.

**Guard:** the scoresheet's table's category must have
`medalRoundStatus ∈ {PENDING, READY}`. If `ACTIVE` or `COMPLETE`, block —
admin must retreat the medal round first (Tier 2).

This also resolves §Q13: SUBMITTED scoresheets are revertible by admin only.

#### Tier 1: Per-table retreat — *implicit only*

`JudgingTable.status: COMPLETE → ROUND_1` is **derived**, not a separate
admin action:
- Auto when any scoresheet at the table flips back to DRAFT.
- Auto when a new DRAFT scoresheet appears at the table (via §2.1
  recategorization sync, or new entry assigned the table's category).
- Mirror of the existing auto `ROUND_1 → COMPLETE` rule.

**Rationale (D4):** there's no scenario where admin needs to "reopen a
table" without touching any scoresheet. If a real need surfaces later,
"reopen table" can become syntactic sugar for "revert all SUBMITTED
scoresheets at this table" — deferred.

#### Tier 2: Per-category Medal Round retreat — explicit

| Transition | Action | Effect on `MedalAward` rows |
|---|---|---|
| `COMPLETE → ACTIVE` | "Reopen medals" | **Preserve** — rows unlock for edit |
| `ACTIVE → READY` | "Reset medals" (confirmation) | **Wipe** all rows for this category |

**Guard:** `Judging.phase = ACTIVE`. Block if division is in `BOS` or
`COMPLETE` — admin must retreat division first (Tier 3).

**Wipe-on-`ACTIVE → READY` rationale:** READY semantically means "ready
to decide, no decisions made yet." Preserving stale rows would create a
zombie state. Confirmation prompt protects against accidental clicks.

#### Tier 3: Division-level retreat — explicit, asymmetric

| Transition | Action | Effect |
|---|---|---|
| `COMPLETE → BOS` | "Reopen BOS" | **Preserve** `BosPlacement` rows; unlock for edit |
| `BOS → ACTIVE` | "Reset BOS" | **Block** unless all `BosPlacement` rows already deleted |

**No-auto-wipe rationale (`BOS → ACTIVE`):** BOS placements are high-stakes
(CHIP Amadora has €100/€50/€25 prizes). Auto-wiping as a side effect of
retreat is too dangerous. Admin must explicitly delete each `BosPlacement`
via a separate UI action — that becomes the visible "I really mean it"
gate before retreat is allowed.

**Mental model summary:** small step back preserves; bigger step back
requires explicit wipe.

#### Compensating events (D6)

Every state-advance event has a paired retreat event, published
**synchronously inside the same transaction** as the state change. Empty
record types defined up front so downstream modules (awards, future)
subscribe to both directions from day 1.

| Advance event | Retreat event | Trigger |
|---|---|---|
| `ScoresheetSubmittedEvent` | `ScoresheetRevertedEvent` | Tier 0 revert |
| `TableCompletedEvent` | `TableReopenedEvent` | Tier 1 implicit retreat |
| `MedalRoundActivatedEvent` | `MedalRoundResetEvent` | Tier 2 ACTIVE → READY |
| `MedalRoundCompletedEvent` | `MedalRoundReopenedEvent` | Tier 2 COMPLETE → ACTIVE |
| `BosStartedEvent` | `BosResetEvent` | Tier 3 BOS → ACTIVE |
| `BosCompletedEvent` | `BosReopenedEvent` | Tier 3 COMPLETE → BOS |

Awards (when implemented) subscribes to both directions and reverses
durable effects (e.g. un-publish ranking, send "results revised" email).

#### DivisionStatusRevertGuard (D7)

Judging module registers a `DivisionStatusRevertGuard` impl on top of the
existing competition-module guard interface pattern (mirrors
`EntryDivisionRevertGuard`):

- Blocks `DivisionStatus: JUDGING → REGISTRATION_CLOSED` when any judging
  data exists for the division (any `JudgingTable` exists, or
  `Judging.phase != NOT_STARTED`).
- Blocks `DivisionStatus: DELIBERATION → JUDGING` if `Judging.phase =
  COMPLETE` and any awards-side state has been published (deferred until
  awards module exists; for now, no-op).

This keeps the competition module unaware of judging internals — it just
asks registered guards "is retreat allowed?" before acting.

#### Service API sketch (for Phase 3)

```
ScoresheetService.revertScoresheet(scoresheetId, adminUserId)
  // SUBMITTED → DRAFT; guard medalRoundStatus ∈ {PENDING, READY}; publishes ScoresheetRevertedEvent
  // Implicit side-effect: table COMPLETE → ROUND_1 if applicable, publishes TableReopenedEvent

JudgingService.reopenMedalRound(divisionCategoryId, adminUserId)
  // COMPLETE → ACTIVE; guard Judging.phase = ACTIVE; preserves MedalAward; publishes MedalRoundReopenedEvent

JudgingService.resetMedalRound(divisionCategoryId, adminUserId)
  // ACTIVE → READY; guard Judging.phase = ACTIVE; deletes MedalAward rows for category; publishes MedalRoundResetEvent

JudgingService.reopenBos(divisionId, adminUserId)
  // COMPLETE → BOS; preserves BosPlacement; publishes BosReopenedEvent

JudgingService.resetBos(divisionId, adminUserId)
  // BOS → ACTIVE; guard: zero BosPlacement rows exist; publishes BosResetEvent

JudgingService.deleteBosPlacement(placementId, adminUserId)
  // standalone — must be called repeatedly until none remain before resetBos() is allowed
```

#### Implications

- **§Q11** (retreat) — fully resolved.
- **§Q13** (SUBMITTED scoresheet revertibility) — fully resolved as a
  side effect: admin-only revert at Tier 0.
- **§2.1** sync rule's "block on SUBMITTED" wording stays — admin must
  call `revertScoresheet` first before recategorization moves a SUBMITTED
  scoresheet's table.

### 2026-05-07 — Phase 2.D: Start trigger preconditions and behaviors (resolves §Q12)

**Decision (D8–D12 from 2026-05-07 conversation).** Three explicit start
actions (already settled by §1.5-A) — preconditions and entry behaviors
specified below. Division `NOT_STARTED → ACTIVE` remains auto on first
table start (no separate trigger).

#### "Start this table" — `JudgingTable.NOT_STARTED → ROUND_1`

**Preconditions (hard blocks):**
- Table has `divisionCategoryId` set.
- `JudgeAssignment count >= Division.minJudgesPerTable`.

**Soft confirmation prompt:**
- If no entry has `finalCategoryId` matching the table's category, the
  start dialog asks "This table has no entries yet. Start anyway?" Admin
  may proceed — the table sits in ROUND_1 with zero scoresheets, and §2.1
  auto-creates them as entries get categorized.

**Entry behavior:**
- Per §2.1: create one DRAFT `Scoresheet` per entry whose `finalCategoryId`
  matches the table's category, attached to this table.
- Publish `TableStartedEvent` (advance counterpart of `TableReopenedEvent`).

**New field on Division (D8):**
- `minJudgesPerTable: Integer` (NOT NULL, DEFAULT 2).
- Editable from DRAFT through REGISTRATION_CLOSED; locked once any
  `JudgingTable` in the division has `status != NOT_STARTED`.
- Hard-block at "Start this table" (no admin override). If a competition
  needs a lower minimum, set the field ahead of time.
- Default 2 matches CHIP §7 / general MJP practice.

#### "Start Medal Round" — `CategoryJudgingConfig.medalRoundStatus: READY → ACTIVE`

**Precondition (hard block):**
- Every `JudgingTable` for this `divisionCategoryId` has `status = COMPLETE`.
  (`PENDING → READY` is auto-derived per §1.5-A; admin can only act once
  the status is READY.)

**Entry behavior depends on `medalRoundMode`:**

**COMPARATIVE:**
- No `MedalAward` rows auto-created.
- Judges manually create `MedalAward` rows during ACTIVE for entries they
  award (gold/silver/bronze).
- Withholding a medal = simply not creating a row for that medal slot
  (e.g. judges award only bronze → no gold or silver rows created → gold
  and silver implicitly withheld).

**SCORE_BASED auto-population algorithm (D10):**

On READY → ACTIVE, the system attempts to auto-assign medals:

1. Sort entries in the category by `Scoresheet.totalScore DESC`.
2. Walk medal slots in order (Gold → Silver → Bronze):
   - If exactly one entry has the highest remaining score → auto-create
     `MedalAward(medal=GOLD/SILVER/BRONZE)`. Continue to next slot.
   - If multiple entries tie at the highest remaining score → no row
     created. **Stop the auto-assignment cascade** — all subsequent slots
     are also unresolved (their candidates depend on judges' resolution
     above).
3. Stop when no entries remain (e.g. category has only 2 entries: only
   Gold + Silver candidates exist).

**Worked example (from 2026-05-07 conversation):**

Scores: 80, 80, 75, 75, 65, 64.
- Gold slot: tied at 80 → no row, cascade stops.
- Silver, Bronze: no rows.
- Judges resolve everything manually from gold down. If they withhold
  gold, silver candidates become the two 80s (still tied, manual choice
  required). And so on.

**During ACTIVE (both modes):**
- Judges may create, edit, or delete `MedalAward` rows.
- A row's `medal` field can be set to `null` to **explicitly record withhold**
  on an auto-populated entry that judges decide doesn't deserve the medal
  (rare — most withholds happen by leaving rows uncreated).
- UNIQUE(entryId) on `MedalAward` (per §1.7) prevents double-medalling.

**Withhold semantics (D11, narrow interpretation):**

| Situation | Row state |
|---|---|
| Entry awarded a medal | Row exists, `medal = GOLD/SILVER/BRONZE` |
| Auto-populated entry that judges decide to withhold | Row exists, `medal = null` |
| Entry never a candidate (below cutoff or tie-cascade-stopped) | **No row** |
| COMPARATIVE entry not awarded | **No row** |

`medal = null` is reserved for "considered as a candidate, explicitly
withheld" — not for non-candidates. Categories with 50 entries don't
generate 47 noise rows.

**Events:** `MedalRoundActivatedEvent` on READY → ACTIVE;
`MedalRoundCompletedEvent` on ACTIVE → COMPLETE.

#### "Start BOS" — `Judging.phase: ACTIVE → BOS`

**Precondition (hard block):**
- Every `CategoryJudgingConfig` for the division has `medalRoundStatus = COMPLETE`.

**No precondition on GOLD count (D12):** start is allowed even if zero
GOLD `MedalAward` rows exist across the division (degenerate "no entries
deserved gold" case). UI surfaces this via info message
("No GOLD medals were awarded; BOS round has no candidates"). Admin
clicks "Finalize BOS" with zero `BosPlacement` rows → `BOS → COMPLETE`.

**Why no GOLD-count precondition:** CHIP §7 mandates "Only Gold medal
winners advance" — this is the input filter, not a start gate. Empty
input is a valid (if vanishingly rare) state. Keeping the gate clean
on category-completion alone avoids hidden state paths.

**Entry behavior:**
- BOS UI lists all entries with `MedalAward.medal = GOLD` in the division.
- Judges assign `BosPlacement(place: 1..bosPlaces)` to entries.
- Per §1.7: `UNIQUE(divisionId, place)` and `UNIQUE(divisionId, entryId)`.

**Events:** `BosStartedEvent` on ACTIVE → BOS;
`BosCompletedEvent` on BOS → COMPLETE.

#### Service API addition (for Phase 3)

```
JudgingService.startTable(tableId, adminUserId)
  // NOT_STARTED → ROUND_1; guards: category set, judges >= minJudgesPerTable
  // optional confirmation flag for empty-category case
  // creates DRAFT scoresheets per §2.1; publishes TableStartedEvent

JudgingService.startMedalRound(divisionCategoryId, adminUserId)
  // READY → ACTIVE; guard: all tables for category COMPLETE
  // SCORE_BASED: runs auto-population algorithm; publishes MedalRoundActivatedEvent

JudgingService.startBos(divisionId, adminUserId)
  // ACTIVE → BOS; guard: all CategoryJudgingConfig.medalRoundStatus = COMPLETE
  // publishes BosStartedEvent
```

#### Implications

- **§Q12** (start trigger) — fully resolved.
- **Division entity** gains `minJudgesPerTable: Integer NOT NULL DEFAULT 2`.
  Migration when judging module is implemented (in the judging-module's
  initial migration or a competition-module migration paired with it).
- **Aggregate sketch (§1.8)** unchanged — `minJudgesPerTable` is a
  Division field, not a judging-module entity.

### 2026-05-07 — Phase 2.E: COI similarity heuristic (resolves §Q7)

**Decision (D13 from 2026-05-07 conversation).** Country-aware normalization
followed by a strict Levenshtein-distance match on normalized strings. Soft
warning surface only — never blocks an admin/judge action.

#### Algorithm

1. **Cross-country skip gate.** If both `judge.country` and `entrant.country`
   are non-null and different, return no warning immediately. Different
   jurisdictions almost always mean different businesses, and the hard-block
   on `judge.userId == entry.userId` already catches the rare multinational
   case where a single person acts in both countries.
2. Otherwise, determine combined suffix list:
   `global ∪ suffixes(judge.country) ∪ suffixes(entrant.country)`
   (one country may be null — fall back to the global list for that side;
   if both are null, just the global list applies).
3. Normalize each meadery name:
   - lowercase
   - replace non-alphanumeric with space
   - strip every entry in the combined suffix list (whole-word match)
   - collapse whitespace, trim
4. Compare normalized strings:
   - exact match → warn
   - Levenshtein distance ≤ 2 → warn
   - otherwise → no warning

If both meadery names are null/blank, no comparison is performed (no warning).

#### Initial suffix lists (v1)

| Country code(s) | Business suffixes | Mead suffixes |
|---|---|---|
| **Global / EN / US / GB / IE** | llc, inc, ltd, co, corp, plc | meadery, mead, meads, meadworks, cellars, farm, brewery |
| PT / BR | lda, sa, ldª | hidromelaria, hidromelina |
| ES / MX / AR | sl, sa, srl | hidromielería, hidromelería, hidromiel |
| IT | srl, spa, sas, sapa | idromeleria, idromele |
| PL | sp z o o, sa, sk | miodosytnia, pasieka, miód |
| FR | sarl, sas, eurl, sa | hydromellerie, hydromel |
| DE / AT / CH | gmbh, ag, ohg, kg | metherei, metmacherei, metbrauerei |
| NL / BE | bv, nv | meddrijf, mede |

Unsupported countries fall back to the global list only. Lists curated by
the developer; expand as new entrant countries appear.

#### Where it lives

- Helper: `app.meads.judging.internal.MeaderyNameNormalizer` (or a
  service-level component). Static normalization + similarity check.
- Suffix lists: constant `Map<String, Set<String>>` keyed by ISO 3166-1
  alpha-2 country code, plus a `GLOBAL` key for the fallback. Compile-time
  constant, not externalized in v1.

#### Where it's called

- Admin assigning a judge to a table — UI shows a warning per entry at
  the table that has a similar meadery to the judge's.
- Judge UI when opening a scoresheet — banner if the entry's meadery is
  similar to the judge's own.
- Both call paths produce a warning surface only; no data-layer block.

#### Coverage and trade-offs

**Catches:**
- Suffix variations within a country: "Acme Meadery" vs "Acme Meads Co."
  (both → "acme").
- Country-internal typos: "Honey Hill" vs "Honey Hil" (distance 1).
- Diacritic variations: "Casa do Mel" vs "Casa do Mél" (distance 1).
- One-side-null country comparisons (e.g. judge unconfigured, entrant in PT).

**Skips (by design, per cross-country gate):**
- "Acme Meads LLC" (US) vs "Acme Hidromelaria, Lda." (PT) — different
  jurisdictions, no warning. The hard `userId == userId` block catches
  the multinational-same-owner case if it actually occurs.

**Misses (acceptable for v1; can extend later if surfaced):**
- Word reorders within a country: "Honey Hill" vs "Hill Honey" — Levenshtein
  doesn't catch these. Could add token-Jaccard if needed.
- Conceptual translations: "Bear Mountain Mead" vs "Hidromel da Montanha
  do Urso" — out of scope.

### 2026-05-07 — Phase 2.F: Judge MJP qualifications storage (resolves §Q10)

**Decision (D14 from 2026-05-07 conversation).** New `JudgeProfile`
aggregate in the judging module. No identity-module changes. Privacy-safe
v1: store qualifications but don't render them on scoresheet PDFs.

#### Schema

```
JudgeProfile (judging module — aggregate root)
  ├─ id : UUID
  ├─ userId : UUID (UNIQUE — optional 1:1 with User)
  ├─ certifications : Set<Certification> (@ElementCollection, may be empty)
  ├─ qualificationDetails : String (nullable, length 200 — free text: level, year, notes)
  └─ createdAt / updatedAt : Instant

Certification (enum, judging module)
  MJP, BJCP, OTHER
```

Storage of `certifications` via JPA `@ElementCollection` →
`judge_profile_certifications` join table:
```
judge_profile_id : UUID FK
certification    : VARCHAR(20)
PRIMARY KEY (judge_profile_id, certification)
```

#### Revisions from §1.10

§1.10's PDF transcription listed "level + certifications: EMMA, AMMA, BJCP,
Other" as separate items. This decision overrides:
- **No separate `mjpLevel` field.** Level is part of `qualificationDetails`
  free-text (e.g. "MJP Master, certified 2018"). MJP levels evolve;
  free-text avoids migration churn.
- **Certifications kept tight: `MJP, BJCP, OTHER`.** EMMA and AMMA dropped —
  member organizations, not certifications they grant; membership goes in
  `qualificationDetails`. `OTHER` retained for non-mead-specific but
  judging-relevant credentials (e.g. WSET Diploma from the wine world);
  the specific credential name lives in `qualificationDetails` when
  `OTHER` is checked.

#### Privacy / printing

- **v1: scoresheet PDF stays anonymized.** No judge details rendered.
- The "Judge MJP Qualifications" header item from §1.10 is **removed from
  the print scope** for v1. Deferred to a future "scoresheet template
  config" feature when per-jurisdiction privacy policies need to be
  honoured.

#### Why a separate aggregate (not on User)

- `Certification` is a judging-domain enum. Identity must not depend on
  judging (module direction: `entry → competition → identity → root`).
- Putting certifications on `User` would force the enum into the root
  module or downgrade to `Set<String>` (loss of type safety).
- `JudgeProfile` keeps identity module clean and provides natural
  extension points for future judge-specific data (availability,
  assignment history, performance metrics).

#### Lifecycle

- Profile created lazily — admin populates it via user-management UI, or
  the judge self-edits via an extension to `ProfileView`.
- Absence of a `JudgeProfile` row = no qualifications recorded; user is
  treated as an unqualified judge candidate (still allowed to judge if
  assigned by admin).
- Aggregate root pattern: `JudgeProfileService.createOrUpdate(userId, ...)`,
  `findByUserId(userId)`, `delete(userId)`.

#### Use cases retained for v1

- Admin filtering when assigning judges to tables ("show only MJP-certified").
- Internal organizational record (panel composition).
- Future audit trail.

#### Implications

- **§Q7** (COI similarity) — fully resolved by §2.E.
- **§Q10** (MJP qualifications storage) — fully resolved by §2.F.
- **§1.10** PDF header item "Judge MJP Qualifications" — descoped from v1
  scoresheet printing.
- **Aggregate sketch (§1.8)** gains a 7th aggregate: `JudgeProfile`.

---

## Open Questions

### Q1 — Workflow state machine
**Status:** ✅ Resolved by Decision §1.5-A (2026-05-07, three-tier model;
supersedes original §1.5).

### Q2 — Round 1 → Medal Round advancement rule
**Status:** ✅ Resolved by Decision §1.9 (judges mark advancement on the spot;
boolean flag on Scoresheet).

### Q3 — Where medal/award data lives
**Status:** ✅ Resolved by Decision §1.7 (`MedalAward` + `BosPlacement` entities).

### Q4 — Session granularity
**Status:** ✅ Resolved by Decision §1.8 (no JudgingSession entity; `Judging` per
division → `JudgingTable` directly under it).

### Q5 — BOS configurability
**Status:** ✅ Resolved by Decision §1.6 (`bosPlaces` Integer on Division).

### Q6 — MJP scoresheet field structure
**Status:** ✅ Resolved by Decision §1.10 (5 fields, max 100; structure recorded
from official MJP V3.0 PDF).

### Q7 — COI similarity heuristic
**Status:** ✅ Resolved by Decision §2.E (2026-05-07).
Cross-country gate (skip comparison if both countries are set and differ);
otherwise country-aware suffix-stripping normalization + Levenshtein distance
≤ 2 + exact-match-on-normalized. Soft warning only, never blocks. Initial
suffix lists cover EN/PT/ES/IT/PL/FR/DE/NL plus a global EN fallback.

### Q8 — Eager vs lazy scoresheet creation
**Status:** ✅ Resolved by Decision §2.1 (eager creation when ROUND_1 starts;
recategorization sync rule covers post-start changes).

### Q9 — Awards module rescope

**Decided implication of Phase 1.1:** Awards module shrinks. Need to revise
`docs/specs/awards.md` later.

**Status:** Deferred — handle when awards module is being designed.

### Q10 — Judge MJP qualifications storage
**Status:** ✅ Resolved by Decision §2.F (2026-05-07).
New `JudgeProfile` aggregate in judging module. Fields: `userId` (UNIQUE),
`certifications: Set<Certification>` (enum: MJP, BJCP, OTHER),
`qualificationDetails: String` (nullable, free-text for level/year/notes; also
specifies what `OTHER` is, e.g. WSET). v1 does NOT print judge details on
scoresheet PDFs (privacy-safe default; per-jurisdiction template config deferred).
§1.10 transcription overridden — no separate `mjpLevel` field; EMMA/AMMA dropped
(member organizations, not certifications).

### Q11 — Retreat allowed?
**Status:** ✅ Resolved by Decision §2.B (2026-05-07).
Per-scoresheet revert (Tier 0) admin-only with cascade guards;
per-table retreat implicit (Tier 1); medal round retreat explicit with
preserve/wipe asymmetry (Tier 2); division retreat asymmetric — preserve
on `COMPLETE → BOS`, require empty BosPlacements on `BOS → ACTIVE` (Tier 3).
Compensating events paired with every advance event. Judging module
registers a `DivisionStatusRevertGuard`.

### Q12 — Judging start trigger
**Status:** ✅ Resolved by Decision §2.D (2026-05-07).
Per-table: hard-block on judges < `Division.minJudgesPerTable` (default 2);
soft confirmation on empty category. Per-category: hard-block on tables-not-COMPLETE;
SCORE_BASED auto-populates with cascade-stop on first tie. Per-division:
hard-block on categories-not-COMPLETE; empty BOS allowed (UX info message).
New field: `Division.minJudgesPerTable`, NOT NULL DEFAULT 2, locked once any
table starts.

### Q13 — Are scoresheets locked once finalized?
**Status:** ✅ Resolved by Decision §2.B (2026-05-07, Tier 0).
SUBMITTED scoresheets are read-only to judges. Admin-only `revertScoresheet`
moves SUBMITTED → DRAFT, guarded by `medalRoundStatus ∈ {PENDING, READY}` for
the scoresheet's category. Score values preserved on revert.

---

## Discussions

(Working space for in-progress topics. Move resolved items to Decisions, leave
unresolved as Open Questions, delete obsolete content.)

### Working sketch — aggregate model after Phase 2.A

This is **not yet a finalized model** — remaining Phase 2 work pins down field
types, nullability, invariants, @PrePersist, and domain methods. Use as a mental
scaffold for Phase 2.B–2.D and Phase 3.

Cross-aggregate references are **UUID FKs only** (no JPA relationships between
aggregates), matching the codebase convention. JPA `@OneToMany` is used only for
within-aggregate children (JudgeAssignment, ScoreField).

```
Division (competition module)
  ├─ bosPlaces : int (NOT NULL DEFAULT 1)               [§1.6]
  └─ DivisionCategory (scope = JUDGING)                 [existing — competition module]

─── Aggregate roots (judging module) ──────────────────────────────────

Judging                                                  [§1.5-A]
  ├─ id : UUID
  ├─ divisionId : UUID (UNIQUE — one Judging per division)
  ├─ phase : JudgingPhase (NOT_STARTED → ACTIVE → BOS → COMPLETE)
  └─ createdAt / updatedAt : Instant

JudgingTable                                             [§1.5-A]
  ├─ id : UUID
  ├─ judgingId : UUID (FK → Judging)
  ├─ name : String
  ├─ divisionCategoryId : UUID (must be JUDGING-scope; same category may appear on multiple tables)
  ├─ scheduledDate : LocalDate (nullable, for grouping/display)  [§1.8]
  ├─ status : JudgingTableStatus (NOT_STARTED → ROUND_1 → COMPLETE)
  ├─ JudgeAssignment[] (within-aggregate child)         [§1.3, §1.4]
  │    ├─ judgeUserId : UUID
  │    └─ assignedAt : Instant
  └─ createdAt / updatedAt : Instant

CategoryJudgingConfig                                    [§1.5-A]
  ├─ id : UUID
  ├─ divisionCategoryId : UUID (UNIQUE; must be JUDGING-scope)
  ├─ medalRoundMode : MedalRoundMode (COMPARATIVE / SCORE_BASED)
  ├─ medalRoundStatus : MedalRoundStatus (PENDING → READY → ACTIVE → COMPLETE)
  └─ createdAt / updatedAt : Instant

Scoresheet (one per Entry, NOT one per judge)            [§1.3, §1.10]
  ├─ id : UUID
  ├─ tableId : UUID (FK → JudgingTable; mutable for DRAFT, captured on SUBMIT, see §2.1)
  ├─ entryId : UUID (UNIQUE per division)
  ├─ filledByJudgeUserId : UUID (informational — which judge entered it)  [§1.3]
  ├─ status : ScoresheetStatus (DRAFT, SUBMITTED)
  ├─ totalScore : Integer (computed on submit)
  ├─ overallComments : String
  ├─ advancedToMedalRound : boolean (default false)     [§1.9]
  ├─ submittedAt : Instant (nullable)
  ├─ ScoreField[] (within-aggregate child, 5 per scoresheet)  [§1.10]
  │    ├─ fieldName : String (Appearance / Aroma/Bouquet / Flavour and Body / Finish / Overall Impression)
  │    ├─ maxValue : int (12 / 30 / 32 / 14 / 12)
  │    ├─ value : Integer (nullable until filled)
  │    └─ comment : String
  └─ createdAt : Instant

MedalAward (one per medalled entry)                      [§1.7]
  ├─ id : UUID
  ├─ entryId : UUID (UNIQUE)
  ├─ divisionId : UUID
  ├─ finalCategoryId : UUID
  ├─ medal : Medal (GOLD / SILVER / BRONZE; nullable = withheld)
  ├─ awardedAt : Instant
  └─ awardedBy : UUID (judge user)

BosPlacement (one per (division, place))                 [§1.7]
  ├─ id : UUID
  ├─ divisionId : UUID
  ├─ entryId : UUID
  ├─ place : int (1..bosPlaces)
  ├─ awardedAt : Instant
  └─ awardedBy : UUID (head judge user)
  UNIQUE(divisionId, place), UNIQUE(divisionId, entryId)

JudgeProfile (one per qualified judge user)              [§2.F]
  ├─ id : UUID
  ├─ userId : UUID (UNIQUE — optional 1:1 with User)
  ├─ certifications : Set<Certification> (@ElementCollection, may be empty)
  │     enum values: MJP, BJCP, OTHER
  ├─ qualificationDetails : String (nullable, length 200)
  └─ createdAt / updatedAt : Instant

─── Cross-aggregate relationships (UUID FKs only) ─────────────────────

JudgingTable.judgingId       → Judging
JudgingTable.divisionCategoryId → DivisionCategory (competition module, JUDGING scope)
CategoryJudgingConfig.divisionCategoryId → DivisionCategory (competition module, JUDGING scope)
Scoresheet.tableId           → JudgingTable
Scoresheet.entryId           → Entry (entry module)
JudgeAssignment.judgeUserId  → User (identity module)
JudgeProfile.userId          → User (identity module)
MedalAward.entryId/divisionId/finalCategoryId → entry / competition modules
BosPlacement.entryId/divisionId → entry / competition modules
```

### Phase 2 prep checklist

Items Phase 2 must address before moving on:

- Field-by-field type / nullability / column lengths / @PrePersist for every entity
- Invariants for each entity (state machine guards, FK rules, domain methods)
- Scoresheet creation strategy: eager (when JudgingTable is configured) vs lazy
  (when judge opens entry) — §Q8
- COI similarity heuristic for meadery names — §Q7
- Judge MJP qualifications storage — §Q10
- Whether SUBMITTED scoresheets are revertible by admin — §Q13
- Locale-sensitivity of score field labels (i18n already in place; field names
  are denormalized but tier descriptions live where?)

---

## Reference Material Index

- `docs/specs/judging.md` — preliminary spec
- `docs/specs/awards.md` — sister module spec (now needs rescope per §1.1)
- `docs/reference/chip-competition-rules.md` — CHIP rules (sections 7, 8 most relevant)
- `docs/reference/Short-version-of-MJP-scoring-sheet-V3.0.pdf` — official MJP scoresheet
  (5 fields, max 100; structure transcribed in §1.10)
- `docs/reference/MEAD-GUIDELINES-2023.pdf` — full MJP mead guidelines (categories,
  styles, etc. — relevant for scoresheet style references)
- `app.meads.competition.DivisionStatus` — current state machine
- `app.meads.competition.CategoryScope` — REGISTRATION/JUDGING split (already supports judging-time category reorg)
- `app.meads.entry.Entry` — has `entryCode` (blind judging), `finalCategoryId`/`assignFinalCategory`
- Migrations start at V20 (current highest is V19)
