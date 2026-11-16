package com.combatgearorganizer.slayer;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import com.combatgearorganizer.ItemValueMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Checks the bundled slayer data against what the plugin can show, and draws every task and monster in the real panel.
 */
public class BundledDataTest
{
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

	private static List<SlayerLocationData.LocationEntry> allLocations(SlayerLocationData.TaskEntry t)
	{
		List<SlayerLocationData.LocationEntry> all = new ArrayList<>(t.getLocations());
		all.addAll(t.getWildernessLocations());
		return all;
	}

	@Test
	public void everyValueMapsToSomethingThePanelKnows()
	{
		List<String> problems = new ArrayList<>();
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				for (String type : v.getAttackTypes())
				{
					if (TaskTags.styleOf(type) == null)
					{
						problems.add(t.getName() + " / " + v.getName() + ": attack type " + type);
					}
				}
				if (v.getWeakness() != null && TaskTags.elementOf(v.getWeakness().getElement()) == null)
				{
					problems.add(t.getName() + " / " + v.getName() + ": weakness " + v.getWeakness().getElement());
				}
			}
			for (SlayerLocationData.LocationEntry l : allLocations(t))
			{
				for (int[] p : l.getPoints())
				{
					if (p.length != 3)
					{
						problems.add(t.getName() + " / " + l.getKey() + ": point of length " + p.length);
					}
				}
				for (int[] p : l.getWildernessPoints())
				{
					if (p.length != 3)
					{
						problems.add(t.getName() + " / " + l.getKey() + ": Wilderness point of length " + p.length);
					}
				}
				SlayerLocationData.Requirements r = l.getRequirements();
				if (r == null)
				{
					continue;
				}
				List<String> quests = new ArrayList<>(r.getQuestsFinished());
				quests.addAll(r.getQuestsStarted());
				for (String q : quests)
				{
					try
					{
						Quest.valueOf(q);
					}
					catch (IllegalArgumentException e)
					{
						problems.add(t.getName() + " / " + l.getKey() + ": unknown quest " + q);
					}
				}
				for (String s : r.getSkills().keySet())
				{
					try
					{
						Skill.valueOf(s);
					}
					catch (IllegalArgumentException e)
					{
						problems.add(t.getName() + " / " + l.getKey() + ": unknown skill " + s);
					}
				}
			}
		}
		if (!problems.isEmpty())
		{
			fail(problems.size() + " problems:\n" + String.join("\n", problems));
		}
	}

	@Test
	public void noPrayerSwitchingAdvice()
	{
		java.util.regex.Pattern switching = java.util.regex.Pattern.compile("\\b(switch|swap|flick|alternat|phase|rotation|per form)",
			java.util.regex.Pattern.CASE_INSENSITIVE);
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			String prayer = t.getInfo() != null ? t.getInfo().getProtectionPrayer() : null;
			assertTrue(t.getName() + ": " + prayer, prayer == null || !switching.matcher(prayer).find());
		}
	}

	@Test
	public void noPrayerAdviceForBosses()
	{
		SlayerLocationData.TaskEntry kraken = data.findTask(-1, "Cave kraken");
		assertNotNull(kraken);
		SlayerLocationData.Variant boss = null;
		SlayerLocationData.Variant regular = null;
		for (SlayerLocationData.Variant v : kraken.getVariants())
		{
			if (v.isBoss())
			{
				boss = v;
			}
			else if (!v.isSuperior())
			{
				regular = v;
			}
		}
		assertNotNull(boss);
		assertNotNull(regular);
		assertTrue(!SlayerPanel.showsPrayer(kraken.getInfo(), boss));
		assertTrue(SlayerPanel.showsPrayer(kraken.getInfo(), regular));
		assertTrue(!SlayerPanel.showsPrayer(data.findTask(-1, "Hydras").getInfo(), null));
	}

	@Test
	public void lenientLookups()
	{
		assertEquals(TaskTags.Element.FIRE, TaskTags.elementOf("Fire"));
		assertNull(TaskTags.elementOf("none"));
		assertNull(TaskTags.elementOf(null));
		assertEquals(TaskTags.Style.RANGED, TaskTags.styleOf("range"));
		assertEquals(TaskTags.Style.MAGIC, TaskTags.styleOf("magic"));
		assertNull(TaskTags.styleOf("dragonfire"));
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

	/** Every drop name in the data matched to a made-up item, as the resolver does in game. */
	private static DropItems stubItems(ItemValueMode mode)
	{
		Map<String, DropItems.Item> items = new HashMap<>();
		int id = 1;
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				List<String> names = new ArrayList<>(v.getAlwaysDrops());
				v.getDrops().forEach(d -> names.add(d.getItem()));
				for (String name : names)
				{
					// Every fourth item untradeable, every tenth worth nothing
					int n = id++;
					long value = n % 10 == 0 ? 0 : mode == ItemValueMode.GRAND_EXCHANGE ? n * 1000L : n * 600L;
					items.putIfAbsent(CollectionLog.key(name), new DropItems.Item(n, value, n % 4 != 0));
				}
			}
		}
		// Known values for the acceptance checks
		items.put("abyssal whip", new DropItems.Item(4151, 1_200_000, true));
		return new DropItems(items, mode);
	}

	private static List<DropSlot> slots(Component c)
	{
		List<DropSlot> out = new ArrayList<>();
		if (c instanceof DropSlot)
		{
			out.add((DropSlot) c);
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				out.addAll(slots(child));
			}
		}
		return out;
	}

	private static DropSlot slotFor(Component c, String item)
	{
		return slots(c).stream().filter(s -> s.getToolTipText().contains("<b>" + item + "</b>")).findFirst().orElse(null);
	}

	private static boolean containsText(Component c, String text)
	{
		if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().contains(text))
		{
			return true;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				if (containsText(child, text))
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void panelDrawsEveryTaskAndMonster() throws Exception
	{
		drawEverything(CollectionLog.Snapshot.EMPTY, DropItems.EMPTY, null);
	}

	@Test
	public void panelDrawsEveryTaskAndMonsterWithItemPictures() throws Exception
	{
		// Some drops logged and the log searched, so all three log states are drawn
		Set<String> logged = new HashSet<>(Arrays.asList("abyssal whip", "dragon chainbody", "vorki"));
		CollectionLog.Snapshot clog = new CollectionLog.Snapshot(logged, true);
		drawEverything(clog, stubItems(ItemValueMode.GRAND_EXCHANGE), null);
		drawEverything(CollectionLog.Snapshot.EMPTY, stubItems(ItemValueMode.HIGH_ALCHEMY), null);
		// With task progress under the kill count, before and after the first timed kill
		drawEverything(clog, stubItems(ItemValueMode.GRAND_EXCHANGE), progress(true).view(50, ItemValueMode.GRAND_EXCHANGE));
		drawEverything(clog, stubItems(ItemValueMode.HIGH_ALCHEMY), TaskProgress.start(1, "Bears", 50, 120, 0)
			.view(50, ItemValueMode.HIGH_ALCHEMY));
	}

	/** A task tracked from 52 left: two kills a minute apart, XP, a superior and some loot. */
	private static TaskProgress progress(boolean withExtras)
	{
		TaskProgress p = TaskProgress.start(42, "Abyssal demons", 52, 120, 0);
		p.kill(1, 60_000);
		p.kill(1, 120_000);
		if (withExtras)
		{
			p.addXp(3000);
			p.addSuperior();
			p.addLoot(1_200_000, 400_000);
		}
		return p;
	}

	@Test
	public void progressShowsUnderTheKillCount() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Abyssal demons");
		assertNotNull(t);
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			TaskProgress p = progress(true);
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				stubItems(ItemValueMode.GRAND_EXCHANGE), p.view(50, ItemValueMode.GRAND_EXCHANGE));
			assertTrue(containsText(panel, "60 kills/hr · about 50 min left"));
			assertTrue(containsText(panel, "3,000 slayer XP this task"));
			assertTrue(containsText(panel, "1 superior this task"));
			assertTrue(containsText(panel, "Loot: 1.2M (GE value)"));
			assertTrue(containsText(panel, "Counted from 52 left"));

			// The loot value follows the "Item value" setting
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				stubItems(ItemValueMode.HIGH_ALCHEMY), p.view(50, ItemValueMode.HIGH_ALCHEMY));
			assertTrue(containsText(panel, "Loot: 400K (High alch)"));

			// Before a timed kill there is no rate yet
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				stubItems(ItemValueMode.GRAND_EXCHANGE), TaskProgress.start(42, "Abyssal demons", 120, 120, 0)
					.view(120, ItemValueMode.GRAND_EXCHANGE));
			assertTrue(containsText(panel, "Kill rate shows after your next kill"));
			assertTrue(!containsText(panel, "Counted from"));

			// Finished: the summary stays until the next task
			p.finish(180_000);
			panel.showNoTask(p.summary(ItemValueMode.GRAND_EXCHANGE));
			assertTrue(containsText(panel, "Task done: 52 Abyssal demons in 3 min, 1 superior, 1.2M loot."));
			assertTrue(containsText(panel, SlayerPanel.NO_TASK_TITLE));
			assertTrue(containsText(panel, SlayerPanel.NO_TASK));
			panel.showNoTask(null);
			assertTrue(!containsText(panel, "Task done"));
		});
	}

	@Test
	public void abyssalDemonDropsArePicturesWithRateAndValue() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Abyssal demons");
		assertNotNull(t);
		CollectionLog.Snapshot clog = new CollectionLog.Snapshot(Collections.singleton("abyssal whip"), true);
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		List<String> tips = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, clog, stubItems(ItemValueMode.GRAND_EXCHANGE));
			assertEquals("Abyssal demon", panel.getSelectedVariant());
			DropSlot whip = slotFor(panel, "Abyssal whip");
			assertNotNull("whip shown as a picture", whip);
			tips.add(whip.getToolTipText());
			assertEquals(CollectionLog.Status.LOGGED, whip.getStatus());
			DropSlot dagger = slotFor(panel, "Abyssal dagger");
			assertNotNull(dagger);
			assertEquals(CollectionLog.Status.MISSING, dagger.getStatus());
			assertTrue(dagger.getToolTipText().contains("Not in your collection log yet"));
			assertTrue(containsText(panel, "7 not logged yet"));
			// The superior's own extras sit next to it
			assertNotNull(slotFor(panel, "Imbued heart"));
			// And the boss's
			assertNotNull(slotFor(panel, "Unsired"));

			// Switching the setting changes every value shown, with no restart
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, clog, stubItems(ItemValueMode.HIGH_ALCHEMY));
			for (DropSlot s : slots(panel))
			{
				assertTrue(s.getToolTipText(), s.getToolTipText().contains("High alch: "));
				assertTrue(s.getToolTipText(), !s.getToolTipText().contains("GE value"));
			}
		});
		assertTrue(tips.get(0), tips.get(0).contains("Drop rate: 1/512"));
		assertTrue(tips.get(0), tips.get(0).contains("GE value: 1.2M"));
		assertTrue(tips.get(0), tips.get(0).contains("In your collection log"));
	}

	private void drawEverything(CollectionLog.Snapshot clog, DropItems items, TaskProgress.View progress) throws Exception
	{
		List<String> problems = Collections.synchronizedList(new ArrayList<>());
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			// With a route line, as after Go
			panel.showRoute(Route.shortestPath("Catacombs of Kourend"));
			for (SlayerLocationData.TaskEntry t : data.getTasks())
			{
				SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
				List<LocationOption> locations = LocationPicker.pick(t, 5, 0, true);
				try
				{
					panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, clog, items, progress);
					// A kill, drawn without a rebuild
					panel.updateCount(new SlayerTaskState(t.getTaskId(), t.getName(), 49, 120, 5, 0, null), progress);
					if (!containsText(panel, "49 of 120 left"))
					{
						problems.add(t.getName() + ": count not updated");
					}
					List<String> names = new ArrayList<>();
					for (SlayerLocationData.Variant v : Variants.listed(t.getVariants(), locations))
					{
						names.add(v.getName());
					}
					for (String name : names)
					{
						panel.selectVariant(name);
						if (containsText(panel, "Couldn't show the details"))
						{
							problems.add(t.getName() + " / " + name);
						}
					}
				}
				catch (RuntimeException e)
				{
					problems.add(t.getName() + ": " + e);
				}
			}
		});
		assertTrue(String.join("\n", problems), problems.isEmpty());
	}

	private static List<JButton> buttons(Component c, String text)
	{
		List<JButton> out = new ArrayList<>();
		if (c instanceof JButton && text.equals(((JButton) c).getText()))
		{
			out.add((JButton) c);
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				out.addAll(buttons(child, text));
			}
		}
		return out;
	}

	private static boolean visibleText(Component c, String text)
	{
		if (!c.isVisible())
		{
			return false;
		}
		if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().contains(text))
		{
			return true;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				if (visibleText(child, text))
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void compactPanelDrawsEveryTask() throws Exception
	{
		List<String> problems = Collections.synchronizedList(new ArrayList<>());
		Set<String> logged = new HashSet<>(Arrays.asList("abyssal whip", "dragon chainbody"));
		CollectionLog.Snapshot clog = new CollectionLog.Snapshot(logged, true);
		DropItems items = stubItems(ItemValueMode.GRAND_EXCHANGE);
		TaskProgress.View progress = progress(true).view(50, ItemValueMode.GRAND_EXCHANGE);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.setCompact(true);
			for (SlayerLocationData.TaskEntry t : data.getTasks())
			{
				SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
				List<LocationOption> locations = LocationPicker.pick(t, 5, 0, true);
				for (Route route : Arrays.asList(Route.NONE, Route.shortestPath("Catacombs of Kourend"), Route.worldMap(null)))
				{
					try
					{
						panel.showRoute(route);
						panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, clog, items, progress);
						String where = t.getName() + " (" + route.getKind() + "): ";
						if (!slots(panel).isEmpty())
						{
							problems.add(where + "drops shown");
						}
						for (String hidden : new String[]{"Found at", "Monster", "Attack", "slayer XP this task", "Loot:"})
						{
							if (containsText(panel, hidden))
							{
								problems.add(where + hidden + " shown");
							}
						}
						if (!containsText(panel, t.getName()) || !containsText(panel, "50 of 120 left")
							|| !containsText(panel, "60 kills/hr"))
						{
							problems.add(where + "header or progress missing");
						}
						if (buttons(panel, "Go").size() != locations.size())
						{
							problems.add(where + buttons(panel, "Go").size() + " Go buttons for " + locations.size() + " places");
						}
						if (buttons(panel, "Go to nearest").size() != 1)
						{
							problems.add(where + "no Go to nearest");
						}
						if (route.isShown() != visibleText(panel, route.isShown() ? route.text() : "On route"))
						{
							problems.add(where + "route line");
						}
					}
					catch (RuntimeException e)
					{
						problems.add(t.getName() + ": " + e);
					}
				}
			}
		});
		assertTrue(String.join("\n", problems), problems.isEmpty());
	}

	@Test
	public void routeLineAfterGoUntilCancel() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Abyssal demons");
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		List<String> events = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel[] holder = new SlayerPanel[1];
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> events.add("go " + l.getLabel()), l -> events.add("nearest"),
				() ->
				{
					events.add("cancel");
					// The helper ends the route after Cancel
					holder[0].showRoute(Route.NONE);
				});
			holder[0] = panel;
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			assertTrue(buttons(panel, "Clear path").isEmpty());
			assertTrue(!visibleText(panel, "On route"));

			LocationOption catacombs = locations.stream().filter(l -> l.getLabel().contains("Catacombs")).findFirst().orElse(null);
			assertNotNull(catacombs);
			// Go, then the helper reports our route
			int index = locations.indexOf(catacombs);
			buttons(panel, "Go").get(index).doClick(0);
			assertEquals("go " + catacombs.getLabel(), events.get(0));
			panel.showRoute(Route.shortestPath(catacombs.getLabel()));
			assertTrue(visibleText(panel, "On route to " + catacombs.getLabel()));

			// Survives a kill and a full redraw
			panel.updateCount(new SlayerTaskState(t.getTaskId(), t.getName(), 49, 120, 5, 0, null), null);
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			assertTrue(visibleText(panel, "On route to " + catacombs.getLabel()));

			buttons(panel, "Cancel").get(0).doClick(0);
			assertEquals("cancel", events.get(events.size() - 1));
			assertTrue(!visibleText(panel, "On route"));

			// Arrival: the helper ends it the same way
			panel.showRoute(Route.shortestPath(null));
			assertTrue(visibleText(panel, "On route to the nearest place"));
			panel.showRoute(Route.NONE);
			assertTrue(!visibleText(panel, "On route"));

			// Without Shortest Path
			panel.showRoute(Route.worldMap(catacombs.getLabel()));
			assertTrue(visibleText(panel, "Marked on your world map"));
			assertEquals(1, buttons(panel, "Remove").size());
		});
	}

	@Test
	public void drawersMonsterAndLastPlaceKeptAcrossKillsAndRelogs() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Black dragons");
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		Gson gson = new Gson();
		List<String> saved = new ArrayList<>();
		List<String> pickedHolder = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { }, m -> saved.add(m.toJson(gson)));
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			String bring = t.getInfo().getRequiredItems();
			String weakness = t.getInfo().getWeakness();
			assertTrue(bring != null || weakness != null);
			String drawer = bring != null ? "Bring" : "Weakness";
			String body = bring != null ? bring : weakness;
			assertTrue(!containsText(panel, body));
			panel.toggleDrawer(drawer);
			assertTrue(containsText(panel, body));
			// Pick a monster other than the default
			String picked = null;
			for (SlayerLocationData.Variant v : Variants.listed(t.getVariants(), locations))
			{
				if (!v.getName().equals(panel.getSelectedVariant()))
				{
					picked = v.getName();
				}
			}
			assertNotNull(picked);
			panel.selectVariant(picked);
			assertEquals(picked, panel.getSelectedVariant());

			// A kill keeps it open
			panel.updateCount(new SlayerTaskState(t.getTaskId(), t.getName(), 49, 120, 5, 0, null), null);
			assertTrue(containsText(panel, body));
			assertTrue(containsText(panel, "49 of 120 left"));

			// Go somewhere the picked monster lives, so the pick stays
			int index = 0;
			for (int i = 0; i < locations.size(); i++)
			{
				if (Variants.at(t.getVariants(), locations.get(i).getKey()).contains(picked))
				{
					index = i;
				}
			}
			LocationOption place = locations.get(index);
			assertTrue(!containsText(panel, SlayerPanel.LAST_TIME));
			buttons(panel, "Go").get(index).doClick(0);
			assertTrue(containsText(panel, SlayerPanel.LAST_TIME));
			assertEquals(place.getKey(), panel.getMemory().lastPlace(PanelMemory.taskKey(t.getTaskId(), t.getName())));
			assertEquals(picked, panel.getSelectedVariant());
			pickedHolder.add(picked);
		});
		assertTrue(!saved.isEmpty());

		// Relog (or a client restart): a new panel gets the saved memory for the account
		String json = saved.get(saved.size() - 1);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showLoggedOut();
			assertTrue(containsText(panel, SlayerPanel.LOGGED_OUT));
			panel.setMemory(PanelMemory.fromJson(gson, json));
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			String body = t.getInfo().getRequiredItems() != null ? t.getInfo().getRequiredItems() : t.getInfo().getWeakness();
			assertTrue(containsText(panel, body));
			assertTrue(containsText(panel, SlayerPanel.LAST_TIME));
			assertEquals(pickedHolder.get(0), panel.getSelectedVariant());

			// Another task starts with its own default
			SlayerLocationData.TaskEntry other = data.findTask(-1, "Hydras");
			List<LocationOption> otherPlaces = LocationPicker.pick(other, 5, 0, false);
			panel.showTask(new SlayerTaskState(other.getTaskId(), other.getName(), 50, 120, 5, 0, null), other.getInfo(),
				other.getVariants(), otherPlaces, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			assertTrue(!containsText(panel, SlayerPanel.LAST_TIME));
		});
	}

	@Test
	public void killUpdatesCountWithoutRebuilding() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Abyssal demons");
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY,
				TaskProgress.start(42, t.getName(), 50, 120, 0).view(50, ItemValueMode.GRAND_EXCHANGE));
			JButton go = buttons(panel, "Go").get(0);
			assertTrue(containsText(panel, "for 50 left"));
			assertTrue(containsText(panel, "Kill rate shows after your next kill"));

			panel.updateCount(new SlayerTaskState(t.getTaskId(), t.getName(), 48, 120, 5, 0, null), progress(true).view(48,
				ItemValueMode.GRAND_EXCHANGE));
			assertTrue(containsText(panel, "48 of 120 left"));
			assertTrue(containsText(panel, "for 48 left"));
			assertTrue(containsText(panel, "60 kills/hr"));
			assertTrue(containsText(panel, "1 superior this task"));
			assertTrue(!containsText(panel, "Kill rate shows after your next kill"));
			// Same components: nothing was rebuilt
			assertTrue(go == buttons(panel, "Go").get(0));

			// Another task is not drawn by a count update
			panel.updateCount(new SlayerTaskState(t.getTaskId() + 1, "Hydras", 10, 120, 5, 0, null), null);
			assertTrue(containsText(panel, "48 of 120 left"));
		});
	}

	@Test
	public void emptyStatesAreOneSentence()
	{
		for (String s : new String[]{SlayerPanel.LOGGED_OUT, SlayerPanel.NO_TASK, SlayerPanel.NO_SHORTEST_PATH, SlayerPanel.NO_DATA,
			SlayerPanel.NO_DATA_BOSS, SlayerPanel.NO_WILDERNESS_PLACES, SlayerPanel.ALL_IN_WILDERNESS})
		{
			assertTrue(s, s.endsWith("."));
			assertEquals(s, 1, s.split("\\. ").length);
		}
	}
}
