# Combat Gear Organizer

[![Build](https://github.com/Kastier1/chard-rose-apple/actions/workflows/build.yml/badge.svg)](https://github.com/Kastier1/chard-rose-apple/actions/workflows/build.yml)

Organizes your combat gear bank tag, and shows where to do your slayer task.

A RuneLite plugin that automatically arranges your combat gear **bank tag tab** into a clean, predictable layout.

It reads every item in your tag, works out what each item is for from its equipment bonuses, and writes a
Bank Tags *layout* so the tab looks like this (default "Combat style" grouping):

```
Melee    | weapons → shields → helms → bodies → legs → gloves → boots → capes → amulets → rings → ammo
(blank)
Ranged   | same slot order
(blank)
Magic    | same slot order
(blank)
Prayer   | prayer-bonus gear with no offensive stats or penalties
Utility  | void, slayer helmets, black masks, salve amulets, and other equipables (teleport jewellery, graceful…)
Supplies | non-equipables you've tagged (potions, food, runes…), alphabetical
```

Within each slot the item with the most damage comes first (strength counts double, defence only breaks ties).
It's a rough ordering from bonuses, not a DPS calculation: weapons like the Twisted bow, Tumeken's shadow and
Scythe of Vitur get a fixed bonus because their strength isn't in their stats.

Switch **Group by** to *Equipment slot* if you'd rather have one row-block per slot (all weapons together,
all helms together, …), each ordered melee → ranged → magic.

Switch it to *Gear set* to line up your armour sets: armour slots are rows (helms, bodies, legs, gloves, boots) and
each set is one column, so Bandos, Karil's or Ahrim's reads straight down. For example:

```
-                     Dharok's helm         Karil's coif          Ahrim's hood          Helm of neitiznot
Bandos chestplate     Dharok's platebody    Karil's leathertop    Ahrim's robetop       Fighter torso
Bandos tassets        Dharok's platelegs    Karil's leatherskirt  Ahrim's robeskirt
-                     -                     -                     -                     Barrows gloves
(blank)
weapons, shields, capes, amulets, rings, ammo and supplies: one row block each, as with Equipment slot
```

- Sets are found from item names (charged and degraded versions count), from a table in `GearSets.java`: Barrows,
  Bandos, Armadyl, Torva, Masori, Virtus, Ancestral, Inquisitor's, Justiciar, void, the moon sets, Sunfire fanatic,
  Proselyte, Crystal, Obsidian, d'hide and blessed d'hide, rune / dragon / god armour, Mystic, Infinity, Lunar,
  Dagon'hai, Elder chaos, Ghostly, Graceful, Spirit angler and others.
- Melee sets come first, then ranged, then magic (a set's style is that of most of its pieces), strongest first.
- Armour that isn't in a set, or is the only piece of its set in the tab, goes to the right of the sets in its own
  row. So does a second piece for a slot a set already has (a second void helm).
- More than 8 columns wrap into another block of rows below.
- Adding a weapon, jewellery or an odd piece of armour never moves a set. A new set, or a set getting stronger, can
  move the sets that come after it.

## Usage

1. Make sure the built-in **Bank Tags** plugin is enabled.
2. Put your gear in a tag (default name: `combat`, change it in the plugin config), either by tagging items in the
   bank as usual, or by wearing a setup and typing `::gearadd`.
3. Open the tag tab and pick **Auto layout: Combat gear** from its menu (right-click the tab, where *Disable
   layout* is). You get a preview with **Keep** / **Undo**.
   Or type `::gearsort` (or press the configured hotkey), which saves straight away; `::gearsort undo` puts the
   previous layout back.

### From the side panel

Click the plugin's button in the side bar and pick **Combat tab** at the top (next to **Slayer**). There you can:

- pick the **Bank tag** from your tag tabs, or type a new name;
- pick the **Layout**: *By style*, *By slot* or *By set* (the same as the **Group by** setting; changing one changes the other);
- click **Organize now** with your bank open. It opens the tab with the new layout and asks **Keep** / **Undo** in
  the chat box, like the Auto layout menu entry. It then shows a line like
  `48 items: 14 melee, 12 ranged, 10 magic, 12 other`;
- click **Add worn gear** to tag what you're wearing, like `::gearadd`;
- follow the **Setup** checklist: Bank Tags plugin on, tag tab exists, tab has gear in it, layout enabled. Each step
  is ticked when done, and says what to click when it isn't.

After `::gearsort` or the hotkey, the panel shows an **Undo** button, like `::gearsort undo`.

| Command | What it does |
| --- | --- |
| `::gearsort` | Organize the configured tag tab |
| `::gearsort <tag>` | Organize a different tag tab, e.g. `::gearsort slayer` |
| `::gearsort undo` | Put back the layout from before the last `::gearsort` (since the plugin started) |
| `::gearadd` | Tag everything you're wearing (any charge/degrade state); re-organizes too if that setting is on |
| `::gearadd <tag>` | Same, for another tag |

Nothing in your actual bank is moved — this only changes the Bank Tags layout for that tab, so you can still drag
items around afterwards. Re-sorting resets them, so use the Auto layout preview or `::gearsort undo` if you
change your mind. Items you untag drop out of the layout on the next sort.

## Config

- **Bank tag** – the tag tab to organize (default `combat`)
- **Group by** – combat style, equipment slot or gear set
- **Blank row between sections** – visual spacing between groups
- **Re-organize after ::gearadd** (off by default)
- **Organize hotkey**
- **Item value** – value items at their Grand Exchange price (default) or their high alchemy value. Used for
  every value the plugin shows, including a task's loot; changing it updates the panel straight away.
- **Task summary in chat** – when you finish a slayer task, also put the summary in your chat box (off by default).
- **Compact panel** – the slayer panel shows only the task, its progress, the route line and the places with their
  Go buttons; the monster details and drops are hidden (off by default).

## How items are classified

Using RuneLite's item stats:

1. Void, slayer helmets, black masks and salve amulets → Utility, so they stay together whatever their bonuses.
2. A weapon with 10+ magic attack → Magic (staves have big melee bonuses, but they're magic weapons).
3. Otherwise the highest of melee (best attack + strength), ranged (ranged attack + ranged strength) and magic
   (magic attack + magic damage) wins.
4. Items with no offensive bonuses: penalises magic *and* ranged (rune platebody, Bandos) → Melee; penalises only
   magic → Ranged; prayer bonus → Prayer; otherwise Utility.

For items tagged "any version" (as `::gearadd` does), the version you own is laid out, not the first version in
RuneLite's list (which is often the broken or uncharged one).

## Slayer task panel

A side panel (the enchanted gem icon) shows your current task, kills left, and every place you can do it.

- **Go** paths you to that place with the [Shortest Path](https://github.com/Skretzo/shortest-path) plugin.
  **Go to nearest** sends every place at once and Shortest Path picks the quickest to reach.
  Without Shortest Path, Go marks the place on your world map instead.
- After Go, a **route line** at the top says where you're headed ("On route to Catacombs of Kourend") with a
  **Cancel** button. It goes away when you arrive, when you set another path in Shortest Path, or when the task
  changes. Without Shortest Path it says "Marked on your world map", with a **Remove** button.
- The panel **remembers**, per account and across relogs, which drawers you opened, the monster you picked for each
  task, and the place you last chose with Go for each task (marked "Last time" when that task comes up again).
- **Konar**: only the place she assigned is shown.
- **Krystilia**: only her Wilderness places are shown, and the route is allowed through the Wilderness.
- **Other masters**: Wilderness places are hidden unless you turn on *Show Wilderness locations*.
- Places whose quests or skills you don't have yet are listed last with what you need, and **Go to nearest**
  skips them.
- Some places have no map point in the data yet; their Go button is greyed out.
- The **monster table** lists every monster that counts for the task, those at a listed place first. A monster's
  *Found at* row names its places, or says "Not at any of the places below" (for example a Wilderness place while
  *Show Wilderness locations* is off) or "No place in the data yet". Long names go onto two lines, and the level and
  XP columns widen for wide ranges (the table shows XP ranges in whole numbers, "600-2.1k"; the card has the exact
  range).
- **Bosses** that count for the task (Vorkath on Blue dragons, K'ril Tsutsaroth and Skotizo on Greater demons,
  Cerberus on Hellhounds, Obor on Hill giants, Scurrius on Rats...) are rows of the table too, after the regular
  monsters, marked with a crown (the tooltip says "Boss"). Click one for its own card: level, hitpoints, XP, drops,
  what to carry, *Best with what you have* and *Found at*. There's no prayer advice while a boss is selected. Each boss
  has its lair as a place, listed last: its Go takes you to where you walk in (Vorkath's ice chunks on Ungael, the
  crawl-through cave to Cerberus' Lair, the God Wars Dungeon entrance), and the card shows what you still need (91
  Slayer for Cerberus, Dragon Slayer II for Vorkath). **Go to nearest** never picks a lair. A Wilderness lair is on
  Krystilia's list when the boss counts for her, and a lair counts as Konar's area where the wiki says it does. A boss
  that doesn't count for your master isn't listed (the King Black Dragon on a Krystilia task, Eldric the Ice King on
  any ice giant task but Vannaka's, a lair outside Konar's area), and its loot isn't counted as task loot. With *Show
  Wilderness locations* off, a Wilderness boss stays in the table and its *Found at* says to turn the setting on. A
  boss's card leaves out the task's advice for the regular monsters (the *Attack* row and the *Weakness*, *They attack
  with* and *Bring* notes). Levels the wiki says can be boosted say so ("91 Slayer (boostable)" for Cerberus; 70
  Hitpoints for K'ril's river) and a boosted level opens the lair. The God Wars Dungeon bodyguards aren't listed:
  they're part of their general's fight, and nor is Kolodion, who can only be fought once, in the Mage Arena
  miniquest.
- Monsters that live away from every place in the slayer location guide (tormented demons for Greater demons,
  ice spiders, penguins...) get a place of their own made from their OSRS Wiki spawn points, such as
  "Tormented Demon (Ancient Guthixian Temple)". Its card says so. If the wiki names the spot like one of the task's
  places (mithril dragons in the Ancient Cavern), the monster joins that place instead. A place named like a guide
  place elsewhere (Ancient Cavern) takes that place's quest and skill needs, and the temple needs While Guthix
  Sleeps; otherwise the card says the data has no access details.
  **Go to nearest** only uses these places when no guide place can be reached.
- **Drops** show as item pictures, with the stack size when a drop comes in a pile. Hover one for its drop rate,
  the better rate on task, whether it's in your collection log, and its value. Drops you haven't logged are faded
  with a dashed outline; logged ones have a small tick. Superiors show their notable drops the same way next to their monster.
  Drop names are matched to items in the first few seconds after you log in; until then they show as text.
- **Carry** suggests the protection, prayer and storage items worth an inventory slot for the selected monster, and
  ticks the ones you already have in your inventory or worn:
  - **Dragonfire** for dragons that breathe fire (not baby dragons or drakes, whose breath you can step away from):
    an anti-dragon shield (or dragonfire shield, dragonfire ward or ancient wyvern shield) or any antifire potion or
    mix ticks it. A shield with antifire, or super antifire on its own, blocks a normal dragon's fire fully;
    Vorkath needs a shield and super antifire together.
  - **Icy breath** for wyverns: only an elemental, mind, dragonfire or ancient wyvern shield ticks it. An
    anti-dragon shield and antifire potions don't help against it.
  - **Bonecrusher** (or bonecrusher necklace) when it always drops bones
  - **Ash sanctifier** when it always drops demonic ashes (not Skotizo, whose ashes it doesn't take)
  - **Ectoplasmator** when it's spectral
  - **Herb sack** and **seed box** when herbs or seeds drop about once in 30 kills or more often
  - **Gem bag** (or gem sack) and **soul bearer** when uncut gems or ensouled heads drop about once in 50 kills or
    more often
  - **Looting bag** when every place shown is in the Wilderness

  Hover an item to see why, for example "Herb sack: Grimy herbs drop about 1 in 4 kills". The rules follow the
  OSRS Wiki, and the rates come from the bundled drop tables.
- **Best with what you have** names your best weapon per style (melee, ranged, magic) for the selected monster,
  with its expected damage per second and why, for example `Arclight 12.2 DPS (demonbane)` and under it
  `then Dual macuahuitl 10.4`. Hover a line for the max hit, hit chance, attack speed, ammo or spell, the slayer
  helmet or salve it assumes you wear, the rest of the gear it assumes, the next three weapons and the assumptions.
  - It uses the items in your bank from the last time you opened it (remembered per account, across relogs), plus
    your inventory and what you wear. Until you open your bank once it says "Open your bank once so the plugin can
    see your gear."
  - The maths is the OSRS Wiki's: max hit and accuracy against the monster's defence level and defence bonus for the
    attack type, its Magic level and magic defence for spells, and light, standard or heavy ranged defence for the
    ammo. Weapon speed comes from the item stats.
  - Fixed assumptions: your real levels with a super combat potion, ranging potion or magic potion, and Piety,
    Rigour or Augury; your other gear is your best piece per slot for that style (the same ranking as the bank
    tab); you're on task. Melee uses accurate or aggressive, whichever does more; ranged uses rapid; powered staves
    cast their own spell on accurate; other staves cast the best standard elemental spell (strike to surge) your
    Magic level allows, or Magic Dart from a staff that casts it. No special attacks, set effects, void, bolt
    effects or ancient spells.
  - Monster bonuses: demonbane (Arclight and Emberlight +70%, Darklight and Silverlight +60%, burning claws +5%,
    scorching bow +30%), dragonbane (dragon hunter lance +20%, crossbow +30% accuracy and +25% damage, wand +75% and +40%),
    the keris against kalphites, the leaf-bladed battleaxe against turoth and kurask, silver and blisterwood or
    Ivandis weapons against vampyres, Twisted bow scaling, Tumeken's shadow, the scythe's extra hits on big
    monsters, the dual macuahuitl's split hit and Osmumten's fang's double accuracy roll, and the spell's elemental
    weakness. A slayer helmet or black mask and a salve amulet don't stack: the salve's bonus replaces the
    helmet's, so the plugin wears whichever does more.
  - Weapons that can't hurt the monster are left out: only leaf-bladed weapons, broad arrows and bolts and Magic
    Dart for turoth and kurask; only silver weapons or silver bolts for tier 2 vampyres; only blisterwood and
    Ivandis weapons for tier 3. If none of yours of a style can, the line says what can.
- **Task progress** shows under the kill count: kills per hour, about how long is left, slayer XP gained, superiors
  seen and the value of what task monsters dropped. Kills are counted from the task count going down, so any way of
  killing counts. Only time while logged in counts, and a gap of 5 minutes or more without a kill is left out of
  the rate. If the plugin started mid-task, the panel says which count it started from. Progress is saved per
  account, so it survives a relog, and starts again with each new task. When you finish, the panel shows a summary
  such as "Task done: 140 Abyssal demons in 24 min, 2 superiors, 1.2M loot" until your next task.

**Preview any task:** type `::slayertask abyssal demons` to see a task in the panel as if you had it, for example
to plan ahead or to try the panel out. Add a master to see it the way they give it: `::slayertask lava dragons
krystilia`. A banner says it's a preview; click **Stop** or type `::slayertask off` to go back to your task. A
preview changes nothing in the game, and your real task's progress keeps counting underneath.

The task is read from the game and RuneLite's built-in Slayer plugin. Location data is bundled (see NOTICE.md),
so the panel makes no network requests.

## Development

```bash
./gradlew run     # launches RuneLite with the plugin loaded
./gradlew test
```

Regenerate the slayer location data with
`python3 tools/build_slayer_locations.py path/to/slayer-sensei/src/main/resources/slayer_areas.json`.
Use the Slayer Sensei commit pinned in `.github/workflows/build.yml` (see HANDOFF.md).

Every pull request and push to `main` runs the tests, and checks that the bundled slayer data still matches what
the generator makes from the pinned inputs.
