# Judging Module — Design Document

**Started:** 2026-05-05
**Status:** Phase 1 ✅ complete (2026-05-05). Phase 2 ✅ complete (2026-05-08).
Phase 2.A–2.F (2026-05-07) decided state machine, retreat semantics, §2.1
trigger re-frame, start trigger preconditions, COI similarity heuristic, and
judge MJP qualifications storage. Phase 2.G (2026-05-08) finalized field-level
entity definitions for the 7 aggregates + the two `Division` field additions
(`bosPlaces`, `minJudgesPerTable`) and produced the V20 schema. Phase 2.H
(2026-05-08) added scoresheet PDF locale + comment-language tagging
(`Scoresheet.commentLanguage`, `JudgeProfile.preferredCommentLanguage`,
`Competition.commentLanguages`; resolves §Q14). Phase 3 next.
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
| 2 | Domain model — entity definitions, eager/lazy creation, COI heuristic, MJP qualifications storage, scoresheet locking | ✅ Complete (2.A–2.F 2026-05-07; 2.G 2026-05-08) |
| 3 | Service + event contracts, authorization, COI mechanism, judging start trigger | ⏳ Pending — Next |
| 4 | View design (admin table mgmt, judge scoresheet UX, results-before-publication) | ⏳ Pending |
| 5 | Implementation sequencing — TDD cycle order, migration plan, MVP slice | ⏳ Pending |

---

## Next Session: Start Here

**Phase 2 complete (2026-05-08).**
- 2.A–2.F (2026-05-07): three-tier state model, retreat semantics, start
  triggers, COI similarity, `JudgeProfile`. See §2.A–§2.F in Decisions.
- 2.G (2026-05-08): field-level finalization for all 7 aggregates +
  `Division.bosPlaces` + `Division.minJudgesPerTable` + V20 schema.
  See §2.G in Decisions.
- 2.H (2026-05-08): scoresheet PDF locale (locale-aware) +
  comment-language tagging on Scoresheet + sticky preference on
  JudgeProfile + admin-curated language list on Competition. JudgeProfile
  lifecycle adjusted: auto-create on first JudgeAssignment. See §2.H.

§Q1, §Q7, §Q8, §Q10, §Q11, §Q12, §Q13, §Q14 fully resolved. **All Phase 2
design questions closed.**

### What next session must address: Phase 3 — services, events, authorization

Phase 3 produces the service + event contracts, authorization rules, COI
mechanism implementation contract, and the V20 migration. Order suggested:

1. **Module skeleton** — `app.meads.judging` package + `package-info.java`
   (`@ApplicationModule(allowedDependencies = {"competition", "entry", "identity"})`).
   Ensure `ModulithStructureTest` passes with the empty module.
2. **Service contracts** (interfaces + method signatures only — no impl):
   - `JudgingService` — `markActive`, `startTable`, `startMedalRound`,
     `startBos`, `completeBos`, `reopenBos`, `resetBos`, `reopenMedalRound`,
     `resetMedalRound`, `deleteBosPlacement`, `deleteMedalAward`.
   - `ScoresheetService` — `createScoresheetsForTable` (eager creation per
     §2.1), `updateScore`, `submit`, `revertScoresheet`, `moveToTable`,
     `setAdvancedToMedalRound`, `setCommentLanguage` (per §2.H).
   - `JudgeAssignmentService` (or merged into `JudgingService`) —
     `assignJudge` (with `JudgeProfileService.ensureProfileForJudge`
     lifecycle hook per §2.H), `removeJudge`.
   - `JudgeProfileService` — `createOrUpdate`, `findByUserId`, `delete`,
     `ensureProfileForJudge`, `updatePreferredCommentLanguage` (per §2.H).
   - `CompetitionService` extension — `updateCommentLanguages` (per §2.H).
3. **Event records** (judging module public API): all advance/retreat
   pairs from §2.B (`ScoresheetSubmittedEvent` / `ScoresheetRevertedEvent`,
   `TableStartedEvent` / `TableCompletedEvent` / `TableReopenedEvent`,
   `MedalRoundActivatedEvent` / `MedalRoundCompletedEvent` /
   `MedalRoundReopenedEvent` / `MedalRoundResetEvent`,
   `BosStartedEvent` / `BosCompletedEvent` / `BosReopenedEvent` /
   `BosResetEvent`).
4. **Authorization rules** — who can do what. Initial sketch:
   - System admin: all judging actions.
   - Competition admin (per-competition `ADMIN` role): all judging actions
     scoped to their competition.
   - Judges (per-competition `JUDGE` role): edit DRAFT scoresheets at
     tables they're assigned to; cannot revert SUBMITTED.
   - COI block: judge cannot edit a scoresheet for an entry where
     `entry.userId == judge.userId` (hard block per §1.4).
5. **COI implementation contract** — `MeaderyNameNormalizer` (§2.E) and
   `CoiCheckService` interfaces; where suffix lists live (compile-time
   constant map).
6. **Cross-module guards to register:**
   - `DivisionStatusRevertGuard` (judging impl) — blocks
     `JUDGING → REGISTRATION_CLOSED` retreat when judging data exists.
   - `MinJudgesPerTableLockGuard` (judging impl) — blocks `Division.updateMinJudgesPerTable`
     once any table has started.
   - Register both via the existing competition-module guard interfaces.
7. **V20 migration** — exact SQL is in §2.G; schedule for Phase 5
   (impl sequencing) but the SQL is ready.

### Suggested start prompt for next session
> "Read `docs/plans/2026-05-05-judging-module-design.md` and
> `docs/SESSION_CONTEXT.md`, then begin Phase 3 — start with the module
> skeleton (package + `package-info.java` + `ModulithStructureTest`) and
> then sketch service interfaces and event record types."

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

### 2026-05-08 — Phase 2.G: Field-level entity finalization (closes Phase 2)

**Scope.** Pin down field types, JPA annotations, nullability, column lengths,
`@PrePersist`/`@PreUpdate`, invariants, and domain methods for the 7 judging
aggregates and the two competition-module `Division` field additions (§1.6,
§2.D). After this section Phase 2 is closed; Phase 3 (service contracts,
events, authorization, COI implementation contracts) can begin.

#### Conventions adopted (mirroring `identity` / `competition` / `entry`)

- `@Entity` + `@Table(name = "...")` + `@Getter` (Lombok). No setters.
- `@Id private UUID id;` — no `@GeneratedValue`; self-generated in the public
  constructor via `UUID.randomUUID()`.
- Enums: `@Enumerated(EnumType.STRING)`, stored as `VARCHAR(20)` unless
  noted otherwise.
- `Instant createdAt` (NOT NULL, `TIMESTAMP WITH TIME ZONE`) populated by
  `@PrePersist`. `Instant updatedAt` (nullable, `TIMESTAMPTZ`) populated by
  `@PreUpdate`. Aggregates without admin-mutable state may omit `updatedAt`.
- Protected no-arg constructor for JPA; public constructor accepts the
  required business fields (never `id`).
- State changes happen via domain methods on the aggregate root. Methods
  enforce state-machine transitions and throw `IllegalStateException` on
  invalid transitions; service-level rule violations (i18n) bubble through
  `BusinessRuleException`.
- Cross-aggregate references are **UUID FKs only** — no JPA `@ManyToOne` /
  `@OneToMany` between aggregates.
- Within-aggregate children (`JudgeAssignment`, `ScoreField`) use
  `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` with
  `@JoinColumn` mapping back to the aggregate root's `id`. The child is a
  `@Entity` in `internal/` (no public API) and has no separate repository.
- Module location: aggregate roots live in `app.meads.judging`; child
  entities, repositories, and service implementations live in
  `app.meads.judging.internal`.

---

#### Aggregate 1: `Judging` — `judgings`

**Class:** `app.meads.judging.Judging` (public API — aggregate root).

**Enum:** `app.meads.judging.JudgingPhase` — `NOT_STARTED, ACTIVE, BOS, COMPLETE`.

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `divisionId` | `UUID` | `division_id` | NO | **UNIQUE**; FK → `divisions.id` |
| `phase` | `JudgingPhase` | `phase` `VARCHAR(20)` | NO | starts `NOT_STARTED` |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected Judging()` — JPA.
- `public Judging(UUID divisionId)` — `id = randomUUID()`, `phase = NOT_STARTED`.

**Domain methods (state transitions):**

| Method | Transition | Notes |
|---|---|---|
| `markActive()` | `NOT_STARTED → ACTIVE` | Auto, called on first `JudgingTable.startRound1()` |
| `startBos()` | `ACTIVE → BOS` | Service guard: every `CategoryJudgingConfig.medalRoundStatus = COMPLETE` |
| `completeBos()` | `BOS → COMPLETE` | All `BosPlacement` rows finalized by service before call |
| `reopenBos()` | `COMPLETE → BOS` | Tier 3 retreat; `BosPlacement` rows preserved |
| `resetBos()` | `BOS → ACTIVE` | Tier 3 retreat; service must verify zero `BosPlacement` rows |

All transitions throw `IllegalStateException` if called from a non-allowed
phase. No `revertStatus()` — retreats are explicit and asymmetric per §2.B.

**Invariants:**
- `UNIQUE(division_id)`.
- Phase progresses strictly per the table above; retreats only via the
  named domain methods.
- The `Judging` row is created (service-level) when admin advances
  `DivisionStatus: REGISTRATION_CLOSED → JUDGING`; before that it does not exist.

**Events emitted (Phase 3):** `BosStartedEvent`, `BosCompletedEvent`,
`BosReopenedEvent`, `BosResetEvent` (per §2.B).

---

#### Aggregate 2: `JudgingTable` (+ child `JudgeAssignment`) — `judging_tables`, `judge_assignments`

**Class:** `app.meads.judging.JudgingTable` (public API — aggregate root).

**Enum:** `app.meads.judging.JudgingTableStatus` —
`NOT_STARTED, ROUND_1, COMPLETE`.

##### `JudgingTable` fields

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `judgingId` | `UUID` | `judging_id` | NO | FK → `judgings.id` |
| `name` | `String` | `name` `VARCHAR(120)` | NO | admin-supplied label, e.g. "Table A" |
| `divisionCategoryId` | `UUID` | `division_category_id` | NO | FK → `division_categories.id`; **service guard**: must be `JUDGING` scope |
| `scheduledDate` | `LocalDate` | `scheduled_date` `DATE` | YES | display/grouping (§1.8) |
| `status` | `JudgingTableStatus` | `status` `VARCHAR(20)` | NO | starts `NOT_STARTED` |
| `assignments` | `List<JudgeAssignment>` | (via FK on child) | — | `@OneToMany`, cascade ALL, orphanRemoval; ordered by `assignedAt` |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected JudgingTable()` — JPA.
- `public JudgingTable(UUID judgingId, String name, UUID divisionCategoryId, LocalDate scheduledDate)` — `id = randomUUID()`, `status = NOT_STARTED`, `assignments = new ArrayList<>()`.

**Domain methods:**

| Method | Effect | Status guard |
|---|---|---|
| `updateName(String name)` | renames table | any status |
| `updateScheduledDate(LocalDate date)` | reschedule | any status |
| `assignJudge(UUID judgeUserId)` | adds `JudgeAssignment` (idempotent — no-op if already assigned) | any status |
| `removeJudge(UUID judgeUserId)` | removes the assignment | any status (service may forbid dropping below `Division.minJudgesPerTable` while `ROUND_1`) |
| `startRound1()` | `NOT_STARTED → ROUND_1` | service guards: `divisionCategoryId` set, `assignments.size() >= Division.minJudgesPerTable`; eager-creates scoresheets externally |
| `markComplete()` | `ROUND_1 → COMPLETE` | auto when last scoresheet at this table SUBMITs (driven by `ScoresheetService`) |
| `reopenToRound1()` | `COMPLETE → ROUND_1` | Tier 1 implicit retreat — driven by scoresheet revert or new DRAFT scoresheet appearing at the table |

**Invariants:**
- `divisionCategoryId` references a `JUDGING`-scope `DivisionCategory` —
  service-enforced (entity does not see other modules' enums).
- The same category may appear on multiple tables (no UNIQUE on
  `division_category_id` alone).
- `JudgeAssignment` cardinality has no hard upper bound; lower bound at
  `startRound1()` is `Division.minJudgesPerTable`.
- Status retreats Tier 1 only — no admin "reopen table" action; reopening
  is a side-effect of scoresheet/recategorization changes (§2.B).

**Events:** `TableStartedEvent`, `TableCompletedEvent`, `TableReopenedEvent`.

##### `JudgeAssignment` fields (within-aggregate child)

**Class:** `app.meads.judging.internal.JudgeAssignment` (`@Entity`,
package-private, no separate repository — accessed through the parent).

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `judgingTableId` | `UUID` | `judging_table_id` | NO | FK → `judging_tables.id`; mapped via parent's `@JoinColumn` |
| `judgeUserId` | `UUID` | `judge_user_id` | NO | FK → `users.id` (identity module) |
| `assignedAt` | `Instant` | `assigned_at` `TIMESTAMPTZ` | NO | `@PrePersist` |

**Constructors:**
- `protected JudgeAssignment()` — JPA.
- `JudgeAssignment(UUID judgingTableId, UUID judgeUserId)` — package-private; constructed by `JudgingTable.assignJudge`.

**Domain methods:** none — assignment is a value-style child; mutation
is via removal+re-add on the parent.

**Invariants:**
- `UNIQUE(judging_table_id, judge_user_id)` — a judge cannot be assigned
  twice to the same table.

---

#### Aggregate 3: `CategoryJudgingConfig` — `category_judging_configs`

**Class:** `app.meads.judging.CategoryJudgingConfig` (public API — aggregate root).

**Enums:**
- `app.meads.judging.MedalRoundMode` — `COMPARATIVE, SCORE_BASED`.
- `app.meads.judging.MedalRoundStatus` — `PENDING, READY, ACTIVE, COMPLETE`.

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `divisionCategoryId` | `UUID` | `division_category_id` | NO | **UNIQUE**; FK → `division_categories.id`; service guard: `JUDGING` scope |
| `medalRoundMode` | `MedalRoundMode` | `medal_round_mode` `VARCHAR(20)` | NO | DEFAULT `COMPARATIVE` |
| `medalRoundStatus` | `MedalRoundStatus` | `medal_round_status` `VARCHAR(20)` | NO | starts `PENDING` |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected CategoryJudgingConfig()` — JPA.
- `public CategoryJudgingConfig(UUID divisionCategoryId)` — `medalRoundMode = COMPARATIVE`, `medalRoundStatus = PENDING`.
- `public CategoryJudgingConfig(UUID divisionCategoryId, MedalRoundMode mode)` — explicit mode at create.

**Lifecycle (service):** A row is created lazily — first time admin
explicitly configures medal-round mode for the category, *or* on
`JudgingTable.startRound1()` for any table covering that category
(whichever comes first; guarantees the row exists by the time tables
start completing). Default mode is `COMPARATIVE`.

**Domain methods:**

| Method | Transition / effect | Notes |
|---|---|---|
| `updateMode(MedalRoundMode mode)` | mode change | allowed only while `medalRoundStatus ∈ {PENDING, READY}` |
| `markReady()` | `PENDING → READY` | auto-derived; service calls when last covering table marks COMPLETE |
| `markPending()` | `READY → PENDING` | auto-derived; service calls when a covering table reopens |
| `startMedalRound()` | `READY → ACTIVE` | explicit; service runs SCORE_BASED auto-population per §2.D |
| `completeMedalRound()` | `ACTIVE → COMPLETE` | explicit |
| `reopenMedalRound()` | `COMPLETE → ACTIVE` | Tier 2 retreat — `MedalAward` rows preserved |
| `resetMedalRound()` | `ACTIVE → READY` | Tier 2 retreat — service deletes `MedalAward` rows for the category |

**Invariants:**
- `UNIQUE(division_category_id)`.
- Status transitions strictly per state machine; mode only mutable
  while `PENDING` or `READY`.
- `Judging.phase = ACTIVE` is enforced at the service layer for all
  Tier-2 transitions (per §2.B Tier 2 guard).

**Events:** `MedalRoundActivatedEvent`, `MedalRoundCompletedEvent`,
`MedalRoundReopenedEvent`, `MedalRoundResetEvent`.

---

#### Aggregate 4: `Scoresheet` (+ child `ScoreField`) — `scoresheets`, `score_fields`

**Class:** `app.meads.judging.Scoresheet` (public API — aggregate root).

**Enum:** `app.meads.judging.ScoresheetStatus` — `DRAFT, SUBMITTED`.

##### `Scoresheet` fields

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `tableId` | `UUID` | `table_id` | NO | FK → `judging_tables.id`; mutable while DRAFT (per §2.1) |
| `entryId` | `UUID` | `entry_id` | NO | **UNIQUE**; FK → `entries.id` |
| `filledByJudgeUserId` | `UUID` | `filled_by_judge_user_id` | YES | informational (§1.3); set when judge first edits |
| `status` | `ScoresheetStatus` | `status` `VARCHAR(20)` | NO | starts `DRAFT` |
| `totalScore` | `Integer` | `total_score` | YES | computed on submit (sum of 5 fields); cleared on revert |
| `overallComments` | `String` | `overall_comments` `VARCHAR(2000)` | YES | free-text |
| `advancedToMedalRound` | `boolean` | `advanced_to_medal_round` | NO | DEFAULT `false` (§1.9) |
| `submittedAt` | `Instant` | `submitted_at` `TIMESTAMPTZ` | YES | non-null iff `status = SUBMITTED` |
| `commentLanguage` | `String` | `comment_language` `VARCHAR(5)` | YES (DRAFT) / NO (SUBMITTED) | language tag for prose; sticky default per §2.H |
| `fields` | `List<ScoreField>` | (via FK on child) | — | `@OneToMany`, cascade ALL, orphanRemoval; size = 5 |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected Scoresheet()` — JPA.
- `public Scoresheet(UUID tableId, UUID entryId)` — generates id; status `DRAFT`; populates the 5 `ScoreField` children from `MjpScoringFieldDefinition` constants (see below).

**`MjpScoringFieldDefinition` (constants, judging module).** A static
list of the 5 MJP fields with their canonical English names and `maxValue`s
(per §1.10):
- `Appearance` (12), `Aroma/Bouquet` (30), `Flavour and Body` (32),
  `Finish` (14), `Overall Impression` (12). Total max = 100.

Stored as compile-time constants in v1; deferred work (`ScoringSystemFieldDefinition`
table for MJP variants) noted in §1.10 stays deferred. UI localization
of field names happens via `MeadsI18NProvider` keyed off the canonical
English name; tier descriptions ("Unacceptable", "Below Average", …) are
UI-only i18n hints, not stored on `ScoreField`.

**Domain methods:**

| Method | Effect | Status guard |
|---|---|---|
| `updateScore(String fieldName, Integer value, String comment)` | mutates the matching `ScoreField`; throws if `fieldName` unknown | DRAFT |
| `updateOverallComments(String text)` | mutates field | DRAFT |
| `setFilledBy(UUID judgeUserId)` | sets/replaces informational judge | DRAFT |
| `setAdvancedToMedalRound(boolean advanced)` | toggles flag | DRAFT or SUBMITTED, but service rejects after `medalRoundStatus = ACTIVE` |
| `submit()` | `DRAFT → SUBMITTED`; computes `totalScore = sum(values)`; sets `submittedAt = now()` | DRAFT; throws if any `ScoreField.value` is null |
| `revertToDraft()` | `SUBMITTED → DRAFT`; clears `totalScore` and `submittedAt`; **preserves all 5 `ScoreField.value` and `comment`** | SUBMITTED; service guard: `medalRoundStatus ∈ {PENDING, READY}` (per §2.B Tier 0) |
| `moveToTable(UUID newTableId)` | reassigns `tableId` | DRAFT only; service ensures the new table's `divisionCategoryId == entry.finalCategoryId` (per §2.1) |
| `setCommentLanguage(String code, UUID judgeUserId)` | sets `commentLanguage` | DRAFT only (per §2.H) |

**Invariants:**
- `UNIQUE(entry_id)` — one scoresheet per entry per division (an entry
  belongs to exactly one division).
- For DRAFT scoresheets: `tableId.divisionCategoryId == entry.finalCategoryId`
  (service-enforced; cross-module invariant).
- For SUBMITTED scoresheets: `tableId` is effectively immutable —
  `moveToTable` rejects.
- Always exactly 5 `ScoreField` children (created at construction).
- `totalScore` non-null ⇔ `status = SUBMITTED`.
- `submittedAt` non-null ⇔ `status = SUBMITTED`.
- `commentLanguage` non-null ⇔ `status = SUBMITTED` (populated on submit
  from default-resolution chain if still null; per §2.H).

**Events:** `ScoresheetSubmittedEvent`, `ScoresheetRevertedEvent`.

##### `ScoreField` fields (within-aggregate child)

**Class:** `app.meads.judging.internal.ScoreField` (`@Entity`,
package-private, no repository).

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `scoresheetId` | `UUID` | `scoresheet_id` | NO | FK → `scoresheets.id`; via parent's `@JoinColumn` |
| `fieldName` | `String` | `field_name` `VARCHAR(50)` | NO | canonical English name (i18n key) |
| `maxValue` | `int` | `max_value` | NO | denormalized at creation per §1.10 |
| `value` | `Integer` | `value` | YES | null while unfilled; required to be non-null at submit |
| `comment` | `String` | `comment` `VARCHAR(2000)` | YES | free-text |

**Domain methods:** mutation only via parent (`Scoresheet.updateScore`).

**Invariants:**
- `UNIQUE(scoresheet_id, field_name)`.
- `0 <= value <= maxValue` when non-null (entity-level validation).

---

#### Aggregate 5: `MedalAward` — `medal_awards`

**Class:** `app.meads.judging.MedalAward` (public API — aggregate root).

**Enum:** `app.meads.judging.Medal` — `GOLD, SILVER, BRONZE`.

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `entryId` | `UUID` | `entry_id` | NO | **UNIQUE**; FK → `entries.id` |
| `divisionId` | `UUID` | `division_id` | NO | denormalized for query/index |
| `finalCategoryId` | `UUID` | `final_category_id` | NO | FK → `division_categories.id`; service guard: `JUDGING` scope |
| `medal` | `Medal` | `medal` `VARCHAR(10)` | YES | `null` = explicit withhold (D11) |
| `awardedAt` | `Instant` | `awarded_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `awardedBy` | `UUID` | `awarded_by` | NO | judge `userId` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` (edits during ACTIVE) |

**Constructors:**
- `protected MedalAward()` — JPA.
- `public MedalAward(UUID entryId, UUID divisionId, UUID finalCategoryId, Medal medal, UUID awardedBy)` — `medal` may be null at creation (explicit withhold of an auto-populated candidate).

**Domain methods:**

| Method | Effect | Notes |
|---|---|---|
| `updateMedal(Medal newValue, UUID awardedBy)` | re-set medal (incl. null = withhold) | service guard: `medalRoundStatus = ACTIVE` |

Deletion is a service-level operation (e.g. `JudgingService.deleteMedalAward`)
under the same `medalRoundStatus = ACTIVE` guard (and unconditional during
`resetMedalRound`'s wipe).

**Invariants:**
- `UNIQUE(entry_id)` — one medal record per entry (per §1.7).
- `finalCategoryId` matches the entry's `finalCategoryId` at the time of
  awarding (service-enforced).
- Absence of a row = entry was not a candidate (per D11). `medal = null`
  ≠ no row.

---

#### Aggregate 6: `BosPlacement` — `bos_placements`

**Class:** `app.meads.judging.BosPlacement` (public API — aggregate root).

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `divisionId` | `UUID` | `division_id` | NO | **UNIQUE** with `place` and with `entry_id` |
| `entryId` | `UUID` | `entry_id` | NO | FK → `entries.id` |
| `place` | `int` | `place` | NO | `1..Division.bosPlaces` |
| `awardedAt` | `Instant` | `awarded_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `awardedBy` | `UUID` | `awarded_by` | NO | head-judge `userId` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Constructors:**
- `protected BosPlacement()` — JPA.
- `public BosPlacement(UUID divisionId, UUID entryId, int place, UUID awardedBy)` — entity-level guard: `place >= 1`.

**Domain methods:**

| Method | Effect | Notes |
|---|---|---|
| `updatePlace(int newPlace, UUID awardedBy)` | reassigns place | service guard: `Judging.phase = BOS`; uniqueness guard at DB |

Deletion is a service-level operation; explicit per-row deletion is
load-bearing for `Judging.resetBos()` per §2.B Tier 3.

**Invariants:**
- `UNIQUE(division_id, place)`, `UNIQUE(division_id, entry_id)` (per §1.7).
- `place ∈ [1, Division.bosPlaces]` — service-enforced (entity sees only
  the lower bound).
- Entry must have `MedalAward.medal = GOLD` in the same division —
  service-enforced (CHIP §7).

---

#### Aggregate 7: `JudgeProfile` — `judge_profiles` (+ join table `judge_profile_certifications`)

**Class:** `app.meads.judging.JudgeProfile` (public API — aggregate root).

**Enum:** `app.meads.judging.Certification` — `MJP, BJCP, OTHER`.

| Field | Java type | Column | Nullable | Notes |
|---|---|---|---|---|
| `id` | `UUID` | `id` (PK) | NO | self-gen in ctor |
| `userId` | `UUID` | `user_id` | NO | **UNIQUE**; FK → `users.id` |
| `certifications` | `Set<Certification>` | (`@ElementCollection` join table) | — | may be empty |
| `qualificationDetails` | `String` | `qualification_details` `VARCHAR(200)` | YES | free-text: level, year, "OTHER" specifics (e.g. WSET) |
| `preferredCommentLanguage` | `String` | `preferred_comment_language` `VARCHAR(5)` | YES | sticky comment-language preference; updated whenever judge changes scoresheet language (§2.H) |
| `createdAt` | `Instant` | `created_at` `TIMESTAMPTZ` | NO | `@PrePersist` |
| `updatedAt` | `Instant` | `updated_at` `TIMESTAMPTZ` | YES | `@PreUpdate` |

**Join table `judge_profile_certifications`** (`@ElementCollection` +
`@CollectionTable`):

| Column | Type | Notes |
|---|---|---|
| `judge_profile_id` | `UUID` | FK → `judge_profiles.id` |
| `certification` | `VARCHAR(20)` | enum name |

Primary key: `(judge_profile_id, certification)`.

**Constructors:**
- `protected JudgeProfile()` — JPA.
- `public JudgeProfile(UUID userId)` — empty `certifications`, null `qualificationDetails`.

**Domain methods:**

| Method | Effect | Notes |
|---|---|---|
| `updateCertifications(Set<Certification> certifications)` | replaces the set | empty set is allowed |
| `updateQualificationDetails(String details)` | sets/clears free text | trims; null/blank stored as null |
| `updatePreferredCommentLanguage(String code)` | sets/clears sticky language | null clears the sticky preference (per §2.H) |

**Invariants:**
- `UNIQUE(user_id)` — one profile per user.
- Lifecycle (refines §2.F per §2.H): row auto-created on first
  `JudgeAssignment` (judge added to a table). Empty `certifications` and
  null `qualificationDetails` are valid initial state. Admin/self-edit
  populates qualifications; service mutations populate
  `preferredCommentLanguage`.

---

#### Competition-module changes

Two new fields on `app.meads.competition.Division`:

| Field | Java type | Column | Nullable | Default | Notes |
|---|---|---|---|---|---|
| `bosPlaces` | `int` | `bos_places` | NO | `1` | §1.6; locked once division advances past `REGISTRATION_OPEN` |
| `minJudgesPerTable` | `int` | `min_judges_per_table` | NO | `2` | §2.D; locked once any `JudgingTable` for the division has `status != NOT_STARTED` |

One new field on `app.meads.competition.Competition` (per §2.H):

| Field | Java type | Storage | Default | Notes |
|---|---|---|---|---|
| `commentLanguages` | `Set<String>` | `@ElementCollection` → `competition_comment_languages` join table; `language_code VARCHAR(5)` | seeded with the 5 UI codes (`en`, `es`, `it`, `pl`, `pt`) at competition creation by `CompetitionService.createCompetition` | admin-curated list of languages judges may pick in the scoresheet comment-language dropdown (§2.H); editable any time |

**New domain method on `Competition`:**

| Method | Effect |
|---|---|
| `updateCommentLanguages(Set<String> codes)` | replaces the set; entity-level validation: each code matches `[a-z]{2}(-[A-Za-z0-9]+)?` |

**New domain methods on `Division`:**

| Method | Effect | Status guard |
|---|---|---|
| `updateBosPlaces(int)` | sets `bosPlaces`; entity-level guard `>= 1` | DRAFT or REGISTRATION_OPEN — beyond that, the field is locked |
| `updateMinJudgesPerTable(int)` | sets `minJudgesPerTable`; entity-level guard `>= 1` | DRAFT through REGISTRATION_CLOSED; **service-level guard**: reject if any `JudgingTable` for this division has `status != NOT_STARTED` (cross-module check via a guard interface — same pattern as `DivisionRevertGuard`) |

**Cross-module guard interface (in `app.meads.competition`):**

```java
public interface MinJudgesPerTableLockGuard {
    boolean isLocked(UUID divisionId); // true if any JudgingTable.status != NOT_STARTED
}
```

Implementation in `app.meads.judging.internal.JudgingMinJudgesLockGuard`.
The competition module calls registered guards from
`updateMinJudgesPerTable` paths; if any returns true, the update is
rejected with a translated `BusinessRuleException`.

(`bosPlaces` does not need a cross-module guard — its lock is purely on
`DivisionStatus`, which `Division` already owns.)

---

#### Initial migration (V20) — full schema

`V20__add_judging_module_and_division_judging_fields.sql` (single migration
for atomicity; pairs the judging-module schema with the two competition
fields).

```sql
-- Competition-module additions
ALTER TABLE divisions
    ADD COLUMN bos_places            INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN min_judges_per_table  INTEGER NOT NULL DEFAULT 2;

-- Per-competition comment language list (§2.H)
CREATE TABLE competition_comment_languages (
    competition_id  UUID NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    language_code   VARCHAR(5) NOT NULL,
    PRIMARY KEY (competition_id, language_code)
);

-- Judging module
CREATE TABLE judgings (
    id           UUID PRIMARY KEY,
    division_id  UUID NOT NULL UNIQUE REFERENCES divisions(id),
    phase        VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE
);

CREATE TABLE judging_tables (
    id                    UUID PRIMARY KEY,
    judging_id            UUID NOT NULL REFERENCES judgings(id),
    name                  VARCHAR(120) NOT NULL,
    division_category_id  UUID NOT NULL REFERENCES division_categories(id),
    scheduled_date        DATE,
    status                VARCHAR(20) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_judging_tables_judging_id            ON judging_tables(judging_id);
CREATE INDEX idx_judging_tables_division_category_id  ON judging_tables(division_category_id);

CREATE TABLE judge_assignments (
    id                UUID PRIMARY KEY,
    judging_table_id  UUID NOT NULL REFERENCES judging_tables(id) ON DELETE CASCADE,
    judge_user_id     UUID NOT NULL REFERENCES users(id),
    assigned_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (judging_table_id, judge_user_id)
);
CREATE INDEX idx_judge_assignments_judge_user_id ON judge_assignments(judge_user_id);

CREATE TABLE category_judging_configs (
    id                    UUID PRIMARY KEY,
    division_category_id  UUID NOT NULL UNIQUE REFERENCES division_categories(id),
    medal_round_mode      VARCHAR(20) NOT NULL,
    medal_round_status    VARCHAR(20) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE
);

CREATE TABLE scoresheets (
    id                          UUID PRIMARY KEY,
    table_id                    UUID NOT NULL REFERENCES judging_tables(id),
    entry_id                    UUID NOT NULL UNIQUE REFERENCES entries(id),
    filled_by_judge_user_id     UUID REFERENCES users(id),
    status                      VARCHAR(20) NOT NULL,
    total_score                 INTEGER,
    overall_comments            VARCHAR(2000),
    advanced_to_medal_round     BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at                TIMESTAMP WITH TIME ZONE,
    comment_language            VARCHAR(5),  -- §2.H; NOT NULL enforced at SUBMIT in service layer
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_scoresheets_table_id ON scoresheets(table_id);

CREATE TABLE score_fields (
    id              UUID PRIMARY KEY,
    scoresheet_id   UUID NOT NULL REFERENCES scoresheets(id) ON DELETE CASCADE,
    field_name      VARCHAR(50) NOT NULL,
    max_value       INTEGER NOT NULL,
    value           INTEGER,
    comment         VARCHAR(2000),
    UNIQUE (scoresheet_id, field_name)
);

CREATE TABLE medal_awards (
    id                  UUID PRIMARY KEY,
    entry_id            UUID NOT NULL UNIQUE REFERENCES entries(id),
    division_id         UUID NOT NULL REFERENCES divisions(id),
    final_category_id   UUID NOT NULL REFERENCES division_categories(id),
    medal               VARCHAR(10),
    awarded_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    awarded_by          UUID NOT NULL REFERENCES users(id),
    updated_at          TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_medal_awards_division_id        ON medal_awards(division_id);
CREATE INDEX idx_medal_awards_final_category_id  ON medal_awards(final_category_id);

CREATE TABLE bos_placements (
    id           UUID PRIMARY KEY,
    division_id  UUID NOT NULL REFERENCES divisions(id),
    entry_id     UUID NOT NULL REFERENCES entries(id),
    place        INTEGER NOT NULL,
    awarded_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    awarded_by   UUID NOT NULL REFERENCES users(id),
    updated_at   TIMESTAMP WITH TIME ZONE,
    UNIQUE (division_id, place),
    UNIQUE (division_id, entry_id)
);

CREATE TABLE judge_profiles (
    id                            UUID PRIMARY KEY,
    user_id                       UUID NOT NULL UNIQUE REFERENCES users(id),
    qualification_details         VARCHAR(200),
    preferred_comment_language    VARCHAR(5),  -- §2.H sticky preference
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                    TIMESTAMP WITH TIME ZONE
);

CREATE TABLE judge_profile_certifications (
    judge_profile_id  UUID NOT NULL REFERENCES judge_profiles(id) ON DELETE CASCADE,
    certification     VARCHAR(20) NOT NULL,
    PRIMARY KEY (judge_profile_id, certification)
);
```

Foreign keys cross modules in the schema (`scoresheets.entry_id →
entries.id`, `scoresheets.filled_by_judge_user_id → users.id`, etc.).
That's consistent with how `entries.user_id → users.id` already works
across the entry/identity boundary. The Modulith boundary is enforced
at the Java level (no cross-module Java references except through public
API), not at the schema level.

---

#### Resolved Phase 2 prep checklist items

| Item | Status |
|---|---|
| Field-by-field type / nullability / column lengths / `@PrePersist` for every entity | ✅ §2.G above |
| Invariants for each entity (state machine guards, FK rules, domain methods) | ✅ §2.G above |
| Scoresheet creation strategy: eager vs lazy (§Q8) | ✅ §2.1 (eager on `JudgingTable.startRound1()`) |
| COI similarity heuristic for meadery names (§Q7) | ✅ §2.E |
| Judge MJP qualifications storage (§Q10) | ✅ §2.F |
| SUBMITTED scoresheet revertibility (§Q13) | ✅ §2.B Tier 0 |
| Locale-sensitivity of score field labels | ✅ canonical English `fieldName` stored as i18n key; tier descriptions UI-only |

---

#### Implications

- All Phase 2 design questions closed (§Q1, §Q7, §Q8, §Q10, §Q11, §Q12, §Q13).
- The Working sketch (line ~986) is now superseded by §2.G — banner added below.
- Phase 3 begins: service contracts, event publication, authorization
  (admin/judge), COI mechanism implementation, `DivisionStatusRevertGuard`
  + `MinJudgesPerTableLockGuard` registrations, and the V20 migration.
- New module `app.meads.judging` will declare `allowedDependencies =
  {"competition", "identity", "entry"}` on its `package-info.java`. (Awards
  is not yet a module; the entry dependency is necessary for `entryId`
  cross-references and the eager scoresheet creation flow — same pattern
  as `entry`'s dependency on `competition` and `identity`.)

### 2026-05-08 — Phase 2.H: Scoresheet PDF locale + comment-language tagging (resolves §Q14)

**Decision (D15 from 2026-05-08 conversation).** Two related questions
resolved together.

#### D15a: Scoresheet PDF render language

**Decision:** locale-aware. The scoresheet PDF mirrors the UI locale of the
user who clicks download (admin or judge). Field names (`Appearance`,
`Aroma/Bouquet`, …) and tier labels (`Unacceptable`, `Below Average`, …)
render through `MeadsI18NProvider`, same mechanism as the entry-side label
PDF (`LabelPdfService`).

**Rationale:** consistency with the rest of the app; better UX for non-English-
speaking judges who print the sheet. Trade-off: a printed PDF in Portuguese
is no longer a verbatim reproduction of the official MJP English scoresheet.
For v1 this is acceptable — the on-screen MJP scoring system remains
canonical (`Scoresheet.totalScore`, field maxima); only label rendering
changes per locale.

#### D15b: Per-scoresheet comment language tag

**Problem:** judges in a multilingual panel may write comments in different
languages, and the language they choose may differ from the system UI
locale (e.g. an Italian judge writing in English so non-Italian readers can
follow). Without a marker, downstream readers (other judges in subsequent
rounds, the entrant receiving their PDF) don't know what language the prose
is in.

**Decision:** track the comment language per scoresheet and surface it in the
PDF. A judge selects the language for their comments via a dropdown on the
scoresheet form. Selection defaults to a sticky per-judge preference,
falling back to UI locale.

##### New field: `Scoresheet.commentLanguage`

- Type: `String` (ISO 639-1 / BCP 47 short form; up to 5 chars to allow
  regional codes like `pt-BR` or `zh-Hant`).
- Column: `comment_language VARCHAR(5)`. Nullable on creation; NOT NULL
  at SUBMIT.
- Mutable while DRAFT via `Scoresheet.setCommentLanguage(String code, UUID judgeUserId)`.
- Captured-and-frozen at SUBMIT — `submit()` populates it from the
  default-resolution chain if still null, otherwise leaves the existing
  value untouched.
- Already-submitted scoresheets are not touched on subsequent language
  changes — the value was frozen at SUBMIT.

##### Default-resolution chain (when judge first opens a DRAFT scoresheet)

1. `JudgeProfile.preferredCommentLanguage` (judge's sticky preference) if set.
2. Else `User.preferredLanguage` (the UI locale).

When the judge changes the language via the dropdown, the service updates
**both**:
- `Scoresheet.commentLanguage` (current sheet)
- `JudgeProfile.preferredCommentLanguage` (sticky — applies to the next
  scoresheets the judge opens, but never retroactively to already-edited ones).

##### Adjustments to JudgeProfile (refines §2.F)

- New nullable field `preferredCommentLanguage : String` (`VARCHAR(5)`).
- **Lifecycle change:** `JudgeProfile` row auto-created on first
  `JudgeAssignment` (no longer only via admin/self-edit of qualifications).
  Keeps the sticky-preference lookup O(1) without scanning prior scoresheets.
  Empty `certifications` and null `qualificationDetails` are valid and
  unchanged from §2.F semantics.

##### New competition-module field: `Competition.commentLanguages`

Admin-curated, per-competition list of languages judges may select in the
dropdown.

- Type: `Set<String>` via JPA `@ElementCollection` →
  `competition_comment_languages` join table (mirrors
  `judge_profile_certifications`).
- Seeded at competition creation with the 5 UI codes (`en`, `es`, `it`,
  `pl`, `pt`) by `CompetitionService.createCompetition`, sourced from
  `MeadsI18NProvider.getSupportedLanguageCodes()`.
- Editable from `CompetitionDetailView` Settings tab (multi-select / chip
  input). Mutable in any `DivisionStatus` — judging-time additions are
  allowed.
- `CompetitionService.updateCommentLanguages(competitionId, Set<String>, adminUserId)`.

**Dropdown contents in the scoresheet form** (Phase 4 UX):
- Union of `competition.commentLanguages` and the judge's current
  `preferredCommentLanguage`. The union ensures a previously-selected
  sticky value always remains visible even if admin later removes it from
  the canonical list — the admin's intent is "these are supported for new
  selections", not "wipe past or in-flight selections".
- Sorted alphabetically by language display name in the user's locale.

##### PDF render

`ScoresheetPdfService` (Phase 4):
- All field names + tier labels render in the printer's UI locale (D15a).
- Each comment block carries a subheader naming the language of the prose,
  e.g. "Comments — written in Português" / "Aroma/Bouquet — in English".
  Subheader is itself rendered in the printer's UI locale; the value
  inside (`Português`) is the localized display name of the comment
  language.
- Uses Liberation Sans (already embedded for label PDFs) for Unicode
  coverage of all supported scripts.

##### Service API additions (for Phase 3)

```
ScoresheetService.setCommentLanguage(scoresheetId, languageCode, judgeUserId)
  // DRAFT only; updates Scoresheet.commentLanguage AND
  // JudgeProfile.preferredCommentLanguage atomically (same transaction).
  // No event needed — internal state.

JudgeProfileService.ensureProfileForJudge(userId)
  // Idempotent; called from JudgingService.assignJudge as part of the
  // first-assignment lifecycle hook.

CompetitionService.updateCommentLanguages(competitionId, Set<String>, adminUserId)
  // Admin-only; validates language codes against a known list (e.g. Locale.getISOLanguages()).
```

##### V20 migration additions

(Folded into the V20 migration block in §2.G, repeated here for clarity.)

```sql
ALTER TABLE scoresheets
    ADD COLUMN comment_language VARCHAR(5);

ALTER TABLE judge_profiles
    ADD COLUMN preferred_comment_language VARCHAR(5);

CREATE TABLE competition_comment_languages (
    competition_id  UUID NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    language_code   VARCHAR(5) NOT NULL,
    PRIMARY KEY (competition_id, language_code)
);
```

For existing prod competitions (none yet at v1), a one-shot backfill could
seed the 5 UI codes; not needed pre-deployment.

#### Implications

- §Q14 (new) — fully resolved by §2.H.
- §2.F lifecycle clarification: `JudgeProfile` is auto-created on first
  `JudgeAssignment`, not strictly lazy on admin/self-edit.
- New Phase 3 work: `ScoresheetService.setCommentLanguage`,
  `JudgeProfileService.ensureProfileForJudge`,
  `CompetitionService.updateCommentLanguages`.
- New Phase 4 work: dropdown on scoresheet form; multi-select on Settings
  tab; PDF subheader rendering in `ScoresheetPdfService`.
- §2.G entity tables and V20 SQL amended in place to incorporate the new
  fields.

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

### Q14 — Scoresheet PDF locale + comment-language tagging
**Status:** ✅ Resolved by Decision §2.H (2026-05-08).
PDF renders in printer's UI locale (locale-aware). `Scoresheet.commentLanguage`
records the language of judge prose; defaults to
`JudgeProfile.preferredCommentLanguage` (sticky) → `User.preferredLanguage`
(UI locale). Frozen at SUBMIT. Dropdown source:
`Competition.commentLanguages` (admin-curated, seeded with the 5 UI codes).
`JudgeProfile` lifecycle adjusted to auto-create on first `JudgeAssignment`.

---

## Discussions

(Working space for in-progress topics. Move resolved items to Decisions, leave
unresolved as Open Questions, delete obsolete content.)

### Working sketch — aggregate model after Phase 2.A

> **⚠️ Field-level details superseded by §2.G (2026-05-08).** This sketch
> remains as a one-page conceptual diagram of the aggregate graph and its
> cross-aggregate UUID FKs. For canonical field types, nullability, column
> lengths, invariants, domain methods, and the V20 schema, see §2.G in
> Decisions.

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

> **All items resolved (2026-05-08).** See the resolution table at the end of
> §2.G in Decisions.

- ✅ Field-by-field type / nullability / column lengths / @PrePersist (§2.G)
- ✅ Invariants for each entity (§2.G)
- ✅ Scoresheet creation strategy — §Q8 (§2.1, eager on `startRound1()`)
- ✅ COI similarity heuristic — §Q7 (§2.E)
- ✅ Judge MJP qualifications storage — §Q10 (§2.F)
- ✅ SUBMITTED scoresheet revertibility — §Q13 (§2.B Tier 0)
- ✅ Locale-sensitivity of score field labels (canonical English `fieldName`
  stored as i18n key; tier descriptions UI-only) (§2.G)

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
