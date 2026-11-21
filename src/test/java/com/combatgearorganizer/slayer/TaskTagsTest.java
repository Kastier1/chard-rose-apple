package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.combatgearorganizer.slayer.SlayerLocationData.TaskInfo;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class TaskTagsTest
{
	private static TaskInfo info(String style, String prayer, String element)
	{
		TaskInfo i = new TaskInfo();
		i.setStyle(style);
		i.setProtectionPrayer(prayer);
		i.setElementalWeakness(element);
		return i;
	}

	@Test
	public void stylesInOrderWithNote()
	{
		TaskTags t = TaskTags.from(info("Melee or magic (barrage)", null, null));
		assertEquals(Arrays.asList(TaskTags.Style.MELEE, TaskTags.Style.MAGIC), t.getStyles());
		assertEquals("barrage", t.getStyleNote());

		assertEquals(Arrays.asList(TaskTags.Style.RANGED, TaskTags.Style.MELEE),
			TaskTags.from(info("Ranged or melee", null, null)).getStyles());
	}

	@Test
	public void anyStyleHasNoIcons()
	{
		TaskTags t = TaskTags.from(info("Any (fast weapon)", null, null));
		assertEquals(Collections.emptyList(), t.getStyles());
		assertEquals("fast weapon", t.getStyleNote());
	}

	@Test
	public void prayers()
	{
		TaskTags melee = TaskTags.from(info(null, "Protect from Melee", null));
		assertEquals(TaskTags.Prayer.MELEE, melee.getPrayer());
		assertFalse(melee.isPrayerOptional());

		assertTrue(TaskTags.from(info(null, "Protect from Melee if needed", null)).isPrayerOptional());
		assertEquals(TaskTags.Prayer.MISSILES, TaskTags.from(info(null, "Protect from Missiles", null)).getPrayer());
		assertNull(TaskTags.from(info(null, "None", null)).getPrayer());

		TaskTags optionalMagic = TaskTags.from(info(null,
			"None strictly needed (low damage); Protect from Magic optional.", null));
		assertEquals(TaskTags.Prayer.MAGIC, optionalMagic.getPrayer());
		assertTrue(optionalMagic.isPrayerOptional());
	}

	@Test
	public void elementalWeakness()
	{
		TaskTags t = TaskTags.from(info(null, null, "Air 30 to 50%"));
		assertEquals(1, t.getWeaknesses().size());
		assertEquals(new TaskTags.Weakness(TaskTags.Element.AIR, "30 to 50%", null), t.getWeaknesses().get(0));
		assertTrue(TaskTags.from(info(null, null, "")).getWeaknesses().isEmpty());
	}

	@Test
	public void weaknessPerVariant()
	{
		TaskTags wyrms = TaskTags.from(info(null, null, "Earth 50% (wyrms), Water 50% (lava strykewyrms)"));
		assertEquals(Arrays.asList(
			new TaskTags.Weakness(TaskTags.Element.EARTH, "50%", "wyrms"),
			new TaskTags.Weakness(TaskTags.Element.WATER, "50%", "lava strykewyrms")), wyrms.getWeaknesses());

		// A comma inside the brackets doesn't split the weakness
		TaskTags trolls = TaskTags.from(info(null, null,
			"Fire 35 to 100% (level 69 mountain trolls, ice trolls), Earth 50% (level 71 mountain trolls)"));
		assertEquals(2, trolls.getWeaknesses().size());
		assertEquals("level 69 mountain trolls, ice trolls", trolls.getWeaknesses().get(0).getVariants());
	}

	@Test
	public void prayerThatChangesListsEveryOption()
	{
		TaskTags hydras = TaskTags.from(info(null,
			"alternate Protect from Missiles / Protect from Magic to match its attack rotation each phase", null));
		assertEquals(Arrays.asList(TaskTags.Prayer.MISSILES, TaskTags.Prayer.MAGIC), hydras.getPrayers());
		assertTrue(hydras.isPrayerNeedsDetail());

		TaskTags ankou = TaskTags.from(info(null, "Protect from Melee (Protect from Missiles/Magic in Stronghold of Security)", null));
		assertEquals(Arrays.asList(TaskTags.Prayer.MELEE, TaskTags.Prayer.MISSILES, TaskTags.Prayer.MAGIC), ankou.getPrayers());

		assertFalse(TaskTags.from(info(null, "Protect from Melee", null)).isPrayerNeedsDetail());
		assertFalse(TaskTags.from(info(null, "Protect from Melee if needed", null)).isPrayerNeedsDetail());
	}

	@Test
	public void noInfo()
	{
		TaskTags t = TaskTags.from(null);
		assertTrue(t.getStyles().isEmpty());
		assertNull(t.getPrayer());
		assertTrue(t.getWeaknesses().isEmpty());
	}

	@Test
	public void elementNotesAreDroppedForASelectedMonster()
	{
		assertEquals("Dragonbane weapons also receive their usual bonuses.", SlayerPanel.withoutElementNotes(
			"Earth spells: 50% elemental weakness on standard Wyrms. Dragonbane weapons also receive their usual bonuses."));
		assertEquals("Crush attacks are especially effective against their chitin.",
			SlayerPanel.withoutElementNotes("Crush attacks are especially effective against their chitin."));
		assertNull(SlayerPanel.withoutElementNotes("Weak to fire spells (50%)."));
		assertEquals("Water spells and demonbane weapons.", SlayerPanel.withoutElementNotes("Water spells and demonbane weapons."));
	}

	@Test
	public void previewCutsAtAWord()
	{
		assertEquals("Short note", SlayerPanel.preview("Short note"));
		assertEquals("Food or stat restore...", SlayerPanel.preview("Food or stat restore if the gaze lowers Strength."));
		// A long drawer title leaves less room for the preview
		assertEquals("Food or stat...", SlayerPanel.preview("Food or stat restore if the gaze lowers Strength.", 15));
		assertEquals("Food or stat restore...", SlayerPanel.preview("Food or stat restore if the gaze lowers Strength.", 40));
	}
}
