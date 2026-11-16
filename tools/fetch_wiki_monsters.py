#!/usr/bin/env python3
"""Downloads slayer monster facts from the OSRS Wiki into tools/data/wiki_monsters.json.

build_slayer_locations.py reads that file to add each task's monster variants (level, hitpoints, slayer XP,
attack styles, elemental weakness, where they spawn). Run this only when you want fresher wiki data; the
plugin itself never calls the wiki.

Data comes from the wiki's Bucket API (infobox_monster, locline and dropsline buckets, and the "Superior slayer monsters"
category). OSRS Wiki content is licensed CC BY-NC-SA 3.0, see NOTICE.md.

Usage: python3 tools/fetch_wiki_monsters.py
"""
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent / "data" / "wiki_monsters.json"
API = "https://oldschool.runescape.wiki/api.php"
# The wiki asks automated clients to identify themselves
HEADERS = {"User-Agent": "combat-gear-organizer-datagen/1.0 (RuneLite plugin data build)"}
COORD = re.compile(r"x:\s*(\d+)\D+y:\s*(\d+)")
# "The shadow wyrm is a [[superior]] variant of the [[wyrm]]."
SUPERIOR_OF = re.compile(r"superior[^.]{0,80}?\b(?:variant|version|form|counterpart)s? of (?:the |an? )?\[\[([^\]|#]+)",
                         re.IGNORECASE)
# Superiors that only existed in a league ("was a variant ... in Trailblazer Reloaded League")
LEAGUE_ONLY = re.compile(r"\[\[[^\]]*League[^\]]*\]\]", re.IGNORECASE)
FIELDS = ["page_name", "name", "version_anchor", "combat_level", "hitpoints", "slayer_experience", "slayer_category",
          "attack_style", "elemental_weakness", "elemental_weakness_percent"]


def get(params):
    url = API + "?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS)) as r:
        return json.load(r)


def bucket(query):
    d = get({"action": "bucket", "format": "json", "query": query})
    if "error" in d:
        raise RuntimeError(d["error"])
    return d["bucket"]


def fetch_monsters():
    rows, offset = [], 0
    select = ",".join(f"'{f}'" for f in FIELDS)
    while True:
        page = bucket(f"bucket('infobox_monster').select({select})"
                      f".where('slayer_category','!=',bucket.Null()).limit(500).offset({offset}).run()")
        rows += page
        if len(page) < 500:
            return rows
        offset += 500
        time.sleep(0.5)


def fetch_superiors():
    names, cont = [], {}
    while True:
        d = get({"action": "query", "list": "categorymembers", "cmtitle": "Category:Superior slayer monsters",
                 "cmlimit": 500, "format": "json", **cont})
        names += [m["title"] for m in d["query"]["categorymembers"]]
        if "continue" not in d:
            return names
        cont = d["continue"]


def strip_infobox(text):
    """The article text after the leading templates, where the intro sentence is."""
    depth, i = 0, 0
    while i < len(text):
        if text.startswith("{{", i):
            depth += 1
            i += 2
        elif text.startswith("}}", i):
            depth -= 1
            i += 2
        elif depth == 0 and not text[i].isspace():
            return text[i:]
        else:
            i += 1
    return ""


def fetch_collection_log_items():
    """Names of every item in the collection log, so drops can be marked as log slots."""
    names, cont = [], {}
    while True:
        d = get({"action": "query", "list": "categorymembers", "cmtitle": "Category:Collection log items",
                 "cmlimit": 500, "cmnamespace": 0, "format": "json", **cont})
        names += [m["title"] for m in d["query"]["categorymembers"]]
        if "continue" not in d:
            return names
        cont = d["continue"]
        time.sleep(0.4)


def fetch_superior_bases(names):
    """superior page -> the monster it replaces, from each page's intro. Also the league-only superiors."""
    bases, league = {}, []
    for i in range(0, len(names), 50):
        d = get({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "format": "json",
                 "redirects": 1, "titles": "|".join(names[i:i + 50])})
        for page in d["query"]["pages"].values():
            if "revisions" not in page:
                continue
            intro = strip_infobox(page["revisions"][0]["slots"]["main"]["*"])[:1500]
            if LEAGUE_ONLY.search(intro.split("\n\n")[0]):
                league.append(page["title"])
                continue
            m = SUPERIOR_OF.search(intro)
            if m:
                base = m.group(1).strip()
                bases[page["title"]] = base[0].upper() + base[1:]
        time.sleep(0.5)
    return bases, league


def fetch_spawns(pages):
    """page name -> [[x, y, plane], ...] from the wiki's map location lines."""
    spawns = {}
    for i in range(0, len(pages), 40):
        batch = pages[i:i + 40]
        cond = "bucket.Or(" + ",".join("{'page_name','%s'}" % p.replace("'", "\\'") for p in batch) + ")"
        offset = 0
        while True:
            page = bucket(f"bucket('locline').select('page_name','coordinates','plane','mapid')"
                          f".where({cond}).limit(500).offset({offset}).run()")
            for row in page:
                pts = spawns.setdefault(row["page_name"], [])
                for c in row.get("coordinates", []):
                    # Usually "x:3238,y:2845"; skip anything else
                    m = COORD.search(c)
                    if m:
                        pts.append([int(m.group(1)), int(m.group(2)), int(row.get("plane") or 0)])
            if len(page) < 500:
                break
            offset += 500
        time.sleep(0.4)
    return spawns


def fetch_drops(pages):
    """page name -> [[item, rarity, on-task rarity, value, quantity low, quantity high], ...] from drop tables."""
    drops = {}
    for i in range(0, len(pages), 40):
        batch = pages[i:i + 40]
        cond = "bucket.Or(" + ",".join("{'page_name','%s'}" % p.replace("'", "\\'") for p in batch) + ")"
        offset = 0
        while True:
            page = bucket(f"bucket('dropsline').select('page_name','item_name','drop_json')"
                          f".where({cond}).limit(500).offset({offset}).run()")
            for row in page:
                try:
                    d = json.loads(row.get("drop_json") or "{}")
                except ValueError:
                    continue
                drops.setdefault(row["page_name"], []).append([
                    row["item_name"], d.get("Rarity") or "", d.get("Alt Rarity") or "", d.get("Drop Value") or 0,
                    d.get("Quantity Low") or 1, d.get("Quantity High") or 1])
            if len(page) < 500:
                break
            offset += 500
        time.sleep(0.4)
    return drops


def main():
    monsters = [m for m in fetch_monsters() if m.get("slayer_experience")]
    print(f"{len(monsters)} monster versions with slayer XP")
    superiors = fetch_superiors()
    bases, league = fetch_superior_bases(superiors)
    print(f"{len(bases)} superiors linked to their monster, {len(league)} league-only: {', '.join(league)}")
    spawns = fetch_spawns(sorted({m["page_name"] for m in monsters}))
    drops = fetch_drops(sorted({m["page_name"] for m in monsters}))
    clog = fetch_collection_log_items()
    print(f"{len(clog)} collection log items")
    print(f"{len(drops)} monsters with drop tables")
    print(f"{len(superiors)} superiors, {len(spawns)} monsters with spawn points")
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text(json.dumps({
        "about": "Fetched by tools/fetch_wiki_monsters.py from the OSRS Wiki (CC BY-NC-SA 3.0).",
        "fetched": time.strftime("%Y-%m-%d"),
        "monsters": monsters,
        "superiors": [s for s in superiors if s not in league],
        "superior_of": bases,
        "superiors_league_only": league,
        "spawns": spawns,
        "drops": drops,
        "collection_log_items": clog,
    }, separators=(",", ":")))
    print(f"-> {OUT}")


if __name__ == "__main__":
    main()
