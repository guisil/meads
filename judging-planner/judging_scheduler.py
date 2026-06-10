#!/usr/bin/env python3
"""Judging-round scheduler for the MEADS competition.

Reads the competition setup (tables, categories+entry counts, judges with level /
COI / availability / languages) and emits a two-day schedule of scoring + medal
rounds + BOS, honouring:

  * flight sizing (5-8 samples; >8 splits into parallel flights)
  * per-judge COI (a judge never judges a category they're conflicted on)
  * per-judge availability (half-day windows)
  * one comparative medal round per category, AFTER its scoring, SAME day
  * medal-round judges disjoint from that category's scoring judges
  * a team stays put for a whole half-day (no mid-session moves)
  * teams of 2 by default; Tiago in a team of 3 (with an experienced backup) so
    he can step out without sinking the table
  * multinational teams (never all-Polish; never all-same-nationality)
  * BOS on Friday afternoon with pinned judges; everything else done before then

Config (slot counts, day split, BOS judges) is at the top — tweak and re-run.
The schedule is found by randomized backtracking; run again for a different valid
layout.
"""
import math
import os
import random
import re
import sys
import unicodedata
from itertools import combinations

INPUT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "rounds_input.txt")

# ----------------------------------------------------------------------------
# CONFIG — the only things not taken from rounds_input.txt. Tweak + re-run.
# Each category is completed (scoring + medal) inside ONE half-day so no bottle
# is left open overnight. Front-loaded so Friday morning finishes and the
# afternoon is free for BOS only.
# ----------------------------------------------------------------------------
# Categories are auto-assigned to days (balanced by capacity) and the solver picks
# the slots — you don't pin anything by hand. PINS is an optional override: map a
# category code to a half-day to force its earliest slot there (e.g. {"AMA M2": "THU-AM"}).
# Leave empty to let the solver place everything.
PINS = {}
HALFDAY_SLOTS = {"THU-AM": 2, "THU-PM": 3, "FRI-AM": 3, "FRI-PM": 2}
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
DAY_FILL_TARGET = 1

MAX_TABLES = 5
# Judges PREFERRED in a 3-judge table (e.g. so they can step out without sinking the
# table). They get first claim on the trio slots the head-count naturally creates; a pair
# is still fine when there's no free trio slot (and there are usually only 1-2 trio slots
# in the whole event, so not everyone listed will get one). Add names as needed.
TRIO_JUDGES = {"Tiago"}

# Judge groups (pairs or trios) you'd PREFER to see judging together on a team at some
# point — a soft nudge, not a hard rule (the schedule won't fail to honour it). Each
# entry is a set of judge keys; they can only end up together in a half-day where all are
# available. The run reports which were achieved.
# Example: TOGETHER = [{"Aleli", "Filip"}, {"Marc", "Ivonne", "Gonçalo"}]
TOGETHER = []

BOS = {
    "BOS Professional": ["Marek L", "Marek P", "Ivonne"],
    "BOS Amateur":      ["Filip", "Marc", "Carlos"],  # Mike withdrew; Filip is the most experienced replacement with no AMA conflict
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
            nat = LANG_TO_NAT.get(langs.split(",")[0].strip().lower(), "??")
            raw_judges.append((name.strip(), level, coi, avail, nat))

    first_counts = {}
    for nm, *_ in raw_judges:
        first_counts[nm.split()[0]] = first_counts.get(nm.split()[0], 0) + 1
    judges = {}
    for full, level, coi, avail, nat in raw_judges:
        parts = full.split()
        key = parts[0] if first_counts[parts[0]] == 1 else f"{parts[0]} {_ascii_initial(parts[-1])}"
        judges[key] = (level, nat, coi, avail)
    return judges, cats, pend, sparkling, scorebased


JUDGES, CATEGORIES, PENDING, SPARKLING, SCORE_BASED = parse_input(INPUT_FILE)
NAT = {n: v[1] for n, v in JUDGES.items()}
LEVEL = {n: v[0] for n, v in JUDGES.items()}
COI = {n: v[2] for n, v in JUDGES.items()}
AVAIL = {n: v[3] for n, v in JUDGES.items()}
ROSTER = {hd: {j for j in JUDGES if hd in AVAIL[j]} for hd in HALFDAY_SLOTS}


def flights(entries):
    return max(1, math.ceil(entries / 8))


def team_pref(members):
    """Soft preference: multinational (not all the same nationality, not all-Polish).
    Teams that fail this are still allowed (see team_ok) — we just try harder to
    avoid them first."""
    nats = [NAT[m] for m in members]
    if len(set(nats)) == 1:
        return False
    if all(n == "PL" for n in nats):
        return False
    return True


def team_ok(members):
    """Hard constraint on team composition. Nationality is now only a soft
    preference (team_pref), so any composition is permitted."""
    return True


def team_can_judge(members, cat):
    return all(cat not in COI[m] for m in members)


def wants_together(team):
    """True if this team is, or contains, a requested TOGETHER co-judging group."""
    return any(set(g) <= team for g in TOGETHER)


def form_teams(roster, n_tables, rng):
    """Partition the whole roster into teams of 2-3 with **no idle judge**, maximising
    the number of tables (<= n_tables). Trios are used only when the head-count forces
    them (odd roster, or more judges than tables); the available trio slots go first to
    requested TOGETHER trios, then to judges in TRIO_JUDGES (each anchoring a trio with a
    preferably experienced >=L2 backup). Teams are preferred (softly) to be multinational
    and to keep TOGETHER groups intact. Returns list[set] or None if everyone can't be
    seated within n_tables."""
    pool = set(roster)
    n = len(pool)
    if n < 2:
        return None
    k = min(n_tables, n // 2)            # tables to open (as many as possible)
    n_trios = n - 2 * k                  # trios required so every judge is placed
    if not 0 <= n_trios <= k:
        return None                      # too many judges for n_tables teams of 2-3

    teams = []

    def pick_trio(anchor=None):
        members = [j for j in pool if j != anchor]
        rng.shuffle(members)
        combos = [set(c) for c in combinations(members, 2 if anchor else 3)]
        if not combos:
            return None
        def key(extra):                  # prefer co-judging group, then multinational, then experienced backup
            tm = ({anchor} | extra) if anchor else extra
            backup = extra if anchor else tm
            return (wants_together(tm), team_pref(tm), max(LEVEL[j] for j in backup) >= 2)
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
        a = rest.pop()
        partner = max(rest, key=lambda b: (wants_together({a, b}), team_pref({a, b})))
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


def assign_categories(blocks_list, rng):
    """Auto-assign every category to a block, balancing rounds against each block's
    cell capacity (slots x tables). PINS force a category into the block holding its
    pinned half-day. Bigger categories placed first for tighter packing."""
    caps = [sum(HALFDAY_SLOTS[hd] * min(MAX_TABLES, len(ROSTER[hd]) // 2) for hd in b)
            for b in blocks_list]
    targets = [c * DAY_FILL_TARGET for c in caps]
    load = [0] * len(blocks_list)
    assigned = [[] for _ in blocks_list]
    cats = list(CATEGORIES)
    rng.shuffle(cats)
    cats.sort(key=lambda c: len(category_rounds(c)), reverse=True)  # big first, shuffle breaks ties
    for c in cats:
        r = len(category_rounds(c))
        if c in PINS:
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


def schedule_block(block, cats, rng, tries=8000):
    """Schedule the given categories across the block's half-days (teams are re-seated
    each half-day). Returns {hd: [(local_slot, table, kind, cat, team)]} or None."""
    slots = [(hd, ls) for hd in block for ls in range(HALFDAY_SLOTS[hd])]  # global slot order
    home_pos = {c: (block.index(PINS[c]) if c in PINS else 0) for c in cats}
    blockpos = {hd: i for i, hd in enumerate(block)}
    predead = [(hd, ls) in PRE_DEADLINE_SLOTS for hd, ls in slots]
    insts = [(c, kind, late) for c in cats for kind, late in category_rounds(c)]
    if len(insts) > len(slots) * MAX_TABLES:
        return None  # assignment overfills this block

    for _ in range(tries):
        teams = {hd: form_teams(ROSTER[hd], MAX_TABLES, rng) for hd in block}
        if any(t is None for t in teams.values()):
            continue
        placed = _place_block(insts, teams, slots, home_pos, blockpos, predead, rng)
        if placed is not None:
            out = {}
            for si, t, kind, c in placed:
                hd, ls = slots[si]
                out.setdefault(hd, []).append((ls, t, kind, c, teams[hd][t]))
            return out
    return None


def _place_block(insts, teams, slots, home_pos, blockpos, predead, rng):
    """Backtracking placement of round-instances onto (global_slot, table). Scoring
    first, medals last (tightest first). Honours: home (no round before its half-day),
    the arrival deadline (late rounds skip pre-deadline slots), one medal strictly
    after all of its scoring with a judge-disjoint panel, and sparkling categories in
    consecutive same-half-day slots. Returns assignment list or None."""
    hd_of = [hd for hd, _ in slots]
    nslot = len(slots)

    def can(c, si):  # slot is usable at-or-after the category's home half-day
        return blockpos[hd_of[si]] >= home_pos[c]

    # feasibility precheck: every category needs at least one team that can judge it
    for c in home_pos:
        if not any(blockpos[hd] >= home_pos[c] and team_can_judge(tm, c)
                   for hd in teams for tm in teams[hd]):
            return None

    scoring = [x for x in insts if x[1] in ("S", "SB")]
    medals = [x for x in insts if x[1] == "M"]
    rng.shuffle(scoring)
    medals.sort(key=lambda x: sum(1 for hd in teams for tm in teams[hd]
                                  if team_can_judge(tm, x[0])))  # tightest first
    order = scoring + medals

    grid = [[False] * MAX_TABLES for _ in range(nslot)]
    sc_slots = {c: [] for c in home_pos}
    sc_judges = {c: set() for c in home_pos}
    assignment = []
    nodes = [0]

    def candidates(c, kind, late, spark):
        for si in range(nslot):
            if not can(c, si) or (late and predead[si]):
                continue
            if kind == "M":
                if not sc_slots[c] or si <= max(sc_slots[c]):
                    continue
                if spark:
                    last = max(sc_slots[c])
                    if hd_of[si] != hd_of[last] or slots[si][1] != slots[last][1] + 1:
                        continue
            ts = teams[hd_of[si]]
            for t in range(len(ts)):
                if grid[si][t] or not team_can_judge(ts[t], c):
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


def main():
    seed = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    rng = random.Random(seed)

    bos_bad = []
    for name, panel in BOS.items():
        for j in panel:
            if j not in JUDGES:
                bos_bad.append(f"{name}: {j} is not a known judge")
            elif "FRI-PM" not in AVAIL[j]:
                bos_bad.append(f"{name}: {j} not available Friday afternoon")
    if bos_bad:
        print("!! BOS PANEL INVALID:")
        for b in sorted(set(bos_bad)):
            print("   -", b)
        return 1

    blocks_list = blocks()
    out = None
    for _ in range(60):  # retry the auto-assignment until every block schedules
        assigned = assign_categories(blocks_list, rng)
        attempt = {}
        for b, cats in zip(blocks_list, assigned):
            res = schedule_block(b, cats, rng, tries=3000)
            if res is None:
                break
            for hd, rows in res.items():
                attempt.setdefault(hd, []).extend(rows)
        else:
            out = attempt
            break
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

    tag_map = {"S": "scoring", "M": "MEDAL  ", "SB": "score  "}
    label = {"THU-AM": "THURSDAY morning", "THU-PM": "THURSDAY afternoon",
             "FRI-AM": "FRIDAY morning", "FRI-PM": "FRIDAY afternoon (pre-BOS rounds)"}
    for hd in HALFDAY_ORDER:
        if hd not in out:
            continue
        pre = sorted(ls for h, ls in PRE_DEADLINE_SLOTS if h == hd)
        note = f"  [slot {','.join(str(l + 1) for l in pre)}: fully-received rounds only]" if pre else ""
        print(f"\n=== {label[hd]}{note} ===")
        for slot, table, kind, cat, team in sorted(out[hd], key=lambda r: (r[0], r[1])):
            print(f"  Slot {slot+1}  Mesa {table+1}  {tag_map[kind]}  {cat:<11}  {', '.join(sorted(team))}")
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
                print(f"  together: {', '.join(sorted(gset))}  ({', '.join(label[h] for h in where)})")
            else:
                elig = [hd for hd in HALFDAY_ORDER if hd in HALFDAY_SLOTS and gset <= ROSTER[hd]]
                why = "no shared available half-day" if not elig else "not in this layout — try another seed"
                print(f"  apart:    {', '.join(sorted(gset))}  ({why})")

    if TRIO_JUDGES:
        trios = {(hd, frozenset(team)) for hd, rows in out.items() for *_, team in rows if len(team) == 3}
        print("\n=== preferred trios ===")
        for tj in sorted(TRIO_JUDGES):
            where = [hd for hd in HALFDAY_ORDER if any(h == hd and tj in t for h, t in trios)]
            if where:
                print(f"  {tj}: trio in {', '.join(label[h] for h in where)}")
            else:
                print(f"  {tj}: paired throughout (no free trio slot)")

    pend = {c: p for c, p in PENDING.items() if p}
    if pend:
        print("\n=== pending entries (still to be received; late flights deferred past Thu AM) ===")
        for c in sorted(pend):
            print(f"  {c}: {pend[c]} of {CATEGORIES[c]} pending")
    return 0


if __name__ == "__main__":
    sys.exit(main())
