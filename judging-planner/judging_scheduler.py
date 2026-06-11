#!/usr/bin/env python3
"""Judging-round scheduler for the MEADS competition.

Reads the competition setup (tables, categories+entry counts, judges with level /
COI / availability / languages) from rounds_input.txt and emits a two-day schedule of
scoring + medal rounds + BOS, honouring:

  * flight sizing (5-8 samples; >8 splits into parallel flights)
  * per-judge COI and per-judge availability (half-day windows)
  * one comparative medal round per category, AFTER its scoring, within the open-bottle
    span (BOTTLE_SPAN: same half-day / same day / unlimited)
  * medal-round judges disjoint from that category's scoring judges (across half-days too)
  * a team stays put for a whole half-day; teams re-seated between half-days; no idle judge
  * no linguistically-isolated judge: each shares a language with a tablemate (a bilingual
    judge can bridge a mono-lingual one); nationality is unconstrained
  * at least one experienced (>=L2) judge per table: two level-1 novices can't pair, but
    may share a table as a trio anchored by a more experienced judge
  * 2- vs 3-judge tables set by TABLE_SIZING (PAIRS / BALANCED / TRIOS), optionally tightened
    per half-day via TABLE_CAP to force trios; trio slots go to TRIO_JUDGES (e.g. Tiago)
  * pending entries (a 2nd number per category) deferred past the arrival deadline
  * sparkling categories kept in consecutive slots; score-based categories run a single
    round (scoring = medal); TOGETHER co-judging preference
  * categories auto-assigned to days (DAY_FILL_TARGET); BOS Friday afternoon, after any
    scoring/medal rounds that spill into the afternoon

Config knobs are at the top — tweak and re-run. The schedule is found by randomized
backtracking; run again for a different valid layout. See the GUIDE for full usage.

Usage:
  python3 judging_scheduler.py [seed]
  python3 judging_scheduler.py [seed] --plan [--done-through HALFDAY]   # write plan.txt
  python3 judging_scheduler.py --resume plan.txt                        # re-plan remaining
"""
import math
import os
import random
import re
import sys
import unicodedata
from itertools import combinations

# Reproducibility: a given [seed] should yield the SAME schedule every run, so you can
# browse seeds and later regenerate the chosen one with --plan. random.Random(seed) already
# pins the RNG, but Python randomizes set-iteration order per process (PYTHONHASHSEED), which
# leaks into team formation. Re-exec once with it fixed. Set PYTHONHASHSEED yourself to override.
if os.environ.get("PYTHONHASHSEED") != "0":
    os.environ["PYTHONHASHSEED"] = "0"
    os.execv(sys.executable, [sys.executable, *sys.argv])

INPUT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "rounds_input.txt")

# ----------------------------------------------------------------------------
# CONFIG — the only things not taken from rounds_input.txt. Tweak + re-run.
# A category's scoring + medal stay within the open-bottle span (BOTTLE_SPAN, currently
# same day, never overnight) so no bottle is left open too long. Work is front-loaded
# onto Thursday; Friday afternoon is kept light, but it carries rounds (not only BOS)
# whenever the earlier half-days don't have room — e.g. once FRI-AM is capped or trimmed.
# ----------------------------------------------------------------------------
# Categories are auto-assigned to days (balanced by capacity) and the solver picks
# the slots — you don't pin anything by hand. PINS is an optional override: map a
# category code to a half-day to force its earliest slot there (e.g. {"AMA M2": "THU-AM"}).
# Leave empty to let the solver place everything.
PINS = {}
HALFDAY_SLOTS = {"THU-AM": 2, "THU-PM": 3, "FRI-AM": 2, "FRI-PM": 2}
HALFDAY_ORDER = ["THU-AM", "THU-PM", "FRI-AM", "FRI-PM"]  # chronological

# Open-bottle span: how far a category's first flight and its medal may be apart.
#   "HALFDAY" — same half-day (tightest)
#   "DAY"     — same day, morning->afternoon OK, never overnight
#   "NONE"    — no limit
BOTTLE_SPAN = "DAY"

# Slots before the entry-arrival deadline, as (half-day, local_slot_index). Late
# flights (those holding entries still to be received) and pending-category medals
# may NOT be scheduled in these slots. We assume hand-delivered entries are in by the
# 2nd Thursday-morning slot, so ONLY the first THU-AM slot is at risk — a category
# with enough entries for 2+ flights still starts its safe flight in slot 1, with the
# pending flight following in slot 2 (no need to push it all the way to the afternoon).
PRE_DEADLINE_SLOTS = {("THU-AM", 0)}

# How full each day is packed before the next day is used, as a fraction of its cell
# capacity (slots x tables). Higher = more onto Thursday / less onto Friday and a fuller
# Thursday afternoon; too close to 1.0 gets hard to schedule (no slack for medal ordering).
# NOTE: at 1.0 each day is packed to 100% capacity, and with TABLE_CAP forcing trios the
# backtracker can thrash for tens of seconds (or appear to hang). Keep some slack (~0.85)
# whenever TABLE_CAP is set.
DAY_FILL_TARGET = 0.85

MAX_TABLES = 5
# Per-half-day table cap (optional). Overrides MAX_TABLES for the named half-day, opening
# FEWER tables there — which forces larger (3-judge) teams, since every judge is still
# seated. Use it to manufacture trio slots on a specific half-day: e.g. {"FRI-AM": 4}
# turns Friday morning's 10 judges from 5 pairs into 2 trios + 2 pairs (so Samuel can take
# a trio). Caution: fewer tables = fewer parallel flights, so the half-day's slots must
# still hold its rounds (a fully-packed half-day like Thursday afternoon will overflow
# unless you also free room — lower DAY_FILL_TARGET or add a slot). Empty = MAX_TABLES.
TABLE_CAP = {"THU-PM": 4, "FRI-AM": 4}
# Table sizing — how many tables to open, which sets the 2- vs 3-judge mix (every judge is
# always seated, never idle):
#   "PAIRS"    — as many tables as possible: mostly 2-judge tables, a trio only when an odd
#                head-count forces one (most parallel flights / fastest)
#   "TRIOS"    — as few tables as possible: mostly 3-judge tables (fewer parallel flights, so
#                the schedule needs more slots — may not fit at a high DAY_FILL_TARGET)
#   "BALANCED" — a middle number of tables: a mix of 2s and 3s (coarse for small rosters,
#                since it can only shift by a whole table)
TABLE_SIZING = "PAIRS"
# Judges PREFERRED in a 3-judge table (e.g. so they can step out without sinking the
# table). They get first claim on the trio slots the head-count naturally creates; a pair
# is still fine when there's no free trio slot (and there are usually only 1-2 trio slots
# in the whole event, so not everyone listed will get one). Add names as needed.
TRIO_JUDGES = {"Tiago", "António", "Samuel", "Carlos"}

# Judge groups (pairs or trios) you'd PREFER to see judging together on a team at some
# point — a soft nudge, not a hard rule (the schedule won't fail to honour it). Each
# entry is a set of judge keys; they can only end up together in a half-day where all are
# available. The run reports which were achieved.
# Example: TOGETHER = [{"Aleli", "Filip"}, {"Marc", "Ivonne", "Gonçalo"}]
TOGETHER = []

BOS = {
    "BOS Professional": ["Marek L", "Marek P", "Ivonne"],
    "BOS Amateur":      ["Filip", "Marc", "Aleli"],
}

LANG_TO_NAT = {"english": "EN", "spanish": "ES", "polish": "PL",
               "portuguese": "PT", "italian": "IT", "french": "FR", "german": "DE"}


def _ascii_initial(word):
    if word[0] == "Ł":  # Polish Ł has no NFKD decomposition
        return "L"
    folded = unicodedata.normalize("NFKD", word[0]).encode("ascii", "ignore").decode()
    return folded[0] if folded else word[0]


def _parse_availability(text):
    """e.g. 'THU/FRI' -> both days all halves; 'THU (all day), FRI-mor';
    'THU-aft'. Returns a set of {DAY}-{AM|PM}."""
    text = re.sub(r"\([^)]*\)", "", text).lower()
    out = set()
    for part in re.split(r"[;,]", text):
        part = part.strip()
        days = [d for d in ("THU", "FRI") if d.lower() in part]
        if not days:
            continue
        if "mor" in part:
            halves = ["AM"]
        elif "aft" in part:
            halves = ["PM"]
        else:
            halves = ["AM", "PM"]
        out.update(f"{d}-{h}" for d in days for h in halves)
    return out


def parse_input(path):
    """Parse rounds_input.txt -> (judges, categories, pending). Category counts are
    written `CODE (total)` or `CODE (total, still_to_receive)`; the optional second
    number is how many of that category's entries have not yet arrived (defaults 0).
    Judge keys are first names, disambiguated with an ASCII last-initial on collision
    (e.g. 'Marek L')."""
    cats = {}
    pend = {}
    sparkling = set()
    scorebased = set()
    raw_judges = []  # (full_name, level, coi_set, avail_set, nat)
    section = None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        low = line.strip().lower()
        if low.startswith("categories"):
            section = "cat"; continue
        if low.startswith("judges"):
            section = "judge"; continue
        if low.startswith("tables"):
            continue
        if low.startswith("sparkling"):
            sparkling |= {c.strip() for c in line.split(":", 1)[1].split(",") if c.strip()}
            continue
        if low.startswith("score-based"):
            scorebased |= {c.strip() for c in line.split(":", 1)[1].split(",") if c.strip()}
            continue
        if section == "cat" or low.startswith(("professional", "amateur")):
            # category counts live in (parens) — parse the RAW line.
            payload = line.split(":", 1)[1] if ":" in line else line
            for m in re.finditer(r"([A-Za-z0-9][^,(]*?)\s*\((\d+)(?:\s*,\s*(\d+))?\)", payload):
                code = m.group(1).strip()
                cats[code] = int(m.group(2))
                pend[code] = int(m.group(3)) if m.group(3) else 0
            continue
        if section == "judge" and ":" in line:
            clean = re.sub(r"\([^)]*\)", "", line)  # strip notes/(all day) for judges only
            name, rest = clean.split(":", 1)
            fields = [f.strip() for f in rest.split(";")]
            level = int(re.search(r"\d+", fields[0]).group())
            coi = set() if fields[1] in ("-", "") else {c.strip() for c in fields[1].split(",")}
            avail = _parse_availability(fields[2]) if len(fields) > 2 else set()
            langs = fields[3] if len(fields) > 3 else "english"
            langset = {l.strip().lower() for l in langs.split(",") if l.strip()}
            nat = LANG_TO_NAT.get(langs.split(",")[0].strip().lower(), "??")
            raw_judges.append((name.strip(), level, coi, avail, nat, langset))

    first_counts = {}
    for nm, *_ in raw_judges:
        first_counts[nm.split()[0]] = first_counts.get(nm.split()[0], 0) + 1
    judges = {}
    for full, level, coi, avail, nat, langset in raw_judges:
        parts = full.split()
        key = parts[0] if first_counts[parts[0]] == 1 else f"{parts[0]} {_ascii_initial(parts[-1])}"
        judges[key] = (level, nat, coi, avail, langset)
    return judges, cats, pend, sparkling, scorebased


JUDGES, CATEGORIES, PENDING, SPARKLING, SCORE_BASED = parse_input(INPUT_FILE)
NAT = {n: v[1] for n, v in JUDGES.items()}
LEVEL = {n: v[0] for n, v in JUDGES.items()}
COI = {n: v[2] for n, v in JUDGES.items()}
AVAIL = {n: v[3] for n, v in JUDGES.items()}
LANGS = {n: v[4] for n, v in JUDGES.items()}
ROSTER = {hd: {j for j in JUDGES if hd in AVAIL[j]} for hd in HALFDAY_SLOTS}


def cap_for(hd):
    """Max tables to open in a half-day: MAX_TABLES, or a tighter per-half-day override."""
    return min(MAX_TABLES, TABLE_CAP.get(hd, MAX_TABLES))


def flights(entries):
    return max(1, math.ceil(entries / 8))


def team_ok(members):
    """Hard constraints on team composition:
    1. NO linguistically-isolated judge — every judge must share a language with at least
       one OTHER judge at the table. A bilingual judge can bridge, so e.g. a Portuguese-only
       judge (Carlos) may sit with a PT+EN judge and an EN-only judge: Carlos shares PT with
       the bridge, the EN judge shares EN with it. (A pair still must share a language directly.)
    2. At least one experienced (>=L2) judge per table — two level-1 novices can't be a pair,
       but they may share a table as a trio anchored by a more experienced judge.
    Nationality is NOT constrained — any mix, including a fully same-country team, is allowed.
    NAT is still parsed if a nationality rule is ever wanted again (add it + mirror in validate)."""
    members = list(members)
    if all(LEVEL[m] == 1 for m in members):
        return False
    return all(any(m != o and LANGS[m] & LANGS[o] for o in members) for m in members)


def team_can_judge(members, cat):
    return all(cat not in COI[m] for m in members)


def wants_together(team):
    """True if this team is, or contains, a requested TOGETHER co-judging group."""
    return any(set(g) <= team for g in TOGETHER)


def form_teams(roster, n_tables, rng):
    """Partition the whole roster into teams of 2-3 with **no idle judge**. How many
    tables are opened (and thus the 2- vs 3-judge mix) follows TABLE_SIZING: PAIRS opens
    as many as possible (mostly pairs), TRIOS as few as possible (mostly trios), BALANCED
    a middle count. Trio slots go first to requested TOGETHER trios, then to judges in
    TRIO_JUDGES (each anchoring a trio with a preferably experienced >=L2 backup). Every
    judge must share a language with a tablemate (team_ok); TOGETHER groups are softly kept
    intact. Nationality is not constrained. Returns
    list[set] or None if everyone can't be seated within n_tables."""
    pool = set(roster)
    n = len(pool)
    if n < 2:
        return None
    lo = -(-n // 3)                      # fewest tables (largest teams; all trios)
    hi = n // 2                          # most tables (all pairs)
    k = {"TRIOS": lo, "BALANCED": (lo + hi) // 2}.get(TABLE_SIZING, hi)
    k = min(k, n_tables)                 # cap at the available table count
    n_trios = n - 2 * k                  # trios needed so every judge is seated
    if not 0 <= n_trios <= k:
        return None                      # too many judges for n_tables teams of 2-3

    teams = []

    def pick_trio(anchor=None):
        members = [j for j in pool if j != anchor]
        rng.shuffle(members)
        combos = [set(c) for c in combinations(members, 2 if anchor else 3)]
        combos = [c for c in combos if team_ok(({anchor} | c) if anchor else c)]
        if not combos:
            return None
        def key(extra):                  # prefer co-judging group, then an experienced backup
            tm = ({anchor} | extra) if anchor else extra
            backup = extra if anchor else tm
            return (wants_together(tm), max(LEVEL[j] for j in backup) >= 2)
        combos.sort(key=key, reverse=True)
        return ({anchor} | combos[0]) if anchor else combos[0]

    # requested co-judging trios claim trio slots first (so they actually form, since
    # otherwise Tiago takes the lone trio); then Tiago's trio; then any remaining trios
    for g in TOGETHER:
        if n_trios == 0:
            break
        gset = set(g)
        if len(gset) == 3 and gset <= pool:
            teams.append(gset); pool -= gset; n_trios -= 1
    # then judges preferred for a trio anchor the remaining slots (shuffled for fairness
    # across half-days when there are more preferred judges than free trio slots)
    preferred = [j for j in TRIO_JUDGES if j in pool]
    rng.shuffle(preferred)
    for tj in preferred:
        if n_trios == 0:
            break
        if tj not in pool:               # already pulled into an earlier trio as a backup
            continue
        trio = pick_trio(anchor=tj)
        if trio is None:
            return None
        teams.append(trio); pool -= trio; n_trios -= 1
    while n_trios > 0:
        trio = pick_trio()
        if trio is None:
            return None
        teams.append(trio); pool -= trio; n_trios -= 1

    rest = list(pool)
    rng.shuffle(rest)
    while len(rest) >= 2:
        # seat the most-constrained judge first (fewest language-compatible partners)
        # so a Portuguese-only judge isn't stranded with no valid partner left
        a = min(rest, key=lambda x: sum(1 for b in rest if b != x and team_ok({x, b})))
        rest.remove(a)
        candidates = [b for b in rest if team_ok({a, b})]
        if not candidates:
            return None
        partner = max(candidates, key=lambda b: wants_together({a, b}))
        rest.remove(partner)
        teams.append({a, partner})
    if rest:                             # leftover means the arithmetic was off
        return None
    return teams


def safe_flights(total, pending):
    """How many of a category's flights are free of pending entries (can run before the
    arrival deadline). Flights are sized as evenly as possible; pending entries occupy
    the last flights, while received entries fill the smallest flights first to maximise
    the pending-free count. With pending == 0 every flight is safe."""
    n = flights(total)
    base, rem = divmod(total, n)
    sizes = sorted([base + 1] * rem + [base] * (n - rem))
    received = total - pending
    safe = 0
    for s in sizes:
        if received >= s:
            received -= s
            safe += 1
        else:
            break
    return safe


def category_rounds(cat):
    """Rounds a category needs, as (kind, is_late):
       'S'  scoring flight · 'M' comparative medal · 'SB' single score-based round.
    is_late = the round holds entries that may still be arriving, so it must wait
    until after the arrival deadline. Already-full ('safe') flights are not late; the
    medal (or a score-based round) is late iff anything is pending."""
    total = CATEGORIES[cat]
    pending = PENDING.get(cat, 0)
    if cat in SCORE_BASED:
        return [("SB", pending > 0)]
    nflights = flights(total)
    safe = safe_flights(total, pending)
    rounds = [("S", i >= safe) for i in range(nflights)]
    rounds.append(("M", pending > 0))
    return rounds


def blocks():
    """Group the active half-days into spannable blocks per BOTTLE_SPAN. A category's
    rounds must all fall inside one block (so its bottle isn't open longer than the span)."""
    present = [hd for hd in HALFDAY_ORDER if hd in HALFDAY_SLOTS]
    if BOTTLE_SPAN == "HALFDAY":
        return [[hd] for hd in present]
    if BOTTLE_SPAN == "NONE":
        return [present]
    return [g for g in ([hd for hd in present if hd.startswith(d)] for d in ("THU", "FRI")) if g]


def assign_categories(blocks_list, rng, cats=None, rounds_fn=None):
    """Auto-assign categories to blocks, balancing rounds against each block's cell
    capacity (slots x tables). PINS force a category into the block holding its pinned
    half-day. Bigger categories placed first for tighter packing. `cats`/`rounds_fn`
    let resume restrict to the still-unfinished categories and their remaining rounds."""
    rounds_of = rounds_fn or category_rounds
    caps = [sum(HALFDAY_SLOTS[hd] * min(cap_for(hd), len(ROSTER[hd]) // 2) for hd in b)
            for b in blocks_list]
    targets = [c * DAY_FILL_TARGET for c in caps]
    load = [0] * len(blocks_list)
    assigned = [[] for _ in blocks_list]
    cats = list(cats if cats is not None else CATEGORIES)
    rng.shuffle(cats)
    cats.sort(key=lambda c: len(rounds_of(c)), reverse=True)  # big first, shuffle breaks ties
    for c in cats:
        r = len(rounds_of(c))
        if c in PINS and any(PINS[c] in b for b in blocks_list):
            bi = next(i for i, b in enumerate(blocks_list) if PINS[c] in b)
        else:
            # fill earlier blocks up to their target before spilling into later ones;
            # once every block is past target, fall back to the least-loaded by ratio
            bi = next((i for i in range(len(blocks_list)) if load[i] + r <= targets[i]), None)
            if bi is None:
                bi = min(range(len(blocks_list)),
                         key=lambda i: (load[i] + r) / caps[i] if caps[i] else float("inf"))
        assigned[bi].append(c)
        load[bi] += r
    return assigned


def schedule_block(block, cats, rng, tries=8000, rounds_fn=None, scored_by=None,
                   pinned_teams=None, occupied=None, seed_sc=None):
    """Schedule the given categories across the block's half-days (teams are re-seated
    each half-day). `rounds_fn(cat)` supplies the rounds to place (defaults to the full
    `category_rounds`; used by resume to pass only the remaining rounds). `scored_by`
    seeds, per category, judges who already scored it elsewhere so the medal panel still
    excludes them. For a partially-completed half-day, `pinned_teams[hd]` fixes its teams
    (a list, `None` for tables not revealed by the done rounds), `occupied` holds the
    cells already used, and `seed_sc` the done-scoring (hd, slot) positions. Returns
    {hd: [(local_slot, table, kind, cat, team)]} or None."""
    rounds_of = rounds_fn or category_rounds
    pinned_teams = pinned_teams or {}
    slots = [(hd, ls) for hd in block for ls in range(HALFDAY_SLOTS[hd])]  # global slot order
    home_pos = {c: (block.index(PINS[c]) if c in PINS else 0) for c in cats}
    blockpos = {hd: i for i, hd in enumerate(block)}
    predead = [(hd, ls) in PRE_DEADLINE_SLOTS for hd, ls in slots]
    insts = [(c, kind, late) for c in cats for kind, late in rounds_of(c)]
    if len(insts) > sum(cap_for(hd) for hd, _ in slots):
        return None  # assignment overfills this block

    for _ in range(tries):
        teams = {hd: (pinned_teams[hd] if hd in pinned_teams else form_teams(ROSTER[hd], cap_for(hd), rng))
                 for hd in block}
        if any(t is None for t in teams.values()):
            continue
        placed = _place_block(insts, teams, slots, home_pos, blockpos, predead, rng,
                              scored_by, occupied, seed_sc)
        if placed is not None:
            out = {}
            for si, t, kind, c in placed:
                hd, ls = slots[si]
                out.setdefault(hd, []).append((ls, t, kind, c, teams[hd][t]))
            return out
    return None


def _place_block(insts, teams, slots, home_pos, blockpos, predead, rng, scored_by=None,
                 occupied=None, seed_sc=None):
    """Backtracking placement of round-instances onto (global_slot, table). Scoring
    first, medals last (tightest first). Honours: home (no round before its half-day),
    the arrival deadline (late rounds skip pre-deadline slots), one medal strictly
    after all of its scoring with a judge-disjoint panel, and sparkling categories in
    consecutive same-half-day slots. Returns assignment list or None."""
    occupied = occupied or set()
    hd_of = [hd for hd, _ in slots]
    nslot = len(slots)

    def can(c, si):  # slot is usable at-or-after the category's home half-day
        return blockpos[hd_of[si]] >= home_pos[c]

    # feasibility precheck: every category needs at least one team that can judge it
    for c in home_pos:
        if not any(blockpos[hd] >= home_pos[c] and tm is not None and team_can_judge(tm, c)
                   for hd in teams for tm in teams[hd]):
            return None

    scoring = [x for x in insts if x[1] in ("S", "SB")]
    medals = [x for x in insts if x[1] == "M"]
    rng.shuffle(scoring)
    medals.sort(key=lambda x: sum(1 for hd in teams for tm in teams[hd]
                                  if tm is not None and team_can_judge(tm, x[0])))  # tightest first
    order = scoring + medals

    grid = [[False] * MAX_TABLES for _ in range(nslot)]
    for si, (hd, ls) in enumerate(slots):           # block cells already used by 'done' rounds
        for t in range(MAX_TABLES):
            if (hd, ls, t) in occupied:
                grid[si][t] = True
    # seed each category's scoring slots already completed in this window (so a still-owed
    # medal lands after them, and a sparkling medal stays consecutive with them)
    sc_slots = {c: [si for si, (hd, ls) in enumerate(slots) if (hd, ls) in (seed_sc or {}).get(c, ())]
                for c in home_pos}
    sc_judges = {c: set(scored_by.get(c, ())) if scored_by else set() for c in home_pos}
    assignment = []
    nodes = [0]

    def candidates(c, kind, late, spark):
        for si in range(nslot):
            if not can(c, si) or (late and predead[si]):
                continue
            if kind == "M" and sc_slots[c]:      # only order vs scoring placed in THIS window
                if si <= max(sc_slots[c]):
                    continue
                if spark:
                    last = max(sc_slots[c])
                    if hd_of[si] != hd_of[last] or slots[si][1] != slots[last][1] + 1:
                        continue
            ts = teams[hd_of[si]]
            for t in range(len(ts)):
                if ts[t] is None or grid[si][t] or not team_can_judge(ts[t], c):
                    continue
                if kind == "M" and (ts[t] & sc_judges[c]):
                    continue
                yield si, t

    def bt(i):
        nodes[0] += 1
        if nodes[0] > 120000:
            return None
        if i == len(order):
            return True
        c, kind, late = order[i]
        spark = c in SPARKLING
        # prefer the earliest allowed slot (front-load: fill the safe Thu-AM window,
        # keep Fri-PM mostly for BOS), random tie-break among cells in the same slot
        cand = sorted(candidates(c, kind, late, spark), key=lambda st: (st[0], rng.random()))
        for si, t in cand:
            grid[si][t] = True
            assignment.append((si, t, kind, c))
            added = None
            if kind in ("S", "SB"):
                added = teams[hd_of[si]][t] - sc_judges[c]
                sc_slots[c].append(si)
                sc_judges[c] |= added
            r = bt(i + 1)
            if r is True:
                return True
            grid[si][t] = False
            assignment.pop()
            if kind in ("S", "SB"):
                sc_slots[c].pop()
                sc_judges[c] -= added
            if r is None:
                return None
        return False

    return assignment if bt(0) is True else None


def _global_slot(hd, local):
    base = 0
    for h in HALFDAY_ORDER:
        if h == hd:
            return base + local
        base += HALFDAY_SLOTS.get(h, 0)


def validate(out):
    """Independent re-check of a completed schedule; returns a list of violations."""
    bad = []
    # per-half-day: availability, COI, double-booking, one-table-per-judge
    for hd, rows in out.items():
        seen = {}
        jtab = {}
        for ls, table, kind, cat, team in rows:
            for j in team:
                if hd not in AVAIL[j]:
                    bad.append(f"{hd}: {j} not available")
                if cat in COI[j]:
                    bad.append(f"{hd}: {j} has COI on {cat}")
                if j in jtab and jtab[j] != table:
                    bad.append(f"{hd}: {j} moved tables ({jtab[j]}->{table})")
                jtab[j] = table
            if (ls, table) in seen:
                bad.append(f"{hd}: double-booked slot{ls} table{table}")
            seen[(ls, table)] = team

    # per-category: span, deadline, medal ordering + disjoint panel, sparkling, home
    by_cat = {}
    for hd, rows in out.items():
        for ls, table, kind, cat, team in rows:
            by_cat.setdefault(cat, []).append((_global_slot(hd, ls), hd, ls, kind, team))
    for cat, rs in by_cat.items():
        sc = [(gs, hd, ls, team) for gs, hd, ls, kind, team in rs if kind in ("S", "SB")]
        med = [(gs, hd, ls, team) for gs, hd, ls, kind, team in rs if kind == "M"]
        sc_judges = set().union(*(t for _, _, _, t in sc)) if sc else set()
        if cat in PINS:
            home_gs = _global_slot(PINS[cat], 0)
            if any(gs < home_gs for gs, hd, ls, kind, team in rs):
                bad.append(f"{cat}: round before its pinned half-day {PINS[cat]}")
        for gs, hd, ls, team in med:
            if any(gs <= s for s, _, _, _ in sc):
                bad.append(f"{cat}: medal not after all scoring")
            if team & sc_judges:
                bad.append(f"{cat}: medal panel shares a judge with scoring")
        # deadline: late rounds must sit after the arrival deadline (safe flights only before it)
        total, pending = CATEGORIES[cat], PENDING.get(cat, 0)
        if pending > 0:
            safe = min(flights(total), (total - pending) // 8)
            pre_sc = sum(1 for gs, hd, ls, team in sc if (hd, ls) in PRE_DEADLINE_SLOTS)
            if pre_sc > safe:
                bad.append(f"{cat}: {pre_sc} scoring flights before the deadline but only {safe} safe")
            if any((hd, ls) in PRE_DEADLINE_SLOTS for gs, hd, ls, team in med):
                bad.append(f"{cat}: medal scheduled before the deadline with entries still pending")
        # span policy
        hds = {hd for gs, hd, ls, kind, team in rs}
        if BOTTLE_SPAN == "HALFDAY" and len(hds) > 1:
            bad.append(f"{cat}: spans more than one half-day under HALFDAY policy")
        if BOTTLE_SPAN == "DAY" and len({hd.split('-')[0] for hd in hds}) > 1:
            bad.append(f"{cat}: spans overnight under DAY policy")
        # sparkling: one half-day, consecutive slots (medal-after-scoring already checked)
        if cat in SPARKLING:
            if len(hds) > 1:
                bad.append(f"{cat} (sparkling): spans more than one half-day")
            else:
                locs = sorted(ls for gs, hd, ls, kind, team in rs)
                if locs and locs[-1] - locs[0] != len(locs) - 1:
                    bad.append(f"{cat} (sparkling): rounds not in consecutive slots")
    return bad


LABEL = {"THU-AM": "THURSDAY morning", "THU-PM": "THURSDAY afternoon",
         "FRI-AM": "FRIDAY morning", "FRI-PM": "FRIDAY afternoon"}
TAG = {"S": "scoring", "M": "MEDAL  ", "SB": "score  "}
PLAN_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "plan.txt")


def print_grid(out, done_cells=None):
    done_cells = done_cells or set()
    for hd in HALFDAY_ORDER:
        if hd not in out:
            continue
        pre = sorted(ls for h, ls in PRE_DEADLINE_SLOTS if h == hd)
        note = f"  [slot {','.join(str(l + 1) for l in pre)}: fully-received rounds only]" if pre else ""
        print(f"\n=== {LABEL[hd]}{note} ===")
        for slot, table, kind, cat, team in sorted(out[hd], key=lambda r: (r[0], r[1])):
            mark = "  (done)" if (hd, slot, table) in done_cells else ""
            print(f"  Slot {slot+1}  Mesa {table+1}  {TAG[kind]}  {cat:<11}  {', '.join(sorted(team))}{mark}")


def print_reports(out):
    print("\n=== FRIDAY afternoon — Best of Show ===")
    for name, judges in BOS.items():
        print(f"  {name}: {', '.join(judges)}")
    if TOGETHER:
        placed = [(hd, frozenset(team)) for hd, rows in out.items() for *_, team in rows]
        print("\n=== requested co-judging (soft preference) ===")
        for g in TOGETHER:
            gset = set(g)
            where = [hd for hd in HALFDAY_ORDER if any(h == hd and gset <= t for h, t in placed)]
            if where:
                print(f"  together: {', '.join(sorted(gset))}  ({', '.join(LABEL[h] for h in where)})")
            else:
                elig = [hd for hd in HALFDAY_ORDER if hd in HALFDAY_SLOTS and gset <= ROSTER[hd]]
                why = "no shared available half-day" if not elig else "not in this layout — try another seed"
                print(f"  apart:    {', '.join(sorted(gset))}  ({why})")
    if TRIO_JUDGES:
        trios = {(hd, frozenset(team)) for hd, rows in out.items() for *_, team in rows if len(team) == 3}
        print("\n=== preferred trios ===")
        for tj in sorted(TRIO_JUDGES):
            where = [hd for hd in HALFDAY_ORDER if any(h == hd and tj in t for h, t in trios)]
            print(f"  {tj}: trio in {', '.join(LABEL[h] for h in where)}" if where
                  else f"  {tj}: paired throughout (no free trio slot)")
    pend = {c: p for c, p in PENDING.items() if p}
    if pend:
        print("\n=== pending entries (still to be received) ===")
        for c in sorted(pend):
            print(f"  {c}: {pend[c]} of {CATEGORIES[c]} pending")


def schedule_all(rng, cats=None, rounds_fn=None, scored_by=None,
                 pinned_teams=None, occupied=None, seed_sc=None):
    """Run the full assign + place loop; returns the schedule dict or None."""
    blocks_list = blocks()
    for _ in range(60):
        assigned = assign_categories(blocks_list, rng, cats, rounds_fn)
        attempt = {}
        for b, bcats in zip(blocks_list, assigned):
            res = schedule_block(b, bcats, rng, tries=3000, rounds_fn=rounds_fn, scored_by=scored_by,
                                 pinned_teams=pinned_teams, occupied=occupied, seed_sc=seed_sc)
            if res is None:
                break
            for hd, rows in res.items():
                attempt.setdefault(hd, []).extend(rows)
        else:
            return attempt
    return None


def check_bos():
    bad = [f"{name}: {j} " + ("is not a known judge" if j not in JUDGES else "not available Friday afternoon")
           for name, panel in BOS.items() for j in panel
           if j not in JUDGES or "FRI-PM" not in AVAIL[j]]
    if bad:
        print("!! BOS PANEL INVALID:")
        for b in sorted(set(bad)):
            print("   -", b)
    return not bad


def emit_plan(out, done_through):
    """Write an editable resume plan: a Remaining line + every round marked done/todo.
    `done_through` is a half-day (whole half-day done) or `half-day:slot` (done through
    that slot — leaves the half-day partial and still in the Remaining window)."""
    if done_through:
        ct_hd, _, ct_s = done_through.partition(":")
        ct_idx = HALFDAY_ORDER.index(ct_hd)
        ct_slot = (int(ct_s) - 1) if ct_s else HALFDAY_SLOTS.get(ct_hd, 1) - 1
    else:
        ct_idx, ct_slot = -1, -1

    def is_done(hd, slot):
        hi = HALFDAY_ORDER.index(hd)
        return hi < ct_idx or (hi == ct_idx and slot <= ct_slot)

    rem = [(hd, HALFDAY_SLOTS[hd]) for hd in HALFDAY_ORDER
           if hd in out and any(not is_done(hd, s) for s, *_ in out[hd])]
    lines = [
        "# Resume plan. Edit, then re-plan the remaining rounds with:",
        f"#     python3 judging_scheduler.py --resume {os.path.basename(PLAN_FILE)}",
        "#",
        "# - Mark each round 'done' (it happened) or 'todo' (missed / not done). Only 'done'",
        "#   rounds are kept; everything still owed is rescheduled into the 'Remaining' half-days.",
        "# - A half-day may be partly done: keep it in 'Remaining' and mark the rounds that",
        "#   happened 'done' (with their Mesa + judges) — its teams are reused for the rest.",
        "# - Pending entries didn't arrive? edit the counts in rounds_input.txt.",
        "# - A judge left? edit their availability in rounds_input.txt (e.g. THU/FRI -> THU).",
        "# - Lost time? trim the 'Remaining' line (cut a slot count or delete a half-day).",
        "",
        "Remaining: " + ", ".join(f"{hd}={n}" for hd, n in rem),
        "",
        "Rounds:",
    ]
    for hd in HALFDAY_ORDER:
        if hd not in out:
            continue
        for slot, table, kind, cat, team in sorted(out[hd], key=lambda r: (r[0], r[1])):
            if is_done(hd, slot):
                lines.append(f"done  {hd}  slot{slot+1}  Mesa{table+1}  {TAG[kind].strip()}  "
                             f"{cat}   {', '.join(sorted(team))}")
            else:
                lines.append(f"todo  {hd}  -  -  {TAG[kind].strip()}  {cat}")
    with open(PLAN_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"\n[plan written to {PLAN_FILE} — edit it, then re-run with --resume]")


def parse_plan(path):
    """Read a resume plan -> (windows, done). windows = [(half-day, slots)]; done =
    [(half-day, slot_idx, table_idx, kind, cat, team)]. 'todo' lines are ignored
    (the remaining work is recomputed from the categories minus what's done)."""
    windows, done = [], []
    kinds = {"scoring": "S", "medal": "M", "score": "SB"}
    codes = sorted(CATEGORIES, key=len, reverse=True)
    section = None
    for raw in open(path, encoding="utf-8"):
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        low = line.strip().lower()
        if low.startswith("remaining"):
            for part in line.split(":", 1)[1].split(","):
                if "=" in part:
                    hd, n = part.split("=")
                    windows.append((hd.strip(), int(n)))
            continue
        if low.startswith("rounds"):
            section = "rounds"; continue
        if section == "rounds" and line.split()[0].lower() == "done":
            p = line.split()
            hd, kind = p[1], kinds.get(p[4].lower())
            slot = int(p[2].lower().replace("slot", "")) - 1
            table = int(p[3].lower().replace("mesa", "")) - 1
            rest = " ".join(p[5:])
            cat = next((c for c in codes if rest == c or rest.startswith(c + " ")), None)
            if cat and kind:
                team = {j.strip() for j in rest[len(cat):].split(",") if j.strip()}
                done.append((hd, slot, table, kind, cat, team))
    return windows, done


def run_resume(rng, plan_path):
    global HALFDAY_SLOTS, ROSTER
    windows, done = parse_plan(plan_path)
    if not windows:
        print("!! no 'Remaining:' half-days in the plan — nothing to schedule."); return 1
    win_hds = {hd for hd, _ in windows}

    scoring_done, medal_done, scored_by = {}, set(), {}
    pinned_idx, occupied, seed_sc = {}, set(), {}   # for partially-completed Remaining half-days
    for hd, slot, table, kind, cat, team in done:
        if kind in ("S", "SB"):
            scoring_done[cat] = scoring_done.get(cat, 0) + 1
            scored_by.setdefault(cat, set()).update(team)
        elif kind == "M":
            medal_done.add(cat)
        if hd in win_hds:                            # done inside a half-day we're still filling
            pinned_idx.setdefault(hd, {})[table] = team   # reuse this team at this table
            occupied.add((hd, slot, table))
            if kind in ("S", "SB"):
                seed_sc.setdefault(cat, set()).add((hd, slot))

    remaining = {}
    for cat in CATEGORIES:
        full = category_rounds(cat)
        sc = [it for it in full if it[0] in ("S", "SB")]
        med = [it for it in full if it[0] == "M"]
        rem_sc = max(0, len(sc) - scoring_done.get(cat, 0))
        rounds = sc[len(sc) - rem_sc:] if rem_sc else []
        if med and cat not in medal_done:
            rounds += med
        if rounds:
            remaining[cat] = rounds
    if not remaining:
        print("Nothing left to schedule — every category is complete.")
        return 0

    HALFDAY_SLOTS = dict(windows)
    ROSTER = {hd: {j for j in JUDGES if hd in AVAIL[j]} for hd in HALFDAY_SLOTS}
    # pinned team list per partial half-day: revealed teams at their table index, None elsewhere
    pinned_teams = {hd: [idx_team.get(i) for i in range(max(idx_team) + 1)]
                    for hd, idx_team in pinned_idx.items()}
    out = schedule_all(rng, cats=list(remaining), rounds_fn=lambda c: remaining[c],
                       scored_by=scored_by, pinned_teams=pinned_teams, occupied=occupied, seed_sc=seed_sc)
    if out is None:
        print("!! could not schedule the remaining rounds (free up a slot in Remaining, "
              "raise MAX_TABLES, or try another seed)")
        return 1
    violations = validate(out)
    if violations:
        print("!! VALIDATION FAILED:")
        for v in sorted(set(violations)):
            print("   -", v)
        return 1

    # unified view: re-planned rounds + the frozen done rounds (marked), past days included
    display = {hd: list(rows) for hd, rows in out.items()}
    done_cells = set()
    for hd, slot, table, kind, cat, team in done:
        display.setdefault(hd, []).append((slot, table, kind, cat, team))
        done_cells.add((hd, slot, table))
    print("=== schedule  ('(done)' = already completed and frozen; the rest is re-planned) ===")
    print_grid(display, done_cells)
    print_reports(display)
    return 0


def run_normal(rng, emit, done_through):
    if not check_bos():
        return 1
    out = schedule_all(rng)
    if out is None:
        print("!! could not schedule (try another seed, add a slot to a tight half-day, "
              "raise MAX_TABLES, or relax BOTTLE_SPAN)")
        return 1
    violations = validate(out)
    if violations:
        print("!! VALIDATION FAILED:")
        for v in sorted(set(violations)):
            print("   -", v)
        return 1
    print_grid(out)
    print_reports(out)
    if emit:
        emit_plan(out, done_through)
    return 0


def main():
    args = sys.argv[1:]
    seed, emit, done_through, resume_path, positional = 1, False, None, None, []
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--resume":
            resume_path = args[i + 1]; i += 2
        elif a == "--plan":
            emit = True; i += 1
        elif a == "--done-through":
            done_through = args[i + 1]; i += 2
        else:
            positional.append(a); i += 1
    if positional:
        seed = int(positional[0])
    rng = random.Random(seed)
    if resume_path:
        return run_resume(rng, resume_path)
    return run_normal(rng, emit, done_through)


if __name__ == "__main__":
    sys.exit(main())
