# Judging Preparation Checklist (per division)

An operational runbook for a **competition admin** preparing a division's judging
data before the actual competition. Work through it **in order** — several steps
have ordering constraints (most importantly, **declare conflicts of interest
before assigning judges** — see the ⚠ note in step 7).

All steps below are done from the division's **Judging Admin** view
(`Manage Judging` button on the division detail page) unless noted. Setup is
allowed from **`REGISTRATION_CLOSED`** onward; only **starting** a round requires
the division to be at **`JUDGING`**.

---

## Phase 0 — People & profiles (any time before judging)

- [ ] All **judges** are added as `JUDGE` participants on the competition
      (Competition Detail → Participants).
- [ ] Each judge has a **JudgeProfile** (certifications / preferred comment
      language) where relevant.
- [ ] **Stewards** added as `STEWARD` participants if you use the steward flow.
- [ ] Entrants' entries are in and paid (credits applied); entries you intend to
      judge are at least `SUBMITTED`.

## Phase 1 — Close registration

- [ ] Advance the division **`REGISTRATION_OPEN` → `REGISTRATION_CLOSED`**
      (Division detail → Advance Status). No more entrant self-service changes
      after this; everything below is admin-driven.

## Phase 2 — Categories & entries

- [ ] **Initialize judging categories** for the division (the JUDGING-scope
      category tree the rounds will reference). Add per-locale translations now if
      needed.
- [ ] **Assign a final category** to every `SUBMITTED`/`RECEIVED` entry
      (individually, or in bulk via assign-by-code). The division cannot advance
      to `JUDGING` until every such entry has a final category.
- [ ] Mark entries **`RECEIVED`** as the physical bottles arrive. Only `RECEIVED`
      entries get a scoresheet when a round starts.

## Phase 3 — Division judging settings

- [ ] Set **Best of Show places** (how many BoS placements this division awards).
      Locked once at `JUDGING`.
- [ ] Set **Minimum judges per round**.
- [ ] Confirm **Shared tables across divisions** (Competition Detail → Settings)
      is set as intended — ON means a started round at "Table 1" locks "Table 1"
      in every other division of the competition.

## Phase 4 — Physical tables

- [ ] Create the **physical tables** (stations) for the division ("Table 1",
      "Table 2", …). Each round runs at one table; only one round can be *active*
      at a table at a time.

## Phase 5 — Conflicts of interest ⚠ (do this BEFORE Phase 6/7)

- [ ] Declare any **manual conflicts of interest** (Judging Admin → *Conflicts of
      Interest* tab → Add). Use this for a real person who appears under two
      accounts (e.g. registers entries on a business email but judges on a
      personal email) — automatic COI only catches matching account/meadery.

  > ⚠ **Why this must come before assigning judges.** Manual COI is enforced
  > **at the moment a judge is assigned** (and again when recording medals).
  > Declaring a COI **after** judges are already assigned to rounds does **not**
  > retroactively remove the now-conflicting assignment — there is no guard or
  > event that prunes existing assignments. Declare all COIs first, then assign
  > judges, and the conflicting pairings simply can't be created. If you discover
  > a COI late, you must **manually un-assign** the affected judge from every
  > round in that entrant's categories.

## Phase 6 — Rounds

- [ ] Create the **scoring rounds** (Rounds tab → Add Round): pick the category,
      a physical table, and an optional scheduled date/time. Split a large
      category across multiple panels (e.g. "M1A Panel A" / "M1A Panel B") here.
- [ ] Create any **SCORE_BASED medal rounds** for small categories that skip a
      preliminary scoring round (Add Round → Type = Medal → mode = Score-based).
      COMPARATIVE medal rounds are created automatically by the scoring-round
      cascade — don't hand-create those.

## Phase 7 — Assignments

- [ ] **Assign entries** to each scoring round (📦 Assign Entries) — typically all
      `RECEIVED` entries with the matching final category; a subset for split
      panels. A round can't start with zero entries.
- [ ] **Assign judges** to each round (👥 Assign Judges) — at least
      *Minimum judges per round*. COI badges show here (orange = soft/similar
      meadery, red = hard/self-entry or declared COI); hard-COI rows can't be
      ticked. *(This is why Phase 5 comes first.)*

## Phase 8 — Readiness check

- [ ] Each scoring round shows **Status = `READY`** once it has: a table, ≥ min
      judges, ≥ 1 entry, **and** the division is at `JUDGING`. Anything missing
      keeps it `PENDING` — the grid tells you what's outstanding.

## Phase 9 — Go live

- [ ] Advance the division **`REGISTRATION_CLOSED` → `JUDGING`**.
- [ ] **Start** each round (▶ Start) when its table is staffed. Starting:
      - creates a scoresheet per `RECEIVED` entry,
      - emails each assigned judge a "Judging round ready" magic link,
      - locks the round's table (and same-label tables in other divisions if
        Shared tables is ON).

---

## Known limitations to keep in mind

- **Late COI declaration is not retroactive** (see Phase 5 ⚠). Order matters.
- **Min-judges / table changes** are blocked once a round is `ACTIVE` — revert the
  round first (only possible before any judge has touched a scoresheet).
