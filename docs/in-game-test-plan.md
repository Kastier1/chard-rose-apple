# In-game test plan

Everything in [Kastier1/chard-rose-apple#23](https://github.com/Kastier1/chard-rose-apple/pull/23) passes its tests
against the real RuneLite client, but none of it has been run while logged in. This list covers the things only the
real game can show. Tick each box, and note anything odd (a screenshot helps).

## 1. Start the developer client

Pick one way. Either way it's a separate RuneLite window with the plugin built in, next to your normal client. You
can't be logged in to the same character in both, so log out of your normal client first.

**A. The ready-made jar** (needs Java 11 or newer installed):

```bash
java -ea -jar combat-gear-organizer-1.0.0-all.jar --developer-mode
```

**B. From the code:**

```bash
git clone -b claude/loving-faraday-7nz9t9 https://github.com/Kastier1/chard-rose-apple.git
```

```bash
cd chard-rose-apple
```

```bash
./gradlew run
```

(On Windows: `gradlew.bat run`.)

**Logging in with a Jagex account:** click the Jagex account button on the sidebar, choose *Add Jagex Account*,
log in in the window that opens, pick your character, then *Play Now*. Next time, just pick the character.

**Set up once:**
- [ ] The built-in **Bank Tags** and **Slayer** plugins are on (they are by default).
- [ ] Install **Shortest Path** from the Plugin Hub in this client.
- [ ] Shortest Path's own setting **Avoid Wilderness** is on (needed for test 4).

## 2. Combat tab (bank organizer)

- [ ] Open the plugin's side panel (enchanted gem icon), then the **Combat tab** tab.
- [ ] The setup checklist matches reality. Follow any unticked step and watch it tick.
- [ ] With the bank open on your combat tag tab, click **Organize now**. The chat box asks **Keep / Undo**.
  - [ ] **Undo** puts the old layout back exactly.
  - [ ] **Keep** keeps the new one, and the summary line ("48 items: 14 melee, ...") looks right.
- [ ] Gear lands in sensible sections: staves under magic, Bandos and Justiciar under melee, void together.
- [ ] Best items come first in each slot: Fang before Elder maul, blowpipe before rune crossbow, Sanguinesti before
  Ahrim's staff.
- [ ] Wear a set and click **Add worn gear**. Items you wear show as themselves, not as a broken or uncharged
  version, and there's no greyed-out duplicate.
- [ ] Switch **By style** / **By slot** / **By set** in the panel; the plugin settings follow, and the other way
  round.
- [ ] **By set**: each set you have two or more pieces of (Bandos, Dharok's, Karil's, black d'hide, Ahrim's,
  Mystic...) reads straight down one column: melee sets, then ranged, then magic. Helms, bodies, legs, gloves and
  boots are the rows.
  - [ ] Odd pieces (Helm of neitiznot, Fighter torso, a lone Verac's helm, Barrows gloves) sit to the right of the
    sets in their own row.
  - [ ] A degraded or charged piece (Dharok's helm 100, Torva full helm (damaged)) still lines up with its set.
  - [ ] Weapons, shields, capes, amulets and rings follow below the armour, a row block each.
  - [ ] Tag one more odd item (a weapon, a ring, a helm) and organize again: no set changes column.
- [ ] `::gearsort` and `::gearsort undo` still work.

## 3. Slayer panel

Have a task, and open the **Slayer** tab.

- [ ] The task name, count and master are right. The count goes down with each kill.
- [ ] **Monsters** table: click a monster; its details change, and it stays selected after the next kill.
- [ ] **Greater demons** task (or `::slayertask greater demons`): Tormented Demon is in the table. Its *Found at* is
  "Tormented Demon (Ancient Guthixian Temple)", and that place card says "Spawn points from the OSRS Wiki".
  **Go** on it paths into the temple. **Go to nearest** still picks a normal greater demon place. On an account
  without While Guthix Sleeps the temple card says it needs that quest.
- [ ] **Metal dragons**: Mithril dragon's *Found at* is "Ancient Cavern". **Aviansies**: the three Armadyl
  bodyguards are found at "God Wars Dungeon".
- [ ] With *Show Wilderness locations* off, pick a monster that only lives in the Wilderness (e.g. Kolodion on Black
  demons): *Found at* says "Not at any of the places below". There's no *Also counts* drawer any more.
- [ ] **Drops** are item pictures. Hover one: rate, on-task rate, value.
- [ ] **Item value** setting: switch Grand Exchange ↔ High alchemy. Every value in the panel changes straight away,
  including task loot.
- [ ] **Carry** row: the suggestions make sense for the monster (ash sanctifier for demons, bonecrusher for
  bone droppers, ectoplasmator for spectral monsters, herb sack/seed box where they drop a lot).
  - [ ] Take a suggested item out of the bank: its icon gets a tick. Put it back: the tick goes.
  - [ ] Blue or metal dragon task: *Dragonfire* shows first, with an anti-dragon shield picture. Hover: bring a
    shield and/or antifire; Vorkath needs a shield and super antifire together. Either an anti-dragon shield or one dose of any antifire ticks it. Baby blue dragon and
    drakes have no *Dragonfire*.
  - [ ] Skeletal or Fossil Island wyvern task: *Icy breath* shows instead, with the ancient wyvern shield picture.
    An anti-dragon shield or antifire doesn't tick it; an elemental, mind, dragonfire or ancient wyvern shield does.
- [ ] **Best with what you have** (under *Carry*):
  - [ ] Before opening your bank on this account: it says "Open your bank once so the plugin can see your gear."
  - [ ] Open your bank, close it: one line per style you have a weapon for, best first, with DPS. Hover: max hit,
    hit chance, speed, ammo or spell, the gear it assumes ("With ..."), the next weapons, the assumptions. The
    tooltip wraps at a readable width rather than running across the screen.
  - [ ] Relog (or restart the client) without opening the bank: the lines are still there. Switch to another account:
    it asks you to open the bank again.
  - [ ] Greater demons with Arclight (or Emberlight) and a dual macuahuitl in the bank: the sword is first, marked
    *demonbane*.
  - [ ] A dragon task with a dragon hunter lance or crossbow: it's first in its style, marked *dragonbane*.
  - [ ] An undead task with a salve amulet (ei) and a slayer helmet (i): the tooltip names one of them, never both.
  - [ ] Kurask or turoth: only leaf-bladed weapons, broad ammo and Magic Dart staves are listed; with none it says
    what hurts them.
  - [ ] Put a weapon from the bank into your inventory and back: the lines don't change (it counts either way).
  - [ ] Compare one line with the wiki's DPS calculator (dps.osrs.wiki) using the gear the tooltip assumes: about
    the same number.
- [ ] **Task progress** after 5+ kills: kills/hour and time left look plausible, slayer XP matches the XP drops,
  loot value roughly matches what dropped.
  - [ ] Log out and back in mid-task: progress is still there.
- [ ] **Compact panel** setting: only the task, progress, route line and places remain.
- [ ] Open a drawer (e.g. *Weakness*), kill a monster, relog: the drawer is still open.

## 4. Going places (Shortest Path)

- [ ] **Go** on a place: Shortest Path draws the route, and the panel shows "On route to ..." with **Cancel**.
- [ ] Walk there: the route line goes away when you arrive.
- [ ] **Cancel** removes the route.
- [ ] **Go to nearest** picks a sensible place and skips places you can't enter yet (missing quest or level).
- [ ] **Wilderness (Krystilia task, or Show Wilderness locations on):** Go to a Wilderness-only place. A chat
  warning appears. Then, after arriving (or after Cancel), set a path to a bank yourself on the world map:
  **that route must avoid the Wilderness again.**
- [ ] Set your own Shortest Path target, then finish or change your task: **your own path stays.**
- [ ] Turn Shortest Path off: Go marks the place on the world map instead, with a Remove button.

## 5. Collection log

No task, or the wrong one? Type `::slayertask abyssal demons` (any task name works) to preview a task in the panel,
and `::slayertask off` to stop.

- [ ] Before searching, drops show no "missing" marks, only plain or ticked.
- [ ] Open the collection log, click **Search** (magnifying glass) and leave it open a second. Back in the panel,
  drops you haven't logged are faded with a dashed outline, and logged ones have a tick.
  - [ ] Check two or three by hand against your real log.
- [ ] Getting a new log item (if you're lucky) ticks it without another search.
- [ ] In someone else's house, look at their Adventure Log's collection log: your own marks don't change.

## 6. General

- [ ] No errors in the client log. In developer mode it's in the terminal you started it from, and in
  `~/.runelite/logs/client.log`. Search for `combatgearorganizer` or `Exception`.
- [ ] Turning the plugin off and on again, and switching accounts, leaves nothing stuck: no route line, no map
  markers, no stale task.

## Reporting back

For anything that fails, note which box and what you saw. A screenshot, plus any `Exception` lines from the log,
is enough to fix it.
