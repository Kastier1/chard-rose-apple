#!/usr/bin/env python3
"""Builds src/main/resources/com/combatgearorganizer/slayer/slayer_locations.json.

Input: slayer_areas.json from Slayer Sensei (https://github.com/rRexhepi/slayer-sensei),
which was written from OSRS Wiki pages (CC BY-NC-SA 3.0) and RuneLite world map data (BSD-2).
See NOTICE.md.

Also reads slayer_tasks.json and slayer_tasks_supplement.json from the same folder, when present, for each
task's weakness, attack style, prayer, required items and notable drops (same sources and licence).

Monster variants (level, hitpoints, slayer XP, attack styles, weakness, which places they spawn at) come from
tools/data/wiki_monsters.json, made by tools/fetch_wiki_monsters.py from the OSRS Wiki (CC BY-NC-SA 3.0).

Usage: python3 tools/build_slayer_locations.py path/to/slayer_areas.json
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/com/combatgearorganizer/slayer/slayer_locations.json"

# Wilderness bounds: x, y, width, height. The first two are RuneLite NpcAggroAreaPlugin's;
# the third covers the Wilderness Slayer Cave, which sits east of the second box.
WILDERNESS = [(2944, 3523, 448, 448), (2944, 9918, 320, 442), (3264, 10030, 192, 200)]


def in_wilderness(p):
    return any(x <= p[0] < x + w and y <= p[1] < y + h for x, y, w, h in WILDERNESS)


def access_note(access):
    if not access:
        return None
    notes = [a.get("note") for a in (access if isinstance(access, list) else [access]) if a.get("note")]
    return " ".join(notes) or None


def requirements(*accesses):
    """Merges the source's access blocks into one {questsFinished, questsStarted, skills} dict (all required)."""
    finished, started, skills = [], [], {}
    for access in accesses:
        for a in (access if isinstance(access, list) else [access]) if access else []:
            finished += [q for q in a.get("questsFinished", []) if q not in finished]
            started += [q for q in a.get("questsStarted", []) if q not in started]
            for skill, level in a.get("skills", {}).items():
                skills[skill] = max(level, skills.get(skill, 0))
    out = {k: v for k, v in (("questsFinished", finished), ("questsStarted", started), ("skills", skills)) if v}
    return out or None


def place(label, key, points, entrance, extra, entrance_routable=True):
    # Some entrances can't be pathed to by Shortest Path; leave those without a point so Go is greyed out
    usable_entrance = entrance if entrance and entrance_routable is not False else None
    pts = points or ([usable_entrance] if usable_entrance else [])
    out = {
        "key": key,
        "label": label,
        "points": [p for p in pts if not in_wilderness(p)],
        "wildernessPoints": [p for p in pts if in_wilderness(p)],
    }
    out.update({k: v for k, v in extra.items() if v is not None})
    return out


WIKI = Path(__file__).resolve().parent / "data" / "wiki_monsters.json"
# Task names whose wiki slayer category is spelled differently
CATEGORY_ALIASES = {
    "aquanites": "aquanite",
    "aviansies": "aviansie",
    "custodian stalkers": "custodian stalker",
    "fleshcrawlers": "flesh crawlers",
}
# Not killed on a normal task: Arceuus reanimations, Leagues echoes, quest/minigame rematches
NOT_A_TASK_KILL = re.compile(r"^reanimated |\((echo|hard)\)", re.IGNORECASE)
# A variant belongs to a place when one of its spawns is this close (tiles) to one of the place's points
NEAR = 40
# Most dungeons sit 6400 tiles north of their entrance on the world map; an entrance-only place gets a looser match
UNDERGROUND_OFFSET = 6400
NEAR_UNDERGROUND = 100
# Big dungeons whose only known point is an entrance far from where the monsters are
NEAR_UNDERGROUND_WIDE = {"neypotzli": 250, "karuulm_slayer_dungeon": 250}
# Wide matches that land on a neighbouring dungeon (Frost Nagua live in the Ruins of Tapoyauik)
NOT_AT = {("Frost Nagua", "neypotzli")}
MELEE_WORDS = {"melee", "stab", "slash", "crush"}


def attack_types(styles):
    """Wiki attack styles ("Slash", "Magical ranged", "Dragonfire") -> melee / ranged / magic, in order."""
    out = []
    for raw in styles:
        s = raw.lower()
        kind = ("magic" if "magic" in s else "ranged" if "ranged" in s or "range" in s
                else "melee" if s in MELEE_WORDS or "melee" in s else None)
        if kind and kind not in out:
            out.append(kind)
    return out


def span(values):
    values = sorted(v for v in values if v is not None)
    return [values[0], values[-1]] if values else None


def place_points(data, locations):
    """location key -> every point known for that place: each task's spawn spots there, plus the entrance."""
    points = {}
    for key, loc in locations.items():
        pts = points.setdefault(key, [])
        if loc.get("entrance"):
            pts.append(loc["entrance"])
        for per_task in loc.get("tasks", []):
            pts += per_task.get("spots") or []
    for t in data["wildernessTaskLocations"]:
        for p in t.get("places", []):
            pts = points.setdefault(p["key"], [])
            pts += (p.get("spots") or []) + ([p["entrance"]] if p.get("entrance") else [])
    return points


def near(spawns, points, radius, dy=0):
    return any(sp[2] == pt[2] and abs(sp[0] - pt[0]) <= radius and abs(sp[1] - (pt[1] + dy)) <= radius
               for sp in spawns for pt in points)


def places_for(spawns, places, all_points):
    keys = []
    for pl in places:
        pts = all_points.get(pl["key"]) or (pl["points"] + pl["wildernessPoints"])
        if near(spawns, pts, NEAR):
            keys.append(pl["key"])
        elif all(pt[1] < UNDERGROUND_OFFSET for pt in pts) and near(
                spawns, pts, NEAR_UNDERGROUND_WIDE.get(pl["key"], NEAR_UNDERGROUND), UNDERGROUND_OFFSET):
            keys.append(pl["key"])
    return keys


RARITY = re.compile(r"~?\s*([\d,.]+)\s*/\s*([\d,.]+)")
# Shared tables (rare drop table, gems, herbs, seeds, runes) rather than this monster's own drops
GENERIC_DROP = re.compile(r"(seed|spore)$|^grimy |^uncut |^clue scroll|talisman$| rune$|^coins$"
                          r"| key$|^looting bag note$|^trouver parchment$|casket", re.IGNORECASE)
ALWAYS_SHOWN = re.compile(r"bones$|ashes$", re.IGNORECASE)
GENERIC_KEEP = {"Long bone", "Curved bone"}
RARE = 1 / 400
MAX_DROPS = 10


def chance(rarity):
    m = RARITY.match(rarity or "")
    if not m:
        return {"Rare": 1 / 1000, "Very rare": 1 / 5000}.get(rarity)
    lo, hi = (float(x.replace(",", "")) for x in m.groups())
    return lo / hi if hi else None


# Items worth carrying for a monster, from the OSRS Wiki's rules for each (see carry_for).
DEMONIC_ASHES = {"fiendish ashes", "vile ashes", "malicious ashes", "abyssal ashes", "infernal ashes"}
# The ash sanctifier works on every demon's ashes except Skotizo's
NO_ASH_SANCTIFIER = {"Skotizo"}
# The herb sack holds the fifteen grimy normal herbs only (not cleaned, noted or quest herbs)
SACK_HERBS = ("guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed", "toadflax", "irit leaf", "avantoe",
              "kwuarm", "huasca", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol")
# Things named like seeds that aren't farming seeds, so don't go in the seed box
NOT_SEEDS = {"crystal teleport seed", "enhanced crystal teleport seed"}
CARRY_BY_DROP = [
    # key, what the drop looks like, suggest when it drops at least once in this many kills
    ("herbSack", re.compile(r"^grimy (%s)$" % "|".join(SACK_HERBS), re.IGNORECASE), 30),
    # Seeds and spores (mushroom, seaweed); plural "seeds" are ammunition like adamant seeds
    ("seedBox", re.compile(r"\b(seed|spore)$", re.IGNORECASE), 30),
    ("gemBag", re.compile(r"^uncut (sapphire|emerald|ruby|diamond|dragonstone)$", re.IGNORECASE), 50),
    ("soulBearer", re.compile(r"^ensouled .* head$", re.IGNORECASE), 50),
]
CARRY_KEYS = {"bonecrusher", "ashSanctifier", "ectoplasmator"} | {k for k, _, _ in CARRY_BY_DROP}


def drop_rate(rows, pattern):
    """About how many matching drops per kill, for the best version in one page's drop rows.

    A page with several versions (e.g. the Catacombs one) lists each version's table one after another, so a new table
    starts when the first matching item comes round again; the tables are rated on their own, not added together.
    """
    best, rate, seen, first = 0.0, 0.0, set(), None
    for item, rarity, *_ in rows:
        if not pattern.search(item) or item.lower() in NOT_SEEDS:
            continue
        if item == first:
            best, rate, seen = max(best, rate), 0.0, set()
        first = first or item
        if (item, rarity) not in seen:
            seen.add((item, rarity))
            rate += 1.0 if rarity == "Always" else (chance(rarity) or 0)
    return max(best, rate)


def carry_for(pages, wiki):
    """Prayer and storage items worth an inventory slot for this monster.

    Bonecrusher: always drops bones (the data counts no tertiary bones: long and curved bones aren't "...bones").
    Ash sanctifier: always drops demonic ashes, except Skotizo. Ectoplasmator: the monster is spectral. Sacks and bags:
    the drop turns up often enough, counted per kill from the drop table. A monster with several pages or versions
    counts its best one, so versions aren't added together.
    """
    drops = [r for p in sorted(pages) for r in wiki.get("drops", {}).get(p, [])]
    always = {r[0].lower() for r in drops if r[1] == "Always"}
    carry = []
    if any(i.endswith("bones") for i in always):
        carry.append({"item": "bonecrusher"})
    if always & DEMONIC_ASHES and not set(pages) & NO_ASH_SANCTIFIER:
        carry.append({"item": "ashSanctifier"})
    if any("spectral" in wiki.get("attributes", {}).get(p, []) for p in pages):
        carry.append({"item": "ectoplasmator"})
    for key, pattern, every in CARRY_BY_DROP:
        best = max((drop_rate(wiki.get("drops", {}).get(p, []), pattern) for p in pages), default=0.0)
        if best >= 1 / every:
            carry.append({"item": key, "per": max(1, round(1 / best))})
    return carry


def generic_items(wiki):
    """Rare items that turn up on dozens of monsters: the shared drop tables."""
    count = Counter()
    for rows in wiki.get("drops", {}).values():
        count.update({r[0] for r in rows if (chance(r[1]) or 1) <= RARE})
    return {item for item, n in count.items() if n >= 50 and item not in GENERIC_KEEP}


def drops_for(pages, wiki, generic):
    """A monster's own rare drops and collection log slots (rarest first), and what it always drops."""
    clog = {n.lower() for n in wiki.get("collection_log_items", [])}
    best, always = {}, []
    for page in sorted(pages):
        for item, rarity, alt, value, low, high in wiki.get("drops", {}).get(page, []):
            if rarity == "Always":
                if ALWAYS_SHOWN.search(item) and item not in always:
                    always.append(item)
                continue
            in_log = item.lower() in clog
            # The shared rare drop table has log slots too (dragon spear), but they'd fill every monster's list
            if item in generic or (not in_log and GENERIC_DROP.search(item)):
                continue
            p = chance(rarity)
            p_alt = chance(alt)
            if p is None or (not in_log and min(p, p_alt or p) > RARE):
                continue
            drop = {"item": item, "rarity": rarity}
            # Stack size, for the icon's number: only when it can be more than one
            if isinstance(high, (int, float)) and high > 1:
                drop["quantity"] = [int(low or high), int(high)]
            if in_log:
                drop["clog"] = True
            if p_alt and p_alt > p:
                drop["onTask"] = alt
            key = max(p, p_alt or 0)
            if item not in best or key > best[item][0]:
                best[item] = (key, drop)
    rare = [d for _, d in sorted(best.values(), key=lambda kd: (kd[0], kd[1]["item"]))][:MAX_DROPS]
    return rare, always


def variants_for(task_name, places, wiki, all_points):
    """Each distinct monster on the task, with the keys of the places it spawns near."""
    if not wiki:
        return []
    category = CATEGORY_ALIASES.get(task_name.lower(), task_name.lower())
    superiors = set(wiki["superiors"])
    generic = generic_items(wiki)
    superior_of = wiki.get("superior_of", {})
    league_only = set(wiki.get("superiors_league_only", []))
    by_name = {}
    for m in wiki["monsters"]:
        if category not in (c.lower() for c in m.get("slayer_category", [])):
            continue
        if NOT_A_TASK_KILL.search(m["name"]) or not m.get("combat_level") or m["page_name"] in league_only:
            continue
        by_name.setdefault(m["name"], []).append(m)

    out = []
    for name, versions in by_name.items():
        pages = {v["page_name"] for v in versions}
        superior = bool(pages & superiors) or name in superiors
        spawns = [pt for p in sorted(pages) for pt in wiki["spawns"].get(p, [])]
        if not spawns and not superior:
            continue  # minigame, quest or instance only
        weak = Counter((v["elemental_weakness"], v.get("elemental_weakness_percent"))
                       for v in versions if v.get("elemental_weakness"))
        found = [k for k in places_for(spawns, places, all_points) if (name, k) not in NOT_AT]
        boss = any(c.lower() == "bosses" for v in versions for c in v.get("slayer_category", []))
        variant = {
            "name": name,
            "level": span(v.get("combat_level") for v in versions),
            "hitpoints": span(v.get("hitpoints") for v in versions),
            "xp": span(v.get("slayer_experience") for v in versions),
            "attackTypes": attack_types(s for v in versions for s in v.get("attack_style", [])),
            "superior": superior,
            "boss": boss,
            "places": sorted(set(found)),
        }
        base = next((superior_of[p] for p in sorted(pages) if p in superior_of), None)
        if superior and base:
            variant["superiorOf"] = base
        carry = carry_for(pages, wiki)
        if carry:
            variant["carry"] = carry
        rare, always = drops_for(pages, wiki, generic)
        if rare:
            variant["drops"] = rare
        if always:
            variant["alwaysDrops"] = always
        if weak:
            (element, percent), _ = weak.most_common(1)[0]
            # The wiki says "None" for monsters with no elemental weakness; only real elements are shown
            if element.lower() in ELEMENTS:
                variant["weakness"] = {"element": element.lower(), "percent": percent}
        out.append(variant)
    out.sort(key=lambda v: (v["superior"], v["boss"], v["level"][0] if v["level"] else 0, v["name"]))
    return out


# Elements the panel knows (TaskTags.Element)
ELEMENTS = {"air", "water", "earth", "fire"}
# Attack styles the panel knows (TaskTags.Style)
ATTACK_TYPES = {"melee", "ranged", "magic"}


# Task names in slayer_tasks*.json that differ from slayer_areas.json
INFO_ALIASES = {
    "cave kraken": "cave kraken (kraken)",
    "hydras": "hydras (regular hydra + alchemical hydra)",
    "kalphite": "kalphites",
    "lizardmen": "lizardmen (incl. brutes and shamans)",
    "mutated zygomites": "zygomites",
    "vampyres": "vampyres / vyrewatch",
}

INFO_FIELDS = ["weakness", "elementalWeakness", "style", "monsterAttackStyle", "protectionPrayer", "requiredItems",
               "notableDrops"]


# Prayer advice that tells the player to switch prayers during a fight (boss forms, phases, specials). The plugin
# hub's guidelines rule out prayer-switching advice and prayer recommendations for bosses, so these are reworded to
# the plain prayer for the normal monsters, or dropped. None removes the advice.
PRAYER_REWRITES = {
    "aquanites": "Protect from Magic",
    "custodian stalkers": "Protect from Melee",
    "hydras": None,
    "kalphite": "Protect from Melee for the standard kalphites.",
}
PRAYER_SWITCHING = re.compile(r"\b(switch|swap|flick|alternat|phase|rotation|per form)", re.IGNORECASE)


# Gear one place needs, said on that place's card rather than for the whole task
PLACE_NOTES = {
    "karuulm_slayer_dungeon": "Wear boots of stone, boots of brimstone or granite boots for the hot floor "
                              "(not needed with the elite Kourend & Kebos Diary).",
}
# The same advice in a task's "Bring" text, removed there so it doesn't apply to every place
PLACE_ONLY_ITEMS = re.compile(r"(?:Mandatory:\s*)?[^.;]*\b(?:boots of stone|granite boots|not covered by a slayer helm\w*|does not cover the floor damage)\b[^.;]*(?:\([^)]*\))?[^.;]*[.;]\s*",
                              re.IGNORECASE)


def load_info(folder):
    info = {}
    for name in ("slayer_tasks.json", "slayer_tasks_supplement.json"):
        path = folder / name
        if path.exists():
            for t in json.loads(path.read_text()):
                info.setdefault(t["task"].lower(), t)
    return info


def task_info(info, name):
    t = info.get(INFO_ALIASES.get(name.lower(), name.lower()))
    if not t:
        return None
    out = {k: t[k].strip() for k in INFO_FIELDS if isinstance(t.get(k), str) and t[k].strip()}
    if name.lower() in PRAYER_REWRITES:
        rewrite = PRAYER_REWRITES[name.lower()]
        if rewrite is None:
            out.pop("protectionPrayer", None)
        else:
            out["protectionPrayer"] = rewrite
    if "requiredItems" in out:
        bring = PLACE_ONLY_ITEMS.sub("", out["requiredItems"]).strip()
        if bring:
            out["requiredItems"] = bring[0].upper() + bring[1:]
        else:
            del out["requiredItems"]
    return out or None


def validate(tasks):
    """Fail the build on values the plugin can't show, instead of breaking the panel in game."""
    problems = []
    for t in tasks:
        for v in t.get("variants", []):
            w = v.get("weakness")
            if w and w["element"] not in ELEMENTS:
                problems.append(f'{t["name"]} / {v["name"]}: weakness element {w["element"]!r}')
            for c in v.get("carry", []):
                if c["item"] not in CARRY_KEYS:
                    problems.append(f'{t["name"]} / {v["name"]}: carry item {c["item"]!r}')
            for a in v.get("attackTypes", []):
                if a not in ATTACK_TYPES:
                    problems.append(f'{t["name"]} / {v["name"]}: attack type {a!r}')
        prayer = (t.get("info") or {}).get("protectionPrayer")
        if prayer and PRAYER_SWITCHING.search(prayer):
            problems.append(f'{t["name"]}: prayer-switching advice {prayer!r}; add it to PRAYER_REWRITES')
    if problems:
        raise SystemExit("Invalid data:\n  " + "\n  ".join(problems))


def main(src):
    data = json.loads(Path(src).read_text())
    info = load_info(Path(src).parent)
    wiki = json.loads(WIKI.read_text()) if WIKI.exists() else None
    if not wiki:
        print(f"No {WIKI}; run tools/fetch_wiki_monsters.py for monster variants")
    locations = {l["key"]: l for l in data["locations"]}
    wild = {t["taskId"]: t for t in data["wildernessTaskLocations"]}
    all_points = place_points(data, locations)

    normal = {t["taskId"]: t for t in data["taskLocations"]}

    tasks = []
    # Some of Krystilia's tasks only appear in her Wilderness list, so walk the union of both
    for tid in sorted(set(normal) | set(wild)):
        # Wilderness-only names come title-cased ("Black Knights"); match the normal list's "Black knights"
        t = normal.get(tid) or {"taskId": tid, "task": wild[tid]["task"].capitalize(), "locations": []}
        entries = []
        for key in t["locations"]:
            loc = locations[key]
            per_task = next((x for x in loc.get("tasks", []) if x["taskId"] == tid), {})
            notes = [n for n in (loc.get("note"), access_note(loc.get("access")), per_task.get("note"),
                                 access_note(per_task.get("access")), PLACE_NOTES.get(key)) if n]
            entries.append(place(loc["label"], key, per_task.get("spots"), loc.get("entrance"), {
                "areaId": loc.get("areaId"),
                "combat": per_task.get("combat") or loc.get("combat"),
                "cannon": per_task.get("cannon") or loc.get("cannon"),
                "note": " ".join(notes) or None,
                "requirements": requirements(loc.get("access"), per_task.get("access")),
            }, loc.get("entranceRoutable")))
        wild_entries = []
        for p in wild.get(tid, {}).get("places", []):
            # Wilderness places often share a key with a normal place; borrow its facts and access
            loc = locations.get(p["key"], {})
            per_task = next((x for x in loc.get("tasks", []) if x["taskId"] == tid), {})
            notes = [n for n in (loc.get("note"), access_note(loc.get("access")), per_task.get("note"),
                                 PLACE_NOTES.get(p["key"])) if n]
            wild_entries.append(place(p["label"], p["key"], p.get("spots"), p.get("entrance"), {
                "combat": p.get("combat") or per_task.get("combat") or loc.get("combat"),
                "cannon": p.get("cannon") or per_task.get("cannon") or loc.get("cannon"),
                "note": " ".join(notes) or None,
                "requirements": requirements(loc.get("access"), per_task.get("access")),
            }, p.get("entranceRoutable", loc.get("entranceRoutable"))))
        tasks.append({
            "taskId": tid,
            "name": t["task"],
            "recommended": t.get("recommended"),
            "locations": entries,
            "wildernessLocations": wild_entries,
            "info": task_info(info, t["task"]),
            "variants": variants_for(t["task"], entries + wild_entries, wiki, all_points),
        })

    tasks.sort(key=lambda x: x["name"].lower())
    out = {
        "about": "Generated by tools/build_slayer_locations.py from Slayer Sensei's slayer_areas.json "
                 "(OSRS Wiki, CC BY-NC-SA 3.0; RuneLite world map data, BSD-2) and OSRS Wiki monster data "
                 "(CC BY-NC-SA 3.0). Points are [x, y, plane].",
        "tasks": tasks,
    }
    validate(tasks)
    OUT.write_text(json.dumps(out, separators=(",", ":")))
    every = [l for t in tasks for l in t["locations"] + t["wildernessLocations"]]
    routable = sum(1 for l in every if l["points"] or l["wildernessPoints"])
    total = len(every)
    with_info = sum(1 for t in tasks if t["info"])
    with_variants = sum(1 for t in tasks if any(not v["superior"] for v in t["variants"]))
    print(f"{with_variants} tasks with variants, {sum(len(t['variants']) for t in tasks)} variants in all")
    print(f"{len(tasks)} tasks ({with_info} with gear info), {total} locations ({routable} with map points) -> {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
