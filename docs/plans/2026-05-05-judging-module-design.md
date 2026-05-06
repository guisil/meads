# Judging Module — Design Document

**Started:** 2026-05-05
**Status:** Phase 1 ✅ complete (2026-05-05). Phase 2 in progress (started 2026-05-06).
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
| 2 | Domain model — entity definitions, eager/lazy creation, COI heuristic, MJP qualifications storage, scoresheet locking | 🔄 In progress |
| 3 | Service + event contracts, authorization, COI mechanism, judging start trigger | ⏳ Pending |
| 4 | View design (admin table mgmt, judge scoresheet UX, results-before-publication) | ⏳ Pending |
| 5 | Implementation sequencing — TDD cycle order, migration plan, MVP slice | ⏳ Pending |

---

## Next Session: Start Here

**Phase 2 in progress.** §Q8 resolved (2026-05-06). **§1.5 needs redesign** —
discovered 2026-05-06 while working on §Q11.

### The trigger: §Q11 surfaced a granularity mismatch

User confirmed (2026-05-06) that real CHIP flow has tables progressing
independently — some are in Round 1 while others are in Medal Round, some
categories skip Medal Round entirely, and a single category may be split
across two tables that move at different speed. The current §1.5 model
(division-level `JudgingPhase: NOT_STARTED → ROUND_1 → MEDAL_ROUND → BOS →
COMPLETE`) doesn't capture this.

**User's mental model:** the division has only **two** distinct phases that
matter at the division level — an active phase (Round 1 + Medal Round work
happening per-table/per-category), and a BOS phase that gates on everything
else completing. Progression is otherwise tracked at the table or category
level, not the division.

### What next session must address (in order)

**Phase 2.A — Redesign workflow state machine (revises §1.5)**
- Define division-level state (likely 2–3 phases: e.g.,
  `NOT_STARTED → ACTIVE → BOS → COMPLETE`).
- Define per-table or per-category state — including how categories split
  across multiple tables aggregate up.
- Decide whether Medal Round lives at the table level, the category level,
  or as a separate session-like construct.
- Add third Medal Round mode if needed (`NONE`) for categories that skip it,
  or fold "no medal round" into the existing modes.
- Re-confirm or revise the working-sketch hierarchy in §Discussions.

**Phase 2.B — Re-resolve §Q11 (retreat) under new model**
- Retreat at table level (and division level for BOS gate).
- Guards still mirror the `revertDivisionStatus` pattern.

**Phase 2.C — Re-frame §2.1 trigger**
- Eager scoresheet creation now triggered by "start this table" action,
  not division-level phase advance. Sync rule itself unchanged.

**Phase 2.D — Re-resolve §Q12 (start trigger) under new model**
- "Start this table" (per-table) + "Start BOS" (division-wide, gated).

### Decisions affected (must revisit)
- §1.5 — workflow state machine + per-category Medal Round mode (mode survives, phase enum changes).
- §1.8 — aggregate hierarchy (`Judging` thinner; state moves down).
- §2.1 — eager creation trigger (sync rule itself unchanged).
- §Q11 — retreat (re-ask against new model).
- §Q12 — start trigger (re-ask against new model).

### Decisions unaffected (no review needed)
- §1.1–§1.4, §1.6, §1.7, §1.9, §1.10
- §Q7, §Q10, §Q13

### Remaining Phase 2 work after the redesign
- Resolve §Q13 (SUBMITTED scoresheet revertibility) — partially decided by §Q8 sync rule.
- Resolve §Q7 (COI similarity heuristic).
- Resolve §Q10 (judge MJP qualifications storage).
- Pin down field-by-field types, nullability, column lengths, @PrePersist.
- Define invariants and domain methods.
- Produce a finalized entity definition section ready for Phase 3.

### Suggested start prompt for next session
> "Read `docs/plans/2026-05-05-judging-module-design.md` and `docs/SESSION_CONTEXT.md`,
> then continue Phase 2 by redesigning the workflow state machine (Phase 2.A in
> the Next Session section) — start by sketching the new division-level vs
> per-table/per-category split."

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

### 2026-05-06 — Phase 2.1: Eager scoresheet creation + recategorization sync rule

**Decision (§Q8):** Eager scoresheet creation. When admin advances `JudgingPhase`
to `ROUND_1`, the system creates one empty `Scoresheet` per entry that has
`finalCategoryId` set, attached to the `JudgingTable` for that category.

- Entries without `finalCategoryId` at ROUND_1 start get no scoresheet — admin
  must categorize them first.
- New entries created (or assigned `finalCategoryId`) **after** ROUND_1 starts
  auto-create their scoresheet at the appropriate table.

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
**Status:** ✅ Resolved by Decision §2.1 (eager creation when ROUND_1 starts;
recategorization sync rule covers post-start changes).

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

Was about a per-division `JudgingPhase`. Surfaced a deeper issue: the
per-division phase enum is wrong (see Next Session §Phase 2.A). Re-ask under
the redesigned model.

**Likely answer (still applies):** yes for admins, with guards mirroring
`revertDivisionStatus`. But the unit of retreat (table, category, or division)
depends on the redesign.

**Status:** Blocked on §1.5 redesign. Re-ask in Phase 2.B.

### Q12 — Judging start trigger

**Question (original):** What action moves `JudgingPhase` from NOT_STARTED → ROUND_1?

**Updated framing (after §1.5 redesign needed):** Two triggers are likely needed —
"Start this table" (per-table) and "Start BOS" (division-wide, gated until all
tables complete). Preconditions and which action belongs where depend on the
§1.5 redesign.

**Status:** Blocked on §1.5 redesign. Re-ask in Phase 2.D.

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

> **⚠️ 2026-05-06 update:** the `JudgingPhase` enum on `Judging` is **wrong**
> per the discussion at end of session. Real flow has tables/categories
> progressing independently; division-level phase only has 2–3 distinct gates
> (active phase + BOS). See "Next Session: Start Here" §Phase 2.A. This sketch
> kept as historical reference; redesign comes next.

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
