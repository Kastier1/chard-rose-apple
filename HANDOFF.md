# Handoff: Combat Gear Organizer (RuneLite plugin)

This file is everything a new Claude session needs to pick up the work. It was written at the end of a cloud
session that built the plugin. Read this first, then `docs/review-findings.md` and `docs/slayer-feature-scope.md`.

## The user and what they want

The user plays Old School RuneScape with RuneLite and uses a mix of slayer masters, including Konar and Krystilia.
Their goals, in their own order:

1. **Organize the combat gear bank tag tab** better. This is the part no existing plugin-hub plugin does well, and the
   main reason for the plugin.
2. **Slayer helper:** on getting a task, see the task's monster variants, each variant's locations and notable drops.
   Picking a location makes the **Shortest Path** plugin (Skretzo/shortest-path, plugin hub) path them there. Where
   there are several locations, they choose.
3. **Gear from the bank:** recommend the best outfit they own for the task. They mentioned GearScape, but it has no
   API. "Gear up" should also include task-required items (rock hammer, nose peg, spade…), the teleport item for the
   route, and supplies.

Decisions the user made:
- **Bundled data, not live wiki calls.** A later opt-in "Refresh from wiki" is acceptable. The optional "check in wiki
  DPS calculator" button may call the network when clicked.
- **Cover every slayer master.**
- **Gear up includes task items and supplies.**

The user likes seeing pictures of a design before or after building it (bank renders, panel renders). They want
honest status, and they test in their own RuneLite.

## Status

| Part | State |
| --- | --- |
| Bank organizer (`::gearsort`, `::gearadd`, hotkey) | Built, has real bugs (see below) |
| Slayer step 1: task panel, locations, Go / Go to nearest via Shortest Path | Built, has bugs (see below) |
| Slayer step 2: monster variants and notable drops | Not started |
| Slayer step 3: travel times via Shortest Path `query` | Not started |
| Slayer step 4: gear from bank, "Gear up" tag tab | Not started; plan needs rework (see review) |
| Slayer step 5: "check in wiki DPS calculator" button | Not started |
| Plugin hub submission | Blocked on `build=standard` and a LICENSE file |

**Nothing has been run in the real RuneLite client yet.** The cloud session couldn't download the RuneLite jar
(`repo.runelite.net` was blocked), so everything was compiled against hand-written stubs. Each stub was checked
against RuneLite's current source on GitHub, and a reviewer confirmed the stubs match. The first local job is to run it
for real.

Branch: `claude/loving-faraday-7nz9t9` on `Kastier1/chard-rose-apple`. No pull request yet.

## Running it locally

```bash
./gradlew run      # launches a developer RuneLite with the plugin loaded (main class: CombatGearOrganizerPluginTest)
./gradlew test     # unit tests (18 at handoff: classifier, layout builder, location picker + bundled data load)
```

The user's normal RuneLite can't load it; `./gradlew run` starts a separate developer client. In that client, turn on
the built-in Bank Tags and Slayer plugins, and install Shortest Path from the plugin hub for the Go buttons.

Watch-outs: the Gradle wrapper files came from Gradle 8.14.3, but `gradle-wrapper.properties` points at 8.10. If the
wrapper misbehaves, run `gradle wrapper --gradle-version 8.10` (or match the official
[example-plugin](https://github.com/runelite/example-plugin) template).

## Code map

```
src/main/java/com/combatgearorganizer/
  CombatGearOrganizerPlugin.java   plugin entry: commands, hotkey, organize(), slayer panel start/stop
  CombatGearOrganizerConfig.java   settings (tag name, grouping, blank rows, hotkey, slayer section)
  GearClassifier.java              item bonuses -> style (MELEE/RANGED/MAGIC/PRAYER/UTILITY) + power score
  LayoutBuilder.java               classified items -> Bank Tags layout array (8 columns, -1 = empty)
  GearSlot.java, GearStyle.java, GearItem.java, Bonuses.java, Grouping.java
  slayer/
    SlayerHelper.java              reads the task each game tick, drives the panel, routes via Shortest Path
    SlayerPanel.java               Swing side panel
    LocationPicker.java            which locations to show (Konar area, Krystilia Wilderness list, Wilderness toggle)
    ShortestPathBridge.java        PluginMessage("shortestpath", "path"/"clear") wrapper
    SlayerLocationData.java        Gson model of the bundled JSON
    SlayerTaskState.java, SlayerMasters.java, LocationOption.java
src/main/resources/com/combatgearorganizer/slayer/
  slayer_locations.json            generated, 102 tasks / 592 places / 488 with map points
  slayer_icon.png                  enchanted gem icon
tools/build_slayer_locations.py    regenerates the JSON from Slayer Sensei's slayer_areas.json
docs/slayer-feature-scope.md       slayer feature plan and decisions
docs/review-findings.md            full verified code review (47 findings with evidence and fixes)
NOTICE.md                          data attribution (CC BY-NC-SA 3.0 wiki-derived data)
```

### How things work

- **Bank organizer:** collects item ids from `TagManager.getItemsForTag(tag)` plus the existing layout, classifies
  each with `ItemManager.getItemStats`, builds an array with `LayoutBuilder`, and saves it with
  `LayoutManager.saveLayout(new Layout(tag, array))`. If that tag tab is open, it reopens it with
  `BankTagsService.openBankTag(tag, OPTION_ALLOW_MODIFICATIONS)` to redraw. Runs on the client thread.
  Plugin depends on core `BankTagsPlugin` and `SlayerPlugin` via `@PluginDependency`.
- **Slayer task:** read every tick from varps `SLAYER_COUNT` (394), `SLAYER_TARGET` (395), `SLAYER_AREA` (2096,
  Konar's area id), `SLAYER_COUNT_ORIGINAL` (4258) and varbit `SLAYER_MASTER` (4067; Krystilia = 7, Konar = 8).
  The name comes from the core `SlayerPluginService.getTask()`, falling back to the bundled data by task id.
- **Shortest Path:** documented cross-plugin API at
  https://github.com/Skretzo/shortest-path/blob/master/docs/Cross-plugin-communication.md.
  - Send `eventBus.post(new PluginMessage("shortestpath", "path", data))`, where `data.target` is a `WorldPoint` or a
    collection of them; with several, it picks the cheapest to reach.
  - `config` overrides user settings until `clear`. We use `avoidWilderness=false` for Wilderness-only places.
  - Targets can carry item `requirements`; Shortest Path then routes via the bank and highlights the items. Use this
    for task items.
  - `query` returns `cost` in ticks; use it for travel times.
- **World map fallback:** without Shortest Path, Go adds `WorldMapPoint` markers.

### Data source and licence

`slayer_locations.json` is generated from `slayer_areas.json` in
[Slayer Sensei](https://github.com/rRexhepi/slayer-sensei) (BSD-2 code). That data is derived from the OSRS Wiki, so it
is **CC BY-NC-SA 3.0**: keep the attribution in NOTICE.md and keep the file under that licence. Regenerate with:

```bash
git clone --depth 1 https://github.com/rRexhepi/slayer-sensei /tmp/slayer-sensei
python3 tools/build_slayer_locations.py /tmp/slayer-sensei/src/main/resources/slayer_areas.json
```

Slayer Sensei's `slayer_tasks.json` / `slayer_tasks_supplement.json` also hold per-task notes, required items,
weaknesses and notable drops (as prose). They're useful inputs for steps 2 and 4.

## Fix these first (verified review findings)

Full detail, evidence and suggested fixes are in `docs/review-findings.md`. In priority order:

### High

1. **`::gearadd` lays out the wrong item version.** It tags with `variation=true`, so TagManager stores
   `-ItemVariationMapping.map(id)`. That's the *first* id in the item's variation group, often a broken, inactive or
   uncharged item (Infernal cape broken 21287, Bow of faerdhinen inactive 25862, Gauntlet crystal helm 23886, empty
   blowpipe 12924). `collectItemIds` turns it back into that base id and both classifies and lays it out. Results:
   - ghost placeholders while the real item is worn
   - wrong stats used for sorting
   - "Remove-tag" can't remove the entry

   Fix: lay out and classify the concrete id the player owns (worn, in the bank, or in the existing layout).
   `LayoutManager` still matches other variations.
2. **Magic staves classify as MELEE.** Staves' crush and strength bonuses beat magic attack plus magic damage %.
   Classify weapons by magic attack or weapon category first.
3. **13 Krystilia tasks are missing from the data.** `build_slayer_locations.py` only loops over `taskLocations`, so
   tasks that are only on Krystilia's `wildernessTaskLocations` are dropped: Pirates, Black knights, Chaos druids,
   Revenants, Lava dragons, Green dragons, Dark warriors, Bandits, Ents, Rogues, Mammoths, Earth warriors, Magic axes.
   Loop over the union of task ids.
4. **Plugin hub blockers:** add `build=standard` to `runelite-plugin.properties`, and add a `LICENSE` file (BSD-2, like
   most hub plugins; the data file stays CC BY-NC-SA per NOTICE.md).

### Medium

- **Use RuneLite's AutoLayout API instead of overwriting.** `LayoutManager.registerAutoLayout(plugin, name,
  AutoLayout)` adds "Auto layout: <name>" to the tag tab menu, with a preview and Keep/Undo. Right now
  `::gearsort` and `::gearadd` overwrite manual layouts with no undo. Also default `organizeOnAdd` to false.
- **Duplicates and stale entries.**
  - An item tagged both normally and via `::gearadd` gets two slots, one a permanent ghost.
  - Untagged items stay in the layout forever, because `collectItemIds` re-adds every old layout id.
  - Fix: de-dupe by variation group, and keep old layout ids only if still tagged.
- **Classifier order.** The prayer check runs before the "penalises magic and ranged, so melee" check. As a result,
  Bandos/god armour, Justiciar, Verac's armour, spirit shields, Max cape and Elite void go to PRAYER. Regular void goes
  to UTILITY, which splits the set.
- **Power score is wrong for "best first".**
  - `defence/4` makes tank gear outrank DPS gear: Dragonfire shield above Avernic, Fury above Torture, Bulwark above
    Fang and Scythe.
  - Bows and crossbows have no strength of their own, so the Twisted bow ranks 9th among ranged weapons.
  - Weight strength and damage higher, drop defence, and handle ammo-fired weapons.
- **Go to nearest lifts Wilderness avoidance for every target** if any place in the list is Wilderness-only
  (`SlayerHelper.route` uses `|=`). Lift it only when all targets are Wilderness-only.
- **Krystilia fallback.** If her task has no Wilderness list, `LocationPicker` falls back to non-Wilderness places,
  where her kills don't count.
- **Dropped access requirements.**
  - The generator discards the source's `questsFinished`, `questsStarted` and `skills` fields, so places the player
    can't enter look routable (Iorwerth Dungeon, Lithkren, the Lighthouse, Slayer Tower without Priest in Peril).
  - It also ignores `entranceRoutable=false` (Wyrmscraig, Waterfall Dungeon, Scabaras Dungeon).
- **Boss tasks (id 98) and Venators have no data.**

### Low (selection)

- The Shortest Path override and `clear` can wipe a path the user set themselves.
- The "install Shortest Path" warning only refreshes when the task changes.
- Spria (master id 9) is missing from `SlayerMasters`.
- `Math.max` on the initial amount ignores Mortimer's negative modifier.
- Each style section is one flat run, so adding an item shifts later items. Consider an equipment-shaped "Loadout"
  grouping.
- The README promises "best-in-slot top-left", which isn't true until the power score is fixed.
- `docs/slayer-feature-scope.md` has wrong required items: brine rats need a spade, not boots of stone; boots of stone
  are for the Karuulm dungeon. About ten mandatory task items are missing from it.

## Recommended plan

1. **Run it locally** and fix anything the real client shows (startup errors, panel layout, layout redraw).
2. **Correctness pass:** all High and Medium items above. Add tests for each fix. The classifier tests can use real
   stats from https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/equipment.json (reference data
   only; don't bundle it, it's GPL-3.0).
3. **Equipment-shaped layout** for the combat tab, so positions are stable.
4. **Task items as Shortest Path `requirements`.** Make required items data (per task and location, "any of" item-id
   groups). This delivers most of goal 3 before any DPS work.
5. Before building variants, drops or gear-from-bank, **compare with All-In Slayer**
   (https://github.com/danieljglover/AIO-Slayer-Assistant). It already does bosses, variants, owned-gear loadouts, task
   items and Shortest Path. The unique value of this plugin is the organized combat tab, and "Gear up" built on it.
6. **Gear from bank, if built:**
   - Save a bank snapshot per account (`ConfigManager.setRSProfileConfiguration`, updated on `ItemContainerChanged`
     for the bank).
   - Search per weapon, not greedy per slot, since sets, slayer helm versus salve and two-handers interact.
   - Write the maths from the wiki's formula pages, not osrs-dps-calc's code (GPL).

## Existing plugins worth knowing

| Plugin | Repo | Overlap |
| --- | --- | --- |
| All-In Slayer | danieljglover/AIO-Slayer-Assistant | Almost all of goals 2–3 |
| Slayer Suite | pricecheckgg/slayer-suite | Task card, drop odds, ranked spots, routing, bank check |
| Slayer Sensei | rRexhepi/slayer-sensei | Locations and gear advice; source of our location data |
| SlayerBIS / Slayer Best in Bank / Slayer Loadout | iiRetroo/slayer-bis, FreeArcanes/slayer-best-in-bank, MikeN3/slayer-loadout | Best gear from bank |
| Bank Tag Layouts (core RuneLite since 2024) | runelite/runelite `banktags/tabs` | The layout system we write into |

## Conventions

- Java 11, tabs, Allman braces, Lombok (same as RuneLite and the example plugin).
- Pure logic (classifier, layout builder, location picker) stays free of RuneLite types so it can be unit-tested.
- Client-thread rules:
  - `ItemManager.getItemComposition` and `getItemStats` must run on the client thread.
  - Panel updates go through `SwingUtilities.invokeLater`.
  - Shortest Path messages are posted from the client thread.
- Never bundle Jagex art beyond what plugins normally use (item icons via RuneLite are fine).
- Keep NOTICE.md current whenever data from another source is added.
