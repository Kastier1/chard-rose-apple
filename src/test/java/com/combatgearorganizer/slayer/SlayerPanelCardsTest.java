package com.combatgearorganizer.slayer;

import com.combatgearorganizer.CombatTabPanel;
import com.combatgearorganizer.OrganizerPanel;
import com.combatgearorganizer.slayer.SlayerLocationData.LocationEntry;
import com.combatgearorganizer.slayer.SlayerLocationData.TaskEntry;
import com.combatgearorganizer.slayer.SlayerLocationData.Variant;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The slayer panel's monster table and place cards, drawn at the sidebar's real width with the client's look and feel
 * for every task and master in the bundled data.
 */
public class SlayerPanelCardsTest
{
	private static final int[] MASTERS = {1, 2, 3, 4, 5, 6, SlayerMasters.KRYSTILIA, SlayerMasters.KONAR, 9, 10};

	private static SlayerLocationData data;

	@BeforeClass
	public static void load() throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		try (Reader r = new InputStreamReader(SlayerHelper.class.getResourceAsStream("slayer_locations.json"), StandardCharsets.UTF_8))
		{
			data = new Gson().fromJson(r, SlayerLocationData.class);
		}
		assertNotNull(data);
	}

	/** Icons load from the game cache, which isn't there in a test. */
	private static class NoIcons extends SlayerIcons
	{
		@Override
		public void setStyle(JLabel label, TaskTags.Style style)
		{
		}

		@Override
		public void setPrayer(JLabel label, TaskTags.Prayer prayer)
		{
		}

		@Override
		public void setElement(JLabel label, TaskTags.Element element)
		{
		}

		@Override
		public void setCannon(JLabel label, boolean allowed)
		{
		}

		@Override
		public void setItem(JLabel label, int itemId, int quantity)
		{
		}
	}

	/** A panel in the sidebar, as the client hosts it; draw() lays it out at the sidebar's width. */
	private static class Host
	{
		final SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
		final Container root;

		Host()
		{
			OrganizerPanel organizer = new OrganizerPanel(new CombatTabPanel(n -> { }, g -> { }, () -> { }, () -> { }, () -> { }));
			organizer.setSlayerPanel(panel);
			root = organizer.getWrappedPanel();
		}

		void show(TaskEntry t, int master, int left, List<LocationOption> locations)
		{
			SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), left, Math.max(left, 120), master, 0, null);
			panel.showTask(state, t.getInfo(), Variants.counting(t, master, 0), locations, true, true,
				CollectionLog.Snapshot.EMPTY, null);
			draw();
		}

		void draw()
		{
			root.setSize(PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH, 800);
			layOut(root);
		}
	}

	/** Runs the check on the EDT with the RuneLite look and feel, as in the client. */
	private static void inClient(Runnable check) throws Exception
	{
		Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(() ->
		{
			javax.swing.LookAndFeel before = UIManager.getLookAndFeel();
			try
			{
				UIManager.setLookAndFeel(new RuneLiteLAF());
				check.run();
			}
			catch (Throwable e)
			{
				failure[0] = e;
			}
			finally
			{
				try
				{
					UIManager.setLookAndFeel(before);
				}
				catch (UnsupportedLookAndFeelException e)
				{
					failure[0] = failure[0] != null ? failure[0] : e;
				}
			}
		});
		if (failure[0] instanceof Error)
		{
			throw (Error) failure[0];
		}
		if (failure[0] != null)
		{
			throw new IllegalStateException(failure[0]);
		}
	}

	private static void layOut(Component c)
	{
		if (c instanceof Container)
		{
			Container k = (Container) c;
			if (k instanceof javax.swing.JViewport)
			{
				Component view = ((javax.swing.JViewport) k).getView();
				if (view != null)
				{
					view.setSize(k.getWidth(), Math.max(k.getHeight(), view.getPreferredSize().height));
				}
			}
			k.doLayout();
			for (Component child : k.getComponents())
			{
				layOut(child);
			}
		}
	}

	private static void find(Component c, Class<?> type, String name, List<Component> out)
	{
		if (!c.isVisible())
		{
			return;
		}
		if (type.isInstance(c) && (name == null || name.equals(c.getName())))
		{
			out.add(c);
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				find(k, type, name, out);
			}
		}
	}

	private static <T extends Component> List<T> all(Component root, Class<T> type, String name)
	{
		List<Component> out = new ArrayList<>();
		find(root, type, name, out);
		return out.stream().map(type::cast).collect(Collectors.toList());
	}

	private static List<String> texts(Component root)
	{
		return all(root, JLabel.class, null).stream()
			.map(l -> l.getText() == null ? "" : l.getText().replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim())
			.collect(Collectors.toList());
	}

	/** The width the label's text (and icon) really takes, not a fixed preferred size it was given. */
	private static int textWidth(JLabel l)
	{
		Dimension natural = l.getUI().getPreferredSize(l);
		java.awt.Insets in = l.getInsets();
		return natural.width - in.left - in.right;
	}

	/** The location picker's place lists a master can show for a task: both Wilderness settings, and Konar's areas. */
	private static List<List<LocationOption>> placeLists(TaskEntry t, int master)
	{
		List<List<LocationOption>> out = new ArrayList<>();
		out.add(LocationPicker.pick(t, master, 0, false));
		out.add(LocationPicker.pick(t, master, 0, true));
		if (master == SlayerMasters.KONAR)
		{
			for (LocationEntry l : t.getLocations())
			{
				if (l.getAreaId() != null)
				{
					out.add(LocationPicker.pick(t, master, l.getAreaId(), false));
				}
			}
		}
		return out;
	}

	/**
	 * Issue: "172-188157-200" on Black demons. The level and XP columns' numbers touched, since each column was its
	 * widest value plus a pixel. Every pair of neighbouring cells, the name included, now has a gap of at least
	 * {@link SlayerPanel#COLUMN_GAP} - 1 px between their text, and no cell overlaps the next, for every task and master,
	 * with small and large kill counts left.
	 */
	@Test
	public void monsterTableColumnsNeverTouch() throws Exception
	{
		Set<String> problems = new TreeSet<>();
		int[] rowsChecked = {0};
		inClient(() ->
		{
			Host host = new Host();
			Set<String> seen = new HashSet<>();
			for (TaskEntry t : data.getTasks())
			{
				for (int master : MASTERS)
				{
					for (List<LocationOption> locations : placeLists(t, master))
					{
						for (int left : new int[]{1, 87, 255})
						{
							List<Variant> listed = Variants.listed(Variants.counting(t, master, 0), locations);
							String key = t.getName() + "|" + left + "|" + listed.stream().map(Variant::getName)
								.collect(Collectors.joining(","));
							if (listed.isEmpty() || !seen.add(key))
							{
								continue;
							}
							host.show(t, master, left, locations);
							for (JPanel row : all(host.root, JPanel.class, SlayerPanel.TABLE_ROW))
							{
								rowsChecked[0]++;
								checkRow(row, t.getName(), problems);
							}
						}
					}
				}
			}
		});
		assertTrue("no table rows found", rowsChecked[0] > 400);
		assertTrue(problems.size() + " rows with touching columns:\n" + String.join("\n", problems), problems.isEmpty());
	}

	private static void checkRow(JPanel row, String task, Set<String> problems)
	{
		List<JLabel> cells = new ArrayList<>();
		for (Component c : row.getComponents())
		{
			if (c instanceof JLabel && c.isVisible())
			{
				cells.add((JLabel) c);
			}
		}
		if (cells.size() != 4)
		{
			problems.add(task + ": a row with " + cells.size() + " cells");
			return;
		}
		String line = cells.stream().map(l -> l.getText().replaceAll("<[^>]*>", " ")).collect(Collectors.joining(" | "));
		for (int i = 0; i + 1 < cells.size(); i++)
		{
			JLabel a = cells.get(i);
			JLabel b = cells.get(i + 1);
			if (a.getX() + a.getWidth() > b.getX())
			{
				problems.add(task + ": cells overlap in " + line);
			}
			int aEnd = a.getHorizontalAlignment() == SwingConstants.RIGHT
				? a.getX() + a.getWidth() - a.getInsets().right
				: a.getX() + a.getInsets().left + textWidth(a);
			int bStart = b.getHorizontalAlignment() == SwingConstants.RIGHT
				? b.getX() + b.getWidth() - b.getInsets().right - textWidth(b)
				: b.getX() + b.getInsets().left;
			if (bStart - aEnd < SlayerPanel.COLUMN_GAP - 1)
			{
				problems.add(task + ": " + (bStart - aEnd) + "px between cells " + i + " and " + (i + 1) + " in " + line);
			}
		}
	}

	@Test
	public void blackDemonsRowHasAGapBetweenLevelAndXp() throws Exception
	{
		inClient(() ->
		{
			Host host = new Host();
			TaskEntry t = data.findTask(-1, "Black demons");
			host.show(t, 5, 87, LocationPicker.pick(t, 5, 0, false));
			JPanel row = all(host.root, JPanel.class, SlayerPanel.TABLE_ROW).stream()
				.filter(r -> texts(r).contains("172-188"))
				.findFirst().orElseThrow(AssertionError::new);
			Set<String> problems = new TreeSet<>();
			checkRow(row, t.getName(), problems);
			assertTrue(problems.toString(), problems.isEmpty());
		});
	}

	/**
	 * Issue: place cards with no monster line (Black demons' Chasm of Fire, Greater demons' Brimhaven Dungeon, Blue
	 * dragons' Dragon Nest). Every place a task lists, other than a boss lair, names a monster that lives there, or is
	 * marked noMonster: the task has several monsters and none of their wiki spawn spots is near the place or named
	 * like it. The generator's validate() enforces the same.
	 */
	@Test
	public void everyPlaceListsAMonsterThatLivesThere()
	{
		List<String> unlinked = new ArrayList<>();
		List<String> wronglyMarked = new ArrayList<>();
		List<String> unreviewed = new ArrayList<>();
		int marked = 0;
		for (TaskEntry t : data.getTasks())
		{
			List<Variant> regular = Variants.regular(t.getVariants());
			List<LocationEntry> places = new ArrayList<>(t.getLocations());
			places.addAll(t.getWildernessLocations());
			for (LocationEntry l : places)
			{
				boolean linked = t.getVariants().stream().anyMatch(v -> v.getPlaces().contains(l.getKey()));
				if (l.isNoMonster())
				{
					marked++;
					// Review: the mark came from the generator alone, so a matching regression could hide behind it;
					// now each one is a reviewed entry (NO_MONSTER_PLACES) with its evidence
					if (l.getNoMonsterWhy() == null || l.getNoMonsterWhy().isEmpty())
					{
						unreviewed.add(t.getName() + " / " + l.getLabel());
					}
					if (linked || regular.size() < 2)
					{
						wronglyMarked.add(t.getName() + " / " + l.getLabel());
					}
				}
				else if (!l.isBossLair() && !linked && !regular.isEmpty())
				{
					unlinked.add(t.getName() + " / " + l.getLabel());
				}
			}
		}
		assertTrue("places no monster lives at: " + unlinked, unlinked.isEmpty());
		assertTrue("marked noMonster but a monster is known: " + wronglyMarked, wronglyMarked.isEmpty());
		assertTrue("marked noMonster with no reviewed reason: " + unreviewed, unreviewed.isEmpty());
		// The exceptions stay few: they are guide rows the monster pages' spawns don't cover
		assertTrue(marked + " places with no known monster", marked <= 29);
	}

	/**
	 * Review (round 3): five reviewed noMonster exceptions were wrong; the bundled wiki data has the task monster at
	 * each (SPOT_PLACES now): Fred's pen is the Chicken's "Lumbridge West Farm", the Mining Guild ladders are the
	 * Dwarf's "Surface of Mining Guild", the Golem temple is the Ghost's "Ruins of Uzer Temple Basement", the Abandoned
	 * Mine half of Tarn's Lair is the Tarn's Lair skeleton's "Abandoned Mine (Level 1)", and the Southern Wilderness
	 * mine (not the list's South-west one) is the Skeleton's "Wilderness, north of Edgeville" spawns at 3105,3569.
	 * Each names its monster and has a point for Go.
	 */
	@Test
	public void handLinkedPlacesHaveTheirMonsterAndAPoint()
	{
		String[][] cases = {
			{"Birds", "Chicken", "farmer_freds_chicken_pen", "3171,3293"},
			{"Dwarves", "Dwarf", "falador_near_the_mining_guild_ladders", "3012,3337"},
			{"Ghosts", "Ghost", "temple_in_the_golem", "2712,4898"},
			{"Skeletons", "Skeleton", "tarns_lair_abandoned_mine", "3427,9644"},
			{"Skeletons", "Skeleton", "southern_wilderness_mine", "3102,3569"},
		};
		for (String[] c : cases)
		{
			TaskEntry t = data.findTask(-1, c[0]);
			List<LocationEntry> places = new ArrayList<>(t.getLocations());
			places.addAll(t.getWildernessLocations());
			LocationEntry l = places.stream().filter(x -> x.getKey().equals(c[2])).findFirst()
				.orElseThrow(() -> new AssertionError(c[0] + " / " + c[2] + " is gone"));
			assertFalse(c[0] + " / " + c[2] + " is still noMonster", l.isNoMonster());
			assertLivesAt(c[0], c[1], c[2]);
			String[] xy = c[3].split(",");
			int x = Integer.parseInt(xy[0]);
			int y = Integer.parseInt(xy[1]);
			List<int[]> points = new ArrayList<>(l.getPoints());
			points.addAll(l.getWildernessPoints());
			assertTrue(c[0] + " / " + c[2] + " has no point at " + c[3],
				points.stream().anyMatch(p -> p[0] == x && p[1] == y));
		}
		// The Southern Wilderness mine isn't the South-west one (about 3014,3589)
		LocationEntry southern = data.findTask(-1, "Skeletons").getWildernessLocations().stream()
			.filter(x -> x.getKey().equals("southern_wilderness_mine")).findFirst().orElseThrow(AssertionError::new);
		assertTrue(southern.getWildernessPoints().stream()
			.allMatch(p -> Math.abs(p[0] - 3105) <= 5 && Math.abs(p[1] - 3569) <= 5));
	}

	/**
	 * Review: a spot was linked to every place whose name held its words, also one only named relative to it: Dad and
	 * the Troll spectators ("Troll Arena", on the surface) were listed in the "Tunnel between Troll arena and
	 * Trollheim" (underground), whose only monster is the mountain troll.
	 */
	@Test
	public void aSpotIsNotLinkedToAPlaceOnlyNamedBesideIt()
	{
		// (Dad is no longer a task monster at all: a quest fight, ONE_TIME_FIGHTS)
		assertNotAt("Trolls", "Troll spectator", "tunnel_between_troll_arena_and_trollheim");
		assertLivesAt("Trolls", "Mountain troll", "tunnel_between_troll_arena_and_trollheim");
		// A place named beside a spot still matches when another monster's spot of that name is right there (the
		// grizzly bear's "Land's End" is three tiles from the bear cub's "North of Land's End")
		assertLivesAt("Bears", "Grizzly bear", "great_kourend_north_of_lands_end");
		// A direction that says which part ("north entrance") is still the place
		assertLivesAt("Bats", "Giant bat", "yanille_agility_dungeon_north_entrance");
		// The Dark Altar paths are linked by hand (SPOT_PLACES), each with the bat spawns on its side of the altar
		assertLivesAt("Bats", "Giant bat", "west_of_dark_altar_path_towards_fairy_ring");
		assertPointsFrom("Bats", "west_of_dark_altar_path_towards_fairy_ring", 1633, 3879);
		assertPointsFrom("Bats", "south_of_dark_altar_path_towards_blood_altar", 1709, 3860);
		assertPointsFrom("Bats", "east_of_dark_altar_path_towards_soul_altar", 1743, 3892);
		assertTrue(entry(data.findTask(-1, "Bats"), "west_of_dark_altar_path_towards_fairy_ring").getPoints()
			.stream().allMatch(p -> p[0] < 1700));
	}

	/**
	 * Review: Krystilia's Hellhounds place "North-east of the Deserted Keep" said no monster was known and its Go was
	 * greyed out, though the Hellhound page's only surface Wilderness spawns ("Outside of the Wilderness Resource
	 * Area") are there.
	 */
	@Test
	public void krystiliasHellhoundsCanBeFoundNorthEastOfTheDesertedKeep()
	{
		TaskEntry t = data.findTask(-1, "Hellhounds");
		LocationEntry keep = t.getWildernessLocations().stream()
			.filter(l -> l.getKey().equals("north_east_of_the_deserted_keep")).findFirst()
			.orElseThrow(AssertionError::new);
		assertFalse(keep.isNoMonster());
		assertLivesAt("Hellhounds", "Hellhound", "north_east_of_the_deserted_keep");
		assertTrue(keep.getWildernessPoints().stream().anyMatch(p -> p[0] == 3180 && p[1] == 3917));
	}

	/**
	 * Review: Krystilia's "Ice spider (several places)" card named non-Wilderness caves as spawn spots, where kills don't
	 * count for her, though its only points are on the Frozen Waste Plateau. Her copy now names only that spot.
	 */
	@Test
	public void krystiliasCopyOfASeveralPlacesCardNamesOnlyWildernessSpots()
	{
		TaskEntry t = data.findTask(-1, "Spiders");
		LocationEntry ice = t.getWildernessLocations().stream().filter(l -> l.getKey().equals("wiki_ice_spider"))
			.findFirst().orElseThrow(AssertionError::new);
		assertEquals("Ice spider (Frozen Waste Plateau)", ice.getLabel());
		assertTrue(ice.getNote(), ice.getNote().contains("Frozen Waste Plateau"));
		for (String elsewhere : new String[]{"Ice Queen's Lair", "Ruins of Tapoyauik", "Temple of Ikov", "several"})
		{
			assertFalse(ice.getNote(), ice.getNote().contains(elsewhere));
		}
		// The normal list's card keeps every spot
		assertTrue(entry(t, "wiki_ice_spider").getNote().contains("Ice Queen's Lair"));
	}

	@Test
	public void theReportedPlacesNameTheirMonsters()
	{
		assertLivesAt("Black demons", "Black demon", "chasm_of_fire");
		assertLivesAt("Greater demons", "Greater demon", "brimhaven_dungeon");
		// Found by the wiki's spot names, which distance alone missed or which differ in word order
		assertLivesAt("Lesser demons", "Lesser demon", "councillor_hughes_house_in_kingstown");
		assertLivesAt("Bats", "Giant bat", "the_underground_pass_part_1");
		// The only regular monster of a task lives at every guide place of it
		assertLivesAt("Werewolves", "Werewolf", "canifis");
		// Review: the wiki's Blue and Baby blue dragon pages do list Dragon Nest spawns, with bare "1230,9491" points
		// the fetcher used to drop; the place was wrongly marked noMonster
		assertLivesAt("Blue dragons", "Blue dragon", "dragon_nest");
		assertLivesAt("Blue dragons", "Baby blue dragon", "dragon_nest");
		TaskEntry blue = data.findTask(-1, "Blue dragons");
		assertFalse(entry(blue, "dragon_nest").isNoMonster());
		// Still unknown: no elf page's spawn spot is at or named like Prifddinas' gates
		assertTrue(entry(data.findTask(-1, "Elves"), "prifddinas_north_gate").isNoMonster());
	}

	private static LocationEntry entry(TaskEntry t, String key)
	{
		return t.getLocations().stream().filter(l -> l.getKey().equals(key)).findFirst()
			.orElseThrow(AssertionError::new);
	}

	/**
	 * Review: a guide place linked to a monster by its wiki spawn spot's name kept no point when the guide had none, so
	 * Go stayed greyed out and the card said "No map point yet" though the wiki has the spot's points. A place named
	 * the same as the spot now takes them; one only matched by some of its words doesn't (one spot can match several).
	 */
	@Test
	public void placesNamedLikeASpawnSpotTakeItsPoints()
	{
		assertPointsFrom("Blue dragons", "heroes_guild_basement", 2908, 9905);
		assertPointsFrom("Black demons", "crash_site_cavern", 2072, 5651);
		assertPointsFrom("Hill giants", "warriors_guild_basement", 2842, 9992);
		assertPointsFrom("Monkeys", "temple_of_marimbo_dungeon", 2761, 9193);
		assertPointsFrom("Goblins", "goblin_village", 2951, 3481);
		assertPointsFrom("Killerwatts", "killerwatt_plane", 2642, 5202);
		assertPointsFrom("Kurask", "kurask_lair", 1185, 9193);
		// Not from an instance Shortest Path can't path into
		assertTrue(entry(data.findTask(-1, "Monkeys"), "north_east_karamja_instanced").getPoints().isEmpty());
		// Nor where the guide says the entrance can't be pathed to (Waterfall Dungeon)
		assertTrue(entry(data.findTask(-1, "Fire giants"), "waterfall_dungeon").getPoints().isEmpty());
		// Only a word match ("Kingstown - Councillor Hughes' house") borrows no points by itself; this one takes the
		// spot's by hand (the generator's SPOT_PLACES)
		assertPointsFrom("Lesser demons", "councillor_hughes_house_in_kingstown", 1680, 3678);
		assertTrue(entry(data.findTask(-1, "Bats"), "mount_karuulm_north_side").getPoints().isEmpty());
	}

	private static void assertPointsFrom(String task, String key, int x, int y)
	{
		List<int[]> points = entry(data.findTask(-1, task), key).getPoints();
		assertTrue(task + " / " + key + " has no point at " + x + "," + y,
			points.stream().anyMatch(p -> p[0] == x && p[1] == y));
	}

	private static void assertNotAt(String task, String monster, String place)
	{
		TaskEntry t = data.findTask(-1, task);
		Variant v = t.getVariants().stream().filter(x -> x.getName().equals(monster)).findFirst()
			.orElseThrow(AssertionError::new);
		assertFalse(task + " / " + monster + " at " + v.getPlaces(), v.getPlaces().contains(place));
	}

	private static void assertLivesAt(String task, String monster, String place)
	{
		TaskEntry t = data.findTask(-1, task);
		Variant v = t.getVariants().stream().filter(x -> x.getName().equals(monster)).findFirst()
			.orElseThrow(AssertionError::new);
		assertTrue(task + " / " + monster + " at " + v.getPlaces(), v.getPlaces().contains(place));
	}

	/** A checker that says every requirement is missing, as an unknown state (quests not loaded yet) would read. */
	private static final RequirementChecker NOTHING_MET = r ->
	{
		List<String> out = new ArrayList<>(r.getQuestsFinished());
		r.getQuestsStarted().forEach(q -> out.add(q + " (started)"));
		r.getSkills().forEach((s, n) -> out.add(n + " " + s));
		out.addAll(r.getDiaries());
		return out;
	};

	/**
	 * Issue: greyed-out Go with no reason on the card (Blue dragons' Heroes' Guild basement, Black demons' Crash Site
	 * Cavern). Those places have no map point; the reason was only in the button's tooltip. Go is greyed out only for
	 * a missing map point, never for a quest or level the player lacks or the client hasn't loaded yet, and a greyed
	 * Go always has its reason on the card. Checked with every requirement met and with none met, for every master.
	 */
	@Test
	public void aGreyedOutGoAlwaysSaysWhyOnTheCard() throws Exception
	{
		Set<String> problems = new TreeSet<>();
		int[] disabled = {0};
		inClient(() ->
		{
			Host host = new Host();
			for (TaskEntry t : data.getTasks())
			{
				for (int master : new int[]{5, SlayerMasters.KRYSTILIA})
				{
					for (RequirementChecker checker : new RequirementChecker[]{RequirementChecker.NONE, NOTHING_MET})
					{
						List<LocationOption> locations = LocationPicker.pick(t, master, 0, true, checker);
						for (boolean compact : new boolean[]{false, true})
						{
							host.panel.setCompact(compact);
							host.show(t, master, 50, locations);
							for (JButton go : all(host.root, JButton.class, null))
							{
								if (!"Go".equals(go.getText()))
								{
									continue;
								}
								Container card = go.getParent().getParent();
								List<String> said = texts(card);
								String label = said.isEmpty() ? "?" : said.get(0);
								LocationOption place = locations.stream()
									.filter(l -> label.equals(l.getLabel().replaceAll("\\s+", " ").trim()))
									.findFirst().orElse(null);
								if (place == null)
								{
									problems.add(t.getName() + ": no place for card " + label);
									continue;
								}
								if (go.isEnabled() != place.isRoutable())
								{
									problems.add(t.getName() + " / " + label + ": Go enabled=" + go.isEnabled()
										+ " but routable=" + place.isRoutable());
								}
								if (!go.isEnabled())
								{
									disabled[0]++;
									if (!said.contains(SlayerPanel.NO_MAP_POINT))
									{
										problems.add(t.getName() + " / " + label + ": Go greyed out with no reason: " + said);
									}
								}
								// The requirement line itself, by name: a place note can also start with "Needs"
								if (!place.isAccessible() != !all(card, JLabel.class, SlayerPanel.CARD_NEEDS).isEmpty())
								{
									problems.add(t.getName() + " / " + label + ": accessible=" + place.isAccessible()
										+ " but requirement line " + all(card, JLabel.class, SlayerPanel.CARD_NEEDS).size());
								}
							}
							checkNearest(host, t, master, locations, problems);
						}
					}
				}
			}
			host.panel.setCompact(false);
		});
		assertTrue(disabled[0] > 0);
		assertTrue(problems.size() + " problems:\n" + String.join("\n", problems), problems.isEmpty());
	}

	/**
	 * Review: Go to nearest was greyed out with no reason on the panel when every place was gated (Gargoyles with
	 * nothing met, or before the client has sent quest states). It is greyed out only when no place it may pick has a
	 * map point, and then says so; when it can only pick gated places it works and says that.
	 */
	private static void checkNearest(Host host, TaskEntry t, int master, List<LocationOption> locations, Set<String> problems)
	{
		List<JButton> nearest = all(host.root, JButton.class, null).stream()
			.filter(b -> "Go to nearest".equals(b.getText())).collect(Collectors.toList());
		if (nearest.size() != 1)
		{
			problems.add(t.getName() + ": " + nearest.size() + " Go to nearest buttons");
			return;
		}
		boolean anyPoint = locations.stream().anyMatch(l -> l.isRoutable() && !l.isBossLair());
		boolean anyOpen = locations.stream().anyMatch(l -> l.isRoutable() && !l.isBossLair() && l.isAccessible());
		List<String> reason = all(host.root, JLabel.class, SlayerPanel.NEAREST_REASON).stream()
			.map(l -> l.getText().replaceAll("<[^>]*>", "").trim()).collect(Collectors.toList());
		String where = t.getName() + " (master " + master + ")";
		if (nearest.get(0).isEnabled() != anyPoint)
		{
			problems.add(where + ": Go to nearest enabled=" + nearest.get(0).isEnabled() + " but a place with a point=" + anyPoint);
		}
		String expected = !anyPoint ? (locations.stream().anyMatch(l -> !l.isBossLair()) ? SlayerPanel.NEAREST_NO_POINT : null)
			: !anyOpen ? SlayerPanel.NEAREST_GATED : null;
		if (!reason.equals(expected == null ? Collections.emptyList() : Collections.singletonList(expected)))
		{
			problems.add(where + ": Go to nearest reason " + reason + ", expected " + expected);
		}
		if (!nearest.get(0).isEnabled() && reason.isEmpty() && locations.stream().anyMatch(l -> !l.isBossLair()))
		{
			problems.add(where + ": Go to nearest greyed out with no reason");
		}
	}

	@Test
	public void goToNearestWorksAndSaysSoWhenEveryPlaceIsGated() throws Exception
	{
		TaskEntry gargoyles = data.findTask(-1, "Gargoyles");
		List<LocationOption> shut = LocationPicker.pick(gargoyles, 5, 0, false, NOTHING_MET);
		assertTrue(shut.stream().filter(l -> !l.isBossLair()).noneMatch(LocationOption::isAccessible));
		List<LocationOption> targets = LocationPicker.nearestTargets(shut);
		assertFalse(targets.isEmpty());
		assertTrue(targets.stream().noneMatch(LocationOption::isBossLair));
		assertEquals(SlayerPanel.NEAREST_GATED, SlayerPanel.nearestReason(shut, targets));
		// Nothing to say once the player can get in
		List<LocationOption> open = LocationPicker.pick(gargoyles, 5, 0, false);
		assertEquals(null, SlayerPanel.nearestReason(open, LocationPicker.nearestTargets(open)));
		inClient(() ->
		{
			Host host = new Host();
			host.show(gargoyles, 5, 50, shut);
			JButton nearest = all(host.root, JButton.class, null).stream()
				.filter(b -> "Go to nearest".equals(b.getText())).findFirst().orElseThrow(AssertionError::new);
			assertTrue(nearest.isEnabled());
			assertEquals(1, all(host.root, JLabel.class, SlayerPanel.NEAREST_REASON).size());
		});
	}

	/**
	 * Review: the reasons on a card (no map point, no known monster) were drawn in the greyed-out button's dark grey,
	 * about 2:1 on the card. Every on-card reason and the Go to nearest reason reads at WCAG AA (4.5:1) or better.
	 */
	@Test
	public void onCardReasonsAreLegible() throws Exception
	{
		Set<String> problems = new TreeSet<>();
		inClient(() ->
		{
			Host host = new Host();
			// Waterfall Dungeon: no map point; Elves at the Prifddinas gates: no known monster; Gargoyles: gated nearest
			Object[][] cases = {
				{"Fire giants", SlayerPanel.NO_MAP_POINT},
				{"Elves", SlayerPanel.NO_MONSTER_HERE},
				{"Gargoyles", SlayerPanel.NEAREST_GATED},
			};
			for (Object[] c : cases)
			{
				TaskEntry t = data.findTask(-1, (String) c[0]);
				host.show(t, 5, 50, LocationPicker.pick(t, 5, 0, true, NOTHING_MET));
				List<JLabel> labels = all(host.root, JLabel.class, null).stream()
					.filter(l -> l.getText() != null && l.getText().replaceAll("<[^>]*>", "").trim().equals(c[1]))
					.collect(Collectors.toList());
				if (labels.isEmpty())
				{
					problems.add(c[0] + ": no \"" + c[1] + "\"");
				}
				for (JLabel l : labels)
				{
					double ratio = contrast(l.getForeground(), background(l));
					if (ratio < 4.5)
					{
						problems.add(c[0] + ": \"" + c[1] + "\" at " + String.format("%.2f", ratio) + ":1");
					}
				}
			}
		});
		assertTrue(String.join("\n", problems), problems.isEmpty());
	}

	private static java.awt.Color background(Component c)
	{
		for (Component k = c; k != null; k = k.getParent())
		{
			if (k.isOpaque() && k.getBackground() != null)
			{
				return k.getBackground();
			}
		}
		throw new AssertionError("no opaque parent");
	}

	/** WCAG 2 contrast ratio. */
	private static double contrast(java.awt.Color a, java.awt.Color b)
	{
		double la = luminance(a), lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(java.awt.Color c)
	{
		double[] v = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
		for (int i = 0; i < 3; i++)
		{
			v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
	}

	@Test
	public void questGatedPlacesKeepGoUnlessTheyHaveNoMapPoint() throws Exception
	{
		TaskEntry blue = data.findTask(-1, "Blue dragons");
		List<LocationOption> places = LocationPicker.pick(blue, 5, 0, false, NOTHING_MET);
		LocationOption heroes = byKey(places, "heroes_guild_basement");
		LocationOption nest = byKey(places, "dragon_nest");
		assertFalse(heroes.isAccessible());
		assertFalse(nest.isAccessible());
		// The Heroes' Guild basement has the wiki's spawn point now: gated, but Go works
		assertTrue(SlayerPanel.goEnabled(heroes));
		assertTrue(SlayerPanel.goEnabled(nest));
		TaskEntry fire = data.findTask(-1, "Fire giants");
		LocationOption waterfall = byKey(LocationPicker.pick(fire, 5, 0, false, NOTHING_MET), "waterfall_dungeon");
		assertFalse(waterfall.isAccessible());
		assertFalse(SlayerPanel.goEnabled(waterfall));
		// The same with every requirement met: only the map point matters
		assertFalse(SlayerPanel.goEnabled(byKey(LocationPicker.pick(fire, 5, 0, false), "waterfall_dungeon")));
		assertTrue(SlayerPanel.goEnabled(byKey(LocationPicker.pick(blue, 5, 0, false), "heroes_guild_basement")));
		assertTrue(SlayerPanel.goEnabled(byKey(LocationPicker.pick(blue, 5, 0, false), "dragon_nest")));
	}

	private static LocationOption byKey(List<LocationOption> places, String key)
	{
		return places.stream().filter(l -> l.getKey().equals(key)).findFirst().orElseThrow(AssertionError::new);
	}

	/** Every character 6px wide, so the wrapping is known. */
	private static final java.awt.FontMetrics SIX_PX = new java.awt.FontMetrics(new java.awt.Font("Dialog", 0, 12))
	{
		@Override
		public int stringWidth(String str)
		{
			return 6 * str.length();
		}
	};

	@Test
	public void monsterLineColoursTheSelectedMonster()
	{
		String nb = "\u00a0";
		assertEquals("<html><body style='width:160px'><div><font color='#ff981f'>Greater"
				+ nb + "demon,</font>" + nb + "Tormented</div><div style='margin-top:1px'>Demon</div></body></html>",
			SlayerPanel.monsterLine(java.util.Arrays.asList("Greater demon", "Tormented Demon"), "Greater demon",
				SIX_PX, 160));
		// The colour starts the selected name even after a wrap, and runs across the line's words
		assertEquals("<html><body style='width:60px'><div>Greater</div><div style='margin-top:1px'>demon,</div>"
				+ "<div style='margin-top:1px'><font color='#ff981f'>Tormented</font>"
				+ "</div><div style='margin-top:1px'><font color='#ff981f'>Demon</font></div></body></html>",
			SlayerPanel.monsterLine(java.util.Arrays.asList("Greater demon", "Tormented Demon"), "Tormented Demon",
				SIX_PX, 60));
		assertEquals("<html><body style='width:120px'><div>Greater" + nb + "demon</div></body></html>",
			SlayerPanel.monsterLine(Collections.singletonList("Greater demon"), "K'ril Tsutsaroth", SIX_PX, 120));
		assertEquals("<html><body style='width:120px'><div>Fish" + nb + "&amp;" + nb + "chips</div></body></html>",
			SlayerPanel.monsterLine(Collections.singletonList("Fish & chips"), null, SIX_PX, 120));
	}

	/** A card label's text as it reads: no tags, entities or no-break spaces. */
	private static String plain(String html)
	{
		return html.replaceAll("<[^>]*>", " ").replace('\u00a0', ' ').replace("&amp;", "&").replace("&lt;", "<")
			.replace("&gt;", ">").replaceAll("\\s+", " ").trim();
	}

	private static final java.util.regex.Pattern SELECTED = java.util.regex.Pattern.compile(
		"<font color='#ff981f'>(.*?)</font>");

	/** The words of a monster line in the selected colour, as they read, without the comma after the name. */
	private static String coloured(String html)
	{
		java.util.regex.Matcher m = SELECTED.matcher(html);
		StringBuilder out = new StringBuilder();
		while (m.find())
		{
			out.append(out.length() > 0 ? " " : "").append(plain(m.group(1)));
		}
		String words = out.toString();
		return words.endsWith(",") ? words.substring(0, words.length() - 1) : words;
	}

	/**
	 * Issue: the monster line looked orange on some tasks' cards and grey on others. The rule: the monster shown
	 * above is marked (in the selected colour, with a tooltip) on every card that lists it, and on no other card,
	 * whichever monster is selected, for every task.
	 */
	@Test
	public void theSelectedMonsterIsMarkedOnEveryCardThatListsIt() throws Exception
	{
		Set<String> problems = new TreeSet<>();
		int[] marked = {0};
		inClient(() ->
		{
			Host host = new Host();
			for (TaskEntry t : data.getTasks())
			{
				List<LocationOption> locations = LocationPicker.pick(t, 5, 0, true);
				List<Variant> listed = Variants.listed(Variants.counting(t, 5, 0), locations);
				if (listed.isEmpty())
				{
					continue;
				}
				host.show(t, 5, 50, locations);
				for (Variant v : listed)
				{
					host.panel.selectVariant(v.getName());
					host.draw();
					for (JLabel line : all(host.root, JLabel.class, SlayerPanel.CARD_MONSTERS))
					{
						String said = plain(line.getText());
						boolean lists = java.util.Arrays.asList(said.split(", ")).contains(v.getName());
						String colour = coloured(line.getText());
						boolean isMarked = colour.equals(v.getName());
						if (lists != isMarked)
						{
							problems.add(t.getName() + " / " + v.getName() + ": lists=" + lists + " marked=" + isMarked
								+ " in " + said);
						}
						if (isMarked)
						{
							marked[0]++;
							if (!SlayerPanel.SELECTED_MONSTER_TIP.equals(line.getToolTipText()))
							{
								problems.add(t.getName() + " / " + v.getName() + ": no tooltip on the marked line");
							}
						}
						else if (!colour.isEmpty())
						{
							problems.add(t.getName() + " / " + v.getName() + ": another monster marked in " + said);
						}
					}
				}
			}
		});
		assertTrue(marked[0] > 500);
		assertTrue(problems.size() + " problems:\n" + String.join("\n", problems), problems.isEmpty());
	}

	/**
	 * Review (round 3): the selected monster's underline took the gap between lines, so it touched the descenders
	 * above it and the next line's ascenders when the monster line wrapped (Blue dragons' Taverley Dungeon: "Baby blue
	 * dragon, Blue / dragon") and the line under the label ("Recommended" on Dagannoth's Lighthouse). The small font's
	 * lines have no gap of their own, so the selected monster is now shown by colour alone, and the card wraps the line
	 * itself with a pixel between lines and under it. Painted with the RuneLite look and feel at the sidebar width,
	 * each line of a card's monster label is its own band of ink, with a clear row between it and the next line on the
	 * card, for every task and selected monster. (With the underline back, 117 of these failed.)
	 */
	@Test
	public void theMonsterLineNeverTouchesTheLinesAroundIt() throws Exception
	{
		Set<String> problems = new TreeSet<>();
		int[] checked = {0};
		inClient(() ->
		{
			Host host = new Host();
			for (TaskEntry t : data.getTasks())
			{
				List<LocationOption> locations = LocationPicker.pick(t, 5, 0, true);
				List<Variant> listed = Variants.listed(Variants.counting(t, 5, 0), locations);
				if (listed.isEmpty())
				{
					continue;
				}
				host.show(t, 5, 50, locations);
				for (Variant v : listed)
				{
					host.panel.selectVariant(v.getName());
					host.draw();
					for (JLabel line : all(host.root, JLabel.class, SlayerPanel.CARD_MONSTERS))
					{
						if (line.getText().contains("ff981f"))
						{
							checked[0]++;
							checkSpacing(line, t.getName() + " / " + v.getName(), problems);
						}
					}
				}
			}
		});
		assertTrue(checked[0] > 500);
		assertTrue(problems.size() + " problems:\n" + String.join("\n", problems), problems.isEmpty());
	}

	/** Whether each row of the image holds any ink (not the background). */
	private static boolean[] inkRows(java.awt.image.BufferedImage img, int bg)
	{
		boolean[] ink = new boolean[img.getHeight()];
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth() && !ink[y]; x++)
			{
				ink[y] = img.getRGB(x, y) != bg;
			}
		}
		return ink;
	}

	/**
	 * Paints the label's text column: each of the label's lines must be its own band of ink, with a clear row between
	 * it and the next, and a clear row between its ink and the ink of the line under the label.
	 */
	private static void checkSpacing(JLabel label, String where, Set<String> problems)
	{
		Container text = label.getParent();
		java.awt.Color bg = background(text);
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(text.getWidth(), text.getHeight(),
			java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setColor(bg);
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		text.paint(g);
		g.dispose();
		boolean[] ink = inkRows(img, bg.getRGB());
		String said = plain(label.getText());

		int lines = label.getText().split("<div", -1).length - 1;
		int bands = 0;
		int lastInk = -1;
		for (int y = label.getY(); y < label.getY() + label.getHeight(); y++)
		{
			if (ink[y] && (y == label.getY() || !ink[y - 1]))
			{
				bands++;
			}
			lastInk = ink[y] ? y : lastInk;
		}
		if (bands != lines)
		{
			problems.add(where + ": " + lines + " lines but " + bands + " bands of ink (lines touch) in " + said);
		}

		Component[] parts = text.getComponents();
		int i = java.util.Arrays.asList(parts).indexOf(label);
		if (i + 1 < parts.length && lastInk >= 0)
		{
			Component next = parts[i + 1];
			int firstInk = -1;
			for (int y = next.getY(); y < next.getY() + next.getHeight() && firstInk < 0; y++)
			{
				firstInk = ink[y] ? y : -1;
			}
			if (firstInk >= 0 && firstInk - lastInk < 2)
			{
				problems.add(where + ": " + said + " touches the line under it (" + texts(next) + ")");
			}
		}
	}

	@Test
	public void greaterDemonsCatacombsFollowsTheSelection() throws Exception
	{
		inClient(() ->
		{
			Host host = new Host();
			TaskEntry t = data.findTask(-1, "Greater demons");
			host.show(t, 5, 87, LocationPicker.pick(t, 5, 0, false));
			Consumer<String> select = name ->
			{
				host.panel.selectVariant(name);
				host.draw();
			};
			select.accept("Greater demon");
			assertEquals("Greater demon", coloured(cardLine(host, "Catacombs of Kourend")));
			select.accept("K'ril Tsutsaroth");
			assertEquals("", coloured(cardLine(host, "Catacombs of Kourend")));
			assertEquals("K'ril Tsutsaroth", coloured(cardLine(host, "K'ril Tsutsaroth's room")));
		});
	}

	private static String cardLine(Host host, String placeStart)
	{
		for (JButton go : all(host.root, JButton.class, null))
		{
			Container card = go.getParent().getParent();
			List<String> said = texts(card);
			if (!said.isEmpty() && said.get(0).startsWith(placeStart))
			{
				return all(card, JLabel.class, SlayerPanel.CARD_MONSTERS).get(0).getText();
			}
		}
		throw new AssertionError("no card " + placeStart);
	}
}
