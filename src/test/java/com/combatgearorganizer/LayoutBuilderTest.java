package com.combatgearorganizer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class LayoutBuilderTest
{
	private static GearItem item(int id, GearSlot slot, GearStyle style, int power)
	{
		return new GearItem(id, "item" + id, slot, style, power);
	}

	private final List<GearItem> items = Arrays.asList(
		item(1, GearSlot.HEAD, GearStyle.MAGIC, 10),
		item(2, GearSlot.WEAPON, GearStyle.MELEE, 100),
		item(3, GearSlot.BODY, GearStyle.MELEE, 50),
		item(4, GearSlot.WEAPON, GearStyle.MELEE, 150),
		item(5, GearSlot.WEAPON, GearStyle.RANGED, 80),
		item(6, GearSlot.NONE, GearStyle.SUPPLIES, 0)
	);

	@Test
	public void byStyleStartsEachSectionOnNewRow()
	{
		int[] layout = LayoutBuilder.build(items, Grouping.BY_STYLE, false);
		int[] expected = {
			4, 2, 3, -1, -1, -1, -1, -1, // melee: strongest weapon first, then body
			5, -1, -1, -1, -1, -1, -1, -1, // ranged
			1, -1, -1, -1, -1, -1, -1, -1, // magic
			6, // supplies
		};
		assertArrayEquals(expected, layout);
	}

	@Test
	public void blankRowBetweenSections()
	{
		int[] layout = LayoutBuilder.build(items, Grouping.BY_STYLE, true);
		assertEquals(5, layout[16]); // melee row, blank row, ranged row
		for (int i = 8; i < 16; i++)
		{
			assertEquals(-1, layout[i]);
		}
	}

	@Test
	public void bySlotGroupsSlotsAndKeepsSuppliesLast()
	{
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SLOT, false);
		int[] expected = {
			4, 2, 5, -1, -1, -1, -1, -1, // weapons: melee (by power) then ranged
			1, -1, -1, -1, -1, -1, -1, -1, // head
			3, -1, -1, -1, -1, -1, -1, -1, // body
			6, // supplies
		};
		assertArrayEquals(expected, layout);
	}

	@Test
	public void sectionsWrapPastEightItems()
	{
		List<GearItem> many = new java.util.ArrayList<>();
		for (int i = 1; i <= 10; i++)
		{
			many.add(item(i, GearSlot.RING, GearStyle.MELEE, 100 - i));
		}
		many.add(item(99, GearSlot.HEAD, GearStyle.MAGIC, 1));
		int[] layout = LayoutBuilder.build(many, Grouping.BY_STYLE, false);
		assertEquals(17, layout.length);
		assertEquals(99, layout[16]);
	}
}
