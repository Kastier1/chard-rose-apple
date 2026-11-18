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
| Bank organizer (`::gearsort`, `::gearadd`, hotkey, Auto layout menu) | Built; High/Medium review findings fixed 2026-11-15 |
| Slayer step 1: task panel, locations, Go / Go to nearest via Shortest Path | Built; High/Medium review findings fixed 2026-11-15; bosses on normal tasks done 2026-11-18 (boss task id 98 still has no data) |
| Slayer step 2: monster variants and notable drops | Not started |
| Slayer step 3: travel times via Shortest Path `query` | Not started |
| Slayer step 4: gear from bank, "Gear up" tag tab | Not started; plan needs rework (see review) |
| Slayer step 5: "check in wiki DPS calculator" button | Not started |
| Plugin hub submission | `build=standard` and LICENSE added; not submitted |

**Update 2026-11-15 (local session):** the plugin now compiles and its 39 unit tests pass against the real RuneLite
1.13.6 jar (the stubs were right). It has not yet been clicked through in a logged-in client. Fixed in that session:

- Variation tags resolve to the version you own (`TagItemResolver`); no ghost base ids, no duplicates, untagged
  items drop out, Remove-tag works again.
- `AutoLayout` registered as "Auto layout: Combat gear" (preview + Keep/Undo); `::gearsort undo`;
  `organizeOnAdd` defaults to false; warns if Bank Tags is off.
- Classifier: staves are Magic; tank check before prayer; void / slayer helm / black mask / salve go to Utility.
- Power: strength ×2, defence only as tie-break, ammo strength for bows/crossbows/ballistae, fixed bonus for
  Twisted bow / Tumeken's shadow / Scythe.
- Data: the 13 Krystilia-only tasks (115 tasks now), `entranceRoutable=false` honoured, quest/skill requirements
  kept and checked in-game (listed last with "Needs …", skipped by Go to nearest), Wilderness places borrow the
  normal place's facts. `META-INF/NOTICE.txt` ships in the jar.
- Slayer runtime: Wilderness override only when every target is Wilderness; Krystilia never falls back to
  non-Wilderness places; name from data by id first (no stale tick), "Task #id" / "Boss task" instead of "No task";
  Mortimer's negative modifier; panel race; Shortest Path warning refreshes on PluginChanged; Spria.

**Update 2026-11-16 (issue #25):** drops in the slayer panel are item pictures with tooltips (rate, on-task rate,
stack, collection log status, value). Collection log status shows by shape: tick = logged, faded + dashed outline =
missing. New "Item value" setting (Grand Exchange / High alchemy); get values only through `ItemValues`. Drop names
become item ids via `ItemNameResolver`, which walks every item definition 2,500 per tick after login; a wiki name
with no exact match, like "Crawling hand (item)", falls back to the name without the brackets once the walk is done.
`ItemValues` values coins and platinum tokens at face value in both modes. Drops now carry an optional `quantity`
[low, high] from the generator. Not yet seen in a logged-in client.

**Update 2026-11-16 (issue #26):** task progress under the kill count: kills/hr, time left, slayer XP, superiors
and loot value (both GE and high alch are summed, so the "Item value" setting can change mid-task). Pure logic in
`TaskProgress` (numbers, summary text) and `TaskProgressTracker` (kill / new task / finish / relog decisions);
`SlayerHelper` feeds it varps, `StatChanged`, the superior game message and `NpcLootReceived` (task monster =
core Slayer targets or `TaskMonsters` name match). Saved as JSON per account in config group
`combatgearorganizerprogress`, key `task`, once per tick when changed. A count drop to 0 of more than 15 is taken
as a cancel, not a finish. Loot is taken for 10 ticks after the last kill; the optional chat summary goes out then.
Not yet seen in a logged-in client.

**Update 2026-11-16 (issue #27):** the slayer panel remembers open drawers (for every task), the monster picked per
task and the place last chosen with Go per task (shown as "Last time"). `PanelMemory` holds it; it is saved as JSON
per account in `combatgearorganizerprogress`, key `panel`, whenever the player changes it, and loaded with the
task progress when the account changes. After Go a route line shows at the top with Cancel (Shortest Path) or Remove
(world map markers); the Clear path button is gone. `ShortestPathBridge.isRouteActive()` goes false on arrival
(within 6 tiles, same plane), on clear, or when another path replaces ours; `SlayerHelper` polls it each tick and
pushes a `Route` to the panel with `showRoute`, which redraws only the route line. New "Compact panel" setting: task
header, kill rate line, route line and the place list only. Empty and waiting states are the constants at the top of
`SlayerPanel` (title plus one sentence; "Log in to see your task." while logged out). Part of #16: a kill (same task, the same places needing the same things,
nothing else changed) calls `SlayerPanel.updateCount`, which updates the count, progress lines and the XP for the
kills left without rebuilding the panel. Not yet seen in a logged-in client.

**Update 2026-11-16 (issue #28):** the side panel is now one panel for the whole plugin (`OrganizerPanel`, a
RuneLite `MaterialTabGroup` with "Slayer" and "Combat tab"); the navigation button belongs to the plugin, not
`SlayerHelper`, and `SlayerPanel` is a plain `JPanel` section. With the slayer panel setting off, the Slayer tab says
how to turn it on. `CombatTabPanel` (Swing only) has the tag picker (tabs from config `banktags.tagtabs`, editable),
By style / By slot radios, Organize now, Undo, Add worn gear, a note line, the summary line and the setup checklist.
Pure logic in `CombatTabStatus`, `SetupChecklist` (steps, why a button is off) and `OrganizeSummary`. The plugin reads
the status on the client thread when a `banktags` or plugin setting changes, on plugin changes, on game state changes
and when the bank opens or closes (polled per tick), and hands it to the panel only when it changed. Organize now does
what LayoutManager's "Auto layout" menu entry does: `BankTagsPlugin.openTag(tag, preview)` (the `BankTagsService` is
the plugin instance) and a `ChatboxPanelManager` Keep / Undo menu, `onClose` → `BankSearch.layoutBank()`. If the
service ever isn't `BankTagsPlugin`, it falls back to the old organize with Undo. Not yet seen in a logged-in client.

**Update 2026-11-18 (issue #32):** monsters that live away from every place in a task's list now get places of
their own. `tools/fetch_wiki_monsters.py --locations` adds `spawn_locations` (each page's `{{LocLine}}` location names
and points, read from the wikitext because the locline bucket has no location field) to `wiki_monsters.json`,
leaving the other keys as they were. The generator's `wiki_places` makes one place per wiki location name for regular
monsters whose spawns match no listed place (close spots merged, a monster in more than 3 spots gets one "several
places" place), with `source: "wiki"`, key `wiki_...` and a note. A spot named like one of the task's own places
joins that place instead (Fever spiders at Braindeath Island, whose only point is the entrance), also when the wiki
name adds a qualifier or drops a plural s ("Ancient Cavern (upper level)", "Underground Pass (Level 2)",
"Revenant Cave"; `name_keys`); one named like another task's guide place borrows its requirements, combat and
cannon. `WIKI_ACCESS` holds access checked on the wiki for spots no guide place covers (the Ancient Guthixian Temple
needs While Guthix Sleeps finished). Wilderness spawns stay in `wildernessPoints`, and go
on Krystilia's list too when she gives the task; a task only she gives gets only those, and drops monsters that never
spawn in the Wilderness (Chaos druid warrior, Elvarg). The build prints a warning for any regular monster still
without a place (none now). The panel's monster table lists every regular monster (those at a shown place first);
*Found at* says "No place in the data yet" or "Not at any of the places below"; the *Also counts* drawer is gone.
`LocationPicker.nearestTargets` leaves the wiki places out of Go to nearest unless no guide place can be reached.
Not yet seen in a logged-in client.

**Update 2026-11-18 (issue #33):** the Carry row suggests breath protection first. The generator's `carry_for` adds
`dragonfire` for any monster with the wiki attack style "Dragonfire" (not drakes, whose breath can be stepped away
from) and `wyvernShield` for "Icy breath" (wyverns). `CarryItem.DRAGONFIRE` ticks for any anti-dragon shield,
dragonfire shield or ward, ancient wyvern shield, or any dose of an antifire potion or mix; `WYVERN_SHIELD` only for an
elemental, mind, dragonfire or ancient wyvern shield (wiki Dragonfire page). Ids are checked against RuneLite's item
variation groups in `CarryItemTest`. Tooltip reasons can hold `\n`, shown as line breaks. The Dragonfire tooltip says
Vorkath needs a shield and super antifire together (the wiki table: super antifire alone still lets 60 through, a
shield with regular antifire 10). Not yet seen in a logged-in client.

**Update 2026-11-18 (issue #31):** new layout choice *By set* (`Grouping.BY_SET`, "Gear set" in the settings, a third
radio in the Combat tab panel). `LayoutBuilder` puts armour (head, body, legs, hands, feet) in a grid: rows are slots,
columns are sets, then the armour in no set to the right in its row (style, then power order); columns past 8 wrap
into another block (blank row between if that setting is on); every other slot then gets its own rows as in By slot.
Sets come from `GearSets`, a prefix table (whole words, longest prefix wins, names normalized: no apostrophes,
"dragonhide" = "dhide"); `GearItem.set` holds it, and the plugin also tries the name of the item's variation group
base. A set needs pieces for two slots in the tab; its column is its strongest piece per slot, and a second piece for
a slot goes with the unsetted armour so it can't widen the set. Sets are ordered by style (majority of the column;
tie goes to melee, ranged, magic order) then strength (sum of power). Adding a weapon, jewellery or odd armour never
moves a set; a new set or a stronger set can move the sets after it. Not yet seen in a logged-in client.

**Update 2026-11-18 (issue #34):** the monster card has a *Best with what you have* section: the player's best
weapon per style for the selected monster by expected DPS, with the runner-up and a tooltip (max hit, hit chance,
speed, ammo or spell, slayer helmet or salve worn, next three, assumptions). Pure maths in the new `dps` package:
`CombatMath` (the wiki's formula pages, cited in comments), `WeaponRules` (demonbane, dragonbane, keris, leaf-bladed,
silver and vampyrebane, powered staff max hits, by item name), `RangedAmmo` (ammo kind, light/standard/heavy defence,
which ammo fits which bow or crossbow), `StandardSpells`, and `WeaponRanker`, which holds the rest of the gear simple:
the best owned piece per slot for the style by `GearClassifier.power`, plus a slayer helmet / black mask or salve when
that does more (they don't stack; salve wins when it applies). Assumptions are in `GearUp.ASSUMPTIONS`. `BankGear`
remembers the bank's wearable item ids per account (config group `combatgearorganizerbank`, key `items`, written to
the profile it was read for, placeholders left out) and adds inventory and worn; `SlayerHelper.updateGear` turns the
ids into `GearPiece`s on the client thread and hands an `OwnedGear` to the panel, which ranks on the EDT and caches the
rows per monster. Monster defence stats come from `tools/fetch_wiki_monsters.py --combat` (key `combat` in
`wiki_monsters.json`) and are bundled per variant as `combat` (most common version; zeros left out) with
`attributes`. Not yet seen in a logged-in client.

**Update 2026-11-18 (bosses as variants):** every boss that counts for a normal task is a row of the monster table,
after the regular monsters, with a crown icon and a "Boss: ..." tooltip (`SlayerPanel.BOSS_CROWN`, `bossTooltip`), and
its normal card; the separate "Boss" line under the card is gone (superiors keep theirs). The list is the documented
`BOSSES` table in `tools/build_slayer_locations.py` (wiki citations at each `BOSS_LAIRS` entry): 32 bosses over 27
tasks, including ones the wiki's "Bosses" category misses (Skotizo, Obor, Bryophyta, Scurrius, Brutus, the Royal
Titans) and ones with no wiki spawns (Grotesque Guardians, from Dusk's page; Calvar'ion, Spindel, Shellbane gryphon).
A wiki "Bosses" monster not in the table is left out (Zulrah, the DT2 bosses: boss tasks only), and
`left_out_bosses` prints any that turn up in a task's category. Each boss's only place is its lair (`source: "boss"`,
key `boss_...`, `LocationEntry.isBossLair`, `LocationOption.bossLair`): the walk-in point (never instance tiles), its
Slayer level and quests as requirements, the rest (diaries, keys, kill count) in the note, `areaId` copied from the
guide place where the wiki says the boss counts for Konar there, and a copy on Krystilia's list for Wilderness bosses
that count for her (not the King Black Dragon). The guide's empty "Ungael" place gives way to Vorkath's lair
(`replaces`). Lairs sort last in `LocationPicker.pick`, and `nearestTargets` never returns one. `Variants.listed`
and `Variants.at` include bosses after the regulars, so the lair card names its boss and its Go selects it; a boss
is never the default. The looting bag for a boss depends on its own lair. God Wars Dungeon bodyguards (`GWD_MINIONS`)
are dropped from every task, which also removed the "God Wars Dungeon (Zamorak boss room)" wiki place; their loot is
only counted for the task when the core Slayer plugin targets them. PvM Arena pages and quest-only versions (Vorkath's
Dragon Slayer II fight, Amoxliatl's quest fight, Brutus's "Bull") no longer widen level, HP and XP ranges. Go targets
for Calvar'ion (Skeletal Tomb) and Spindel (Web Chasm) are RuneLite world map icons, the others wiki or Shortest Path
tiles; Kraken, Alchemical Hydra, the Dagannoth Kings and the GWD generals go to the dungeon entrance because Shortest
Path can't path the last stretch. Not yet seen in a logged-in client.

**Update 2026-11-18 (bosses as variants, review round 1):** `Variants.counting(task, masterId, konarAreaId)` drops
the bosses that don't count for the shown master: a variant's `masters` (only these; Eldric: Vannaka) and
`notMasters` (KBD: Krystilia; Branda: Konar, Krystilia), from `BOSSES`, and any boss whose lair isn't among
`LocationPicker.pick(task, master, area, true)` (outside the Wilderness for Krystilia, outside Konar's area).
`SlayerHelper` hands the panel only those, and the loot fallback ignores the left-out ones (`Variants.notCounting`,
`TaskMonsters.matches(..., notCounting)`), also when the core Slayer plugin targets them. A listed boss whose lair is
hidden (Wilderness setting off) says `Variants.BOSS_IN_WILDERNESS`. A boss's card has no task-wide *Attack* chips
and no *Weakness* / *They attack with* / *Bring* drawers. Lair requirements can list `boostable` skills (Cerberus
Slayer, K'ril Hitpoints, per the wiki); `RequirementChecker.meets` takes the boosted level for those and the text says
"(boostable)". Kolodion is left out (`ONE_TIME_FIGHTS`: once, in the Mage Arena miniquest), Scorpia's offspring live
at Scorpia's lair (`AT_LAIR`), and the KBD lair `replaces` the guide's "King Black Dragon Lair" (the black dragons
there were the Lava Maze Dungeon's, `NOT_AT`). The Abyssal Nexus note says a first visit to the Abyss is needed. The
monster table sizes its level and XP columns to the widest value (`Variants.xpEachShort` for the table), and wraps
names to fit (`SlayerPanel.wrapName`, `TABLE_ROW_WIDTH`); `noTextIsCutOffAtTheSidebarWidth` now also measures
plain-text labels. Not yet seen in a logged-in client.

**Update 2026-11-18 (bosses as variants, review round 2):** place requirements can list `diaries`
(`Diaries.VARBITS`: `WILDERNESS_EASY`..`WILDERNESS_ELITE`, read from the `*_DIARY_*_COMPLETE` varbits; the generator's
`DIARIES` must match); the six Wilderness boss lairs need the medium or hard Wilderness Diary (the wiki: a bear,
skeleton or spider task doesn't bypass it), and their note says the 50,000 coin fee is shared by all the lairs.
`BoostWatch` keeps the boosted levels of every skill some place lists as `boostable` (Slayer, Hitpoints); when one
changes, `SlayerHelper.refresh` recomputes the shown places and redraws if any opened or closed. The fetcher's
`EXTRA_DROP_PAGES` (`--extra-drops` refreshes only those) adds the drop tables of "Grotesque Guardians" (the boss's
`dropPages` in `BOSSES`; Dusk's page has none) and "Unsired"; the generator's `DROP_CONTENTS` lists what comes out
of an Unsired on the Sire at the Unsired's rate times the reward's. A boss's *Found at* ends with
`SlayerPanel.BOSS_GO_HINT`, and Go to nearest's tooltip (`NEAREST_TIP`) says it skips boss lairs. The cut-off test
measures labels with a fixed preferred size (the table's wrapped names) by their UI's own preferred size. The
Porazdir comment now gives the real reason he's left out (he only appears for the Mage Arena II errand), not "once".
Not yet seen in a logged-in client.

**Update 2026-11-18 (bosses as variants, review round 3):** every boss lair's Slayer level is `boostable` (wiki
"Temporary skill boost/Slayer"; Araxxor's task page: "boost from level 87 Slayer") except the Kraken's ("cannot be
boosted"): the generator checks this against `UNBOOSTABLE_SLAYER_LAIRS`. Obor's and Bryophyta's notes say the key is
needed only for the first entry and the loot chest (they respawn without one; the generator rejects "key per kill"),
and Bryophyta's gate is east of the moss giants. `BOSS_GO_HINT` shows only when the boss's lair card is listed (not for a
Wilderness boss with *Show Wilderness locations* off). Not yet seen in a logged-in client.

**Update 2026-11-18 (render fixes: table gaps, unlinked places, greyed Go, monster line):**
- *Monster table:* each number column is its widest value (header included) plus `SlayerPanel.COLUMN_GAP` (6px), the
  Task column too, so right-aligned neighbours never touch (Black demons read "172-188157-200"). Rows are named
  `TABLE_ROW`; `SlayerPanelCardsTest.monsterTableColumnsNeverTouch` checks the text gap and cell bounds of every row
  for every task, master (and Konar area), Wilderness setting and kill count left.
- *Places no monster was linked to* (117 of them, Chasm of Fire for black demons and Brimhaven Dungeon for greater
  demons among them; not a regression, f152a69's JSON had the same gaps). The generator matched variants to places by
  distance only, and only joined by the wiki's spot name for monsters that matched no place at all; an entrance-only
  place far from the spawns (Chasm of Fire: 185 tiles underground), spawns on another plane (Brimhaven's greater
  demons upstairs) or a guide row with no points never matched. Now `variants_for` also links every regular monster to
  the places its `{{LocLine}}` spot names match (`named_places` / `own_places`: `name_keys`, which also drops a bare
  floor like "upper level", else the same words in any order or a word subset of two or more words, "Kingstown -
  Councillor Hughes' house"). Wiki-place creation still joins by exact name only. A guide place still unmatched goes
  to the task's only regular monster when it has one (`link_unmatched`); otherwise it is marked `noMonster` (38
  places, e.g. Blue dragons' Dragon Nest: the wiki's blue dragon pages have no spawn there, its red dragon pages do)
  and the card says `NO_MONSTER_HERE`. `validate()` and `everyPlaceListsAMonsterThatLivesThere` fail on any other
  unlinked place. (Round 1 below corrects the Dragon Nest example and the three tasks said to have no monster.)
- *Greyed-out Go:* Go is greyed out only when the place has no map point (`SlayerPanel.goEnabled`; Heroes' Guild
  basement and Crash Site Cavern have none), never for a requirement. The card now says `NO_MAP_POINT` (also in
  compact mode); before, only the tooltip did. Requirements show as "Needs ..."; `SlayerHelper` re-checks them on the
  10 ticks after each login and every 25 ticks, so a quest state read before the server sent it doesn't stick.
- *Monster line on cards:* the rule was already "the selected monster in orange" (the Greater demons render had
  K'ril selected); it is now also underlined, with a tooltip (`SELECTED_MONSTER_TIP`), so it reads without colour.
  `theSelectedMonsterIsMarkedOnEveryCardThatListsIt` checks every task and every listed monster.

**Update 2026-11-18 (table gaps and place links, review round 1):**
- *Bare LocLine points.* Some `{{LocLine}}`s write points as `|1230,9491|...` (Blue dragon at the Dragon Nest); the
  locline bucket returns them as `"1230,9491"`. `fetch_wiki_monsters.py` dropped those (`COORD` wanted `x:..,y:..`);
  it now also takes `BARE_COORD`, and the new `--spawns` flag refreshes only `spawns` and `spawn_locations` (done
  2026-11-18: 41 pages gained points, none lost any). Blue and Baby blue dragons now live at the Dragon Nest (no longer
  `noMonster`; 35 such places now, e.g. Elves at the Prifddinas gates), and Custodian stalkers, Sourhogs and Warped
  creatures have regular monsters with places.
- *Spawn points for empty places.* `fill_from_spawns` gives a non-lair place with no map point the points of its
  linked monsters' wiki spots named the same (`find_by_name`): Heroes' Guild basement, Crash Site Cavern, Warriors'
  Guild basement, Goblin Village, Kurask Lair and 49 more. Word-subset matches stay link-only; instance and
  quest-only spots (`NOT_A_GO_SPOT`) and `entranceRoutable=false` places (Waterfall Dungeon) stay without points.
- *Go to nearest* (`LocationPicker.nearestTargets`) falls back to gated places when no open one has a point, so a
  missing or not-yet-loaded requirement never greys it out; a line under it (`SlayerPanel.NEAREST_REASON`) says
  `NEAREST_GATED` (orange) or, when it's greyed out because no non-lair place has a point, `NEAREST_NO_POINT`.
  `aGreyedOutGoAlwaysSaysWhyOnTheCard` checks it for every task, and finds the "Needs ..." line by name
  (`CARD_NEEDS`), not by a text prefix that place notes share.
- `NO_MAP_POINT` and `NO_MONSTER_HERE` are light grey (about 6.8:1 on the card, was about 2:1);
  `onCardReasonsAreLegible` checks 4.5:1.
- Left as is: prose notes that start with "Needs" (Brimhaven's "Needs 10 Woodcutting to cut the vines") are Slayer
  Sensei's notes, not structured requirements; making them requirements needs the wiki checked per place.

**Update 2026-11-18 (table gaps and place links, review round 2):**
- *Places only named beside a spot.* `own_places` no longer takes a word-subset match whose extra words place one
  name relative to the other (`RELATION_WORDS`: north/south/east/west, between, outside, path, towards...; `same_spot`),
  unless they say which part (`PART_WORDS`: "north entrance", "north side") or the shorter name is the longer one's head
  ("Mount Karuulm (north side)"). Such a match is kept only when the spot is within `NEAR` of the place's points or of
  another monster's spot named like the place (`beside_places`: the grizzly bear's "Land's End" is 3 tiles from the bear
  cub's "North of Land's End"). Dad and the Troll spectators ("Troll Arena", surface) are no longer in the
  "Tunnel between Troll arena and Trollheim" (Mountain troll only).
- *Hand links with evidence* (`SPOT_PLACES`: task and place -> monster, wiki spot, its points, why; checked against
  `wiki_monsters.json`, and the place takes the points when it has none): the three Dark Altar paths (Bats) get the
  Giant bat's "Dark Altar" spawns on their side of the altar, so Go works there; Krystilia's Hellhounds place
  "North-east of the Deserted Keep" gets the Hellhound and the 6 "Outside of the Wilderness Resource Area" points (was
  `noMonster` with a dead Go). Skeletons' "Southern Wilderness mine" stays `noMonster`: the Skeleton page's mine spot is
  her list's own "South-west Wilderness mine".
- *`noMonster` is a reviewed list now* (`NO_MONSTER_PLACES`: task and place -> evidence, 34 places). `link_unmatched`
  marks only those, bundled with `noMonsterWhy`; `validate()` fails on any other unmatched place, on an entry that is
  matched or gone, and on a stale `SPOT_PLACES` entry. `everyPlaceListsAMonsterThatLivesThere` checks every mark has
  its reason (cap 34).
- *Krystilia's copy of a "several places" wiki card* names only its Wilderness spots (Ice spider: "Ice spider (Frozen
  Waste Plateau)"; the note no longer lists the Ice Queen's Lair, Temple of Ikov and other caves where kills don't count
  for her). Tests: 298.

**Update 2026-11-18 (table gaps and place links, review round 3):**
- *Five `NO_MONSTER_PLACES` entries were wrong* (the bundled wiki data has the monster there); they are `SPOT_PLACES`
  now, with points, so the card names the monster and Go works: Birds' Farmer Fred's chicken pen (Chicken "Lumbridge
  West Farm"), Dwarves' Falador near the Mining Guild ladders (Dwarf "Surface of Mining Guild", plane 0), Ghosts' Temple
  in The Golem (Ghost "Ruins of Uzer Temple Basement"), Skeletons' Tarn's Lair, Abandoned Mine (Skeleton (Tarn's Lair)
  "Abandoned Mine (Level 1)"; `tarns_lair` is the other half) and Skeletons' Southern Wilderness mine (the 5 Skeleton
  "Wilderness, north of Edgeville" points at about 3105,3569; not her "South-west Wilderness mine" at about 3014,3589).
  29 `noMonster` places now (`everyPlaceListsAMonsterThatLivesThere` caps 29); `handLinkedPlacesHaveTheirMonsterAndAPoint`.
- *Monster line spacing.* The selected monster's underline took the rows between lines: the RuneScape small font's
  lines have no gap, so it touched the wrapped line and the line under the label. The mark is now `SELECTED_MARK` (a
  dot before the name, within the letters' height; tooltip `SELECTED_MONSTER_TIP`), and `SlayerPanel.monsterLine` wraps
  the names itself (`FontMetrics`, no-break spaces inside a line) into `<div>`s with `MONSTER_LINE_GAP` (1px) between
  them, plus a 1px bottom border on the label (Swing's HTML ignores `line-height`; bold is a taller face and touched
  too). `theMonsterLineNeverTouchesTheLinesAroundIt` paints every marked card label: one band of ink per line and a clear
  row above the next line (117 failures with the underline back).
- *Requirement re-check tested.* The schedule moved from `SlayerHelper` into `RequirementRecheck` (`onLogin`, `due`,
  `needsRedraw`; same behaviour: each of the 10 refreshes after LOGGED_IN, then every 25th). `RequirementRecheckTest`
  drives it with quests that read as not done until tick N: redrawn on that tick within the login window, at the next
  periodic check after it, never otherwise. Not covered: that `onGameStateChanged` calls `onLogin` (SlayerHelper needs
  the injected client services). Tests: 304.

Still open from the review: Venator data, the low-severity Shortest Path `clear` items, ammo-slot grouping,
hybrid items, per-slot rows (the "Loadout" grouping; By set covers armour only), and the scope doc's wrong required items.

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
  OrganizerPanel.java              the one side panel: Slayer / Combat tab switcher
  CombatTabPanel.java              Swing: Combat tab section (tag, layout, Organize now, checklist)
  CombatTabStatus.java, SetupChecklist.java, OrganizeSummary.java  its pure logic
  ItemValues.java, ItemValueMode.java  the one place to ask what an item is worth (GE or high alch setting)
  EquipmentBonuses.java            RuneLite item stats -> Bonuses
  dps/                             pure DPS maths: CombatMath, WeaponRules, RangedAmmo, StandardSpells, WeaponRanker
  slayer/
    SlayerHelper.java              reads the task each game tick, drives the panel, routes via Shortest Path
    ItemNameResolver.java          drop name -> item id, by walking item definitions a batch per tick
    DropItems.java, DropSlot.java  drop ids/values handed to the panel; one item picture with its log status
    BankGear.java, GearUp.java     remembered bank gear per account; the "Best with what you have" rows
    SlayerPanel.java               Swing: the Slayer section of the side panel
    LocationPicker.java            which locations to show (Konar area, Krystilia Wilderness list, Wilderness toggle)
    ShortestPathBridge.java        PluginMessage("shortestpath", "path"/"clear") wrapper
    SlayerLocationData.java        Gson model of the bundled JSON
    SlayerTaskState.java, SlayerMasters.java, LocationOption.java
    PanelMemory.java, Route.java   what the panel remembers per account; the route line after Go
    TaskProgress.java, TaskProgressTracker.java, TaskMonsters.java  task kill rate, XP, superiors, loot, summary
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
git clone https://github.com/rRexhepi/slayer-sensei /tmp/slayer-sensei
git -C /tmp/slayer-sensei checkout 5528412856f36f0c9efb9b9c5c164f526406c2c9   # pinned; see below
python3 tools/build_slayer_locations.py /tmp/slayer-sensei/src/main/resources/slayer_areas.json
```

The generator also reads the committed `tools/data/wiki_monsters.json`; it makes no network calls.

**CI check.** `.github/workflows/build.yml` runs `./gradlew build` (Java 11, Temurin) and a `slayer-data` job on
every pull request and push to `main`. That job checks out Slayer Sensei at the commit in `SLAYER_SENSEI_COMMIT`
(`5528412856f36f0c9efb9b9c5c164f526406c2c9`, which reproduces the committed file byte for byte), reruns the
generator and fails on `git diff --exit-code`. To take newer Sensei data, or after changing the generator or
`wiki_monsters.json`, regenerate locally, commit the new `slayer_locations.json` and bump `SLAYER_SENSEI_COMMIT` in
the same pull request. Refresh `wiki_monsters.json` with `tools/fetch_wiki_monsters.py` locally only; CI never calls
the wiki.

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
- **Boss tasks (id 98) and Venators have no data.** (Bosses that count for normal tasks are done: see the
  2026-11-18 update above.)

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
