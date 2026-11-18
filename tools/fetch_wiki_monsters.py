#!/usr/bin/env python3
"""Downloads slayer monster facts from the OSRS Wiki into tools/data/wiki_monsters.json.

build_slayer_locations.py reads that file to add each task's monster variants (level, hitpoints, slayer XP,
attack styles, elemental weakness, where they spawn). Run this only when you want fresher wiki data; the
plugin itself never calls the wiki.

Data comes from the wiki's Bucket API (infobox_monster, locline and dropsline buckets, and the "Superior slayer monsters"
category). OSRS Wiki content is licensed CC BY-NC-SA 3.0, see NOTICE.md.

Usage: python3 tools/fetch_wiki_monsters.py
       python3 tools/fetch_wiki_monsters.py --attributes   (only refresh monster attributes, e.g. spectral)
       python3 tools/fetch_wiki_monsters.py --locations    (only refresh the place names of each page's spawns)
       python3 tools/fetch_wiki_monsters.py --combat       (only refresh defence levels, defence bonuses and size)
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent / "data" / "wiki_monsters.json"
API = "https://oldschool.runescape.wiki/api.php"
# The wiki asks automated clients to identify themselves
HEADERS = {"User-Agent": "combat-gear-organizer-datagen/1.0 (RuneLite plugin data build; "
                         "https://github.com/Kastier1/chard-rose-apple)"}
COORD = re.compile(r"x:\s*(\d+)\D+y:\s*(\d+)")
# "The shadow wyrm is a [[superior]] variant of the [[wyrm]]."
SUPERIOR_OF = re.compile(r"superior[^.]{0,80}?\b(?:variant|version|form|counterpart)s? of (?:the |an? )?\[\[([^\]|#]+)",
                         re.IGNORECASE)
# Superiors that only existed in a league ("was a variant ... in Trailblazer Reloaded League")
LEAGUE_ONLY = re.compile(r"\[\[[^\]]*League[^\]]*\]\]", re.IGNORECASE)
# What the gear-up DPS maths needs about each monster version (infobox_monster bucket)
COMBAT_FIELDS = ["combat_level", "hitpoints", "defence_level", "magic_level", "magic_attack_bonus", "stab_defence_bonus", "slash_defence_bonus",
                 "crush_defence_bonus", "magic_defence_bonus", "light_range_defence_bonus",
                 "standard_range_defence_bonus", "heavy_range_defence_bonus", "size"]
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


def fetch_attributes():
    """page name -> sorted monster attributes (demon, dragon, spectral, undead, ...) across all its versions."""
    attrs, offset = {}, 0
    while True:
        page = bucket("bucket('infobox_monster').select('page_name','attribute')"
                      f".where('slayer_category','!=',bucket.Null()).orderBy('page_name','asc')"
                      f".limit(500).offset({offset}).run()")
        for row in page:
            for a in row.get("attribute") or []:
                attrs.setdefault(row["page_name"], set()).add(a.strip().lower())
        if len(page) < 500:
            return {k: sorted(v) for k, v in sorted(attrs.items())}
        offset += 500
        time.sleep(0.5)


def update_attributes():
    """Refresh only the attributes in the existing data file, leaving everything else as it was fetched."""
    data = json.loads(OUT.read_text())
    data["attributes"] = fetch_attributes()
    OUT.write_text(json.dumps(data, separators=(",", ":")))
    spectral = sum(1 for v in data["attributes"].values() if "spectral" in v)
    print(f"{len(data['attributes'])} monsters with attributes ({spectral} spectral) -> {OUT}")


def fetch_combat():
    """[{page_name, version_anchor, defence_level, ...}, ...] for every slayer monster version, sorted."""
    rows, offset = [], 0
    select = ",".join(f"'{f}'" for f in ["page_name", "version_anchor"] + COMBAT_FIELDS)
    while True:
        page = bucket(f"bucket('infobox_monster').select({select})"
                      f".where('slayer_category','!=',bucket.Null()).orderBy('page_name','asc')"
                      f".limit(500).offset({offset}).run()")
        rows += page
        if len(page) < 500:
            break
        offset += 500
        time.sleep(0.5)
    out = [{k: r[k] for k in ["page_name", "version_anchor"] + COMBAT_FIELDS if r.get(k) is not None} for r in rows]
    # Some pages repeat a version name with other stats, so the whole row breaks ties
    return sorted(out, key=lambda r: (r["page_name"], r.get("version_anchor", ""), json.dumps(r, sort_keys=True)))


def update_combat():
    """Refresh only the combat stats in the existing data file, leaving everything else as it was fetched."""
    data = json.loads(OUT.read_text())
    data["combat"] = fetch_combat()
    OUT.write_text(json.dumps(data, separators=(",", ":")))
    print(f"{len(data['combat'])} monster versions with combat stats -> {OUT}")


def templates(text, name):
    """The bodies of every {{name ...}} template in a page's wikitext, nested templates and all."""
    out, start = [], re.compile(r"\{\{\s*" + re.escape(name) + r"\s*\|", re.IGNORECASE)
    for m in start.finditer(text):
        depth, i = 1, m.end()
        while i < len(text) and depth:
            if text.startswith("{{", i):
                depth, i = depth + 1, i + 2
            elif text.startswith("}}", i):
                depth, i = depth - 1, i + 2
            else:
                i += 1
        if depth == 0:  # skip a template left open at the end of a broken page
            out.append(text[m.end():i - 2])
    return out


def plain(wikitext):
    """"[[Ancient Guthixian Temple]]" -> "Ancient Guthixian Temple"; drops refs, templates and markup."""
    s = re.sub(r"<ref[^>]*/>|<ref.*?</ref>", "", wikitext, flags=re.DOTALL | re.IGNORECASE)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"<[^>]+>|'{2,}", "", s)
    # A template that was the only thing in brackets ("Brine Rat Cavern ({{Fairycode|dks}})") leaves "()"
    s = re.sub(r"\(\s*\)", "", s)
    s = re.sub(r"\(\s+", "(", re.sub(r"\s+\)", ")", s))
    return " ".join(s.split())


def loclines(text):
    """[[location name, [[x, y, plane], ...]], ...] from the {{LocLine}} templates in one page's wikitext."""
    out = []
    for body in templates(text, "LocLine"):
        location, plane = "", 0
        for part in re.split(r"\|(?![^{]*\}\})(?![^\[]*\]\])", body):
            key, _, value = part.partition("=")
            key = key.strip().lower()
            if key == "location":
                location = plain(value)
            elif key == "plane" and value.strip().isdigit():
                plane = int(value.strip())
        points = [[int(x), int(y), plane] for x, y in COORD.findall(body)]
        if points:
            out.append([location, points])
    return out


def fetch_spawn_locations(pages):
    """page name -> [[location name, [[x, y, plane], ...]], ...] from each page's {{LocLine}} templates.

    The locline bucket has the points but not the place name, so this reads the pages' wikitext.
    """
    found = {}
    for i in range(0, len(pages), 50):
        batch = pages[i:i + 50]
        cont = {}
        while True:
            d = get({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "format": "json",
                     "redirects": 1, "titles": "|".join(batch), **cont})
            # Key the result by the page name we asked for, through any title normalisation or redirect
            asked = {t: t for t in batch}
            for step in d["query"].get("normalized", []) + d["query"].get("redirects", []):
                for name, current in list(asked.items()):
                    if current == step["from"]:
                        asked[name] = step["to"]
            by_title = {current: name for name, current in asked.items()}
            for page in d["query"]["pages"].values():
                if "revisions" in page and page["title"] in by_title:
                    lines = loclines(page["revisions"][0]["slots"]["main"]["*"])
                    if lines:
                        found[by_title[page["title"]]] = lines
            # Big batches come back in parts
            if "continue" not in d:
                break
            cont = d["continue"]
            time.sleep(0.5)
        time.sleep(0.5)
    return {k: found[k] for k in sorted(found)}


def update_locations():
    """Refresh only the spawn place names in the existing data file, leaving everything else as it was fetched."""
    data = json.loads(OUT.read_text())
    data["spawn_locations"] = fetch_spawn_locations(sorted(data["spawns"]))
    OUT.write_text(json.dumps(data, separators=(",", ":")))
    print(f"{len(data['spawn_locations'])} monsters with named spawn places -> {OUT}")


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
    attributes = fetch_attributes()
    spawn_locations = fetch_spawn_locations(sorted(spawns))
    combat = fetch_combat()
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
        "attributes": attributes,
        "spawn_locations": spawn_locations,
        "combat": combat,
    }, separators=(",", ":")))
    print(f"-> {OUT}")


if __name__ == "__main__":
    if "--attributes" in sys.argv[1:]:
        update_attributes()
    elif "--locations" in sys.argv[1:]:
        update_locations()
    elif "--combat" in sys.argv[1:]:
        update_combat()
    else:
        main()
