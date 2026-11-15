# Combat Gear Organizer

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
Prayer   | prayer-bonus gear with no offensive stats
Utility  | other equipables (teleport jewellery, graceful, etc.)
Supplies | non-equipables you've tagged (potions, food, runes…), alphabetical
```

Within each slot your strongest item comes first, so best-in-slot is always top-left.

Switch **Group by** to *Equipment slot* if you'd rather have one row-block per slot (all weapons together,
all helms together, …), each ordered melee → ranged → magic.

## Usage

1. Make sure the built-in **Bank Tags** plugin is enabled.
2. Put your gear in a tag (default name: `combat`, change it in the plugin config), either by tagging items in the
   bank as usual, or by wearing a setup and typing `::gearadd`.
3. Type `::gearsort` (or press the configured hotkey). The tab is re-laid out and redrawn if it's open.

| Command | What it does |
| --- | --- |
| `::gearsort` | Organize the configured tag tab |
| `::gearsort <tag>` | Organize a different tag tab, e.g. `::gearsort slayer` |
| `::gearadd` | Tag everything you're wearing (any charge/degrade state) and re-organize |
| `::gearadd <tag>` | Same, for another tag |

Nothing in your actual bank is moved — this only changes the Bank Tags layout for that tab, so you can still drag
items around afterwards (re-running `::gearsort` will reset them).

## Config

- **Bank tag** – the tag tab to organize (default `combat`)
- **Group by** – combat style or equipment slot
- **Blank row between sections** – visual spacing between groups
- **Re-organize after ::gearadd**
- **Organize hotkey**

## How items are classified

Using RuneLite's item stats: the highest of melee (best attack + strength), ranged (ranged attack + ranged strength)
and magic (magic attack + magic damage) wins. Items with no offensive bonuses fall back to: prayer bonus → Prayer;
penalises magic *and* ranged (e.g. rune platebody) → Melee; penalises only magic → Ranged; otherwise Utility.

## Development

```bash
./gradlew run     # launches RuneLite with the plugin loaded
./gradlew test
```
