package com.combatgearorganizer.slayer;

import com.combatgearorganizer.ItemValueMode;
import com.combatgearorganizer.ItemValues;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DropItemsTest
{
	private static final ItemValueMode GE = ItemValueMode.GRAND_EXCHANGE;
	private static final ItemValueMode HA = ItemValueMode.HIGH_ALCHEMY;

	@Test
	public void formatsLikeRuneLite()
	{
		assertEquals("999", ItemValues.format(999));
		assertEquals("35K", ItemValues.format(35_000));
		assertEquals("350K", ItemValues.format(350_000));
		assertEquals("1.2M", ItemValues.format(1_200_000));
	}

	@Test
	public void valueText()
	{
		DropItems.Item whip = new DropItems.Item(4151, 1_200_000, true);
		assertEquals("GE value: 1.2M", DropItems.valueText(GE, whip, null));
		assertEquals("High alch: 1.2M", DropItems.valueText(HA, whip, new int[]{1, 1}));

		DropItems.Item tips = new DropItems.Item(9189, 1_200, true);
		assertEquals("GE value: 30K-36K (1,200 each)", DropItems.valueText(GE, tips, new int[]{25, 30}));
		assertEquals("GE value: 6,000 (1,200 each)", DropItems.valueText(GE, tips, new int[]{5, 5}));

		DropItems.Item pet = new DropItems.Item(21992, 0, false);
		assertEquals("GE value: not tradeable", DropItems.valueText(GE, pet, null));
		assertEquals("High alch: none", DropItems.valueText(HA, pet, null));
		assertEquals("GE value: none", DropItems.valueText(GE, new DropItems.Item(1, 0, true), null));
	}

	@Test
	public void quantityText()
	{
		assertNull(DropItems.quantityText(null));
		assertNull(DropItems.quantityText(new int[]{1, 1}));
		assertEquals("5", DropItems.quantityText(new int[]{5, 5}));
		assertEquals("25-30", DropItems.quantityText(new int[]{25, 30}));
	}

	@Test
	public void lookupIgnoresCase()
	{
		DropItems.Item whip = new DropItems.Item(4151, 1, true);
		DropItems items = new DropItems(Collections.singletonMap("abyssal whip", whip), GE);
		assertSame(whip, items.get("Abyssal Whip"));
		assertNull(items.get("Abyssal dagger"));
		assertNull(DropItems.EMPTY.get("Abyssal whip"));
	}

	@Test
	public void tooltipHasNameRatesLogStatusAndValue()
	{
		SlayerLocationData.Drop d = new SlayerLocationData.Drop();
		d.setItem("Eternal gem");
		d.setRarity("1/352");
		d.setOnTask("1/200");
		d.setClog(true);
		DropItems.Item gem = new DropItems.Item(13227, 3_000_000, true);
		String tip = SlayerPanel.dropTooltip(d, "Greater abyssal demon", CollectionLog.Status.MISSING, gem, HA, true);
		for (String part : Arrays.asList("<b>Eternal gem</b>", "From Greater abyssal demon", "Drop rate: 1/352",
			"On task: 1/200", "Not in your collection log yet", "High alch: 3M"))
		{
			assertTrue(tip, tip.contains(part));
		}

		d.setOnTask(null);
		d.setClog(false);
		d.setQuantity(new int[]{25, 30});
		tip = SlayerPanel.dropTooltip(d, null, CollectionLog.Status.UNKNOWN, gem, GE, false);
		assertTrue(tip, !tip.contains("On task") && !tip.contains("collection log") && !tip.contains("From"));
		assertTrue(tip, tip.contains("Stack: 25-30"));
	}
}
