package com.combatgearorganizer.slayer;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import com.combatgearorganizer.CombatTabPanel;
import com.combatgearorganizer.ItemValueMode;
import com.combatgearorganizer.OrganizerPanel;
import com.combatgearorganizer.dps.OwnedGear;
import com.combatgearorganizer.dps.WikiGear;
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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

	/** The tooltip of the first label whose text contains this, or null. */
	private static String tooltipOf(Component c, String text)
	{
		if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().contains(text))
		{
			return ((JLabel) c).getToolTipText();
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				String tip = tooltipOf(child, text);
				if (tip != null)
				{
					return tip;
				}
			}
		}
		return null;
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

	/** Every weapon and piece of armour the tests have wiki stats for. */
	private static final OwnedGear BIG_BANK = WikiGear.bank("Darklight", "Arclight", "Emberlight", "Dual macuahuitl",
		"Abyssal whip", "Dragon hunter lance", "Dragon hunter crossbow", "Armadyl crossbow", "Twisted bow",
		"Toxic blowpipe", "Trident of the Swamp", "Kodai wand", "Tumeken's shadow", "Sanguinesti staff", "Keris partisan",
		"Leaf-bladed battleaxe", "Slayer's staff (e)", "Ghrazi rapier", "Osmumten's fang", "Scythe of Vitur",
		"Blisterwood flail", "Ivandis flail", "Dragon hunter wand", "Scorching bow", "Dragon bolts",
		"Ruby dragon bolts (e)", "Dragon arrow", "Broad bolts", "Amethyst broad bolts", "Silver bolts", "Rune arrow",
		"Dragon dart", "Slayer helmet (i)", "Salve amulet(ei)", "Salve amulet", "Black mask", "Leaf-bladed sword",
		"Rune crossbow", "Magic shortbow (i)");

	@Test
	public void panelDrawsBestGearForEveryMonster() throws Exception
	{
		drawEverything(CollectionLog.Snapshot.EMPTY, DropItems.EMPTY, null, BIG_BANK);
		// Only the inventory: a bank never seen
		drawEverything(CollectionLog.Snapshot.EMPTY, DropItems.EMPTY, null,
			new OwnedGear(false, WikiGear.all("Abyssal whip"), BIG_BANK.getLevels()));
		// A bank with no weapons in it
		drawEverything(CollectionLog.Snapshot.EMPTY, DropItems.EMPTY, null, WikiGear.bank());
	}

	@Test
	public void greaterDemonShowsTheBestWeaponPerStyle() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Greater demons");
		assertNotNull(t);
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY);
			panel.selectVariant("Greater demon");
			assertTrue(containsText(panel, SlayerPanel.BEST_GEAR));
			assertTrue(containsText(panel, GearUp.OPEN_BANK));

			panel.setGear(WikiGear.bank("Darklight", "Arclight", "Dual macuahuitl", "Slayer helmet (i)"));
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY);
			assertTrue(!containsText(panel, GearUp.OPEN_BANK));
			assertTrue(containsText(panel, "Arclight "));
			assertTrue(containsText(panel, " DPS (demonbane)"));
			assertTrue(containsText(panel, "then "));
			// The long tooltips wrap instead of running across the screen, and are escaped HTML
			String tip = tooltipOf(panel, "Arclight ");
			assertNotNull(tip);
			assertTrue(tip, tip.startsWith("<html><body style='width:"));
			assertTrue(tip, tip.contains("<br>With Slayer helmet (i), "));
			assertTrue(tip, !tip.contains("\n"));
			String heading = tooltipOf(panel, SlayerPanel.BEST_GEAR);
			assertTrue(heading, heading.startsWith("<html><body style='width:") && heading.contains("Piety"));

			panel.setGear(WikiGear.bank());
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY);
			assertTrue(containsText(panel, GearUp.NO_WEAPONS));

			// Not in compact mode: the monster card isn't drawn there
			panel.setCompact(true);
			panel.setGear(WikiGear.bank("Arclight"));
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY);
			assertTrue(!containsText(panel, SlayerPanel.BEST_GEAR));
		});
	}

	@Test
	public void kuraskSaysWhatHurtsIt() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Kurask");
		assertNotNull(t);
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.setGear(WikiGear.bank("Abyssal whip", "Leaf-bladed sword"));
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY);
			assertTrue(containsText(panel, "Leaf-bladed sword "));
			assertTrue(!containsText(panel, "Abyssal whip "));
			panel.setGear(WikiGear.bank("Abyssal whip"));
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY);
			assertTrue(containsText(panel, "Only leaf-bladed weapons"));
		});
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
			// The boss is a row of its own now, not a line under the demon's drops
			assertNull(slotFor(panel, "Unsired"));
			panel.selectVariant("Abyssal Sire");
			assertNotNull(slotFor(panel, "Unsired"));
			assertTrue(containsText(panel, "Abyssal Nexus"));
			panel.selectVariant("Abyssal demon");
			assertEquals("Abyssal demon", panel.getSelectedVariant());

			// The Carry row suggests the ash sanctifier, and ticks it once it's in the inventory
			CarrySlot ash = carrySlot(panel, CarryItem.ASH_SANCTIFIER);
			assertNotNull("ash sanctifier suggested", ash);
			assertTrue(!ash.isCarried());
			tips.add(ash.getToolTipText());
			// Ash sanctifier, herb sack, gem bag and soul bearer; no looting bag outside the Wilderness
			assertNull(carrySlot(panel, CarryItem.LOOTING_BAG));
			assertTrue(containsText(panel, "4 worth bringing"));
			panel.updateCarried(java.util.EnumSet.of(CarryItem.ASH_SANCTIFIER));
			ash = carrySlot(panel, CarryItem.ASH_SANCTIFIER);
			assertTrue(ash.isCarried());
			assertTrue(!carrySlot(panel, CarryItem.HERB_SACK).isCarried());
			tips.add(ash.getToolTipText());
			assertTrue(containsText(panel, "3 worth bringing"));
			panel.updateCarried(java.util.EnumSet.of(CarryItem.ASH_SANCTIFIER, CarryItem.HERB_SACK, CarryItem.GEM_BAG,
				CarryItem.SOUL_BEARER));
			assertTrue(containsText(panel, "All with you"));

			// Switching the setting changes every value shown, with no restart
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, clog, stubItems(ItemValueMode.HIGH_ALCHEMY));
			for (DropSlot s : slots(panel))
			{
				if (s instanceof CarrySlot)
				{
					continue;
				}
				assertTrue(s.getToolTipText(), s.getToolTipText().contains("High alch: "));
				assertTrue(s.getToolTipText(), !s.getToolTipText().contains("GE value"));
			}
		});
		assertTrue(tips.get(0), tips.get(0).contains("Drop rate: 1/512"));
		assertTrue(tips.get(1), tips.get(1).contains("Not in your inventory or worn"));
		assertTrue(tips.get(2), tips.get(2).contains("You have one with you"));
		assertTrue(tips.get(0), tips.get(0).contains("GE value: 1.2M"));
		assertTrue(tips.get(0), tips.get(0).contains("In your collection log"));
	}

	@Test
	public void dragonAndWyvernTasksSuggestBreathProtection() throws Exception
	{
		List<String> tips = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			SlayerLocationData.TaskEntry blue = data.findTask(-1, "Blue dragons");
			panel.showTask(new SlayerTaskState(blue.getTaskId(), blue.getName(), 50, 120, 5, 0, null), blue.getInfo(),
				blue.getVariants(), LocationPicker.pick(blue, 5, 0, false), true, true, CollectionLog.Snapshot.EMPTY,
				stubItems(ItemValueMode.GRAND_EXCHANGE));
			panel.selectVariant("Blue dragon");
			CarrySlot fire = carrySlot(panel, CarryItem.DRAGONFIRE);
			assertNotNull("dragonfire protection suggested", fire);
			assertNull(carrySlot(panel, CarryItem.WYVERN_SHIELD));
			assertFalse(fire.isCarried());
			tips.add(fire.getToolTipText());
			// An antifire potion alone ticks it
			panel.updateCarried(java.util.EnumSet.of(CarryItem.DRAGONFIRE));
			assertTrue(carrySlot(panel, CarryItem.DRAGONFIRE).isCarried());
			// Baby dragons can't breathe fire
			panel.selectVariant("Baby blue dragon");
			assertNull(carrySlot(panel, CarryItem.DRAGONFIRE));

			SlayerLocationData.TaskEntry wyverns = data.findTask(-1, "Skeletal wyverns");
			panel.showTask(new SlayerTaskState(wyverns.getTaskId(), wyverns.getName(), 50, 120, 5, 0, null),
				wyverns.getInfo(), wyverns.getVariants(), LocationPicker.pick(wyverns, 5, 0, false), true, true, CollectionLog.Snapshot.EMPTY,
				stubItems(ItemValueMode.GRAND_EXCHANGE));
			// Still carrying dragonfire protection (an anti-dragon shield or antifire): it doesn't tick the wyvern shield
			CarrySlot shield = carrySlot(panel, CarryItem.WYVERN_SHIELD);
			assertNotNull("wyvern shield suggested", shield);
			assertNull(carrySlot(panel, CarryItem.DRAGONFIRE));
			assertFalse("antifire doesn't tick the wyvern shield", shield.isCarried());
			tips.add(shield.getToolTipText());
		});
		assertTrue(tips.get(0), tips.get(0).startsWith("<html>Dragonfire: Bring an anti-dragon shield and/or an antifire potion.<br>Super antifire"));
		assertTrue(tips.get(0), tips.get(0).contains("fire fully.<br>Vorkath needs a shield and super antifire together<br>"));
		assertTrue(tips.get(0), tips.get(0).contains("Not in your inventory or worn"));
		assertTrue(tips.get(1), tips.get(1).contains("Icy breath: Wear an elemental, mind, dragonfire or ancient wyvern shield.<br>"));
		assertTrue(tips.get(1), tips.get(1).contains("Only the ancient wyvern shield stops the freeze"));
	}

	private static CarrySlot carrySlot(SlayerPanel panel, CarryItem item)
	{
		for (DropSlot s : slots(panel))
		{
			if (s instanceof CarrySlot && ((CarrySlot) s).getItem() == item)
			{
				return (CarrySlot) s;
			}
		}
		return null;
	}

	private void drawEverything(CollectionLog.Snapshot clog, DropItems items, TaskProgress.View progress) throws Exception
	{
		drawEverything(clog, items, progress, OwnedGear.NONE);
	}

	private void drawEverything(CollectionLog.Snapshot clog, DropItems items, TaskProgress.View progress, OwnedGear gear)
		throws Exception
	{
		List<String> problems = Collections.synchronizedList(new ArrayList<>());
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.setGear(gear);
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
	public void everyRegularMonsterHasAPlace()
	{
		// The generator only keeps a regular monster when the wiki has spawns for it, so each needs a place
		List<String> problems = new ArrayList<>();
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			Set<String> keys = new HashSet<>();
			allLocations(t).forEach(l -> keys.add(l.getKey()));
			for (SlayerLocationData.Variant v : Variants.regular(t.getVariants()))
			{
				if (v.getPlaces().isEmpty())
				{
					problems.add(t.getName() + " / " + v.getName() + ": no place");
				}
				v.getPlaces().stream().filter(k -> !keys.contains(k))
					.forEach(k -> problems.add(t.getName() + " / " + v.getName() + ": unknown place " + k));
			}
		}
		assertTrue(String.join("\n", problems), problems.isEmpty());
	}

	@Test
	public void wikiSpawnPlacesKeepTheWildernessRules()
	{
		int wikiPlaces = 0;
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.LocationEntry l : allLocations(t))
			{
				if (!l.isFromWiki())
				{
					continue;
				}
				wikiPlaces++;
				assertTrue(l.getLabel(), !l.getPoints().isEmpty() || !l.getWildernessPoints().isEmpty());
				assertNotNull(l.getLabel(), l.getNote());
				assertTrue(l.getKey(), l.getKey().startsWith("wiki_"));
				// Someone lives there
				assertTrue(t.getName() + " / " + l.getLabel(), !Variants.at(t.getVariants(), l.getKey()).isEmpty());
			}
			// Krystilia's list only ever gets the Wilderness spawns
			for (SlayerLocationData.LocationEntry l : t.getWildernessLocations())
			{
				if (l.isFromWiki())
				{
					assertTrue(l.getLabel(), l.getPoints().isEmpty());
					assertTrue(t.getName() + " / " + l.getLabel() + " is on her list only when it's on the normal one too, or she alone gives the task",
						t.getLocations().isEmpty() || t.getLocations().stream().anyMatch(n -> n.getKey().equals(l.getKey())));
				}
			}
			// A task only Krystilia gives gets nothing outside the Wilderness
			if (t.getLocations().isEmpty())
			{
				assertTrue(t.getName(), LocationPicker.pick(t, SlayerMasters.KRYSTILIA, 0, false).stream().allMatch(LocationOption::isWilderness));
			}
		}
		assertTrue("wiki places: " + wikiPlaces, wikiPlaces > 50);
		// Unique keys in each list
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			Set<String> seen = new HashSet<>();
			t.getLocations().forEach(l -> assertTrue(t.getName() + " / " + l.getKey(), seen.add(l.getKey())));
			Set<String> wild = new HashSet<>();
			t.getWildernessLocations().forEach(l -> assertTrue(t.getName() + " / " + l.getKey(), wild.add(l.getKey())));
		}
	}

	private static List<SlayerLocationData.LocationEntry> wikiPlaces(SlayerLocationData.TaskEntry t)
	{
		List<SlayerLocationData.LocationEntry> out = new ArrayList<>();
		for (SlayerLocationData.LocationEntry l : t.getLocations())
		{
			if (l.isFromWiki())
			{
				out.add(l);
			}
		}
		return out;
	}

	@Test
	public void wikiSpawnPlacesJoinBorrowAndMerge()
	{
		// Named like the task's own place (whose only point is the entrance): joins it, no extra place
		SlayerLocationData.TaskEntry fever = data.findTask(-1, "Fever spiders");
		assertTrue(Variants.byName(fever.getVariants(), "Fever spider").getPlaces().contains("braindeath_island"));
		assertTrue(wikiPlaces(fever).isEmpty());
		SlayerLocationData.TaskEntry killerwatts = data.findTask(-1, "Killerwatts");
		assertTrue(Variants.byName(killerwatts.getVariants(), "Killerwatt").getPlaces().contains("killerwatt_plane"));
		assertTrue(wikiPlaces(killerwatts).isEmpty());

		// Named like another task's guide place: borrows its access
		SlayerLocationData.LocationEntry cavern = wikiPlaces(data.findTask(-1, "Skeletons")).stream()
			.filter(l -> l.getLabel().equals("Ancient Cavern")).findFirst().orElse(null);
		assertNotNull(cavern);
		assertNotNull(cavern.getRequirements());
		assertEquals(Collections.singletonList("BARBARIAN_TRAINING"), cavern.getRequirements().getQuestsStarted());
		assertEquals("Spawn points from the OSRS Wiki.", cavern.getNote());
		// Borrowing by the name before a qualifier: "Underground Pass (Level 2)" is in the Underground Pass
		SlayerLocationData.LocationEntry pass = wikiPlaces(data.findTask(-1, "Rats")).stream()
			.filter(l -> l.getLabel().contains("Underground Pass (Level 2)")).findFirst().orElse(null);
		assertNotNull(pass);
		assertEquals(Collections.singletonList("PLAGUE_CITY"), pass.getRequirements().getQuestsStarted());
		assertEquals("Spawn points from the OSRS Wiki.", pass.getNote());
		// The God Wars Dungeon bodyguards are gone, and the wiki place made from their spawns with them
		assertTrue(wikiPlaces(data.findTask(-1, "Greater demons")).stream().noneMatch(l -> l.getLabel().contains("Zamorak")));
		// No guide place of that name, but the access is known from the wiki
		SlayerLocationData.LocationEntry temple = wikiPlaces(data.findTask(-1, "Greater demons")).stream()
			.filter(l -> l.getLabel().contains("Ancient Guthixian Temple")).findFirst().orElse(null);
		assertNotNull(temple);
		assertEquals(Collections.singletonList("WHILE_GUTHIX_SLEEPS"), temple.getRequirements().getQuestsFinished());
		assertEquals("Spawn points from the OSRS Wiki.", temple.getNote());
		// Neither: it says the access isn't known
		SlayerLocationData.LocationEntry unknown = wikiPlaces(data.findTask(-1, "Hellhounds")).stream()
			.filter(l -> l.getLabel().startsWith("Bouncer")).findFirst().orElse(null);
		assertNotNull(unknown);
		assertNull(unknown.getRequirements());
		assertTrue(unknown.getNote().contains("no access details"));

		// Named like the task's own place with a qualifier or plural: joins it, no extra place
		SlayerLocationData.TaskEntry aviansies = data.findTask(-1, "Aviansies");
		assertTrue(wikiPlaces(aviansies).isEmpty());
		assertEquals(Arrays.asList("god_wars_dungeon", "wilderness_god_wars_dungeon"),
			Variants.byName(aviansies.getVariants(), "Aviansie").getPlaces());
		SlayerLocationData.TaskEntry metal = data.findTask(-1, "Metal dragons");
		assertEquals(Collections.singletonList("ancient_cavern"), Variants.byName(metal.getVariants(), "Mithril dragon").getPlaces());
		assertTrue(wikiPlaces(metal).isEmpty());
		SlayerLocationData.TaskEntry revs = data.findTask(-1, "Revenants");
		assertEquals(Collections.singletonList("revenant_caves"), Variants.byName(revs.getVariants(), "Revenant hobgoblin").getPlaces());
		assertTrue(revs.getWildernessLocations().stream().noneMatch(SlayerLocationData.LocationEntry::isFromWiki));
		assertEquals(Collections.singletonList("stronghold_of_security"),
			Variants.byName(data.findTask(-1, "Catablepon").getVariants(), "Catablepon").getPlaces());

		// A monster in many spots gets one place that lists them
		SlayerLocationData.LocationEntry birds = wikiPlaces(data.findTask(-1, "Birds")).stream()
			.filter(l -> l.getLabel().equals("Bird (several places)")).findFirst().orElse(null);
		assertNotNull(birds);
		assertTrue(birds.getNote().contains("Go takes you to the nearest"));

		// Krystilia-only tasks leave out monsters that never spawn in the Wilderness
		assertNull(Variants.byName(data.findTask(-1, "Chaos druids").getVariants(), "Chaos druid warrior"));
		assertNull(Variants.byName(data.findTask(-1, "Green dragons").getVariants(), "Elvarg"));
	}

	@Test
	public void tormentedDemonsCountForGreaterDemonsAndYouCanGo() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Greater demons");
		SlayerLocationData.Variant td = Variants.byName(t.getVariants(), "Tormented Demon");
		assertNotNull(td);
		assertTrue(td.isRegular());
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		LocationOption temple = locations.stream().filter(l -> td.getPlaces().contains(l.getKey())).findFirst().orElse(null);
		assertNotNull(temple);
		assertEquals("Tormented Demon (Ancient Guthixian Temple)", temple.getLabel());
		assertTrue(temple.isFromWiki());
		assertTrue(temple.isRoutable());
		assertTrue(temple.isAccessible());
		assertEquals(26, temple.getTargets().size());
		assertTrue(Variants.listed(t.getVariants(), locations).contains(td));
		// Go to nearest sticks to the guide's places
		assertTrue(LocationPicker.nearestTargets(locations).stream().noneMatch(LocationOption::isFromWiki));
		assertTrue(!LocationPicker.nearestTargets(locations).isEmpty());
		// Without While Guthix Sleeps the temple is shown as closed
		RequirementChecker noWgs = r -> r.getQuestsFinished().contains("WHILE_GUTHIX_SLEEPS")
			? Collections.singletonList("While Guthix Sleeps") : Collections.emptyList();
		LocationOption closed = LocationPicker.pick(t, 5, 0, false, noWgs).stream()
			.filter(l -> l.getKey().equals(temple.getKey())).findFirst().orElse(null);
		assertNotNull(closed);
		assertTrue(!closed.isAccessible());
		assertEquals(Collections.singletonList("While Guthix Sleeps"), closed.getMissingRequirements());

		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<String> events = new ArrayList<>();
		List<List<LocationOption>> nearest = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> events.add("go " + l.getLabel()), nearest::add, () -> { });
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			panel.selectVariant("Tormented Demon");
			assertEquals("Tormented Demon", panel.getSelectedVariant());
			assertTrue(containsText(panel, "Tormented Demon (Ancient Guthixian Temple)"));
			assertTrue(containsText(panel, "Spawn points from the OSRS Wiki"));
			buttons(panel, "Go").get(locations.indexOf(temple)).doClick(0);
			assertEquals("go " + temple.getLabel(), events.get(0));
			buttons(panel, "Go to nearest").get(0).doClick(0);
			assertEquals(LocationPicker.nearestTargets(locations), nearest.get(0));
		});
	}

	/** Each task's bosses, from the OSRS Wiki's "Slayer task" page and each boss's page (tools/build_slayer_locations.py BOSSES). */
	private static final Map<String, List<String>> BOSSES_BY_TASK = new HashMap<>();

	static
	{
		BOSSES_BY_TASK.put("Abyssal demons", Collections.singletonList("Abyssal Sire"));
		BOSSES_BY_TASK.put("Araxytes", Collections.singletonList("Araxxor"));
		BOSSES_BY_TASK.put("Aviansies", Collections.singletonList("Kree'arra"));
		BOSSES_BY_TASK.put("Bears", Arrays.asList("Artio", "Callisto"));
		BOSSES_BY_TASK.put("Black demons", Collections.singletonList("Skotizo"));
		BOSSES_BY_TASK.put("Black dragons", Collections.singletonList("King Black Dragon"));
		BOSSES_BY_TASK.put("Blue dragons", Collections.singletonList("Vorkath"));
		BOSSES_BY_TASK.put("Cave kraken", Collections.singletonList("Kraken"));
		BOSSES_BY_TASK.put("Cows", Collections.singletonList("Brutus"));
		BOSSES_BY_TASK.put("Dagannoth", Arrays.asList("Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme"));
		BOSSES_BY_TASK.put("Fire giants", Collections.singletonList("Branda the Fire Queen"));
		BOSSES_BY_TASK.put("Gargoyles", Collections.singletonList("Grotesque Guardians"));
		BOSSES_BY_TASK.put("Greater demons", Arrays.asList("K'ril Tsutsaroth", "Skotizo"));
		BOSSES_BY_TASK.put("Gryphons", Collections.singletonList("Shellbane gryphon"));
		BOSSES_BY_TASK.put("Hellhounds", Collections.singletonList("Cerberus"));
		BOSSES_BY_TASK.put("Hill giants", Collections.singletonList("Obor"));
		BOSSES_BY_TASK.put("Hydras", Collections.singletonList("Alchemical Hydra"));
		BOSSES_BY_TASK.put("Ice giants", Collections.singletonList("Eldric the Ice King"));
		BOSSES_BY_TASK.put("Kalphite", Collections.singletonList("Kalphite Queen"));
		BOSSES_BY_TASK.put("Lesser Nagua", Collections.singletonList("Amoxliatl"));
		BOSSES_BY_TASK.put("Moss giants", Collections.singletonList("Bryophyta"));
		BOSSES_BY_TASK.put("Rats", Collections.singletonList("Scurrius"));
		BOSSES_BY_TASK.put("Scorpions", Collections.singletonList("Scorpia"));
		BOSSES_BY_TASK.put("Skeletons", Arrays.asList("Calvar'ion", "Vet'ion"));
		BOSSES_BY_TASK.put("Smoke devils", Collections.singletonList("Thermonuclear smoke devil"));
		BOSSES_BY_TASK.put("Spiders", Arrays.asList("Araxxor", "Sarachnis", "Spindel", "Venenatis"));
		BOSSES_BY_TASK.put("Zombies", Collections.singletonList("Vorkath"));
	}

	private static final List<String> GWD_BODYGUARDS = Arrays.asList("Tstanon Karlak", "Zakl'n Gritch", "Balfrug Kreeyath",
		"Wingman Skree", "Flockleader Geerin", "Flight Kilisa", "Sergeant Strongstack", "Sergeant Grimspike",
		"Sergeant Steelwill", "Starlight", "Growler", "Bree");

	private static SlayerLocationData.LocationEntry place(SlayerLocationData.TaskEntry t, String key)
	{
		return allLocations(t).stream().filter(l -> l.getKey().equals(key)).findFirst().orElse(null);
	}

	private static SlayerLocationData.LocationEntry lairOf(String task, String boss)
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, task);
		SlayerLocationData.Variant v = Variants.byName(t.getVariants(), boss);
		assertNotNull(task + " / " + boss, v);
		return place(t, v.getPlaces().get(0));
	}

	@Test
	public void eachTaskHasTheBossesThatCountAndNoBodyguards()
	{
		List<String> problems = new ArrayList<>();
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			List<String> bosses = new ArrayList<>();
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				if (GWD_BODYGUARDS.contains(v.getName()))
				{
					problems.add(t.getName() + " / " + v.getName() + ": a God Wars Dungeon bodyguard");
				}
				if (v.isBoss())
				{
					bosses.add(v.getName());
				}
				if (v.getName().contains("PvM Arena"))
				{
					problems.add(t.getName() + " / " + v.getName());
				}
			}
			Collections.sort(bosses);
			List<String> expected = BOSSES_BY_TASK.getOrDefault(t.getName(), Collections.emptyList());
			if (!expected.equals(bosses))
			{
				problems.add(t.getName() + ": bosses " + bosses + ", expected " + expected);
			}
		}
		assertTrue(String.join("\n", problems), problems.isEmpty());

		// Flagged as bosses now, so they're in their lairs rather than at the guide places
		for (String task : Arrays.asList("Black demons", "Greater demons"))
		{
			assertTrue(Variants.byName(data.findTask(-1, task).getVariants(), "Skotizo").isBoss());
		}
		assertTrue(Variants.byName(data.findTask(-1, "Rats").getVariants(), "Scurrius").isBoss());
		// Kolodion counts, but only once, in the Mage Arena miniquest: no row and no place to go (like Porazdir)
		SlayerLocationData.TaskEntry blackDemons = data.findTask(-1, "Black demons");
		assertNull(Variants.byName(blackDemons.getVariants(), "Kolodion"));
		assertTrue(allLocations(blackDemons).stream().noneMatch(l -> l.getLabel().contains("Kolodion")));
		// Quest-only versions don't stretch a boss's level range
		assertArrayEquals(new int[]{732, 732}, Variants.byName(data.findTask(-1, "Blue dragons").getVariants(), "Vorkath").getLevel());
		assertArrayEquals(new int[]{263, 263}, Variants.byName(data.findTask(-1, "Lesser Nagua").getVariants(), "Amoxliatl").getLevel());
		assertNull(Variants.byName(data.findTask(-1, "Cows").getVariants(), "Bull"));
		// Bosses come after the regular monsters, before superiors
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			boolean seenBoss = false;
			boolean seenSuperior = false;
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				assertTrue(t.getName() + " / " + v.getName(), !(v.isRegular() && (seenBoss || seenSuperior)));
				assertTrue(t.getName() + " / " + v.getName(), !(v.isBoss() && seenSuperior));
				seenBoss |= v.isBoss();
				seenSuperior |= v.isSuperior();
			}
		}
	}

	@Test
	public void everyBossHasItsLairWithAWorkingGo()
	{
		List<String> problems = new ArrayList<>();
		int lairs = 0;
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : Variants.bosses(t.getVariants()))
			{
				if (v.getPlaces().size() != 1)
				{
					problems.add(t.getName() + " / " + v.getName() + ": places " + v.getPlaces());
					continue;
				}
				SlayerLocationData.LocationEntry lair = place(t, v.getPlaces().get(0));
				if (lair == null || !lair.isBossLair() || lair.isFromWiki())
				{
					problems.add(t.getName() + " / " + v.getName() + ": not a lair " + v.getPlaces());
				}
				else if (lair.getPoints().isEmpty() && lair.getWildernessPoints().isEmpty())
				{
					problems.add(t.getName() + " / " + v.getName() + ": no point to go to");
				}
			}
			for (SlayerLocationData.LocationEntry l : allLocations(t))
			{
				if (!l.isBossLair())
				{
					continue;
				}
				lairs++;
				if (!l.getKey().startsWith("boss_") || l.getNote() == null || l.getNote().toLowerCase().contains("pray"))
				{
					problems.add(t.getName() + " / " + l.getKey() + ": " + l.getNote());
				}
				// Only the boss lives there, and monsters the wiki says live only in its lair (Scorpia's offspring)
				List<String> here = Variants.at(t.getVariants(), l.getKey());
				if (here.isEmpty() || here.stream().anyMatch(n -> Variants.byName(t.getVariants(), n).isRegular()
					&& !n.equals("Scorpia's offspring")))
				{
					problems.add(t.getName() + " / " + l.getKey() + ": " + here);
				}
			}
			// Never "Go to nearest", whatever else is open, for any master or setting
			for (int master : new int[]{5, SlayerMasters.KRYSTILIA})
			{
				for (boolean wilderness : new boolean[]{false, true})
				{
					if (LocationPicker.nearestTargets(LocationPicker.pick(t, master, 0, wilderness)).stream()
						.anyMatch(LocationOption::isBossLair))
					{
						problems.add(t.getName() + ": a lair in Go to nearest");
					}
				}
			}
		}
		assertTrue(String.join("\n", problems), problems.isEmpty());
		assertTrue("lairs: " + lairs, lairs >= 33);

		// The Go target is where you walk in, not a spot inside the instance
		SlayerLocationData.LocationEntry vorkath = lairOf("Blue dragons", "Vorkath");
		assertEquals("Ungael (Vorkath)", vorkath.getLabel());
		assertArrayEquals(new int[]{2272, 4053, 0}, vorkath.getPoints().get(0));
		assertEquals(Collections.singletonList("DRAGON_SLAYER_II"), vorkath.getRequirements().getQuestsFinished());
		assertEquals(vorkath.getKey(), lairOf("Zombies", "Vorkath").getKey());
		// The guide's empty Ungael place was only there for Vorkath
		assertNull(place(data.findTask(-1, "Blue dragons"), "ungael"));
		assertNull(place(data.findTask(-1, "Zombies"), "ungael"));
		assertArrayEquals(new int[]{2873, 9848, 0}, lairOf("Hellhounds", "Cerberus").getPoints().get(0));
		assertArrayEquals(new int[]{2917, 3747, 0}, lairOf("Greater demons", "K'ril Tsutsaroth").getPoints().get(0));
		assertArrayEquals(new int[]{2917, 3747, 0}, lairOf("Aviansies", "Kree'arra").getPoints().get(0));

		// Skill and quest needs, so a player without them sees the lair as closed
		Object[][] slayer = {
			{"Abyssal demons", "Abyssal Sire", 85}, {"Gargoyles", "Grotesque Guardians", 75}, {"Cave kraken", "Kraken", 87},
			{"Hellhounds", "Cerberus", 91}, {"Araxytes", "Araxxor", 92}, {"Spiders", "Araxxor", 92},
			{"Smoke devils", "Thermonuclear smoke devil", 93}, {"Hydras", "Alchemical Hydra", 95},
			{"Gryphons", "Shellbane gryphon", 51},
		};
		for (Object[] row : slayer)
		{
			SlayerLocationData.LocationEntry lair = lairOf((String) row[0], (String) row[1]);
			assertEquals(row[0] + " / " + row[1], row[2], lair.getRequirements().getSkills().get("SLAYER"));
		}
		assertEquals(Integer.valueOf(70), lairOf("Aviansies", "Kree'arra").getRequirements().getSkills().get("RANGED"));
		assertEquals(Integer.valueOf(70), lairOf("Greater demons", "K'ril Tsutsaroth").getRequirements().getSkills().get("HITPOINTS"));
		assertEquals(Collections.singletonList("THE_IDES_OF_MILK"), lairOf("Cows", "Brutus").getRequirements().getQuestsFinished());

		// The lair counts as the guide place's Konar area where the wiki says the boss counts for her there
		SlayerLocationData.TaskEntry hydras = data.findTask(-1, "Hydras");
		assertEquals(place(hydras, "karuulm_slayer_dungeon").getAreaId(), lairOf("Hydras", "Alchemical Hydra").getAreaId());
		List<LocationOption> konar = LocationPicker.pick(hydras, 8, place(hydras, "karuulm_slayer_dungeon").getAreaId(), false);
		assertEquals(2, konar.size());
		assertTrue(konar.stream().anyMatch(LocationOption::isBossLair));
		assertNull(lairOf("Greater demons", "K'ril Tsutsaroth").getAreaId());

		// Krystilia: the Wilderness bosses that count for her are on her list; the King Black Dragon isn't
		SlayerLocationData.TaskEntry bears = data.findTask(-1, "Bears");
		for (String boss : Arrays.asList("Callisto", "Artio"))
		{
			String key = lairOf("Bears", boss).getKey();
			assertTrue(boss, bears.getWildernessLocations().stream().anyMatch(l -> l.getKey().equals(key)));
		}
		SlayerLocationData.TaskEntry black = data.findTask(-1, "Black dragons");
		String kbd = lairOf("Black dragons", "King Black Dragon").getKey();
		assertTrue(black.getWildernessLocations().stream().noneMatch(l -> l.getKey().equals(kbd)));
		assertTrue(lairOf("Black dragons", "King Black Dragon").getPoints().isEmpty());
		assertFalse(lairOf("Black dragons", "King Black Dragon").getWildernessPoints().isEmpty());
	}

	/** The first component (a monster table row) whose tooltip is exactly this, or null. */
	private static javax.swing.JComponent withTooltip(Component c, String tooltip)
	{
		if (c instanceof javax.swing.JComponent && tooltip.equals(((javax.swing.JComponent) c).getToolTipText()))
		{
			return (javax.swing.JComponent) c;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				javax.swing.JComponent found = withTooltip(child, tooltip);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Whether some label's whole text is exactly this. */
	private static boolean hasLabel(Component c, String text)
	{
		if (c instanceof JLabel && text.equals(((JLabel) c).getText()))
		{
			return true;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				if (hasLabel(child, text))
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void bossesAreRowsInTheTableWithTheirOwnCardAndLair() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Blue dragons");
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		LocationOption lair = locations.get(locations.size() - 1);
		assertTrue(lair.isBossLair());
		assertEquals("Ungael (Vorkath)", lair.getLabel());
		List<SlayerLocationData.Variant> listed = Variants.listed(t.getVariants(), locations);
		assertEquals("Vorkath", listed.get(listed.size() - 1).getName());
		List<String> events = new ArrayList<>();
		List<List<LocationOption>> nearest = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> events.add(l.getKey()), nearest::add, () -> { });
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				stubItems(ItemValueMode.GRAND_EXCHANGE));
			// Not the default; a regular monster is, with the task's prayer advice
			assertTrue(!"Vorkath".equals(panel.getSelectedVariant()));
			assertTrue(hasLabel(panel, "Pray"));
			// Its row says it's a boss, by the crown and the tooltip; the card has no separate "Boss" line
			javax.swing.JComponent row = withTooltip(panel, SlayerPanel.bossTooltip("Vorkath"));
			assertNotNull("Vorkath's row", row);
			assertTrue(SlayerPanel.bossTooltip("Vorkath").startsWith("Boss"));
			JLabel name = (JLabel) ((Container) row).getComponent(0);
			assertEquals("Vorkath", name.getText());
			assertEquals(SlayerPanel.BOSS_CROWN, name.getIcon());
			assertEquals(SlayerPanel.BOSS_TIP, name.getAccessibleContext().getAccessibleDescription());
			assertFalse(hasLabel(panel, "Boss"));

			// Selecting it shows its own card: level, drops, carry, where to fight it; no prayer advice
			panel.selectVariant("Vorkath");
			assertEquals("Vorkath", panel.getSelectedVariant());
			assertTrue(containsText(panel, "732 · 750 HP"));
			assertTrue(hasLabel(panel, "Drops"));
			assertTrue(hasLabel(panel, "Carry"));
			assertTrue(hasLabel(panel, "Found at"));
			assertTrue(containsText(panel, "Ungael (Vorkath)"));
			assertFalse(hasLabel(panel, "Pray"));
			assertFalse(containsText(panel, "<b>Prayer</b>"));
			assertFalse(hasLabel(panel, "Boss"));
			assertNull(withTooltip(panel, SlayerPanel.bossTooltip("Vorkath")));

			// The lair's Go goes to the walk-in point and keeps the boss selected; from a regular monster it switches
			panel.selectVariant("Blue dragon");
			buttons(panel, "Go").get(locations.indexOf(lair)).doClick(0);
			assertEquals(Collections.singletonList(lair.getKey()), events);
			assertEquals("Vorkath", panel.getSelectedVariant());
			// "Go to nearest" never sends the player to it
			buttons(panel, "Go to nearest").get(0).doClick(0);
			assertTrue(nearest.get(0).stream().noneMatch(LocationOption::isBossLair));
			assertTrue(!nearest.get(0).isEmpty());

			// Compact mode still lists the lair's card
			panel.setCompact(true);
			assertTrue(containsText(panel, "Ungael (Vorkath)"));
		});

		// A lair the player can't get into yet says why
		SlayerLocationData.TaskEntry hounds = data.findTask(-1, "Hellhounds");
		RequirementChecker lowSlayer = r -> r.getSkills().containsKey("SLAYER")
			? Collections.singletonList(r.getSkills().get("SLAYER") + " Slayer") : Collections.emptyList();
		List<LocationOption> houndPlaces = LocationPicker.pick(hounds, 5, 0, false, lowSlayer);
		LocationOption cerberus = houndPlaces.stream().filter(LocationOption::isBossLair).findFirst().orElse(null);
		assertNotNull(cerberus);
		assertFalse(cerberus.isAccessible());
		assertEquals(houndPlaces.size() - 1, houndPlaces.indexOf(cerberus));
		SlayerTaskState houndState = new SlayerTaskState(hounds.getTaskId(), hounds.getName(), 50, 120, 5, 0, null);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(houndState, hounds.getInfo(), hounds.getVariants(), houndPlaces, true, true,
				CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			assertTrue(containsText(panel, "Needs 91 Slayer"));
			panel.selectVariant("Cerberus");
			assertEquals("Cerberus", panel.getSelectedVariant());
			assertFalse(hasLabel(panel, "Pray"));
		});
	}

	@Test
	public void theCutOffCheckMeasuresAWrappedNameWithAFixedSize() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			// Like the monster table's two-line names: a fixed preferred width of 10, laid out too narrow
			JLabel name = new JLabel("<html>Grotesque<br>Guardians</html>");
			name.setPreferredSize(new java.awt.Dimension(10, 16));
			name.setSize(30, 16);
			Set<String> problems = new HashSet<>();
			clipped(name, "test", problems);
			assertEquals(problems.toString(), 1, problems.size());
			// Wide and tall enough: no problem
			name.setSize(name.getUI().getPreferredSize(name));
			problems.clear();
			clipped(name, "test", problems);
			assertTrue(problems.toString(), problems.isEmpty());
		});
	}

	@Test
	public void everyBossHasDropsFromItsWikiPages()
	{
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				if (v.isBoss())
				{
					assertFalse(t.getName() + " / " + v.getName() + " has no drops", v.getDrops().isEmpty());
				}
			}
		}
		// The Grotesque Guardians' loot is on their own wiki page, not Dusk's
		SlayerLocationData.Variant gg = Variants.byName(data.findTask(-1, "Gargoyles").getVariants(), "Grotesque Guardians");
		List<String> ggDrops = new ArrayList<>();
		gg.getDrops().forEach(d -> ggDrops.add(d.getItem()));
		assertTrue(ggDrops.toString(), ggDrops.containsAll(Arrays.asList("Granite gloves", "Granite ring", "Granite hammer",
			"Black tourmaline core", "Noon", "Jar of Stone")));
		// The Abyssal Sire's uniques come out of the Unsired it drops (1/100), at the Unsired's rate times that
		SlayerLocationData.Variant sire = Variants.byName(data.findTask(-1, "Abyssal demons").getVariants(), "Abyssal Sire");
		Map<String, String> rates = new HashMap<>();
		sire.getDrops().forEach(d -> rates.put(d.getItem(), d.getRarity()));
		assertEquals("1/100", rates.get("Unsired"));
		assertEquals("1/206.5", rates.get("Bludgeon claw"));
		assertEquals("1/492.3", rates.get("Abyssal dagger"));
		assertEquals("1/2,560", rates.get("Abyssal orphan"));
		assertTrue(rates.containsKey("Jar of Miasma"));
		assertTrue(rates.containsKey("Abyssal head"));
	}

	@Test
	public void wildernessBossLairsNeedTheirDiary()
	{
		Object[][] lairs = {
			{"Bears", "Callisto", "WILDERNESS_MEDIUM"}, {"Bears", "Artio", "WILDERNESS_HARD"},
			{"Skeletons", "Vet'ion", "WILDERNESS_MEDIUM"}, {"Skeletons", "Calvar'ion", "WILDERNESS_HARD"},
			{"Spiders", "Venenatis", "WILDERNESS_MEDIUM"}, {"Spiders", "Spindel", "WILDERNESS_HARD"},
		};
		for (Object[] row : lairs)
		{
			SlayerLocationData.TaskEntry t = data.findTask(-1, (String) row[0]);
			String key = Variants.byName(t.getVariants(), (String) row[1]).getPlaces().get(0);
			// The normal list and Krystilia's Wilderness list both need it
			List<SlayerLocationData.LocationEntry> both = new ArrayList<>();
			t.getLocations().stream().filter(l -> l.getKey().equals(key)).forEach(both::add);
			t.getWildernessLocations().stream().filter(l -> l.getKey().equals(key)).forEach(both::add);
			assertEquals(row[1] + "", 2, both.size());
			for (SlayerLocationData.LocationEntry l : both)
			{
				assertEquals(row[1] + "", Collections.singletonList(row[2]), l.getRequirements().getDiaries());
				// One fee for every Wilderness boss lair, not one per entry
				assertTrue(l.getNote(), l.getNote().contains("opens all the Wilderness boss lairs"));
				assertFalse(l.getNote(), l.getNote().contains("to enter"));
			}
			// Without the diary the lair is closed, and says what it needs
			RequirementChecker noDiary = r -> r.getDiaries().isEmpty() ? Collections.<String>emptyList()
				: Collections.singletonList(Diaries.text(r.getDiaries().get(0)));
			LocationOption lair = LocationPicker.pick(t, 5, 0, true, noDiary).stream()
				.filter(o -> o.getKey().equals(key)).findFirst().orElse(null);
			assertNotNull(lair);
			assertFalse(lair.isAccessible());
			assertEquals(Collections.singletonList(row[2].equals("WILDERNESS_HARD") ? "Hard Wilderness Diary"
				: "Medium Wilderness Diary"), lair.getMissingRequirements());
		}
		// Every diary in the data is one the plugin can read
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.LocationEntry l : allLocations(t))
			{
				if (l.getRequirements() != null)
				{
					for (String d : l.getRequirements().getDiaries())
					{
						assertTrue(t.getName() + " / " + l.getKey() + ": " + d, Diaries.VARBITS.containsKey(d));
					}
				}
			}
		}
	}

	@Test
	public void aBoostOpensAndClosesABoostableLair()
	{
		// The skills the plugin watches for boosts are the ones some place lists as boostable
		assertEquals(new java.util.TreeSet<>(Arrays.asList("HITPOINTS", "SLAYER")), BoostWatch.boostableSkills(data));
		SlayerLocationData.TaskEntry hounds = data.findTask(-1, "Hellhounds");
		int[] boosted = {89};
		RequirementChecker slayer89 = r ->
		{
			List<String> missing = new ArrayList<>();
			for (Map.Entry<String, Integer> e : r.getSkills().entrySet())
			{
				boolean boostable = r.getBoostable().contains(e.getKey());
				if (!RequirementChecker.meets(89, boosted[0], e.getValue(), boostable))
				{
					missing.add(RequirementChecker.skillText(e.getValue(), e.getKey(), boostable));
				}
			}
			return missing;
		};
		BoostWatch watch = new BoostWatch(BoostWatch.boostableSkills(data));
		Map<String, Integer> levels = new HashMap<>();
		levels.put("SLAYER", 89);
		levels.put("HITPOINTS", 99);
		assertFalse("the first look only remembers", watch.changed(levels));
		List<LocationOption> shown = LocationPicker.pick(hounds, 5, 0, false, slayer89);
		assertFalse(shown.stream().filter(LocationOption::isBossLair).findFirst().get().isAccessible());

		// A wild pie: 89 -> 91. The boost is noticed and the Cerberus lair opens, so the panel draws again
		boosted[0] = 91;
		levels.put("SLAYER", 91);
		assertTrue(watch.changed(levels));
		List<LocationOption> now = LocationPicker.pick(hounds, 5, 0, false, slayer89);
		assertTrue(now.stream().filter(LocationOption::isBossLair).findFirst().get().isAccessible());
		assertTrue(BoostWatch.placesChanged(now, shown));

		// Hitpoints going down in a fight is noticed, but changes no place here, so nothing is drawn again
		levels.put("HITPOINTS", 80);
		assertTrue(watch.changed(levels));
		assertFalse(BoostWatch.placesChanged(LocationPicker.pick(hounds, 5, 0, false, slayer89), now));
		assertFalse("no change", watch.changed(levels));

		// The boost wears off: the lair closes again
		boosted[0] = 89;
		levels.put("SLAYER", 89);
		assertTrue(watch.changed(levels));
		assertTrue(BoostWatch.placesChanged(LocationPicker.pick(hounds, 5, 0, false, slayer89), now));
	}

	@Test
	public void aBossCardSaysGoToNearestSkipsItsLair() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Blue dragons");
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY);
			JButton nearest = buttons(panel, "Go to nearest").get(0);
			assertEquals(SlayerPanel.NEAREST_TIP, nearest.getToolTipText());
			assertTrue(SlayerPanel.NEAREST_TIP.contains("boss lairs"));
			assertFalse(containsText(panel, SlayerPanel.BOSS_GO_HINT));
			panel.selectVariant("Vorkath");
			assertTrue(containsText(panel, SlayerPanel.BOSS_GO_HINT));
			panel.selectVariant("Blue dragon");
			assertFalse(containsText(panel, SlayerPanel.BOSS_GO_HINT));
		});
	}

	@Test
	public void aWildernessBossGetsALootingBagOnlyForItsOwnLair() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Bears");
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, true);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY);
			panel.selectVariant("Callisto");
			assertEquals("Callisto", panel.getSelectedVariant());
			assertTrue(containsText(panel, "Callisto's Den"));
			assertNotNull(carrySlot(panel, CarryItem.LOOTING_BAG));
			panel.selectVariant("Grizzly bear");
			assertNull(carrySlot(panel, CarryItem.LOOTING_BAG));
		});
	}

	@Test
	public void monstersWithoutAShownPlaceAreInTheTableAndSayWhy() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Ghosts");
		SlayerLocationData.Variant placeless = new SlayerLocationData.Variant();
		placeless.setName("Placeless demon");
		placeless.setLevel(new int[]{1, 1});
		placeless.setXp(new double[]{10, 10});
		List<SlayerLocationData.Variant> variants = new ArrayList<>(t.getVariants());
		variants.add(placeless);
		// The Wilderness setting off hides the places some of them live at
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		SlayerLocationData.Variant hidden = Variants.regular(t.getVariants()).stream()
			.filter(v -> !Variants.isAtAny(v, locations)).findFirst().orElse(null);
		// The revenants live in the Wilderness
		assertNotNull(hidden);
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, t.getInfo(), variants, locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			assertTrue(containsText(panel, "Placeless demon"));
			assertTrue(!containsText(panel, "Also counts"));
			panel.selectVariant("Placeless demon");
			assertEquals("Placeless demon", panel.getSelectedVariant());
			assertTrue(containsText(panel, Variants.NO_PLACE));
			panel.selectVariant(hidden.getName());
			assertEquals(hidden.getName(), panel.getSelectedVariant());
			assertTrue(containsText(panel, Variants.NOT_SHOWN));
		});
	}

	@Test
	public void panelDrawsEveryTaskForKrystiliaAndWithoutTheWilderness() throws Exception
	{
		List<String> problems = Collections.synchronizedList(new ArrayList<>());
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			for (SlayerLocationData.TaskEntry t : data.getTasks())
			{
				for (int master : new int[]{5, SlayerMasters.KRYSTILIA})
				{
					SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, master, 0, null);
					List<LocationOption> locations = LocationPicker.pick(t, master, 0, false);
					try
					{
						List<SlayerLocationData.Variant> counting = Variants.counting(t, master, 0);
						panel.showTask(state, t.getInfo(), counting, locations, true, true,
							CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
						for (SlayerLocationData.Variant v : Variants.listed(counting, locations))
						{
							panel.selectVariant(v.getName());
							if (containsText(panel, "Couldn't show the details") || !containsText(panel, "Found at"))
							{
								problems.add(t.getName() + " (" + master + ") / " + v.getName());
							}
						}
					}
					catch (RuntimeException e)
					{
						problems.add(t.getName() + " (" + master + "): " + e);
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
			for (SlayerLocationData.Variant v : Variants.regular(Variants.listed(t.getVariants(), locations)))
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
	public void slayerAndCombatTabShareOnePanel() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Abyssal demons");
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		SwingUtilities.invokeAndWait(() ->
		{
			CombatTabPanel combat = new CombatTabPanel(n -> { }, g -> { }, () -> { }, () -> { }, () -> { });
			OrganizerPanel organizer = new OrganizerPanel(combat);
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			organizer.setSlayerPanel(panel);
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				DropItems.EMPTY, null);
			assertTrue(containsText(organizer, "50 of 120 left"));

			// The Combat tab section replaces the slayer one, and the task is still there on the way back
			organizer.showCombatTab();
			assertTrue(!containsText(organizer, "50 of 120 left"));
			assertTrue(containsText(organizer, "Bank Tags plugin on"));
			organizer.showSlayer();
			assertTrue(containsText(organizer, "50 of 120 left"));
			assertTrue(!containsText(organizer, "Bank Tags plugin on"));

			// Slayer panel turned off
			organizer.setSlayerPanel(null);
			assertTrue(!containsText(organizer, "50 of 120 left"));
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

	@Test
	public void previewFindsTasksByName()
	{
		assertEquals("Abyssal demons", SlayerHelper.findByName(data, "Abyssal Demons").getName());
		assertEquals("Abyssal demons", SlayerHelper.findByName(data, "abyssal").getName());
		assertEquals("Lava dragons", SlayerHelper.findByName(data, "lava dragons").getName());
		assertNull(SlayerHelper.findByName(data, "not a task at all"));
		// By monster name, when no task name matches
		assertEquals("Lesser Nagua", SlayerHelper.findByName(data, "sulphur nagua").getName());
		assertEquals("Lesser Nagua", SlayerHelper.findByName(data, "nagua").getName());
		assertNull(SlayerHelper.findByName(data, "  "));
		assertEquals(SlayerMasters.KONAR, SlayerMasters.idForName("Konar"));
		assertEquals(0, SlayerMasters.idForName("demons"));
	}

	@Test
	public void previewShowsABannerWithStop() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Abyssal demons");
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 100, 100, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		boolean[] stopped = {false};
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.setPreview(true, () -> stopped[0] = true);
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				stubItems(ItemValueMode.GRAND_EXCHANGE));
			assertTrue(containsText(panel, "Preview, not your current task"));
			javax.swing.JButton stop = findButton(panel, "Stop");
			assertNotNull(stop);
			stop.doClick();
			panel.setPreview(false, null);
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY,
				stubItems(ItemValueMode.GRAND_EXCHANGE));
			assertTrue(!containsText(panel, "Preview, not your current task"));
		});
		assertTrue(stopped[0]);
	}

	private static javax.swing.JButton findButton(Component c, String text)
	{
		if (c instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) c).getText()))
		{
			return (javax.swing.JButton) c;
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				javax.swing.JButton b = findButton(k, text);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
	}

	/**
	 * Lays the panel out at the sidebar's real width and checks that no wrapped label needs more room than it gets,
	 * which would cut its text off on the right, and that no label shows a character its font lacks.
	 */
	@Test
	public void noTextIsCutOffAtTheSidebarWidth() throws Exception
	{
		Set<String> problems = Collections.synchronizedSet(new java.util.TreeSet<>());
		SwingUtilities.invokeAndWait(() ->
		{
			// The client's own look and feel: its buttons and insets set how much room the text gets
			javax.swing.LookAndFeel before = javax.swing.UIManager.getLookAndFeel();
			try
			{
				javax.swing.UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());
			}
			catch (javax.swing.UnsupportedLookAndFeelException e)
			{
				throw new IllegalStateException(e);
			}
			try
			{
				drawAtSidebarWidth(problems);
			}
			finally
			{
				try
				{
					javax.swing.UIManager.setLookAndFeel(before);
				}
				catch (javax.swing.UnsupportedLookAndFeelException e)
				{
					throw new IllegalStateException(e);
				}
			}
		});
		assertTrue(problems.size() + " labels cut off or with a glyph their font lacks:\n" + String.join("\n", problems), problems.isEmpty());
	}

	private void drawAtSidebarWidth(Set<String> problems)
	{
		{
			CombatTabPanel combat = new CombatTabPanel(n -> { }, g -> { }, () -> { }, () -> { }, () -> { });
			OrganizerPanel organizer = new OrganizerPanel(combat);
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			organizer.setSlayerPanel(panel);
			panel.setGear(BIG_BANK);
			panel.setPreview(true, () -> { });
			panel.showRoute(Route.shortestPath("Catacombs of Kourend"));
			java.awt.Container host = organizer.getWrappedPanel();
			for (SlayerLocationData.TaskEntry t : data.getTasks())
			{
				SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
				for (boolean wilderness : new boolean[]{false, true})
				{
					List<LocationOption> locations = LocationPicker.pick(t, 5, 0, wilderness);
					panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true,
						CollectionLog.Snapshot.EMPTY, stubItems(ItemValueMode.GRAND_EXCHANGE));
					for (SlayerLocationData.Variant v : Variants.listed(t.getVariants(), locations))
					{
						panel.selectVariant(v.getName());
						host.setSize(net.runelite.client.ui.PluginPanel.PANEL_WIDTH
							+ net.runelite.client.ui.PluginPanel.SCROLLBAR_WIDTH, 800);
						layOut(host);
						clipped(host, t.getName(), problems);
						missingGlyphs(host, problems);
					}
				}
			}
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

	private static void clipped(Component c, String task, Set<String> problems)
	{
		if (!c.isVisible())
		{
			return;
		}
		if (c instanceof JLabel)
		{
			JLabel l = (JLabel) c;
			String text = l.getText();
			// The size the text really needs: a label given a fixed preferred size (the monster table's wrapped names)
			// would otherwise always look like it fits
			java.awt.Dimension natural = l.isPreferredSizeSet() && l.getUI() != null ? l.getUI().getPreferredSize(l)
				: l.getPreferredSize();
			if (text != null && text.startsWith("<html>")
				&& (natural.width > l.getWidth() + 1 || (l.isPreferredSizeSet() && natural.height > l.getHeight() + 1)))
			{
				problems.add(natural.width + "x" + natural.height + ">" + l.getWidth() + "x" + l.getHeight() + " "
					+ text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim());
			}
			else if (text != null && !text.isEmpty() && !text.startsWith("<html>") && l.getWidth() > 0)
			{
				// Plain text (the monster table's cells): its text and icon must fit inside the label's insets, or
				// Swing shows "..." in place of the end of it
				java.awt.Insets in = l.getInsets();
				int need = l.getFontMetrics(l.getFont()).stringWidth(text)
					+ (l.getIcon() != null ? l.getIcon().getIconWidth() + l.getIconTextGap() : 0);
				int room = l.getWidth() - in.left - in.right;
				if (need > room)
				{
					problems.add(need + ">" + room + " " + text);
				}
			}
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				clipped(child, task, problems);
			}
		}
	}

	/**
	 * The client's RuneScape fonts as their files, by font name (the bold one shares the plain one's family). The registered fonts can't be asked: Java falls back
	 * to another font for a character they lack, so they say they can show it, and the client then draws that fallback
	 * glyph as a box over the next letter.
	 */
	private static final java.util.Map<String, java.awt.Font> RAW_FONTS = new java.util.HashMap<>();

	static
	{
		for (String file : new String[]{"runescape.ttf", "runescape_small.ttf", "runescape_bold.ttf"})
		{
			try (java.io.InputStream in = net.runelite.client.ui.FontManager.class.getResourceAsStream(file))
			{
				java.awt.Font f = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
				RAW_FONTS.put(f.getFontName(), f);
			}
			catch (java.io.IOException | java.awt.FontFormatException | NullPointerException e)
			{
				throw new IllegalStateException("RuneLite font " + file, e);
			}
		}
	}

	@Test
	public void theRawFontsLackTheBullet()
	{
		// What the glyph check relies on: the files say what the registered fonts won't
		assertEquals(3, RAW_FONTS.size());
		for (java.awt.Font f : RAW_FONTS.values())
		{
			assertFalse(f.getFontName(), f.canDisplay('\u2022'));
			assertTrue(f.getFontName(), f.canDisplay('\u00bb'));
		}
		for (java.awt.Font f : new java.awt.Font[]{net.runelite.client.ui.FontManager.getRunescapeFont(),
			net.runelite.client.ui.FontManager.getRunescapeSmallFont(), net.runelite.client.ui.FontManager.getRunescapeBoldFont()})
		{
			assertTrue(f.getFontName(), RAW_FONTS.containsKey(f.getFontName()));
		}
	}

	/**
	 * Every character a label or button shows must be in its font's file: the RuneScape fonts lack many symbols (a
	 * bullet, arrows, curly quotes), and the client draws a missing one as a box that overlaps the next letter.
	 */
	private static void missingGlyphs(Component c, Set<String> problems)
	{
		if (!c.isVisible())
		{
			return;
		}
		String text = c instanceof JLabel ? ((JLabel) c).getText()
			: c instanceof javax.swing.AbstractButton ? ((javax.swing.AbstractButton) c).getText() : null;
		if (text != null && !text.isEmpty())
		{
			String shown = text.startsWith("<html>") ? text.replaceAll("<[^>]*>", "").replace("&lt;", "<")
				.replace("&gt;", ">").replace("&amp;", "&") : text;
			java.awt.Font raw = RAW_FONTS.get(c.getFont().getFontName());
			int bad = raw == null ? -1 : raw.canDisplayUpTo(shown);
			if (bad >= 0)
			{
				problems.add(String.format("U+%04X not in %s: %s", (int) shown.charAt(bad), raw.getFontName(),
					shown.trim()));
			}
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				missingGlyphs(child, problems);
			}
		}
	}

	private static List<String> bossNames(List<SlayerLocationData.Variant> variants)
	{
		List<String> out = new ArrayList<>();
		Variants.bosses(variants).forEach(v -> out.add(v.getName()));
		Collections.sort(out);
		return out;
	}

	@Test
	public void bossesThatDontCountForTheMasterAreLeftOut() throws Exception
	{
		// Krystilia (wiki Krystilia page): "The King Black Dragon and lava dragons do not count"; for spiders only
		// Venenatis and Spindel also count; her kills only count in the Wilderness
		SlayerLocationData.TaskEntry black = data.findTask(-1, "Black dragons");
		assertEquals(Collections.singletonList("King Black Dragon"), bossNames(Variants.counting(black, 5, 0)));
		assertEquals(Collections.emptyList(), bossNames(Variants.counting(black, SlayerMasters.KRYSTILIA, 0)));
		assertEquals(Collections.singletonList("Krystilia"), Variants.byName(black.getVariants(), "King Black Dragon").getNotMasters());
		SlayerLocationData.TaskEntry spiders = data.findTask(-1, "Spiders");
		assertEquals(Arrays.asList("Spindel", "Venenatis"), bossNames(Variants.counting(spiders, SlayerMasters.KRYSTILIA, 0)));
		assertEquals(Arrays.asList("Araxxor", "Sarachnis", "Spindel", "Venenatis"), bossNames(Variants.counting(spiders, 6, 0)));
		SlayerLocationData.TaskEntry greater = data.findTask(-1, "Greater demons");
		assertEquals(Collections.emptyList(), bossNames(Variants.counting(greater, SlayerMasters.KRYSTILIA, 0)));
		assertEquals(Arrays.asList("Callisto", "Artio").size(),
			bossNames(Variants.counting(data.findTask(-1, "Bears"), SlayerMasters.KRYSTILIA, 0)).size());
		// Eldric only for Vannaka; Branda not for Konar or Krystilia
		SlayerLocationData.TaskEntry ice = data.findTask(-1, "Ice giants");
		assertEquals(Collections.singletonList("Eldric the Ice King"), bossNames(Variants.counting(ice, 3, 0)));
		assertEquals(Collections.emptyList(), bossNames(Variants.counting(ice, 5, 0)));
		assertEquals(Collections.emptyList(), bossNames(Variants.counting(ice, SlayerMasters.KRYSTILIA, 0)));
		SlayerLocationData.TaskEntry fire = data.findTask(-1, "Fire giants");
		assertEquals(Collections.singletonList("Branda the Fire Queen"), bossNames(Variants.counting(fire, 5, 0)));
		assertEquals(Collections.emptyList(), bossNames(Variants.counting(fire, SlayerMasters.KONAR, 0)));
		// Unknown master: every boss in the data
		assertEquals(bossNames(black.getVariants()), bossNames(Variants.counting(black, 0, 0)));
		// Konar's area: only a lair that counts as her place (the Alchemical Hydra's, in the Karuulm Slayer Dungeon)
		SlayerLocationData.TaskEntry hydras = data.findTask(-1, "Hydras");
		int karuulm = place(hydras, "karuulm_slayer_dungeon").getAreaId();
		assertEquals(Collections.singletonList("Alchemical Hydra"), bossNames(Variants.counting(hydras, SlayerMasters.KONAR, karuulm)));
		// The regular monsters are never left out
		assertEquals(Variants.regular(black.getVariants()), Variants.regular(Variants.counting(black, SlayerMasters.KRYSTILIA, 0)));
		// So loot from the King Black Dragon isn't taken as Krystilia task loot, though "black dragon" is in his name
		List<String> names = new ArrayList<>();
		Variants.counting(black, SlayerMasters.KRYSTILIA, 0).forEach(v -> names.add(v.getName()));
		List<String> not = Variants.notCounting(black, SlayerMasters.KRYSTILIA, 0);
		assertEquals(Collections.singletonList("King Black Dragon"), not);
		assertFalse(TaskMonsters.matches("King Black Dragon", "Black dragons", names, not));
		assertTrue(TaskMonsters.matches("Black dragon", "Black dragons", names, not));
		assertTrue(TaskMonsters.matches("King Black Dragon", "Black dragons", names, Collections.emptyList()));

		// The panel for Krystilia's black dragons has no King Black Dragon row
		SlayerTaskState state = new SlayerTaskState(black.getTaskId(), black.getName(), 50, 120, SlayerMasters.KRYSTILIA, 0, null);
		List<LocationOption> her = LocationPicker.pick(black, SlayerMasters.KRYSTILIA, 0, false);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, black.getInfo(), Variants.counting(black, SlayerMasters.KRYSTILIA, 0), her, true, true,
				CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			assertNull(withTooltip(panel, SlayerPanel.bossTooltip("King Black Dragon")));
			assertFalse(containsText(panel, "King Black Dragon"));
		});
	}

	@Test
	public void aWildernessBossWithTheWildernessOffSaysWhyItHasNoLair() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Spiders");
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		List<SlayerLocationData.Variant> variants = Variants.counting(t, 5, 0);
		SlayerLocationData.Variant venenatis = Variants.byName(variants, "Venenatis");
		assertTrue(Variants.listed(variants, locations).contains(venenatis));
		assertEquals(Collections.singletonList(Variants.BOSS_IN_WILDERNESS), Variants.foundAt(venenatis, locations));
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, t.getInfo(), variants, locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			panel.selectVariant("Venenatis");
			assertEquals("Venenatis", panel.getSelectedVariant());
			assertTrue(containsText(panel, "Show Wilderness locations"));
			assertFalse(containsText(panel, Variants.NOT_SHOWN));
			// No lair card is listed, so no "use Go on the lair's card below"
			assertFalse(containsText(panel, SlayerPanel.BOSS_GO_HINT));
			// A boss outside the Wilderness still names its lair, and its lair card is there to press
			panel.selectVariant("Sarachnis");
			assertTrue(containsText(panel, "Sarachnis' crypt"));
			assertTrue(containsText(panel, SlayerPanel.BOSS_GO_HINT));
		});
	}

	@Test
	public void aBossCardLeavesOutTheTaskAdviceForTheRegularMonsters() throws Exception
	{
		SlayerLocationData.TaskEntry t = data.findTask(-1, "Gargoyles");
		assertTrue(t.getInfo().getRequiredItems().toLowerCase().contains("rock hammer"));
		SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
		List<LocationOption> locations = LocationPicker.pick(t, 5, 0, false);
		SlayerLocationData.TaskEntry dags = data.findTask(-1, "Dagannoth");
		SlayerTaskState dagState = new SlayerTaskState(dags.getTaskId(), dags.getName(), 50, 120, 5, 0, null);
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			// The gargoyles: the task's attack advice and what to bring
			assertTrue(hasLabel(panel, "Attack"));
			assertTrue(containsText(panel, "<b>Bring</b>"));
			// The Grotesque Guardians: their own facts only, no rock hammer, no task-wide attack style or notes
			panel.selectVariant("Grotesque Guardians");
			assertEquals("Grotesque Guardians", panel.getSelectedVariant());
			assertFalse(hasLabel(panel, "Attack"));
			assertFalse(containsText(panel, "<b>Bring</b>"));
			assertFalse(containsText(panel, "<b>Weakness</b>"));
			assertFalse(containsText(panel, "<b>They attack with</b>"));
			assertFalse(containsText(panel, "ock hammer"));
			assertTrue(hasLabel(panel, "Level"));
			assertTrue(hasLabel(panel, "Found at"));

			// Dagannoth Rex is weak to magic; the task's "Melee or ranged" isn't shown with him
			panel.showTask(dagState, dags.getInfo(), dags.getVariants(), LocationPicker.pick(dags, 5, 0, false), true, true,
				CollectionLog.Snapshot.EMPTY, DropItems.EMPTY);
			assertTrue(hasLabel(panel, "Attack"));
			panel.selectVariant("Dagannoth Rex");
			assertEquals("Dagannoth Rex", panel.getSelectedVariant());
			assertFalse(hasLabel(panel, "Attack"));
			assertTrue(hasLabel(panel, "Level"));
		});
	}

	@Test
	public void lairFixesFromTheWiki()
	{
		// Boostable levels: Cerberus (wild pies on a hellhound task) and K'ril's river (70 Hitpoints, boostable); not
		// the Kraken ("cannot be boosted") or Kree'arra ("cannot be boosted")
		assertEquals(Collections.singletonList("SLAYER"), lairOf("Hellhounds", "Cerberus").getRequirements().getBoostable());
		assertEquals(Collections.singletonList("HITPOINTS"), lairOf("Greater demons", "K'ril Tsutsaroth").getRequirements().getBoostable());
		assertTrue(lairOf("Cave kraken", "Kraken").getRequirements().getBoostable().isEmpty());
		assertTrue(lairOf("Aviansies", "Kree'arra").getRequirements().getBoostable().isEmpty());
		// Every other Slayer boss's level can be boosted on task (wiki Temporary skill boost/Slayer; Slayer task/Araxytes:
		// "boost from level 87 Slayer to kill Araxxor")
		String[][] boostedSlayer = {{"Spiders", "Araxxor"}, {"Abyssal demons", "Abyssal Sire"},
			{"Smoke devils", "Thermonuclear smoke devil"}, {"Hydras", "Alchemical Hydra"},
			{"Gargoyles", "Grotesque Guardians"}};
		for (String[] b : boostedSlayer)
		{
			SlayerLocationData.Requirements r = lairOf(b[0], b[1]).getRequirements();
			assertEquals(b[1], Collections.singletonList("SLAYER"), r.getBoostable());
			assertTrue(b[1], r.getSkills().containsKey("SLAYER"));
		}
		// 88 Slayer plus a mature Slayer's respite (+4) opens Araxxor's 92
		assertTrue(RequirementChecker.meets(88, 92, 92, true));
		// Obor and Bryophyta respawn without a key: the key opens the gate once and the chest
		for (String[] b : new String[][]{{"Hill giants", "Obor"}, {"Moss giants", "Bryophyta"}})
		{
			String note = lairOf(b[0], b[1]).getNote();
			assertFalse(b[1] + ": " + note, note.contains("per kill"));
			assertTrue(b[1] + ": " + note, note.contains("first time"));
		}
		// Bryophyta's gate is just east of the moss giants
		assertTrue(lairOf("Moss giants", "Bryophyta").getNote().contains("east of"));
		// The Abyssal Nexus needs a first visit to the Abyss, by either route
		assertTrue(lairOf("Abyssal demons", "Abyssal Sire").getNote().contains("first visit to the Abyss"));
		// Scorpia's offspring live only in her lair
		SlayerLocationData.TaskEntry scorpions = data.findTask(-1, "Scorpions");
		assertEquals(Variants.byName(scorpions.getVariants(), "Scorpia").getPlaces(),
			Variants.byName(scorpions.getVariants(), "Scorpia's offspring").getPlaces());
		assertTrue(Variants.byName(scorpions.getVariants(), "Scorpia's offspring").isRegular());
		// One King Black Dragon lair, the boss's: the guide's place for it is gone, and Go to nearest never goes there
		SlayerLocationData.TaskEntry black = data.findTask(-1, "Black dragons");
		assertNull(place(black, "king_black_dragon_lair"));
		assertEquals(1, allLocations(black).stream().filter(l -> l.getLabel().contains("King Black Dragon")).count());
		assertFalse(Variants.byName(black.getVariants(), "Black dragon").getPlaces().contains("king_black_dragon_lair"));
		assertTrue(LocationPicker.nearestTargets(LocationPicker.pick(black, 5, 0, true)).stream()
			.noneMatch(l -> l.getLabel().contains("King Black Dragon")));
		// The Lesser demons' place by the lair's entrance is a different place, and stays
		assertNotNull(place(data.findTask(-1, "Lesser demons"), "king_black_dragon_lair"));
	}
}
