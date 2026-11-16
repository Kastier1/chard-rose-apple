package com.combatgearorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OrganizeSummaryTest
{
	private static void add(List<GearItem> items, GearStyle style, int count)
	{
		for (int i = 0; i < count; i++)
		{
			items.add(new GearItem(items.size() + 1, "Item " + items.size(), GearSlot.WEAPON, style, 0));
		}
	}

	@Test
	public void countsMeleeRangedMagicAndTheRestAsOther()
	{
		List<GearItem> items = new ArrayList<>();
		add(items, GearStyle.MELEE, 14);
		add(items, GearStyle.RANGED, 12);
		add(items, GearStyle.MAGIC, 10);
		add(items, GearStyle.PRAYER, 4);
		add(items, GearStyle.UTILITY, 5);
		add(items, GearStyle.SUPPLIES, 3);
		assertEquals("48 items: 14 melee, 12 ranged, 10 magic, 12 other", OrganizeSummary.of(items));
	}

	@Test
	public void leavesOutStylesWithNoItems()
	{
		List<GearItem> items = new ArrayList<>();
		add(items, GearStyle.RANGED, 1);
		assertEquals("1 item: 1 ranged", OrganizeSummary.of(items));
		add(items, GearStyle.UTILITY, 2);
		assertEquals("3 items: 1 ranged, 2 other", OrganizeSummary.of(items));
		assertEquals("0 items", OrganizeSummary.of(Collections.emptyList()));
	}
}
