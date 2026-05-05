# Judging Module — Design Document

**Started:** 2026-05-05
**Status:** Phase 1 ✅ complete (2026-05-05). Phase 2 not yet started.
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
| 2 | Domain model — entity definitions, eager/lazy creation, COI heuristic, MJP qualifications storage, scoresheet locking | ⏳ Pending |
| 3 | Service + event contracts, authorization, COI mechanism, judging start trigger | ⏳ Pending |
| 4 | View design (admin table mgmt, judge scoresheet UX, results-before-publication) | ⏳ Pending |
| 5 | Implementation sequencing — TDD cycle order, migration plan, MVP slice | ⏳ Pending |

---

## Next Session: Start Here

**Phase 1 is complete.** Start Phase 2 — domain model.

**Phase 2 scope:**
- Pin down field-by-field types, nullability, column lengths, @PrePersist for every
  entity in the working sketch (Discussions §)
- Define invariants and domain methods (state machine guards, FK rules)
- Resolve the Phase-2 deferred questions:
  - **§Q7** COI similarity heuristic
  - **§Q8** eager vs lazy scoresheet creation
  - **§Q10** judge MJP qualifications storage
  - **§Q11** `JudgingPhase` retreat allowed?
  - **§Q13** SUBMITTED scoresheet revertibility
- Produce a finalized entity definition section in this doc, ready for Phase 3
  (services, events) to consume.

**Suggested start prompt for next session:**
> "Read `docs/plans/2026-05-05-judging-module-design.md` and `docs/SESSION_CONTEXT.md`,
> then start Phase 2 — domain model — beginning with §Q8 (eager vs lazy scoresheet
> creation) since it shapes the JudgingTable invariants."

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

**Decision (state machine):** Option C. Keep `DivisionStatus` linear (DRAFT →
REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION →
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

---

## Open Questions

### Q1 — Workflow state machine
**Status:** ✅ Resolved by Decision §1.5.

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

**Decided high-level approach:** soft warning on similar meadery name (Phase 1.4).

**Open implementation question:** what similarity function?
- Exact match (case-insensitive, normalized whitespace)
- Levenshtein distance with threshold (e.g., distance ≤ 2)
- Jaccard similarity on tokens
- Just substring match

**Status:** Deferred to Phase 2 (entity/service design).

### Q8 — Eager vs lazy scoresheet creation

**From preliminary spec.** Are scoresheets created when the table is set up
(eager — pre-populated empty scoresheets for every entry in the table's
category), or when a judge opens an entry for the first time (lazy)?

**Status:** Deferred to Phase 2.

### Q9 — Awards module rescope

**Decided implication of Phase 1.1:** Awards module shrinks. Need to revise
`docs/specs/awards.md` later.

**Status:** Deferred — handle when awards module is being designed.

### Q10 — Judge MJP qualifications storage (new — surfaced in §1.10)

The MJP scoresheet PDF has a "Judge MJP Qualifications" header (level + EMMA /
AMMA / BJCP / Other certifications). This is per-judge profile data, not
per-scoresheet.

**Options:**
- Add fields directly to `User` entity (e.g. `mjpLevel`, `certifications` Set<enum>)
- New `JudgeProfile` entity in identity or judging module, optional 1:1 with User
- Store as a free-text field on `Scoresheet` (denormalized — printed-only)

**Status:** Deferred to Phase 2.

### Q11 — `JudgingPhase` retreat allowed?

`JudgingPhase` (NOT_STARTED → ROUND_1 → MEDAL_ROUND → BOS → COMPLETE) — is reverting
allowed (e.g. MEDAL_ROUND → ROUND_1 to fix a scoresheet)? Likely yes for admins
but with guards. Mirrors the `revertDivisionStatus` pattern.

**Status:** Deferred to Phase 2/3.

### Q12 — Judging start trigger

**Question:** What action moves `JudgingPhase` from NOT_STARTED → ROUND_1?

**Likely:** explicit admin action ("Start Round 1" button) once the division is
in `DivisionStatus.JUDGING`. Tables and judge assignments must be set up first.
Need to define preconditions.

**Status:** Deferred to Phase 3 (service contracts).

### Q13 — Are scoresheets locked once finalized?

The preliminary spec has DRAFT → SUBMITTED on Scoresheet. CHIP rules talk about
"one official scoresheet" per entry, suggesting it's locked after submission. But
admins may need to fix typos. **Likely:** SUBMITTED is read-only to judges, but
admins can revert to DRAFT.

**Status:** Deferred to Phase 2/3.

---

## Discussions

(Working space for in-progress topics. Move resolved items to Decisions, leave
unresolved as Open Questions, delete obsolete content.)

### Working sketch — entity hierarchy after Phase 1

This is **not yet a finalized model** — Phase 2 will pin down field types,
nullability, invariants, and add the entities still missing. Use as a mental
scaffold for Phase 2.

```
Division (competition module)
  ├─ bosPlaces : int (NOT NULL DEFAULT 1)               [§1.6]
  └─ DivisionCategory (scope = JUDGING)                 [existing]

Judging (judging module — aggregate root, one per division)         [§1.5]
  ├─ divisionId : UUID
  ├─ phase : JudgingPhase (NOT_STARTED → ROUND_1 → MEDAL_ROUND → BOS → COMPLETE)
  └─ JudgingTable
       ├─ name : String
       ├─ divisionCategoryId : UUID (must be JUDGING-scope)
       ├─ scheduledDate : LocalDate (nullable, for grouping/display)  [§1.8]
       ├─ JudgeAssignment
       │    ├─ judgeUserId : UUID
       │    └─ assignedAt : Instant
       └─ Scoresheet (one per Entry, NOT one per judge)               [§1.3]
            ├─ entryId : UUID
            ├─ filledByJudgeUserId : UUID (which judge entered it)    [§1.3]
            ├─ status : ScoresheetStatus (DRAFT, SUBMITTED)
            ├─ totalScore : Integer (computed on submit)
            ├─ overallComments : String
            ├─ advancedToMedalRound : boolean (default false)         [§1.9]
            ├─ submittedAt : Instant
            └─ ScoreField (5 of these per Scoresheet, denormalized)   [§1.10]
                 ├─ fieldName : String (Appearance / Aroma/Bouquet / ...)
                 ├─ maxValue : int (12 / 30 / 32 / 14 / 12)
                 ├─ value : Integer (nullable until filled)
                 └─ comment : String

CategoryJudgingConfig (judging module, optional)                      [§1.5]
  ├─ divisionCategoryId : UUID (must be JUDGING-scope)
  └─ medalRoundMode : MedalRoundMode (COMPARATIVE / SCORE_BASED)

MedalAward (judging module)                                            [§1.7]
  ├─ entryId : UUID (UNIQUE)
  ├─ divisionId : UUID
  ├─ finalCategoryId : UUID
  ├─ medal : Medal (GOLD / SILVER / BRONZE; nullable = withheld)
  ├─ awardedAt : Instant
  └─ awardedBy : UUID

BosPlacement (judging module)                                          [§1.7]
  ├─ divisionId : UUID
  ├─ entryId : UUID
  ├─ place : int (1..bosPlaces)
  ├─ awardedAt : Instant
  └─ awardedBy : UUID
  UNIQUE(divisionId, place), UNIQUE(divisionId, entryId)
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
