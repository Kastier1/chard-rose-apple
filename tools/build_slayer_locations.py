#!/usr/bin/env python3
"""Builds src/main/resources/com/combatgearorganizer/slayer/slayer_locations.json.

Input: slayer_areas.json from Slayer Sensei (https://github.com/rRexhepi/slayer-sensei),
which was written from OSRS Wiki pages (CC BY-NC-SA 3.0) and RuneLite world map data (BSD-2).
See NOTICE.md.

Usage: python3 tools/build_slayer_locations.py path/to/slayer_areas.json
"""
import json
import sys
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


def main(src):
    data = json.loads(Path(src).read_text())
    locations = {l["key"]: l for l in data["locations"]}
    wild = {t["taskId"]: t for t in data["wildernessTaskLocations"]}

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
            notes = [n for n in (loc.get("note"), access_note(loc.get("access")), per_task.get("note"), access_note(per_task.get("access"))) if n]
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
            notes = [n for n in (loc.get("note"), access_note(loc.get("access")), per_task.get("note")) if n]
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
        })

    tasks.sort(key=lambda x: x["name"].lower())
    out = {
        "about": "Generated by tools/build_slayer_locations.py from Slayer Sensei's slayer_areas.json "
                 "(OSRS Wiki, CC BY-NC-SA 3.0; RuneLite world map data, BSD-2). Points are [x, y, plane].",
        "tasks": tasks,
    }
    OUT.write_text(json.dumps(out, separators=(",", ":")))
    every = [l for t in tasks for l in t["locations"] + t["wildernessLocations"]]
    routable = sum(1 for l in every if l["points"] or l["wildernessPoints"])
    total = len(every)
    print(f"{len(tasks)} tasks, {total} locations ({routable} with map points) -> {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
