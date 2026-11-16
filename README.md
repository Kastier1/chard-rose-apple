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

## Usage

1. Make sure the built-in **Bank Tags** plugin is enabled.
2. Put your gear in a tag (default name: `combat`, change it in the plugin config), either by tagging items in the
   bank as usual, or by wearing a setup and typing `::gearadd`.
3. Open the tag tab and pick **Auto layout: Combat gear** from its menu (right-click the tab, where *Disable
   layout* is). You get a preview with **Keep** / **Undo**.
   Or type `::gearsort` (or press the configured hotkey), which saves straight away; `::gearsort undo` puts the
   previous layout back.

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
- **Group by** – combat style or equipment slot
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
- **Drops** show as item pictures, with the stack size when a drop comes in a pile. Hover one for its drop rate,
  the better rate on task, whether it's in your collection log, and its value. Drops you haven't logged are faded
  with a dashed outline; logged ones have a small tick. Superiors and bosses show their notable drops the same way.
  Drop names are matched to items in the first few seconds after you log in; until then they show as text.
- **Task progress** shows under the kill count: kills per hour, about how long is left, slayer XP gained, superiors
  seen and the value of what task monsters dropped. Kills are counted from the task count going down, so any way of
  killing counts. Only time while logged in counts, and a gap of 5 minutes or more without a kill is left out of
  the rate. If the plugin started mid-task, the panel says which count it started from. Progress is saved per
  account, so it survives a relog, and starts again with each new task. When you finish, the panel shows a summary
  such as "Task done: 140 Abyssal demons in 24 min, 2 superiors, 1.2M loot" until your next task.

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
