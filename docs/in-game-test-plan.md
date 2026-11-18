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
- [ ] **Greater demons**: K'ril Tsutsaroth and Skotizo are rows at the bottom of the table, each with a crown; hover
  says "Boss". There's no Tstanon Karlak (or any God Wars Dungeon bodyguard) and no "God Wars Dungeon (Zamorak boss
  room)" place. **Go to nearest** never picks K'ril's or Skotizo's lair.
- [ ] **Metal dragons**: Mithril dragon's *Found at* is "Ancient Cavern". **Aviansies**: only Aviansie and Kree'arra
  are listed (no Wingman Skree, Flockleader Geerin or Flight Kilisa).
- [ ] **Blue dragons**: Vorkath is the last row. Click it: Level "732 · 750 HP", Drops, Carry (dragonfire), *Best with
  what you have*, *Found at* "Ungael (Vorkath)", and no *Pray* row or *Prayer* drawer. The card has no separate
  "Boss" line. The "Ungael (Vorkath)" card is last in the place list; on an account without Dragon Slayer II it says
  "Needs Dragon Slayer II". Its **Go** paths to Rellekka's boat to Ungael and on to the ice chunks, and selects
  Vorkath. Go from a blue dragon place switches back to a dragon.
- [ ] Boss lairs whose Go should end where you walk in (check each once): Cerberus (the crawl-through cave in
  Taverley Dungeon's hellhound area), Abyssal Sire (fairy ring DIP), Grotesque Guardians (Slayer Tower roof
  entrance), Thermonuclear smoke devil (the crevice in the Smoke Devil Dungeon), Kalphite Queen (the tunnel down in
  the Kalphite Lair), Skotizo (the Catacombs altar), Obor and Bryophyta (their gates), Scurrius (the broken bars),
  Royal Titans (the tunnel near the scoreboard in the Asgarnian Ice Dungeon), Calvar'ion (Skeletal Tomb) and Spindel
  (Web Chasm), whose entrances come from RuneLite's world map rather than the wiki.
- [ ] A closed lair: with under 91 Slayer, the Cerberus card on Hellhounds says "Needs 91 Slayer" and sits at the
  bottom. Same for Kraken (87), Thermonuclear smoke devil (93), Alchemical Hydra (95), Araxxor (92).
- [ ] Konar Hydras task in the Karuulm Slayer Dungeon: the dungeon and "Alchemical Hydra's lair" are listed, and Go to
  nearest picks the dungeon. Krystilia Bears task: Callisto's Den and Hunter's End are on her list.
- [ ] Compact mode: the lair cards still show in the place list.
- [ ] With *Show Wilderness locations* off, pick a monster that only lives in the Wilderness (e.g. a revenant on
  Ghosts): *Found at* says "Not at any of the places below". There's no *Also counts* drawer any more. Black demons
  has no Kolodion row and no Mage Arena place.
- [ ] With *Show Wilderness locations* off, Spiders: Venenatis and Spindel are still rows; selecting one says "In the
  Wilderness: turn on "Show Wilderness locations" ...". Turn it on: their lairs appear with Go.
- [ ] Krystilia Black dragons task: no King Black Dragon row (the wiki: he doesn't count for her). Kill a black dragon
  and check the loot line counts it; on a normal master's black dragon task, there's one King Black Dragon place
  (the lever lair), not two. Krystilia Spiders: only Venenatis and Spindel as bosses. An ice giant task from anyone
  but Vannaka: no Eldric row.
- [ ] Gargoyles: select Grotesque Guardians. No *Attack* row and no *Bring* (rock hammer) or *Weakness* drawers; select
  a gargoyle and they're back. Dagannoth: Dagannoth Rex has no "Attack: Melee or ranged" row.
- [ ] The monster table at the normal sidebar width: "Grotesque Guardians", "Thermonuclear smoke devil" and "Dagannoth
  Supreme" wrap onto two lines instead of ending in "..."; Gargoyles' "248-328" level is readable.
- [ ] With 89 Slayer, eat a wild pie (to 91+): the Cerberus card stops saying "Needs 91 Slayer (boostable)" within a
  tick, without a kill or XP drop; when the boost wears off it says it again. The
  Abyssal Nexus card says it needs a first visit to the Abyss. Scorpions: Scorpia's offspring is found at Scorpia's
  cave, not the Scorpion Pit.
- [ ] Bears, Skeletons, Spiders (Wilderness setting on) on an account without the medium Wilderness Diary: Callisto's
  Den, Vet'ion's Rest and the Silk Chasm say "Needs Medium Wilderness Diary" and sit with the closed places; Hunter's
  End, the Skeletal Tomb and the Web Chasm say "Needs Hard Wilderness Diary" until the hard diary is done. Their note
  says the 50,000 coin fee is paid once for all the Wilderness boss lairs.
- [ ] Select Vorkath: under *Found at* it says "Go to nearest skips boss lairs: use Go on the lair's card below."
  Hover **Go to nearest**: the tooltip says it leaves out boss lairs. Select a blue dragon: the line is gone.
- [ ] Spiders with *Show Wilderness locations* off: select Venenatis; *Found at* says to turn the setting on and does
  not say "use Go on the lair's card below". With 88 Slayer and a mature Slayer's respite (to 92), Araxxor's lair stops
  saying "Needs 92 Slayer (boostable)". Cave kraken: the Kraken's 87 Slayer has no "(boostable)".
- [ ] Hill giants / Moss giants: Obor's and Bryophyta's lair notes say the key is only for the first entry and the
  chest; Bryophyta's gate is just east of the moss giants.
- [ ] Gargoyles, select Grotesque Guardians: a Drops row with the granite gloves, ring, hammer and maul, Black
  tourmaline core, Noon and Jar of stone. Abyssal demons, select the Abyssal Sire: the bludgeon pieces (about 1/206
  each), abyssal dagger, whip, head, Jar of miasma and orphan, as well as the Unsired.
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
