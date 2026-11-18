#!/usr/bin/env python3
"""Builds src/main/resources/com/combatgearorganizer/slayer/slayer_locations.json.

Input: slayer_areas.json from Slayer Sensei (https://github.com/rRexhepi/slayer-sensei),
which was written from OSRS Wiki pages (CC BY-NC-SA 3.0) and RuneLite world map data (BSD-2).
See NOTICE.md.

Also reads slayer_tasks.json and slayer_tasks_supplement.json from the same folder, when present, for each
task's weakness, attack style, prayer, required items and notable drops (same sources and licence).

Monster variants (level, hitpoints, slayer XP, attack styles, weakness, which places they spawn at, and the defence
stats and attributes the gear-up DPS maths needs) come from
tools/data/wiki_monsters.json, made by tools/fetch_wiki_monsters.py from the OSRS Wiki (CC BY-NC-SA 3.0).
A monster whose spawns are near none of a task's places gets a place of its own made from those spawns
(source "wiki"), named from the wiki's location for them.

Bosses that count for a task (BOSSES, checked against the OSRS Wiki; see the citations there) are listed as monsters of
that task, after the regular ones, each with its lair as its only place (source "boss", BOSS_LAIRS): the entrance the
player walks to, and the quests and skill levels the wiki says it needs. God Wars Dungeon bodyguards are left out.

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
    """Merges the source's access blocks into one {questsFinished, questsStarted, skills, diaries} dict (all required)."""
    finished, started, skills, diaries = [], [], {}, []
    for access in accesses:
        for a in (access if isinstance(access, list) else [access]) if access else []:
            finished += [q for q in a.get("questsFinished", []) if q not in finished]
            started += [q for q in a.get("questsStarted", []) if q not in started]
            diaries += [d for d in a.get("diaries", []) if d not in diaries]
            for skill, level in a.get("skills", {}).items():
                skills[skill] = max(level, skills.get(skill, 0))
    out = {k: v for k, v in (("questsFinished", finished), ("questsStarted", started), ("skills", skills),
                             ("diaries", diaries)) if v}
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
# Wiki pages of the same monster that aren't fought on a task: the PvM Arena copies of bosses ("Kree'arra (PvM Arena)")
NOT_A_TASK_PAGE = re.compile(r"\(PvM Arena\)$", re.IGNORECASE)
# Versions only fought during a quest ("Vorkath": "Dragon Slayer II" beside "Post-quest"; "Amoxliatl": "Quest"), which
# would widen the level, hitpoints and XP ranges with a fight the player can't have on a task
QUEST_VERSIONS = {"quest", "dragon slayer ii"}
# A variant belongs to a place when one of its spawns is this close (tiles) to one of the place's points
NEAR = 40
# Most dungeons sit 6400 tiles north of their entrance on the world map; an entrance-only place gets a looser match
UNDERGROUND_OFFSET = 6400
NEAR_UNDERGROUND = 100
# Big dungeons whose only known point is an entrance far from where the monsters are
NEAR_UNDERGROUND_WIDE = {"neypotzli": 250, "karuulm_slayer_dungeon": 250}
# Wide matches that land on a neighbouring dungeon (Frost Nagua live in the Ruins of Tapoyauik)
NOT_AT = {("Frost Nagua", "neypotzli"),
          # The KBD lair's map icon sits above the Lava Maze Dungeon, whose black dragons are listed there already
          ("Black dragon", "king_black_dragon_lair")}
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
        if pl.get("source") == BOSS_SOURCE:
            continue  # a boss's lair is for the boss alone
        pts = all_points.get(pl["key"]) or (pl["points"] + pl["wildernessPoints"])
        if near(spawns, pts, NEAR):
            keys.append(pl["key"])
        # A place made from wiki spawns is where the monsters are, not an entrance, so no looser underground match
        elif pl.get("source") != WIKI_SOURCE and all(pt[1] < UNDERGROUND_OFFSET for pt in pts) and near(
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
# Breath attacks that a shield or potion protects against (wiki Dragonfire page). Dragonfire: an anti-dragon shield
# (or dragonfire shield, ward, ancient wyvern shield) and/or an antifire potion. Wyverns' icy breath: only an elemental,
# mind, dragonfire or ancient wyvern shield; an anti-dragon shield and antifire potions do nothing.
BREATH_CARRY = [("Dragonfire", "dragonfire"), ("Icy breath", "wyvernShield")]
# Drakes' volcanic breath counts as dragonfire, but they don't need protection: a step aside dodges it (wiki Drake page)
NO_BREATH_PROTECTION = {"Drake"}
CARRY_KEYS = ({"bonecrusher", "ashSanctifier", "ectoplasmator"} | {k for _, k in BREATH_CARRY}
              | {k for k, _, _ in CARRY_BY_DROP})


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
    """Protection, prayer and storage items worth an inventory slot for this monster.

    Dragonfire protection: a wiki attack style of any version is "Dragonfire" (or "Icy breath" for wyverns), except
    for drakes.
    Bonecrusher: always drops bones (the data counts no tertiary bones: long and curved bones aren't "...bones").
    Ash sanctifier: always drops demonic ashes, except Skotizo. Ectoplasmator: the monster is spectral. Sacks and bags:
    the drop turns up often enough, counted per kill from the drop table. A monster with several pages or versions
    counts its best one, so versions aren't added together.
    """
    drops = [r for p in sorted(pages) for r in wiki.get("drops", {}).get(p, [])]
    always = {r[0].lower() for r in drops if r[1] == "Always"}
    carry = []
    styles = {s for m in wiki.get("monsters", []) if m["page_name"] in pages for s in m.get("attack_style", [])}
    if not set(pages) & NO_BREATH_PROTECTION:
        carry += [{"item": key} for style, key in BREATH_CARRY if style in styles]
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


def in_game_name(item):
    return item[:-len(" (item)")] if item.endswith(" (item)") else item


# Drops that are a bag of other drops, each with its own wiki drop table. https://oldschool.runescape.wiki/w/Unsired:
# "The unsired is a drop from the Abyssal Sire ... it can be offered to the Font of Consumption ... for one of the
# following rewards" (the bludgeon pieces, abyssal dagger, whip, head, Jar of miasma, orphan). Each reward is listed on
# the dropping monster too, at the bag's rate times the reward's.
DROP_CONTENTS = {"Unsired"}


def rate_text(p):
    """1/206.5, written the way the wiki writes a rate (thousands with a comma, at most one decimal)."""
    n = round(1 / p, 1)
    return "1/" + (f"{n:,.0f}" if n == int(n) else f"{n:,.1f}")


def drop_rows(page, wiki):
    """A page's drop rows, with each DROP_CONTENTS bag followed by what comes out of it (its rate times the bag's)."""
    rows = []
    for row in wiki.get("drops", {}).get(page, []):
        rows.append(row)
        item, rarity = row[0], row[1]
        bag = chance(rarity)
        if item in DROP_CONTENTS and bag:
            for sub, sub_rarity, _alt, value, low, high in wiki.get("drops", {}).get(item, []):
                p = chance(sub_rarity)
                if p:
                    # The bag's own alternative rate is for players who already have its pet, not an on-task rate
                    rows.append([sub, rate_text(bag * p), "", value, low, high])
    return rows


def drops_for(pages, wiki, generic):
    """A monster's own rare drops and collection log slots (rarest first), and what it always drops."""
    clog = {n.lower() for n in wiki.get("collection_log_items", [])}
    best, always = {}, []
    for page in sorted(pages):
        for item, rarity, alt, value, low, high in drop_rows(page, wiki):
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
            # The wiki adds "(item)" to tell an item from a monster of the same name ("Crawling hand (item)"); the
            # game, and so the collection log, calls it "Crawling hand"
            drop = {"item": in_game_name(item), "rarity": rarity}
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


def task_monsters(task_name, wiki):
    """monster name -> its wiki versions that count for this task, in the wiki data's order.

    Leaves out the God Wars Dungeon bodyguards, and bosses that aren't in BOSSES for this task (the wiki's category for
    a boss can be wider than what counts); a boss in BOSSES for the task is found by name even outside the category.
    """
    category = CATEGORY_ALIASES.get(task_name.lower(), task_name.lower())
    league_only = set(wiki.get("superiors_league_only", []))
    bosses = bosses_of(task_name)
    by_name = {}
    for m in wiki["monsters"]:
        categories = [c.lower() for c in m.get("slayer_category", [])]
        if m["name"] not in bosses and (category not in categories or "bosses" in categories):
            continue
        if m["name"] in GWD_MINIONS | ONE_TIME_FIGHTS or NOT_A_TASK_KILL.search(m["name"]) or NOT_A_TASK_PAGE.search(m["page_name"]):
            continue
        if (m.get("version_anchor") or "").lower() in QUEST_VERSIONS:
            continue
        if not m.get("combat_level") or m["page_name"] in league_only:
            continue
        by_name.setdefault(m["name"], []).append(m)
    return by_name


def is_superior(name, pages, wiki):
    return bool(pages.intersection(wiki["superiors"])) or name in wiki["superiors"]


def is_boss(name, task_name):
    return name in bosses_of(task_name)


def spawns_of(pages, wiki):
    return [pt for p in sorted(pages) for pt in wiki["spawns"].get(p, [])]


def places_of(name, spawns, places, all_points):
    return [k for k in places_for(spawns, places, all_points) if (name, k) not in NOT_AT]


# Places made from a monster's own wiki spawn points, for monsters that live away from every listed place
WIKI_SOURCE = "wiki"
# A monster spread over more named spots than this gets one place for them all (Go picks the nearest)
WIKI_MAX_SPOTS = 3
WIKI_NOTE = "Spawn points from the OSRS Wiki."
# wiki_places' note (and label) for a several-places place's copy on Krystilia's list, which keeps only the
# Wilderness spots (not bundled)
WILD_NOTE = "_wildernessNote"
NO_ACCESS_NOTE = "The data has no access details for this place."
# Access for wiki locations no guide place is named like, checked on the wiki (keyed by name_keys' first form)
WIKI_ACCESS = {
    # "They can be found throughout the Ancient Guthixian Temple after completion of the quest."
    "ancientguthixiantemple": {"questsFinished": ["WHILE_GUTHIX_SLEEPS"]},
}


# God Wars Dungeon bodyguards: they only appear in their general's room and are killed as part of the boss fight, so
# they aren't listed as monsters for a task even where the wiki puts them in its category (Tstanon Karlak under greater
# demons). Each wiki page says "<name> is one of <general>'s bodyguards", e.g.
# https://oldschool.runescape.wiki/w/Tstanon_Karlak, https://oldschool.runescape.wiki/w/Wingman_Skree,
# https://oldschool.runescape.wiki/w/Sergeant_Strongstack, https://oldschool.runescape.wiki/w/Starlight. Nex's
# generals (https://oldschool.runescape.wiki/w/Nex) are left out the same way.
GWD_MINIONS = {
    "Tstanon Karlak", "Zakl'n Gritch", "Balfrug Kreeyath",  # K'ril Tsutsaroth
    "Wingman Skree", "Flockleader Geerin", "Flight Kilisa",  # Kree'arra
    "Sergeant Strongstack", "Sergeant Grimspike", "Sergeant Steelwill",  # General Graardor
    "Starlight", "Growler", "Bree",  # Commander Zilyana
    "Fumus", "Umbra", "Cruor", "Glacies",  # Nex
}
# Fights a player has once per account, so never a place to go on a task: Kolodion counts as a black demon, but
# https://oldschool.runescape.wiki/w/Slayer_task/Black_demons says "Kolodion can only be killed once during the Mage
# Arena miniquest". (Porazdir is left out of BOSSES for a different reason, given there.)
ONE_TIME_FIGHTS = {"Kolodion"}
# Regular monsters that live only in a boss's lair (wiki name -> BOSS_LAIRS id). The lair is their only place.
# https://oldschool.runescape.wiki/w/Slayer_task/Scorpions on Scorpia's offspring: "Only found in Scorpia's Lair in the
# Wilderness."
AT_LAIR = {"Scorpia's offspring": "scorpias_cave"}

# Where each boss that counts for a normal slayer task is fought. Every point is where the player walks in (the
# entrance object, or the tile Shortest Path uses for it), never a spot inside an instance. The plugin never sends
# "Go to nearest" to these places: a boss is a choice the player makes. Requirements use RuneLite Quest and Skill enum
# names, and "diaries" Achievement Diary tiers (SlayerHelper.DIARIES: REGION_TIER, read from the game's diary varbits);
# what they can't say (keys, kill count, fees) is in the note. "konar": the guide place whose
# Konar area the lair counts as (the wiki says the boss can be killed on her task there; each of these drops a brimstone
# key on her tasks). "krystilia": the lair is in the Wilderness and the boss counts on her task.
# Achievement Diary tiers a place can need; SlayerHelper.DIARIES reads each from its *_DIARY_<TIER>_COMPLETE varbit
DIARIES = {"WILDERNESS_EASY", "WILDERNESS_MEDIUM", "WILDERNESS_HARD", "WILDERNESS_ELITE"}
# Slayer levels on boss lairs are "boostable" on task: https://oldschool.runescape.wiki/w/Temporary_skill_boost/Slayer
# "Players can boost the Slayer skill in order to be able to kill higher-level Slayer monsters" (Slayer_task/Araxytes:
# "Players can also boost from level 87 Slayer to kill Araxxor"). The one exception is the Kraken: "It requires level 87
# Slayer to kill and cannot be boosted". (The thermonuclear smoke devil's "Stat-boosts are not accepted" applies only to
# its off-task Western Provinces Diary kill.)
UNBOOSTABLE_SLAYER_LAIRS = {"kraken"}
BOSS_LAIRS = {
    # https://oldschool.runescape.wiki/w/Abyssal_Sire: "requires level 85 Slayer". https://oldschool.runescape.wiki/w/
    # Abyssal_Nexus: "a one-way entrance in the southern part of the outer ring of the Abyss ... the code DIP in the fairy
    # ring network". Abyssal Sire page: "To access the boss, the player must have visited the Abyss at least once, either
    # through the Mage of Zamorak or the Fairy Ring network" (either route, so not a hard quest requirement: ENTER_THE_ABYSS
    # alone would close it to players who came by fairy ring). Point: the DIP fairy ring (Shortest Path fairy_rings.tsv).
    "abyssal_nexus": {
        "label": "Abyssal Nexus",
        "points": [[3037, 4763, 0]],
        "requirements": {"skills": {"SLAYER": 85}, "boostable": ["SLAYER"]},
        "note": "Needs a first visit to the Abyss (through the Mage of Zamorak or a fairy ring). Then fairy ring DIP, "
                "or the one-way entrance in the south of the Abyss's outer ring. The Sire is fought in one of four "
                "chambers.",
        "konar": "the_abyss",
    },
    # https://oldschool.runescape.wiki/w/Araxxor: "requires level 92 Slayer". Point: the web tunnel into his lair in the
    # Morytania Spider Cave (Shortest Path transports.tsv "Araxxor lair", 92 Slayer; wiki Web tunnel 3656,9816).
    "araxxor": {
        "label": "Araxxor's lair (Morytania Spider Cave)",
        "points": [[3658, 9815, 0]],
        "requirements": {"questsFinished": ["PRIEST_IN_PERIL"], "skills": {"SLAYER": 92}, "boostable": ["SLAYER"]},
        "note": "Squeeze through the web tunnel on the west side of the Morytania Spider Cave.",
    },
    # https://oldschool.runescape.wiki/w/God_Wars_Dungeon: entrance and access (as the guide's place).
    # https://oldschool.runescape.wiki/w/Kree%27arra/Strategies: "70 Ranged and a mithril grapple"; 40 kill count.
    "kreearra": {
        "label": "Kree'arra's room (God Wars Dungeon)",
        "points": [[2917, 3747, 0]],
        "requirements": {"skills": {"RANGED": 70}},
        "note": "Go takes you to the dungeon entrance. Needs part of Troll Stronghold or the easy Combat Achievements, "
                "60 Strength or 60 Agility, a crossbow and mithril grapple to reach Armadyl's Eyrie, and 40 Armadyl "
                "kill count to open the door.",
        "konar": "god_wars_dungeon",
    },
    # https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth/Strategies: "70 Hitpoints (boostable) are required to access
    # Zamorak's Fortress"; 40 kill count. Entrance as above. (The other generals' 70s can't be boosted: God Wars Dungeon
    # page, "Accessing the bases requires 70 in their respective stat, which cannot be boosted", Zamorak's aside.)
    "kril": {
        "label": "K'ril Tsutsaroth's room (God Wars Dungeon)",
        "points": [[2917, 3747, 0]],
        "requirements": {"skills": {"HITPOINTS": 70}, "boostable": ["HITPOINTS"]},
        "note": "Go takes you to the dungeon entrance. Needs part of Troll Stronghold or the easy Combat Achievements, "
                "60 Strength or 60 Agility, 70 Hitpoints (boostable) to cross the river to Zamorak's Fortress, and 40 Zamorak "
                "kill count to open the door.",
    },
    # The six Wilderness lairs below need a diary on a normal task (a boss task, which this data doesn't cover, gets in
    # without it). https://oldschool.runescape.wiki/w/Callisto%27s_Den: "Entering the boss lair requires completion of the
    # medium Wilderness diary OR a Callisto boss Slayer task, and a fee of 50,000 coins ... This fee is shared across
    # Callisto's Den, Hunter's End, the Skeletal Tomb, Vet'ion's Rest, the Silk Chasm, the Web Chasm and the Escape Caves.
    # The fee does not need to be paid again, unless the player dies to another player".
    # https://oldschool.runescape.wiki/w/Callisto: "requires the medium Wilderness Diary ... a bear Slayer task does not
    # bypass the diary requirement". Point: RuneLite world map DungeonLocation.CALLISTOS_DEN (wiki 3292,3849).
    "callistos_den": {
        "label": "Callisto's Den",
        "points": [[3292, 3850, 0]],
        "requirements": {"diaries": ["WILDERNESS_MEDIUM"]},
        "note": "Needs the medium Wilderness Diary (or a Callisto boss task; a bear task doesn't let you in). "
                "A 50,000 coin fee, taken from the bank, opens all the Wilderness boss lairs at once; it is paid again only "
                "after dying to another player.",
        "krystilia": True,
    },
    # https://oldschool.runescape.wiki/w/Hunter%27s_End: hard Wilderness Diary or a Callisto boss task. Point: RuneLite
    # DungeonLocation.HUNTERS_END (wiki Artio/Strategies 3116,3677).
    "hunters_end": {
        "label": "Hunter's End",
        "points": [[3115, 3677, 0]],
        "requirements": {"diaries": ["WILDERNESS_HARD"]},
        "note": "Needs the hard Wilderness Diary (or a Callisto boss task; a bear task doesn't let you in). "
                "A 50,000 coin fee, taken from the bank, opens all the Wilderness boss lairs at once; it is paid again only "
                "after dying to another player.",
        "krystilia": True,
    },
    # https://oldschool.runescape.wiki/w/Vet%27ion%27s_Rest: medium Wilderness Diary or a Vet'ion boss task. Point:
    # RuneLite DungeonLocation.VETIONS_REST (wiki Crevice (Vet'ion's Rest) 3222,3788).
    "vetions_rest": {
        "label": "Vet'ion's Rest",
        "points": [[3221, 3788, 0]],
        "requirements": {"diaries": ["WILDERNESS_MEDIUM"]},
        "note": "Needs the medium Wilderness Diary (or a Vet'ion boss task; a skeleton task doesn't let you in). "
                "A 50,000 coin fee, taken from the bank, opens all the Wilderness boss lairs at once; it is paid again only "
                "after dying to another player.",
        "krystilia": True,
    },
    # https://oldschool.runescape.wiki/w/Skeletal_Tomb: "the hard Wilderness diary, OR ... a Vet'ion boss Slayer task (a
    # skeleton Slayer task does not bypass the diary requirement)". Point: RuneLite DungeonLocation.SKELETAL_TOMB.
    "skeletal_tomb": {
        "label": "Skeletal Tomb (Graveyard of Shadows)",
        "points": [[3179, 3684, 0]],
        "requirements": {"diaries": ["WILDERNESS_HARD"]},
        "note": "Needs the hard Wilderness Diary (or a Vet'ion boss task; a skeleton task doesn't let you in). "
                "A 50,000 coin fee, taken from the bank, opens all the Wilderness boss lairs at once; it is paid again only "
                "after dying to another player.",
        "krystilia": True,
    },
    # https://oldschool.runescape.wiki/w/Silk_Chasm: medium Wilderness Diary or a Venenatis boss task. Point: RuneLite
    # DungeonLocation.SILK_CHASM (wiki Cave entrance (Silk Chasm) 3321,3794).
    "silk_chasm": {
        "label": "Silk Chasm",
        "points": [[3320, 3798, 0]],
        "requirements": {"diaries": ["WILDERNESS_MEDIUM"]},
        "note": "Needs the medium Wilderness Diary (or a Venenatis boss task; a spider task doesn't let you in). "
                "A 50,000 coin fee, taken from the bank, opens all the Wilderness boss lairs at once; it is paid again only "
                "after dying to another player.",
        "krystilia": True,
    },
    # https://oldschool.runescape.wiki/w/Web_Chasm: "the hard Wilderness diary, OR ... a Venenatis boss Slayer task (a
    # spider Slayer task does not bypass the diary requirement)". Point: RuneLite DungeonLocation.WEB_CHASM.
    "web_chasm": {
        "label": "Web Chasm (eastern ruins)",
        "points": [[3184, 3745, 0]],
        "requirements": {"diaries": ["WILDERNESS_HARD"]},
        "note": "Needs the hard Wilderness Diary (or a Venenatis boss task; a spider task doesn't let you in). "
                "A 50,000 coin fee, taken from the bank, opens all the Wilderness boss lairs at once; it is paid again only "
                "after dying to another player.",
        "krystilia": True,
    },
    # https://oldschool.runescape.wiki/w/Scorpia: in the cave under the Scorpion Pit. Points: the three "Enter Cavern"
    # entrances (Shortest Path transports.tsv; RuneLite DungeonLocation.SCORPIA_N/E/S).
    "scorpias_cave": {
        "label": "Scorpia's cave (Scorpion Pit)",
        "points": [[3233, 3950, 0], [3242, 3948, 0], [3232, 3938, 0]],
        "note": "Any of the three cave entrances around the Scorpion Pit. Deep Wilderness.",
        "krystilia": True,
    },
    # https://oldschool.runescape.wiki/w/King_Black_Dragon: "counts towards a black dragon Slayer task, except when
    # assigned by Krystilia". Point: the lever in the Lava Maze Dungeon (wiki Lever (Lava Maze Dungeon); Shortest Path
    # teleportation_levers.tsv).
    "king_black_dragon": {
        "label": "King Black Dragon's lair (lever in the Lava Maze Dungeon)",
        "points": [[3067, 10253, 0]],
        "note": "Pull the lever in the Lava Maze Dungeon, deep in the Wilderness. Doesn't count on a task from "
                "Krystilia.",
        # https://oldschool.runescape.wiki/w/Slayer_task/Black_dragons lists the "King Black Dragon Lair" for the King
        # Black Dragon alone ("1 King Black Dragon"); the guide's place for it is the lair's world map icon
        "replaces": "king_black_dragon_lair",
    },
    # https://oldschool.runescape.wiki/w/Vorkath: "Vorkath is considered a blue dragon and zombie for the purpose of a
    # Slayer task"; fought after Dragon Slayer II. Point: Ice chunks (Vorkath) on Ungael, from the wiki's map.
    "vorkath": {
        "label": "Ungael (Vorkath)",
        "points": [[2272, 4053, 0]],
        "requirements": {"questsFinished": ["DRAGON_SLAYER_II"]},
        "note": "Torfinn's boat from Rellekka's north-east pier, then climb over the ice chunks.",
        "replaces": "ungael",
    },
    # https://oldschool.runescape.wiki/w/Kraken: "Players need level 87 Slayer ... it can only be attacked if the player
    # has it, or cave krakens as a Slayer task". Point: Kraken Cove's entrance (as the guide's place; Shortest Path has
    # no way into the cove).
    "kraken": {
        "label": "Kraken's chamber (Kraken Cove)",
        "points": [[2277, 3611, 0]],
        "requirements": {"skills": {"SLAYER": 87}},
        "note": "Go takes you to the cove's entrance. The boss's crevice is at the north end of the cove, with a "
                "public room or a private one for a fee.",
        "konar": "kraken_cove",
    },
    # https://oldschool.runescape.wiki/w/Thermonuclear_smoke_devil: "requires level 93 Slayer". Point: the crevice into
    # its room (Shortest Path "Use Crevice 535"; RuneLite DungeonLocation.SMOKE_DEVIL_BOSS).
    "thermonuclear_smoke_devil": {
        "label": "Thermonuclear smoke devil's room (Smoke Devil Dungeon)",
        "points": [[2379, 9452, 0]],
        "requirements": {"skills": {"SLAYER": 93}, "boostable": ["SLAYER"]},
        "note": "The crevice in the north-west of the Smoke Devil Dungeon.",
        "konar": "smoke_devil_dungeon",
    },
    # https://oldschool.runescape.wiki/w/Cerberus: "Defeating Cerberus requires a Slayer level of 91"; on a hellhound task
    # "wild pies may be used to boost the player's Slayer level. While boosting, the player's Slayer level must remain at
    # or above 91". Point: the cave in
    # Taverley Dungeon you crawl through (Shortest Path "Crawl Cave 26568"); Konar: "Cerberus lives in her chamber linked
    # to Taverley dungeon".
    "cerberus": {
        "label": "Cerberus' Lair (Taverley Dungeon)",
        "points": [[2873, 9848, 0]],
        "requirements": {"skills": {"SLAYER": 91}, "boostable": ["SLAYER"]},
        "note": "Crawl through the cave in the hellhound area of Taverley Dungeon (a dusty key, or 70 Agility for the "
                "pipe shortcut). Then pick one of the three arenas.",
        "konar": "taverley_dungeon",
    },
    # https://oldschool.runescape.wiki/w/Alchemical_Hydra: "requires level 95 Slayer ... can only be killed while on a
    # hydra Slayer task". The lair door has no wiki coordinate, so Go takes the player to the dungeon's entrance.
    "alchemical_hydra": {
        "label": "Alchemical Hydra's lair (Karuulm Slayer Dungeon)",
        "points": [[1308, 3807, 0]],
        "requirements": {"skills": {"SLAYER": 95}, "boostable": ["SLAYER"]},
        "note": "Go takes you to the dungeon's entrance; the lair is at the north side of the bottom level. Wear boots "
                "of stone, boots of brimstone or granite boots for the hot floor (not needed with the elite Kourend & "
                "Kebos Diary).",
        "konar": "karuulm_slayer_dungeon",
    },
    # https://oldschool.runescape.wiki/w/Kalphite_Queen: lower level of the Kalphite Lair. Point: the tunnel down to her
    # (Shortest Path "Climb-down Tunnel entrance 23609").
    "kalphite_queen": {
        "label": "Kalphite Queen's chamber (Kalphite Lair)",
        "points": [[3508, 9498, 2]],
        "note": "Down the tunnel at the east of the Kalphite Lair. Needs a rope for the entrance and one for the "
                "tunnel (not after the hard Desert Diary).",
        "konar": "kalphite_lair",
    },
    # https://oldschool.runescape.wiki/w/Dagannoth_Kings: "a Slayer-only lair for players who have been assigned
    # dagannoth". Point: the Waterbirth Island Dungeon entrance (as the guide's place; Shortest Path can't path the
    # dungeon's doors).
    "dagannoth_kings": {
        "label": "Dagannoth Kings' lair (Waterbirth Island Dungeon)",
        "points": [[2520, 3740, 0]],
        "note": "Go takes you to the dungeon entrance. Take the Kings' ladder at the bottom of the dungeon and pick "
                "\"Slayer\" for the task-only lair. The doors need a rune thrownaxe and a pet rock (or a friend), or "
                "85 Agility for the shortcut.",
        "konar": "waterbirth_island",
    },
    # https://oldschool.runescape.wiki/w/Sarachnis: "She can be killed as part of a spider Slayer task". Point: Thick Web
    # in the Forthos Dungeon, from the wiki's map.
    "sarachnis": {
        "label": "Sarachnis' crypt (Forthos Dungeon)",
        "points": [[1841, 9912, 0]],
        "note": "Through the thick web in the south of the Forthos Dungeon.",
        "konar": "forthos_dungeon",
    },
    # https://oldschool.runescape.wiki/w/Grotesque_Guardians: "requires level 75 Slayer ... can only be killed while the
    # player is assigned gargoyles". Point: Roof Entrance of the Slayer Tower, from the wiki's map.
    "grotesque_guardians": {
        "label": "Slayer Tower roof (Grotesque Guardians)",
        "points": [[3426, 3542, 2]],
        "requirements": {"questsFinished": ["PRIEST_IN_PERIL"], "skills": {"SLAYER": 75}, "boostable": ["SLAYER"]},
        "note": "The roof entrance at the top of the Slayer Tower. The first visit needs a brittle key.",
        "konar": "slayer_tower",
    },
    # https://oldschool.runescape.wiki/w/Skotizo: "can be substituted for both black demon and greater demon kills";
    # "Skotizo's Lair is part of the Catacombs of Kourend, and does count as such for the purpose of Konar Slayer tasks".
    # Point: Altar (Catacombs of Kourend), from the wiki's map.
    "skotizo": {
        "label": "Skotizo's lair (Catacombs of Kourend altar)",
        "points": [[1664, 10048, 0]],
        "note": "Use a dark totem on the altar in the middle of the Catacombs. One totem per kill.",
        "konar": "catacombs_of_kourend",
    },
    # https://oldschool.runescape.wiki/w/Obor (counts for hill giants: wiki Slayer task page, "Superiors and
    # alternatives"). Keys: "A giant key is needed to unlock the gate ... for the first time ... Subsequent access to the
    # lair does not require a key"; he respawns on trying the chest "regardless of whether the player has a giant key".
    # Point: Gate (Obor) in the Edgeville Dungeon, from the wiki's map.
    "obor": {
        "label": "Obor's lair (Edgeville Dungeon)",
        "points": [[3095, 9832, 0]],
        "note": "The gate at the west of the Edgeville Dungeon's hill giant room. The giant key is only needed to open "
                "the gate the first time and to open his loot chest; he respawns when you try the chest, so you can "
                "keep killing him for the task without keys.",
    },
    # https://oldschool.runescape.wiki/w/Slayer_task/Moss_giants: "Bryophyta, the moss giant boss".
    # https://oldschool.runescape.wiki/w/Bryophyta: "The entrance to the lair is located just east of the moss giants";
    # the key is needed only for the first entry and the chest; she "respawns ... regardless of whether the player has a
    # mossy key". Point: Gate
    # (Bryophyta) in the Varrock Sewers, from the wiki's map.
    "bryophyta": {
        "label": "Bryophyta's lair (Varrock Sewers)",
        "points": [[3174, 9901, 0]],
        "note": "The gate just east of the Varrock Sewers' moss giants. The mossy key is only needed to open the gate the "
                "first time and to open her loot chest; she respawns when you try the chest, so you can keep killing her "
                "for the task without keys.",
    },
    # https://oldschool.runescape.wiki/w/Scurrius: "Scurrius now counts towards rat Slayer tasks". Point: Broken bars in
    # the Varrock Sewers, from the wiki's map.
    "scurrius": {
        "label": "Scurrius' lair (Varrock Sewers)",
        "points": [[3282, 9868, 0]],
        "note": "Climb through the broken bars in the east of the Varrock Sewers.",
    },
    # https://oldschool.runescape.wiki/w/Slayer_task/Cows: "Brutus can be killed on a cow task"; "may be fought
    # repeatedly after the quest" (https://oldschool.runescape.wiki/w/Brutus). Point: Warning Sign (Brutus).
    "brutus": {
        "label": "Brutus' pen (Lumbridge cow field)",
        "points": [[3260, 3294, 0]],
        "requirements": {"questsFinished": ["THE_IDES_OF_MILK"]},
        "note": "By the warning sign at the cow field east of Lumbridge.",
    },
    # https://oldschool.runescape.wiki/w/Royal_Titans: "The entrance tunnel to the bosses can be found in the western part
    # of the dungeon". Point: Scoreboard (Royal Titans), which "can be found by the tunnel".
    "royal_titans": {
        "label": "Royal Titans (Asgarnian Ice Dungeon)",
        "points": [[2954, 9577, 0]],
        "note": "The tunnel in the west of the Asgarnian Ice Dungeon (60 Agility opens a shortcut from the ladder).",
    },
    # https://oldschool.runescape.wiki/w/Shellbane_gryphon: "requiring level 51 Slayer and completion of Troubled
    # Tortugans". Point: the cave entrance on the Great Conch, from the wiki's map.
    "shellbane_gryphon": {
        "label": "Shellbane gryphon's nest (Great Conch)",
        "points": [[3176, 2477, 0]],
        "requirements": {"questsFinished": ["TROUBLED_TORTUGANS"], "skills": {"SLAYER": 51}, "boostable": ["SLAYER"]},
        "note": "The cave north of fairy ring CJQ, in the middle of the Great Conch. Bring a tortugan shield.",
    },
    # https://oldschool.runescape.wiki/w/Slayer_task/Lesser_Nagua: "those assigned Nagua at the Ruins of Tapoyauik may
    # kill Amoxliatl". Point: the Twilight Temple lift down to her (Shortest Path "Use Lift Platform 54524", needs The
    # Heart of Darkness).
    "amoxliatl": {
        "label": "Amoxliatl's chamber (Ruins of Tapoyauik)",
        "points": [[1642, 3222, 0]],
        "requirements": {"questsFinished": ["THE_HEART_OF_DARKNESS"]},
        "note": "The lift platform at the Twilight Temple. Counts on a Nagua task at the Ruins of Tapoyauik.",
        "konar": "ruins_of_tapoyauik",
    },
}

# Bosses that count for a normal slayer task (wiki name -> lair and the tasks it counts for), from the OSRS Wiki's
# https://oldschool.runescape.wiki/w/Slayer_task "Superiors and alternatives" column and each boss's page (cited at its
# lair above). Only these bosses are listed: a monster in the wiki's "Bosses" category that isn't here is left out (Zulrah,
# the Desert Treasure II bosses and the like count only on a boss task). Not here on purpose: Kolodion (one fight per
# account, left out with ONE_TIME_FIGHTS); Porazdir, who does count as a black demon ("Porazdir is considered a black
# demon for the purpose of a Slayer assignment", https://oldschool.runescape.wiki/w/Porazdir) and can be fought again,
# but only as part of the Mage Arena II miniquest "or when reobtaining an imbued Zamorak cape after completing the
# miniquest" (https://oldschool.runescape.wiki/w/Slayer_task/Black_demons): he only appears for the player who is on
# that errand, so there is no lair to send a player to on a task; Elvarg (Dragon Slayer I only), TzTok-Jad and TzKal-Zuk
# (offered instead of a TzHaar task, not killed on one), the Giant Sea Snake (not fightable after Royal Trouble) and the
# Hell-Rat Behemoth (your cat fights it).
# "masters": the only masters whose task the boss counts for; "notMasters": masters whose task it doesn't count for (by
# SlayerMasters name). The plugin also drops a boss whose lair isn't among the places that count for the master: outside
# the Wilderness for Krystilia, outside Konar's area for her.
BOSSES = {
    "Abyssal Sire": {"lair": "abyssal_nexus", "tasks": ["Abyssal demons"]},
    "Araxxor": {"lair": "araxxor", "tasks": ["Araxytes", "Spiders"]},
    "Kree'arra": {"lair": "kreearra", "tasks": ["Aviansies"]},
    "K'ril Tsutsaroth": {"lair": "kril", "tasks": ["Greater demons"]},
    "Callisto": {"lair": "callistos_den", "tasks": ["Bears"]},
    "Artio": {"lair": "hunters_end", "tasks": ["Bears"]},
    "Vet'ion": {"lair": "vetions_rest", "tasks": ["Skeletons"]},
    "Calvar'ion": {"lair": "skeletal_tomb", "tasks": ["Skeletons"]},
    "Venenatis": {"lair": "silk_chasm", "tasks": ["Spiders"]},
    "Spindel": {"lair": "web_chasm", "tasks": ["Spiders"]},
    "Scorpia": {"lair": "scorpias_cave", "tasks": ["Scorpions"]},
    # "counts towards a black dragon Slayer task, except when assigned by Krystilia"
    "King Black Dragon": {"lair": "king_black_dragon", "tasks": ["Black dragons"], "notMasters": ["Krystilia"]},
    "Vorkath": {"lair": "vorkath", "tasks": ["Blue dragons", "Zombies"]},
    "Kraken": {"lair": "kraken", "tasks": ["Cave kraken"]},
    "Thermonuclear smoke devil": {"lair": "thermonuclear_smoke_devil", "tasks": ["Smoke devils"]},
    "Cerberus": {"lair": "cerberus", "tasks": ["Hellhounds"]},
    "Alchemical Hydra": {"lair": "alchemical_hydra", "tasks": ["Hydras"]},
    "Kalphite Queen": {"lair": "kalphite_queen", "tasks": ["Kalphite"]},
    "Dagannoth Prime": {"lair": "dagannoth_kings", "tasks": ["Dagannoth"]},
    "Dagannoth Rex": {"lair": "dagannoth_kings", "tasks": ["Dagannoth"]},
    "Dagannoth Supreme": {"lair": "dagannoth_kings", "tasks": ["Dagannoth"]},
    "Sarachnis": {"lair": "sarachnis", "tasks": ["Spiders"]},
    # The wiki's page for the pair is Dusk's (Dawn has no infobox of her own); the core Slayer plugin counts both names
    # (Dusk's page has no drop table: the pair's loot is on https://oldschool.runescape.wiki/w/Grotesque_Guardians)
    "Dusk": {"lair": "grotesque_guardians", "tasks": ["Gargoyles"], "display": "Grotesque Guardians",
             "dropPages": ["Grotesque Guardians"]},
    "Skotizo": {"lair": "skotizo", "tasks": ["Black demons", "Greater demons"]},
    "Obor": {"lair": "obor", "tasks": ["Hill giants"]},
    "Bryophyta": {"lair": "bryophyta", "tasks": ["Moss giants"]},
    "Scurrius": {"lair": "scurrius", "tasks": ["Rats"]},
    "Brutus": {"lair": "brutus", "tasks": ["Cows"]},
    # https://oldschool.runescape.wiki/w/Slayer_task/Fire_giants: "Players who are not assigned fire giants from Konar or
    # Krystilia can also choose to kill Branda the Fire Queen"
    "Branda the Fire Queen": {"lair": "royal_titans", "tasks": ["Fire giants"], "notMasters": ["Konar", "Krystilia"],
                              "note": "Doesn't count on a fire giant task from Konar or Krystilia."},
    # https://oldschool.runescape.wiki/w/Slayer_task/Ice_giants: "Eldric the Ice King ... can be killed in place of
    # normal ice giants if assigned by Vannaka"
    "Eldric the Ice King": {"lair": "royal_titans", "tasks": ["Ice giants"], "masters": ["Vannaka"],
                            "note": "Counts only on an ice giant task from Vannaka."},
    "Shellbane gryphon": {"lair": "shellbane_gryphon", "tasks": ["Gryphons"]},
    "Amoxliatl": {"lair": "amoxliatl", "tasks": ["Lesser Nagua"]},
}
# Master names the plugin knows (SlayerMasters)
MASTERS = {"Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel", "Nieve", "Krystilia", "Konar", "Spria", "Mortimer"}
# Where a lair's place comes from (LocationEntry.source): never used by "Go to nearest"
BOSS_SOURCE = "boss"
BOSS_KEY_PREFIX = "boss_"


def bosses_of(task_name):
    """{wiki name: BOSSES entry} for the bosses that count for this task."""
    return {name: b for name, b in BOSSES.items() if task_name in b["tasks"]}


def slug(text):
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def same_name(text):
    """"Alice's farm, west of the Ectofuntus" and "Alice's farm west of the Ectofuntus" are one place."""
    return re.sub(r"[^a-z0-9]", "", text.lower())


# A floor named after the place without brackets: "Brimhaven Dungeon upper level", "Ancient Cavern lower floor"
LEVEL_SUFFIX = re.compile(r"\s+(?:(?:upper|lower|top|bottom|middle|ground|first|second|third)\s+(?:level|floor)"
                          r"|(?:level|floor)\s+\d+)$", re.IGNORECASE)


def name_keys(text):
    """The forms a wiki location name can match a place's label by: the whole name, then the part before a
    qualifier ("Ancient Cavern (upper level)", "Stronghold of Security: Pit of Pestilence", "Uzer Mastaba - lower
    level" are in "Ancient Cavern", "Stronghold of Security", "Uzer Mastaba") or a bare floor ("Brimhaven Dungeon
    upper level"), each also without a plural s ("Revenant Cave" is "Revenant Caves")."""
    out = []
    head = name_head(text)
    for part in (text, head, LEVEL_SUFFIX.sub("", head)):
        whole = same_name(part)
        for k in (whole, re.sub(r"s$", "", whole)):
            if k and k not in out:
                out.append(k)
    return out


def by_name(labelled):
    """{label form: value} for [(label, value)], so name_keys of a wiki location finds them; the first wins."""
    out = {}
    for label, value in labelled:
        whole = same_name(label)
        for k in (whole, re.sub(r"s$", "", whole)):
            out.setdefault(k, value)
    return out


def find_by_name(index, text):
    return next((index[k] for k in name_keys(text) if k in index), None)


# Words that don't tell two places apart ("In and around Lumbridge" is "Lumbridge")
FILLER_WORDS = {"the", "of", "in", "at", "a", "an", "and", "around", "near", "to", "on", "by"}


def name_words(text):
    """The words of a place name that matter, without plural s: "The Underground Pass, part 1" ->
    {"underground", "pass", "part", "1"}. Wiki markup after the name ("{{Refn") is dropped."""
    text = text.split("{{", 1)[0].lower().replace("'", "")
    return frozenset(re.sub(r"(?<=[a-z]{3})s$", "", w) for w in re.findall(r"[a-z0-9]+", text)
                     if w not in FILLER_WORDS)


def place_index(places):
    """What own_places looks places up in: by_name of the labels, and each label's words."""
    return (by_name((pl["label"], pl["key"]) for pl in places),
            [(name_words(pl["label"]), name_words(name_head(pl["label"])), pl["key"]) for pl in places])


def own_places(index, loc, beside=None):
    """Keys of the places (place_index) a wiki location name is one of: the same name (name_keys), else the same
    words in another order or with more said ("Kingstown - Councillor Hughes' house" is "Councillor Hughes' house in
    Kingstown"; "Mount Karuulm" is in "Mount Karuulm (north side)"; "Hobgoblin Peninsula west of the Crafting Guild"
    is "Peninsula West of the Crafting Guild"). One word alone only matches one word ("Lumbridge" is "In and around
    Lumbridge", but "Crandor" isn't "Crandor and Karamja Dungeon").

    A word match where the longer name only places one relative to the other ("Troll Arena" and "Tunnel between Troll
    arena and Trollheim", "Dark Altar" and "East of Dark Altar": same_spot) isn't the same place: its keys go into
    beside (a set) when given, for named_places to check by distance, and are not returned.
    """
    by_label, worded = index
    same = find_by_name(by_label, loc)
    if same:
        return [same]
    words, head = name_words(loc), name_words(name_head(loc))
    out = []
    for label_words, label_head, key in worded:
        if not words or not label_words:
            continue
        if words == label_words:
            out.append(key)
        elif min(len(words), len(label_words)) >= 2 and (words <= label_words or label_words <= words):
            fewer, more, more_head = ((words, label_words, label_head) if words <= label_words
                                      else (label_words, words, head))
            if same_spot(fewer, more, more_head):
                out.append(key)
            elif beside is not None:
                beside.add(key)
    return out


# Words that place a spot relative to another ("North-west of Miscellania", "Tunnel between Troll arena and
# Trollheim", "South of the Slayer Tower", "West of Dark Altar, path towards Fairy Ring"): a name with one of these
# more than a shorter one is somewhere else, not a part of it
RELATION_WORDS = {"north", "south", "east", "west", "northern", "southern", "eastern", "western", "between",
                  "outside", "beside", "next", "behind", "beyond", "past", "path", "towards", "toward", "along",
                  "above", "below", "under", "over", "across", "opposite", "from", "off"}
# ...unless they say which part of it ("Yanille Agility Dungeon north entrance", "Mount Karuulm north side")
PART_WORDS = {"entrance", "side", "part", "wing", "level", "floor"}


def name_head(text):
    """The name before a qualifier: "Mount Karuulm" of "Mount Karuulm (north side)" (as name_keys splits it)."""
    return re.split(r"\s*(?:\(|,|:|\s-\s)", text, maxsplit=1)[0]


def same_spot(fewer, more, more_head):
    """Whether a name of the words `more` is the place named `fewer` with more said, not a place beside it: the extra
    words place nothing relative to it, say which part of it, or `fewer` is the longer name's head ("Mount Karuulm
    (north side)" is on Mount Karuulm; "North of the Troll arena" and "East of Dark Altar" are not the Troll arena or
    the Dark Altar)."""
    extra = more - fewer
    return not extra & RELATION_WORDS or bool(extra & PART_WORDS) or fewer == more_head


def spawn_groups(pages, wiki):
    """[(wiki location name or None, [points])] for one monster's spawns, named groups first, by name.

    The points are the locline bucket's (as everywhere else); each takes the name of the {{LocLine}} it's in, matched
    on x, y and plane, or on x and y when the plane differs.
    """
    names, flat = {}, {}
    for p in sorted(pages):
        for location, pts in wiki.get("spawn_locations", {}).get(p, []):
            for pt in pts:
                names.setdefault(tuple(pt), location or None)
                flat.setdefault((pt[0], pt[1]), location or None)
    groups = {}
    for pt in spawns_of(pages, wiki):
        group = groups.setdefault(names.get(tuple(pt), flat.get((pt[0], pt[1]))), [])
        if pt not in group:
            group.append(pt)
    return sorted(groups.items(), key=lambda g: (g[0] is None, g[0] or ""))


def named_places(pages, wiki, listed, wilderness_only):
    """The listed places (place_index) the wiki names one of the monster's spawn spots like (own_places), as
    ({key: the spot's points}, {key: points} for the spots only named beside the place).

    Distance alone misses a place whose only point is an entrance far from the monsters (the Chasm of Fire's black
    demons, 185 tiles from the entrance underground) or on another plane (Brimhaven Dungeon's greater demons, upstairs),
    and a guide place with no points at all. With wilderness_only, only spots with a spawn in the Wilderness count.
    """
    named, beside = {}, {}
    for loc, pts in spawn_groups(pages, wiki):
        pts = [pt for pt in pts if in_wilderness(pt) or not wilderness_only]
        if pts and loc:
            near_keys = set()
            for key in own_places(listed, loc, near_keys):
                named.setdefault(key, []).extend(pts)
            for key in near_keys:
                beside.setdefault(key, []).extend(pts)
    return named, beside


def beside_places(beside, anchors):
    """Keys of the places a monster's spots are only named beside (named_places) that the spots are still within NEAR
    of: of the place's own points, or of a spot another monster's wiki page names the place itself (anchors: key ->
    points). The grizzly bear's "Land's End" is three tiles from the bear cub's "North of Land's End"; Dad's "Troll
    Arena" is nowhere near the "Tunnel between Troll arena and Trollheim"."""
    return [key for key, pts in beside.items() if near(pts, anchors.get(key, []), NEAR)]


def merge_close(spots):
    """Joins spots whose spawns are within NEAR tiles of each other, directly or through another spot: one room the
    wiki names two ways ("Godwars Dungeon, Armadyl bossroom" and "God Wars Dungeon (Armadyl boss room)"). The
    biggest named spot's name wins. A monster's "several places" spot stays as it is. Keeps the first spot's key."""
    keys = list(spots)
    group = {k: k for k in keys}

    def root(k):
        while group[k] != k:
            k = group[k]
        return k

    for i, a in enumerate(keys):
        for b in keys[i + 1:]:
            if not spots[a]["spots"] and not spots[b]["spots"] and near(spots[a]["points"], spots[b]["points"], NEAR):
                ra, rb = root(a), root(b)
                if ra != rb:
                    group[max(ra, rb, key=keys.index)] = min(ra, rb, key=keys.index)
    merged = {}
    for k in keys:
        spot, r = spots[k], root(k)
        if r not in merged:
            merged[r] = dict(spot, monsters=list(spot["monsters"]), points=list(spot["points"]), size=0)
        target = merged[r]
        if spot["location"] and len(spot["points"]) > target["size"]:
            target["location"], target["size"] = spot["location"], len(spot["points"])
        target["monsters"] += [m for m in spot["monsters"] if m not in target["monsters"]]
        target["points"] += [pt for pt in spot["points"] if pt not in target["points"]]
    return merged


def wiki_places(task_name, places, wiki, all_points, wilderness_only, guide):
    """Places for the task's regular monsters whose spawns match none of the listed places, from their spawns.

    A spawn spot the wiki names like one of the task's places (the match missed: the place's only point is an
    entrance) joins that place instead. Other spots make one place per wiki location name, shared by every such
    monster there; a monster in many spots gets one place for all of them, and spawns with no name one place named
    after the monster. A place named like a guide place of another task borrows its access, combat and cannon facts.
    With wilderness_only (a Krystilia-only task), only spawns in the Wilderness count.

    Returns (the new places sorted by label, {monster name: keys of the listed places it joins}).
    """
    if not wiki:
        return [], {}
    listed = place_index(places)
    joined = {}
    spots = {}
    taken = {pl["key"] for pl in places}
    for name, versions in task_monsters(task_name, wiki).items():
        pages = {v["page_name"] for v in versions}
        if is_superior(name, pages, wiki) or is_boss(name, task_name):
            continue
        spawns = spawns_of(pages, wiki)
        if not spawns or places_of(name, spawns, places, all_points):
            continue
        groups = []
        for loc, pts in spawn_groups(pages, wiki):
            pts = [pt for pt in pts if in_wilderness(pt) or not wilderness_only]
            # Only the same name joins: a place with more words ("Harmony Island Church") keeps its own spawn
            # points for Go (variants_for still links the monster to "Harmony Island" too)
            own = find_by_name(listed[0], loc) if pts and loc else None
            if own:
                joined.setdefault(name, set()).add(own)
            elif pts:
                groups.append((loc, pts))
        several, several_wild = [], []
        if len(groups) > WIKI_MAX_SPOTS:
            several = [loc for loc, _ in groups if loc]
            several_wild = [loc for loc, pts in groups if loc and any(in_wilderness(pt) for pt in pts)]
            groups = [(None, [pt for _, pts in groups for pt in pts])]
        for loc, pts in groups:
            key = "wiki_" + slug(loc or name)
            spot = spots.setdefault(key, {"location": loc, "monsters": [], "points": [], "spots": several,
                                          "wildSpots": several_wild})
            if name not in spot["monsters"]:
                spot["monsters"].append(name)
            spot["points"] += [pt for pt in pts if pt not in spot["points"]]

    out = []
    for key, spot in merge_close(spots).items():
        loc, monsters = spot["location"], spot["monsters"]
        if loc and len(monsters) == 1:
            # "Tormented Demon (Ancient Guthixian Temple)", but not "Arrg (Troll Stronghold (Middle Level))"
            label = f"{monsters[0]}, {loc}" if "(" in loc else f"{monsters[0]} ({loc})"
        elif loc:
            label = loc
        elif spot["spots"]:
            label = f"{monsters[0]} (several places)"
        else:
            label = ", ".join(monsters)
        known = find_by_name(guide, loc) if loc else None
        access = WIKI_ACCESS.get(name_keys(loc)[0]) if loc else None
        wild_note = None
        if spot["spots"]:
            note = (f"Spawn points from the OSRS Wiki: {', '.join(spot['spots'])}. Go takes you to the nearest. "
                    "The data has no access details for these places.")
            # Krystilia's copy only keeps the Wilderness points, so it names only those spots
            wild = spot["wildSpots"]
            wild_note = (f"Spawn points from the OSRS Wiki: {wild[0]}, the only one of these spots in the "
                         "Wilderness, where kills count for Krystilia. The data has no access details for this place."
                         if len(wild) == 1 else
                         f"Spawn points from the OSRS Wiki: {', '.join(wild)}, the spots in the Wilderness, where "
                         "kills count for Krystilia. Go takes you to the nearest. "
                         "The data has no access details for these places.")
        else:
            note = WIKI_NOTE if known or access else WIKI_NOTE + " " + NO_ACCESS_NOTE
        unique, n = key, 2
        while unique in taken:
            unique, n = f"{key}_{n}", n + 1
        taken.add(unique)
        out.append(place(label, unique, sorted(spot["points"]), None, {
            "combat": known.get("combat") if known else None,
            "cannon": known.get("cannon") if known else None,
            "note": note,
            "requirements": requirements(known.get("access") if known else None, access),
            "source": WIKI_SOURCE,
        }))
        if wild_note and spot["wildSpots"]:
            out[-1][WILD_NOTE] = {"note": wild_note}
            if len(spot["wildSpots"]) == 1:
                out[-1][WILD_NOTE]["label"] = f"{monsters[0]} ({spot['wildSpots'][0]})"
    out.sort(key=lambda pl: (pl["label"].lower(), pl["key"]))
    return out, {k: sorted(v) for k, v in sorted(joined.items())}


def lair_places(task_name, locations, taken):
    """The lairs of the bosses that count for this task as [(lair id, place)], and {boss wiki name: lair key}.

    A lair's Konar area comes from the guide place it counts as, when that place is on the task's list (locations: the
    guide's places by key, as listed for this task). Keys start with "boss_" and don't clash with the task's others.
    """
    out, keys = [], {}
    by_lair = {}
    for name, boss in bosses_of(task_name).items():
        by_lair.setdefault(boss["lair"], []).append(name)
    for lair_id, names in by_lair.items():
        lair = BOSS_LAIRS[lair_id]
        key, n = BOSS_KEY_PREFIX + lair_id, 2
        while key in taken:
            key, n = f"{BOSS_KEY_PREFIX}{lair_id}_{n}", n + 1
        taken.add(key)
        notes = [lair.get("note")] + [BOSSES[name].get("note") for name in names]
        konar = locations.get(lair.get("konar"))
        out.append((lair_id, place(lair["label"], key, lair["points"], None, {
            "areaId": konar.get("areaId") if konar else None,
            "note": " ".join(n for n in notes if n) or None,
            "requirements": lair.get("requirements"),
            "source": BOSS_SOURCE,
        })))
        keys.update({name: key for name in names})
    return out, keys


def variants_for(task_name, places, wiki, all_points, wilderness_only=False, joined=None, lairs=None):
    """Each distinct monster on the task, with the keys of the places it spawns near, plus those wiki_places found by
    name (joined: monster name -> keys). A boss's only place is its lair (lairs: boss wiki name -> lair key); a boss
    with no lair on this task is left out.

    With wilderness_only (a task only Krystilia gives), a monster that never spawns in the Wilderness is left out:
    her kills only count there.
    """
    if not wiki:
        return []
    generic = generic_items(wiki)
    superior_of = wiki.get("superior_of", {})
    lairs = lairs or {}
    # A boss's lair is for the boss alone; the others can also be found by the name the wiki gives a spawn spot
    listed = place_index([pl for pl in places if pl.get("source") != BOSS_SOURCE])

    # Points a place is known by (its own, and spots named like it), for the spots only named beside one
    anchors = {pl["key"]: list(pl["points"]) + list(pl["wildernessPoints"]) for pl in places}
    pending = []
    out = []
    for name, versions in task_monsters(task_name, wiki).items():
        pages = {v["page_name"] for v in versions}
        superior = is_superior(name, pages, wiki)
        spawns = spawns_of(pages, wiki)
        boss = is_boss(name, task_name)
        if boss:
            if name not in lairs:
                continue  # its lair isn't on this task's list (Krystilia's only task, outside the Wilderness)
            found = [lairs[name]]
        elif name in AT_LAIR:
            found = [key for boss_name, key in lairs.items() if BOSSES[boss_name]["lair"] == AT_LAIR[name]][:1]
            if not found:
                continue  # its boss's lair isn't on this task's list
        elif not spawns and not superior:
            continue  # minigame, quest or instance only
        elif wilderness_only and not superior and not any(in_wilderness(pt) for pt in spawns):
            continue  # doesn't count for Krystilia, the only master who gives this task
        else:
            named, beside = named_places(pages, wiki, listed, wilderness_only)
            for key, pts in named.items():
                anchors.setdefault(key, []).extend(pts)
            if beside:
                pending.append((name, beside))
            found = places_of(name, spawns, places, all_points) + (joined or {}).get(name, []) + [
                k for k in named if (name, k) not in NOT_AT]
        weak = Counter((v["elemental_weakness"], v.get("elemental_weakness_percent"))
                       for v in versions if v.get("elemental_weakness"))
        variant = {
            "name": BOSSES[name].get("display", name) if boss else name,
            "level": span(v.get("combat_level") for v in versions),
            "hitpoints": span(v.get("hitpoints") for v in versions),
            "xp": span(v.get("slayer_experience") for v in versions),
            "attackTypes": attack_types(s for v in versions for s in v.get("attack_style", [])),
            "superior": superior,
            "boss": boss,
            "places": sorted(set(found)),
        }
        for field in ("masters", "notMasters") if boss else ():
            if BOSSES[name].get(field):
                variant[field] = BOSSES[name][field]
        base = next((superior_of[p] for p in sorted(pages) if p in superior_of), None)
        if superior and base:
            variant["superiorOf"] = base
        # A boss whose loot is on another wiki page (the Grotesque Guardians') takes its drops from there too
        loot_pages = pages | set(BOSSES[name].get("dropPages", []) if boss else [])
        carry = carry_for(loot_pages, wiki)
        if carry:
            variant["carry"] = carry
        rare, always = drops_for(loot_pages, wiki, generic)
        if rare:
            variant["drops"] = rare
        if always:
            variant["alwaysDrops"] = always
        combat = combat_of(versions, wiki)
        if combat:
            variant["combat"] = combat
        attributes = sorted({a for p in pages for a in wiki.get("attributes", {}).get(p, [])})
        if attributes:
            variant["attributes"] = attributes
        if weak:
            (element, percent), _ = weak.most_common(1)[0]
            # The wiki says "None" for monsters with no elemental weakness; only real elements are shown
            if element.lower() in ELEMENTS:
                variant["weakness"] = {"element": element.lower(), "percent": percent}
        out.append(variant)
    by_name_out = {v["name"]: v for v in out}
    for name, beside in pending:
        extra = [k for k in beside_places(beside, anchors) if (name, k) not in NOT_AT]
        if extra and name in by_name_out:
            by_name_out[name]["places"] = sorted(set(by_name_out[name]["places"]) | set(extra))
    out.sort(key=lambda v: (v["superior"], v["boss"], v["level"][0] if v["level"] else 0, v["name"]))
    return out


# Wiki infobox_monster field -> bundled name, for the gear-up DPS maths (SlayerLocationData.Combat)
COMBAT_KEYS = [("defence_level", "defence"), ("magic_level", "magic"), ("magic_attack_bonus", "magicAttack"),
               ("stab_defence_bonus", "stab"), ("slash_defence_bonus", "slash"), ("crush_defence_bonus", "crush"),
               ("magic_defence_bonus", "magicDefence"), ("light_range_defence_bonus", "light"),
               ("standard_range_defence_bonus", "standard"), ("heavy_range_defence_bonus", "heavy"), ("size", "size")]


def combat_of(versions, wiki):
    """The defence stats of a monster's most common version (the first such version on a tie), or None.

    A version's stats are the combat row with its page, version name, combat level and hitpoints (some pages repeat a
    version name). Zeros are left out to keep the file small; the plugin reads a missing value as 0 (size as 1).
    """
    index = wiki.get("_combat_index")
    if index is None:
        index = {}
        for r in wiki.get("combat", []):
            key = (r["page_name"], r.get("version_anchor", ""), r.get("combat_level"), r.get("hitpoints"))
            index.setdefault(key, []).append(r)
        wiki["_combat_index"] = index
    seen = Counter()
    for v in versions:
        key = (v["page_name"], v.get("version_anchor", ""), v.get("combat_level"), v.get("hitpoints"))
        for row in index.get(key, []):
            if "defence_level" in row:
                seen[tuple(row.get(field) or 0 for field, _ in COMBAT_KEYS)] += 1
    if not seen:
        return None
    values, _ = seen.most_common(1)[0]
    return {name: value for (_, name), value in zip(COMBAT_KEYS, values) if value}


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


# Shown on a guide place no monster of the task could be matched to (the panel says which monster is unknown)
NO_MONSTER = "noMonster"
# The reviewed reason, from NO_MONSTER_PLACES, bundled with it
NO_MONSTER_WHY = "noMonsterWhy"

# Guide places linked to a monster by hand, from a wiki spawn spot that names them neither the same nor close enough
# for own_places, with the points of that spot the place stands for (all of them when None). Each is checked against
# tools/data/wiki_monsters.json (the spot must be on the monster's pages, with these points), and the place takes the
# points when it has none, so Go can path there. (task, place key) -> (monster, wiki spot, points or None, evidence).
# The Dark Altar points are split by side of the altar (about 1712,3883): west towards the CIS fairy ring (about
# 1639,3868), south towards the Blood Altar (about 1716,3827), east towards the Soul Altar (about 1815,3856).
SPOT_PLACES = {
    ("Bats", "west_of_dark_altar_path_towards_fairy_ring"): (
        "Giant bat", "Dark Altar", [[1612, 3849, 0], [1633, 3842, 0], [1633, 3879, 0], [1662, 3870, 0],
                                    [1673, 3861, 0]],
        "the Giant bat page's \"Dark Altar\" spawns west of the altar, on the path to the fairy ring"),
    ("Bats", "south_of_dark_altar_path_towards_blood_altar"): (
        "Giant bat", "Dark Altar", [[1700, 3857, 0], [1709, 3860, 0]],
        "the Giant bat page's \"Dark Altar\" spawns just south of the altar, towards the Blood Altar"),
    ("Bats", "east_of_dark_altar_path_towards_soul_altar"): (
        "Giant bat", "Dark Altar", [[1732, 3855, 0], [1743, 3892, 0]],
        "the Giant bat page's \"Dark Altar\" spawns east of the altar, towards the Soul Altar"),
    # Krystilia's list; the normal list calls the same spot "Outside the Wilderness Resource Area"
    ("Hellhounds", "north_east_of_the_deserted_keep"): (
        "Hellhound", "Outside of the Wilderness Resource Area", None,
        "the Hellhound page's only surface Wilderness spawns (3169-3191, 3906-3956) are just north-east of the "
        "Deserted Keep's lever landing (about 3154,3924)"),
}

# Guide places no monster of the task lives at, as far as the bundled wiki data knows, with the evidence (reviewed by
# hand). link_unmatched marks only these NO_MONSTER; any other unmatched place, or one of these that matches, fails
# the build, so a matching regression can't hide behind the mark. Where nothing more is said: the guide row has no
# point to match by distance, and no task monster's wiki page has a spawn spot named like it or near it.
NONE_NAMED = "no task monster's wiki page has a spawn spot named like it, and the guide row has no point"
NO_MONSTER_PLACES = {
    ("Bats", "graveyard_of_heroes"): NONE_NAMED,
    ("Bats", "karamja_volcano_dungeon"): NONE_NAMED + " (the bat pages' Karamja spawns are \"Crandor and Karamja "
                                         "Dungeon\", which is another place on the list)",
    ("Bears", "great_kourend_farming_guild"): "the Black bear page's \"East of the Farming Guild\" is beside it, "
                                             "not named as it, and the guide row has no point",
    ("Bears", "north_east_of_tyras_camp_instanced"): NONE_NAMED + " (an instance)",
    ("Bears", "prifddinas_outside_the_city"): NONE_NAMED,
    ("Bears", "south_of_prifddinas_instanced"): NONE_NAMED + " (an instance)",
    ("Bears", "south_east_of_dark_warriors_fortress"): "the Grizzly bear page's Dark Warriors' Fortress spawns are "
                                                      "north-west of it, and the guide row has no point",
    ("Bears", "west_of_lletya"): NONE_NAMED,
    ("Bears", "west_of_the_graveyard_of_shadows"): NONE_NAMED,
    ("Bears", "south_of_the_western_ruins"): NONE_NAMED,
    ("Birds", "farmer_freds_chicken_pen"): NONE_NAMED + " (the bird pages' chicken pens are elsewhere: Rellekka, "
                                           "Lumbridge East Farm)",
    ("Dwarves", "falador_near_the_mining_guild_ladders"): "the Dwarf page's \"Falador Mining Guild\" and \"Surface "
                                                         "of Mining Guild\" name the guild, not the ladders, and the "
                                                         "guide row has no point",
    ("Elves", "prifddinas_south_gate"): "the elf pages name only \"Prifddinas\" (the Guard page), not a gate, and the "
                                        "guide row has no point",
    ("Elves", "prifddinas_east_gate"): "the elf pages name only \"Prifddinas\" (the Guard page), not a gate, and the "
                                       "guide row has no point",
    ("Elves", "prifddinas_north_gate"): "the elf pages name only \"Prifddinas\" (the Guard page), not a gate, and the "
                                        "guide row has no point",
    ("Elves", "prifddinas_west_gate"): "the elf pages name only \"Prifddinas\" (the Guard page), not a gate, and the "
                                       "guide row has no point",
    ("Elves", "prifddinas_market_place"): "the elf pages name only \"Prifddinas\" (the Guard page), not the market, "
                                          "and the guide row has no point",
    ("Elves", "mourner_headquarters_downstairs"): "the Mourner page names the \"Mourner Tunnels\", not the "
                                                  "headquarters, and the guide row has no point",
    ("Elves", "tower_of_voices"): NONE_NAMED,
    ("Ghosts", "temple_in_the_golem"): "the Ghost page's Uzer spawns are the \"Ruins of Uzer Temple Basement\", not "
                                       "named as this temple, and the guide row has no point",
    ("Moss giants", "south_east_of_the_lava_maze"): NONE_NAMED + " in the Wilderness",
    ("Ogres", "myths_guild_dungeon"): "no ogre page has a spawn spot named like it or within reach of its entrance "
                                      "(2456,2847)",
    ("Scorpions", "stonecutter_outpost_temple_east_of_colossal_wyrm_remains"): (
        "the Scorpion page's \"South-west of Stonecutter Outpost\" is another spot, and the guide row has no point"),
    ("Scorpions", "scorpion_pit_cave"): "the cave's scorpions are Scorpia's offspring, which are only listed at "
                                        "Scorpia's lair (AT_LAIR), and the guide row has no point",
    ("Skeletons", "tarns_lair_abandoned_mine"): "the skeleton pages name \"Tarn's Lair\" and \"Abandoned Mine "
                                                "(Level 1)\" apart, and the guide row has no point",
    ("Skeletons", "wizards_tower"): NONE_NAMED,
    ("Skeletons", "southern_wilderness_mine"): "the Skeleton page's Wilderness mine spot (\"South-west Wilderness mine, "
                                               "just south of the Dark Warriors' Fortress\") is the list's own "
                                               "\"South-west Wilderness mine\", and the guide row has no point",
    ("Spiders", "south_of_callisto"): NONE_NAMED + " in the Wilderness",
    ("Trolls", "enchanted_valley"): NONE_NAMED,
    ("Vampyres", "burgh_de_rott"): "the vampyre pages' spots are \"West of\" and \"East of Burgh de Rott\", beside "
                                   "it, and the guide row has no point",
    ("Vampyres", "vampyrium"): "no vampyre page has a spawn spot named like it or near its entrance (3494,3464)",
    ("Zombies", "harmony_island_underground"): "the zombie pages name \"Harmony Island\" and \"Harmony Island "
                                               "Church\", not the underground, and the guide row has no point",
    ("Zombies", "south_of_the_graveyard_of_heroes"): NONE_NAMED,
}


def link_spot_places(task_name, places, variants, wiki, unroutable):
    """Links the SPOT_PLACES of this task to their monster and gives an empty place the spot's points. Fails on an
    entry whose monster, spot or points aren't in the wiki data, or whose place is linked already. Changes places and
    variants in place."""
    by_key = {pl["key"]: pl for pl in places}
    by_name = {v["name"]: v for v in variants}
    for (task, key), (monster, spot, points, _) in SPOT_PLACES.items():
        if task != task_name or key not in by_key:
            continue
        v = by_name.get(monster)
        if not v:
            raise SystemExit(f"SPOT_PLACES {task} / {key}: no monster {monster!r} on the task")
        if any(key in x["places"] for x in variants):
            raise SystemExit(f"SPOT_PLACES {task} / {key}: already linked; remove the entry")
        pages = {x["page_name"] for x in task_monsters(task_name, wiki).get(monster, [])}
        found = [pt for loc, pts in spawn_groups(pages, wiki) if loc == spot for pt in pts]
        if not found or points and any(pt not in found for pt in points):
            raise SystemExit(f"SPOT_PLACES {task} / {key}: {monster}'s wiki spot {spot!r} doesn't have those points")
        v["places"] = sorted(set(v["places"]) | {key})
        pl = by_key[key]
        if not pl["points"] and not pl["wildernessPoints"] and key not in unroutable:
            pts = sorted(points or found)
            pl["points"] = [pt for pt in pts if not in_wilderness(pt)]
            pl["wildernessPoints"] = [pt for pt in pts if in_wilderness(pt)]


def link_unmatched(task_name, places, variants):
    """Guide places that no monster's spawns or spot names matched (an empty "Heroes' Guild (basement)"-style row of
    the wiki's Slayer task table, or a spot the monster pages name another way).

    The guide's table says the task's monsters are there, so a task with one regular monster gets it (Werewolves at
    Canifis, Rockslugs in the Crypt of Tonali). With several, which one is a guess: the place is marked NO_MONSTER and
    the panel says the data doesn't know (Elves at the Prifddinas gates, Bears west of Lletya: no elf or bear page's
    spawn spot is named or placed there). Changes both lists in place.
    """
    regular = [v for v in variants if not v["superior"] and not v["boss"]]
    for pl in places:
        if pl.get("source") == BOSS_SOURCE or any(pl["key"] in v["places"] for v in variants):
            continue
        if len(regular) == 1:
            regular[0]["places"] = sorted(set(regular[0]["places"]) | {pl["key"]})
        elif regular and (task_name, pl["key"]) in NO_MONSTER_PLACES:
            pl[NO_MONSTER] = True
            pl[NO_MONSTER_WHY] = NO_MONSTER_PLACES[(task_name, pl["key"])]


# Spawn spots fill_from_spawns doesn't take points from: an instance Shortest Path can't path into ("North-east Karamja
# (Instanced)") or spawns only there during a quest ("Digsite Dungeon (During The Dig Site)")
NOT_A_GO_SPOT = re.compile(r"\binstanced?\b|\(during\b", re.IGNORECASE)


def fill_from_spawns(task_name, places, variants, wiki, wilderness_list, unroutable):
    """Gives a place with no map point the spawn points of its monsters' wiki spots named like it (find_by_name, the
    same name only), so Go can path there: Blue dragons in the "Heroes' Guild (basement)" (the wiki's "Heroes' Guild
    Basement"), the demonic gorillas' "Crash Site Cavern". Only a monster linked to the place gives points, and not
    from an instance or a quest-only spot (NOT_A_GO_SPOT). A looser
    word match ("Dark Altar", "Mount Karuulm") can name several places, so it stays a link without points. Places
    whose entrance Shortest Path can't path to (unroutable: keys) and boss lairs are left as they are. With
    wilderness_list (Krystilia's list, or a task only she gives), only Wilderness points count. Changes places in
    place; returns the keys of the places that got points.
    """
    if not wiki:
        return []
    empty = [pl for pl in places if not pl["points"] and not pl["wildernessPoints"]
             and pl.get("source") != BOSS_SOURCE and pl["key"] not in unroutable]
    index = by_name((pl["label"], pl["key"]) for pl in empty)
    found, monsters = {}, task_monsters(task_name, wiki)
    for v in variants:
        if v["superior"] or v["boss"]:
            continue
        versions = monsters.get(v["name"], [])
        for loc, pts in spawn_groups({x["page_name"] for x in versions}, wiki):
            key = find_by_name(index, loc) if loc and not NOT_A_GO_SPOT.search(loc) else None
            if key and key in v["places"] and (v["name"], key) not in NOT_AT:
                pts = [pt for pt in pts if in_wilderness(pt) or not wilderness_list]
                spot = found.setdefault(key, [])
                spot += [pt for pt in pts if pt not in spot]
    filled = []
    for pl in empty:
        pts = sorted(found.get(pl["key"], []))
        if pts:
            pl["points"] = [pt for pt in pts if not in_wilderness(pt)]
            pl["wildernessPoints"] = [pt for pt in pts if in_wilderness(pt)]
            filled.append(pl["key"])
    return filled


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
        places = {pl["key"]: pl for pl in t["locations"] + t["wildernessLocations"]}
        for v in t.get("variants", []):
            if not v["boss"]:
                continue
            lairs = [places.get(k) for k in v["places"]]
            if len(lairs) != 1 or not lairs[0] or lairs[0].get("source") != BOSS_SOURCE:
                problems.append(f'{t["name"]} / {v["name"]}: a boss needs exactly one place, its lair: {v["places"]}')
            elif not lairs[0]["points"] and not lairs[0]["wildernessPoints"]:
                problems.append(f'{t["name"]} / {v["name"]}: its lair has no point for Go')
        regular = [v for v in t.get("variants", []) if not v["superior"] and not v["boss"]]
        for pl in places.values():
            linked = any(pl["key"] in v["places"] for v in t.get("variants", []))
            if pl.get("source") != BOSS_SOURCE and regular and not linked and not pl.get(NO_MONSTER):
                problems.append(f'{t["name"]} / {pl["key"]}: no monster lives here and it isn\'t in NO_MONSTER_PLACES')
            if pl.get(NO_MONSTER) and (linked or len(regular) < 2):
                problems.append(f'{t["name"]} / {pl["key"]}: marked {NO_MONSTER} but a monster is known')
            # Never prayer advice for a boss (plugin hub rules): not even in a lair's note
            if pl.get("source") == BOSS_SOURCE and re.search(r"\bpray", pl.get("note") or "", re.IGNORECASE):
                problems.append(f'{t["name"]} / {pl["key"]}: prayer advice in a boss lair note')
        for key in [k for task, k in NO_MONSTER_PLACES if task == t["name"]]:
            if not places.get(key, {}).get(NO_MONSTER):
                problems.append(f'{t["name"]} / {key}: in NO_MONSTER_PLACES but a monster is linked or it is gone')
        found = {v["name"] for v in t.get("variants", []) if v["boss"]}
        for name, boss in bosses_of(t["name"]).items():
            if boss.get("display", name) not in found and (t["locations"] or BOSS_LAIRS[boss["lair"]].get("krystilia")):
                problems.append(f'{t["name"]} / {name}: in BOSSES but not in the wiki data for the task')
        for v in t.get("variants", []):
            if v["name"] in GWD_MINIONS:
                problems.append(f'{t["name"]} / {v["name"]}: a God Wars Dungeon bodyguard')
    names = {t["name"] for t in tasks}
    problems += [f"NO_MONSTER_PLACES / {task}: no such task" for task, _ in NO_MONSTER_PLACES if task not in names]
    by_task = {t["name"]: t for t in tasks}
    for (task, key), (monster, *_) in SPOT_PLACES.items():
        t = by_task.get(task)
        if not t or not any(v["name"] == monster and key in v["places"] for v in t.get("variants", [])):
            problems.append(f"SPOT_PLACES / {task} / {key}: not linked to {monster} (gone from the task?)")
    for name, boss in BOSSES.items():
        problems += [f"BOSSES / {name}: no task {task!r}" for task in boss["tasks"] if task not in names]
        problems += [f"BOSSES / {name}: no master {m!r}" for m in boss.get("masters", []) + boss.get("notMasters", [])
                     if m not in MASTERS]
        if boss["lair"] not in BOSS_LAIRS:
            problems.append(f"BOSSES / {name}: no lair {boss['lair']!r}")
    for lair_id, lair in BOSS_LAIRS.items():
        req = lair.get("requirements") or {}
        problems += [f"BOSS_LAIRS / {lair_id}: boostable {s!r} isn't a needed skill" for s in req.get("boostable", [])
                     if s not in req.get("skills", {})]
        problems += [f"BOSS_LAIRS / {lair_id}: unknown diary {d!r}" for d in req.get("diaries", []) if d not in DIARIES]
        if "SLAYER" in req.get("skills", {}) and ("SLAYER" in req.get("boostable", [])) == (lair_id in
                                                                                           UNBOOSTABLE_SLAYER_LAIRS):
            problems.append(f"BOSS_LAIRS / {lair_id}: Slayer must be boostable unless the wiki says it can't be "
                            f"(UNBOOSTABLE_SLAYER_LAIRS)")
        if re.search(r"\bkey per kill\b", lair.get("note") or "", re.IGNORECASE):
            problems.append(f"BOSS_LAIRS / {lair_id}: 'key per kill' (Obor/Bryophyta respawn without a key)")
    for name, lair_id in AT_LAIR.items():
        if lair_id not in BOSS_LAIRS:
            problems.append(f"AT_LAIR / {name}: no lair {lair_id!r}")
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
    # Guide places by name, so a wiki-spawn place with the same name can borrow their access facts
    guide = by_name((locations[key]["label"], locations[key]) for key in sorted(locations))

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
        # Monsters that live away from every listed place get places of their own, from their wiki spawns. Spawns in
        # the Wilderness stay in wildernessPoints (shown with the Wilderness setting), and go on Krystilia's list
        # too when she gives the task; a task only she gives gets only those.
        krystilia_only = tid not in normal
        her_list = bool(wild_entries)
        extras, joined = wiki_places(t["task"], entries + wild_entries, wiki, all_points, krystilia_only, guide)
        for extra in extras:
            wild_note = extra.pop(WILD_NOTE, None)
            if not krystilia_only:
                entries.append(extra)
            # Only onto a list she has: an empty one makes the panel fall back to the normal places' Wilderness spawns
            if extra["wildernessPoints"] and (her_list or krystilia_only):
                wild_entries.append(dict(extra, points=[], **(wild_note or {})))
        # Each boss that counts gets its lair, after the other places. A Wilderness lair goes on Krystilia's list too
        # when the boss counts for her; a task only she gives gets only those.
        guide_here = {key: locations[key] for key in t["locations"]}
        lairs, lair_keys = lair_places(t["task"], guide_here, {pl["key"] for pl in entries + wild_entries})
        for lair_id, lair in lairs:
            her_lair = lair["wildernessPoints"] and BOSS_LAIRS[lair_id].get("krystilia")
            if not krystilia_only:
                entries.append(lair)
            if her_lair and (her_list or krystilia_only):
                wild_entries.append(dict(lair, points=[]))
            elif krystilia_only:
                lair_keys = {name: key for name, key in lair_keys.items() if key != lair["key"]}
        variants = variants_for(t["task"], entries + wild_entries, wiki, all_points, krystilia_only, joined, lair_keys)
        # A guide place that only stood for a boss's lair gives way to the lair
        for lair_id in sorted({BOSSES[name]["lair"] for name in lair_keys}):
            gone = BOSS_LAIRS[lair_id].get("replaces")
            if gone and any(pl["key"] == gone for pl in entries + wild_entries):
                users = [v["name"] for v in variants if gone in v["places"]]
                if users or t.get("recommended") == gone:
                    raise SystemExit(f'{t["task"]}: the {lair_id} lair can\'t replace {gone}; it\'s used by {users}')
                entries = [pl for pl in entries if pl["key"] != gone]
                wild_entries = [pl for pl in wild_entries if pl["key"] != gone]
        unroutable = {key for key, loc in locations.items() if loc.get("entranceRoutable") is False}
        link_spot_places(t["task"], entries + wild_entries, variants, wiki, unroutable)
        link_unmatched(t["task"], entries + wild_entries, variants)
        fill_from_spawns(t["task"], entries, variants, wiki, krystilia_only, unroutable)
        fill_from_spawns(t["task"], wild_entries, variants, wiki, True, unroutable)
        tasks.append({
            "taskId": tid,
            "name": t["task"],
            "recommended": t.get("recommended"),
            "locations": entries,
            "wildernessLocations": wild_entries,
            "info": task_info(info, t["task"]),
            "variants": variants,
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
    from_wiki = sum(1 for l in every if l.get("source") == WIKI_SOURCE)
    print(f"{from_wiki} of those places made from monsters' wiki spawns")
    lairs = sum(1 for l in every if l.get("source") == BOSS_SOURCE)
    print(f"{lairs} boss lairs for {sum(1 for t in tasks for v in t['variants'] if v['boss'])} bosses")
    for problem in left_out_bosses(tasks, wiki):
        print(f"Note: {problem} is in the wiki's Bosses category but not in BOSSES, so it isn't listed")
    for problem in unplaced(tasks):
        print(f"Warning: {problem} has no place")


def unplaced(tasks):
    """'Task / monster' for every regular monster or boss no place was found for."""
    return [f'{t["name"]} / {v["name"]}' for t in tasks for v in t["variants"]
            if not v["superior"] and not v["places"]]


def left_out_bosses(tasks, wiki):
    """'Task / monster' for each wiki boss in a task's category that BOSSES doesn't list for it (to review)."""
    out = []
    for t in tasks if wiki else []:
        category = CATEGORY_ALIASES.get(t["name"].lower(), t["name"].lower())
        for m in wiki["monsters"]:
            cats = [c.lower() for c in m.get("slayer_category", [])]
            if (category in cats and "bosses" in cats and m["name"] not in bosses_of(t["name"])
                    and m.get("combat_level") and not NOT_A_TASK_KILL.search(m["name"])):
                item = f'{t["name"]} / {m["name"]}'
                if item not in out:
                    out.append(item)
    return out


if __name__ == "__main__":
    main(sys.argv[1])
