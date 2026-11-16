package com.combatgearorganizer.slayer;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
		List<String> problems = Collections.synchronizedList(new ArrayList<>());
		SwingUtilities.invokeAndWait(() ->
		{
			SlayerPanel panel = new SlayerPanel(new NoIcons(), l -> { }, l -> { }, () -> { });
			for (SlayerLocationData.TaskEntry t : data.getTasks())
			{
				SlayerTaskState state = new SlayerTaskState(t.getTaskId(), t.getName(), 50, 120, 5, 0, null);
				List<LocationOption> locations = LocationPicker.pick(t, 5, 0, true);
				try
				{
					panel.showTask(state, t.getInfo(), t.getVariants(), locations, true, true, CollectionLog.Snapshot.EMPTY);
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
}
