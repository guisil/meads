# Judging Scheduler — usage guide

A standalone planning tool (NOT part of the MEADS app) that builds a two-day
judging schedule for the competition from `rounds_input.txt`.

## Files (all in `judging-planner/`)
- `rounds_input.txt` — **the single source of competition data** you edit (tables, categories+entry counts, judges).
- `original_rounds_input.txt` — untouched backup of the very first input.
- `judging_scheduler.py` — the solver (finds its input next to itself, so run from anywhere).
- `judging_scheduler_GUIDE.md` — this file.

All four live in `judging-planner/` and are **uncommitted** (working tree only), by request.

## Run it
```
python3 judging-planner/judging_scheduler.py [seed]        # from repo root
# or:  cd judging-planner && python3 judging_scheduler.py [seed]
```
- `seed` is optional (default 1). Each seed gives a different valid layout — re-run with `2`, `3`, … to see alternatives.
- It prints the grid per half-day (THURSDAY morning / afternoon, FRIDAY morning) + the Friday-afternoon BOS, with `sc` = scoring flight, `MEDAL` = comparative medal round, `Mesa N` = table.
- It will print `!! VALIDATION FAILED` or `!! could not schedule …` instead of a grid if it can't satisfy everything — it never emits an invalid schedule.

## What it always enforces (don't need to re-specify)
- Flight sizing: 5–8 samples; >8 splits into parallel scoring flights (`ceil(entries/8)`).
- Per-judge COI and per-judge availability (half-day windows).
- One comparative medal round per category, **after** its scoring, within the **open-bottle span** (see `BOTTLE_SPAN`).
- Medal-round judges are **disjoint** from that category's scoring judges (now checked across the AM→PM boundary too).
- A team stays at one table for a whole half-day; teams are **re-seated** between half-days.
- **No idle judge**: every judge in a half-day is seated, with as many tables as possible (teams of 2-3). A trio is used only when the head-count forces one (odd roster); when one is needed it goes to a judge in `TRIO_JUDGES` (e.g. Tiago, so he can step out), preferably with an experienced backup. With an even roster there's no trio.
- **Co-judging preference** (`TOGETHER`): requested judge pairs/trios are softly preferred to share a team (see config note); never enforced, always reported.
- Multinational teams are **preferred** (avoid all-Polish / all-same-nationality), but a same-nationality team is allowed when nothing else fits.
- **Pending entries** (the optional 2nd number in `CODE (total, pending)`): already-full ("safe") flights may run in the first Thursday-morning slot; the flight(s) holding pending entries — and the medal — are deferred past the **arrival deadline** (`PRE_DEADLINE_SLOTS`, default just the first `THU-AM` slot — so a 2-flight category starts in slot 1 and runs its pending flight in slot 2, still Thursday morning). `safe_flights = (total − pending) // 8`.
- **Sparkling** categories (the `Sparkling:` list): their rounds are forced into **consecutive slots in one half-day** (so the open sparkling bottle isn't left to go flat). A category on the `Score-based:` list instead runs as a **single score-based round** (scoring = medal, no second pour).
- Scheduling **packs early** (fills the safe Thu-AM window, pulls rounds forward, keeps Fri-PM mostly for BOS).
- BOS judges must exist and be available Friday afternoon (checked up front); everything else finishes before then.
- BOS itself runs Friday afternoon; the `FRI-PM` half-day may carry 1-2 leftover rounds before it.

## Editing the DATA → `rounds_input.txt`
Format (one judge per line):
```
Tables: Mesa 1, Mesa 2, ...
Categories:
PROFESSIONAL: PRO M1A/B (14, 4), PRO M1C (8, 1), ...
AMATEUR: AMA M1 (8, 1), AMA M2 (14), ...
Sparkling: AMA M4S
Score-based:
Judges
Full Name: level N; COI cat, COI cat (or -); AVAILABILITY; Lang1, Lang2
```
- **Category** = `CODE (total)` or `CODE (total, pending)`. `pending` (optional, default 0) = entries of that category **still to be received**. The code is used verbatim in the schedule (and in `PINS`, if you pin anything).
- **`Sparkling:`** — comma list of category codes whose rounds must stay in consecutive same-half-day slots (open sparkling bottle goes flat). Absent line = none.
- **`Score-based:`** — comma list of categories to run as a single score-based round (no separate prelim flight; sidesteps the open-bottle issue entirely). Absent line = none.
- **COI** = comma-separated category codes the judge canNOT judge, or `-` for none.
- **Availability** tokens: `THU/FRI` (both days, all day) · `THU` (Thursday all day) · `THU-mor` / `THU-aft` / `FRI-mor` / `FRI-aft` (one half) · combine with commas, e.g. `THU (all day), FRI-mor`.
- **Languages**: first one = native = nationality (English→EN, Spanish→ES, Polish→PL, Portuguese→PT, …).
- Parenthetical notes like `(head judge; may step out)` are ignored by the parser — annotate freely.
- Judge keys are first names; the two Mareks become `Marek L` / `Marek P` (first name + ASCII last initial). Use those keys in the CONFIG.

## Editing the POLICY → the `CONFIG` block at the top of `judging_scheduler.py`
- `PINS` — **optional** overrides, `{category_code: half_day}`. You normally leave this empty: categories are auto-assigned to days (balanced by capacity) and the solver picks the slots, packing rounds as early as possible. Pin a category only when you must force it somewhere (the pin sets the *earliest* slot it may use).
- `BOTTLE_SPAN` — how far a category's first flight and its medal may be apart: `"HALFDAY"` (tightest), `"DAY"` (morning→afternoon OK, never overnight — current default), `"NONE"` (no limit). This sets the block size the solver schedules at once.
- `PRE_DEADLINE_SLOTS` — `(half-day, local_slot)` pairs before the entry-arrival deadline (default `{("THU-AM", 0)}` = only the first Thursday-morning slot). Late flights + pending-category medals are kept out of these slots; from the next slot on, all entries are assumed in hand.
- `DAY_FILL_TARGET` — how full each day is packed before the next is used (fraction of cell capacity, default `0.88`). Higher → more onto Thursday, lighter Friday, fuller Thursday afternoon; too near `1.0` becomes hard to schedule. This is the knob for day balance now that there's no `DAY_SPLIT`.
- `HALFDAY_SLOTS` / `HALFDAY_ORDER` — slots per half-day, and their chronological order. **If you get "could not schedule", give a tight half-day one more slot**, raise `MAX_TABLES`, or relax `BOTTLE_SPAN`. Aim for ≤ ~80% cell fill.
- `MAX_TABLES` — max parallel tables (currently 5; you have 6 physical).
- `TRIO_JUDGES` — set of judges preferred in a 3-judge table (currently `{"Tiago"}`); add more, e.g. `{"Tiago", "Marc"}`. They get first claim on the trio slots the head-count creates. Note there are usually only 1-2 trio slots in the whole event, so listing many won't give them all a trio every run — the "preferred trios" report shows who got one (try another seed to rebalance).
- `TOGETHER` — optional list of judge groups (pairs/trios) you'd **prefer** to see judging together at some point, e.g. `[{"Aleli", "Filip"}, {"Marc", "Ivonne", "Gonçalo"}]`. A soft nudge, never a hard rule: they only end up together in a half-day where all are available, the run prints a "requested co-judging" report of which were achieved, and a requested *trio* takes a trio slot ahead of `TRIO_JUDGE` (so Tiago pairs that half-day). If one shows as "apart", try another seed.
- `BOS` — the BOS Professional / BOS Amateur judge lists (use judge keys).
- `LANG_TO_NAT` — language→nationality map (extend if you add languages).
- Note: `Sparkling` / `Score-based` are set in `rounds_input.txt`, not here.

## Where to change the RULES themselves (code)
- `team_pref(members)` — the **soft** multinational preference (avoid all-same / all-Polish). `team_ok(members)` is the **hard** constraint and currently always passes. To make nationality mandatory again, move the logic back into `team_ok` and re-add the validate() check.
- `team_can_judge(members, cat)` — COI check.
- `form_teams(...)` — partitions the whole roster into 2-3 person teams with no idle judge, maximising tables; trios only when forced (odd roster / more judges than tables). Trio slots go to TOGETHER trios, then `TRIO_JUDGES` (shuffled), then generic, each with a preferably experienced (`>=L2`) backup.
- `safe_flights(total, pending)` / `category_rounds(cat)` — how many flights are pending-free, and the list of rounds with which are "late" (must wait past the deadline).
- `blocks()` — groups half-days into spannable blocks per `BOTTLE_SPAN`.
- `assign_categories(...)` — auto-assigns categories to blocks, balanced by each block's cell capacity (honours `PINS`). Retried in `main()` until every block schedules.
- `schedule_block(...)` / `_place_block(...)` — the per-block solver: forms teams per half-day, then backtracks placing rounds across the block's slots (deadline, medal-after-scoring + disjoint panel, sparkling-consecutive, earliest-first packing, optional pins). Node budget is 120000 then a randomized restart with fresh teams; raise it for harder instances (but lower budgets often run faster overall by restarting sooner).
- `validate(out)` — the independent re-check; mirrors every rule (per-half-day + per-category span/deadline/sparkling). **If you add a rule, add it here too** so bad schedules can't slip through.

## Likely tweaks next session & how
- **More/fewer entries still to arrive** → edit the 2nd number in `CODE (total, pending)`.
- **Tighter/looser open-bottle rule** → set `BOTTLE_SPAN` to `"HALFDAY"` / `"DAY"` / `"NONE"`.
- **A category is sparkling / should be score-based** → add its code to the `Sparkling:` / `Score-based:` line in `rounds_input.txt`.
- **Certain judges should judge together** → add a pair/trio set to `TOGETHER` (soft preference; check the "requested co-judging" report, try another seed if "apart").
- **More/less parallelism** → `MAX_TABLES` and/or team-size logic in `form_teams`.
- **Different day balance / earlier Friday finish** → raise/lower `DAY_FILL_TARGET` (higher = more on Thursday), adjust `HALFDAY_SLOTS`, or `PINS` a category onto a specific day/half-day.
- **Change/again-soften a rule** → edit `team_pref` / `form_teams` and mirror in `validate`.
- **BOS judges / a late-Friday-only judge** → `BOS` config; add the judge to `rounds_input.txt` with availability `FRI-aft`.
- **A judge/category/count changes** → just edit `rounds_input.txt`.

## Tips
- Re-run with several seeds and pick the layout you like.
- The schedule shows tables as `Mesa 1..N`; within a half-day a table = one fixed team across its slots.
- Last validated state (2026-06-10): 11 judges (Mike withdrew). 13 categories, 135 entries, 19 pending across 10 categories. **No hand-assignment** — `PINS={}`, categories auto-assigned to days. `BOTTLE_SPAN="DAY"`; per-day solver; pending-driven flight deferral with a **slot-level** deadline (`PRE_DEADLINE_SLOTS={("THU-AM",0)}`) — safe flights run Thu-AM slot 1, pending flights slot 2 (still morning), medals later. `Sparkling: AMA M4S` (consecutive same-half-day slots); no score-based categories yet. Slots THU-AM 2 / THU-PM 4 / FRI-AM 3 / FRI-PM 2. Thu-AM fills both slots (9-10 rounds, 5 tables × 2 — no idle judge, Tiago paired since the roster is even). `DAY_FILL_TARGET` packs Thursday heavy (Thursday afternoon uses its slots, Friday a short morning, Fri-PM free for BOS); values near 1.0 fill most but get slow/fragile. `TOGETHER=[]` by default (soft co-judging preference available). BOS Amateur = Filip, Marc, Carlos. Default (no TOGETHER) valid on seeds 1-5.
