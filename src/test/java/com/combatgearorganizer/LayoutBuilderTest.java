package com.combatgearorganizer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	// By set

	private static GearItem gear(int id, String name, GearSlot slot, GearStyle style, int power)
	{
		return new GearItem(id, name, slot, style, power);
	}

	/** Bank position of each item id in the layout. */
	private static Map<Integer, Integer> positions(int[] layout)
	{
		Map<Integer, Integer> positions = new HashMap<>();
		for (int i = 0; i < layout.length; i++)
		{
			if (layout[i] != -1)
			{
				positions.put(layout[i], i);
			}
		}
		return positions;
	}

	private static int column(int[] layout, int id)
	{
		Integer position = positions(layout).get(id);
		assertTrue("item " + id + " is laid out", position != null);
		return position % LayoutBuilder.BANK_COLUMNS;
	}

	/** Every id is in the same column, each in a lower row than the one before. */
	private static void assertLinedUp(int[] layout, int... ids)
	{
		Map<Integer, Integer> positions = positions(layout);
		int col = column(layout, ids[0]);
		for (int i = 1; i < ids.length; i++)
		{
			assertEquals("item " + ids[i] + " lines up with " + ids[0], col, column(layout, ids[i]));
			assertTrue(positions.get(ids[i]) > positions.get(ids[i - 1]));
		}
	}

	private final List<GearItem> threeSets = Arrays.asList(
		gear(11, "Bandos chestplate", GearSlot.BODY, GearStyle.MELEE, 8),
		gear(12, "Bandos tassets", GearSlot.LEGS, GearStyle.MELEE, 4),
		gear(13, "Bandos boots", GearSlot.FEET, GearStyle.MELEE, 2),
		gear(21, "Karil's coif", GearSlot.HEAD, GearStyle.RANGED, 6),
		gear(22, "Karil's leathertop", GearSlot.BODY, GearStyle.RANGED, 15),
		gear(23, "Karil's leatherskirt", GearSlot.LEGS, GearStyle.RANGED, 12),
		gear(31, "Ahrim's hood", GearSlot.HEAD, GearStyle.MAGIC, 6),
		gear(32, "Ahrim's robetop", GearSlot.BODY, GearStyle.MAGIC, 30),
		gear(33, "Ahrim's robeskirt", GearSlot.LEGS, GearStyle.MAGIC, 22)
	);

	@Test
	public void bySetPutsEachSetInOneColumn()
	{
		int[] layout = LayoutBuilder.build(threeSets, Grouping.BY_SET, false);
		int[] expected = {
			-1, 21, 31, -1, -1, -1, -1, -1, // head: Bandos has none
			11, 22, 32, -1, -1, -1, -1, -1, // body
			12, 23, 33, -1, -1, -1, -1, -1, // legs
			13, // feet; no hands, so no hands row
		};
		assertArrayEquals(expected, layout);
	}

	@Test
	public void bySetOrdersSetsByStyleThenStrength()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		// A weaker melee set: after Bandos. A ranged set stronger than Karil's: before it, still after melee.
		items.add(gear(41, "Dharok's helm", GearSlot.HEAD, GearStyle.MELEE, 1));
		items.add(gear(42, "Dharok's platebody", GearSlot.BODY, GearStyle.MELEE, 1));
		items.add(gear(51, "Masori mask", GearSlot.HEAD, GearStyle.RANGED, 20));
		items.add(gear(52, "Masori body", GearSlot.BODY, GearStyle.RANGED, 40));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		assertEquals(0, column(layout, 11));
		assertEquals(1, column(layout, 41));
		assertEquals(2, column(layout, 51));
		assertEquals(3, column(layout, 21));
		assertEquals(4, column(layout, 31));
	}

	@Test
	public void bySetStyleIsTheMajorityOfItsPieces()
	{
		// Two magic pieces and one melee piece: a magic set, so after the ranged set even though it's stronger
		List<GearItem> items = Arrays.asList(
			gear(1, "Void mage helm", GearSlot.HEAD, GearStyle.MAGIC, 50),
			gear(2, "Void knight top", GearSlot.BODY, GearStyle.MAGIC, 50),
			gear(3, "Void knight robe", GearSlot.LEGS, GearStyle.MELEE, 50),
			gear(21, "Karil's coif", GearSlot.HEAD, GearStyle.RANGED, 1),
			gear(22, "Karil's leathertop", GearSlot.BODY, GearStyle.RANGED, 1)
		);
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		assertEquals(0, column(layout, 21));
		assertLinedUp(layout, 1, 2, 3);
		assertEquals(1, column(layout, 1));
	}

	@Test
	public void bySetUnsettedArmourFillsColumnsToTheRightInItsRow()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(61, "Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 6));
		items.add(gear(62, "Verac's helm", GearSlot.HEAD, GearStyle.MELEE, 2)); // the only Verac's piece
		items.add(gear(63, "Mystic hat", GearSlot.HEAD, GearStyle.MAGIC, 9)); // the only Mystic piece
		items.add(gear(64, "Fighter torso", GearSlot.BODY, GearStyle.MELEE, 8));
		items.add(gear(65, "Barrows gloves", GearSlot.HANDS, GearStyle.MELEE, 24));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		int[] expected = {
			-1, 21, 31, 61, 62, 63, -1, -1, // head: sets, then melee by power, then magic
			11, 22, 32, 64, -1, -1, -1, -1, // body
			12, 23, 33, -1, -1, -1, -1, -1, // legs
			-1, -1, -1, 65, -1, -1, -1, -1, // hands: nothing in a set, so after the set columns
			13, // feet
		};
		assertArrayEquals(expected, layout);
	}

	@Test
	public void bySetSetWithOnlyOneSlotCountsAsUnsetted()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(71, "Void melee helm", GearSlot.HEAD, GearStyle.MELEE, 3));
		items.add(gear(72, "Void ranger helm", GearSlot.HEAD, GearStyle.RANGED, 3));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		// No void column: both helms are on the right of the head row
		assertEquals(3, column(layout, 71));
		assertEquals(4, column(layout, 72));
		assertEquals(0, column(layout, 11));
	}

	@Test
	public void bySetSecondPieceForASlotGoesWithTheUnsettedArmour()
	{
		List<GearItem> items = Arrays.asList(
			gear(1, "Void melee helm", GearSlot.HEAD, GearStyle.MELEE, 3),
			gear(2, "Void ranger helm", GearSlot.HEAD, GearStyle.RANGED, 2),
			gear(3, "Elite void top", GearSlot.BODY, GearStyle.MELEE, 5),
			gear(4, "Void knight robe", GearSlot.LEGS, GearStyle.MELEE, 5),
			gear(9, "Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 6),
			gear(21, "Karil's coif", GearSlot.HEAD, GearStyle.RANGED, 6),
			gear(22, "Karil's leathertop", GearSlot.BODY, GearStyle.RANGED, 15)
		);
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		int[] expected = {
			1, 21, 9, 2, -1, -1, -1, -1, // the ranger helm is after the melee helm of neitiznot
			3, 22, -1, -1, -1, -1, -1, -1,
			4,
		};
		assertArrayEquals(expected, layout);
	}

	@Test
	public void bySetColumnHoldsTheStrongestPieceForEachSlot()
	{
		// The ranger helm is stronger than the melee helm, so it's the void column's helm whatever its style
		List<GearItem> items = Arrays.asList(
			gear(1, "Void melee helm", GearSlot.HEAD, GearStyle.MELEE, 2),
			gear(2, "Void ranger helm", GearSlot.HEAD, GearStyle.RANGED, 9),
			gear(3, "Elite void top", GearSlot.BODY, GearStyle.RANGED, 5),
			gear(4, "Void knight robe", GearSlot.LEGS, GearStyle.RANGED, 5)
		);
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		int[] expected = {
			2, 1, -1, -1, -1, -1, -1, -1,
			3, -1, -1, -1, -1, -1, -1, -1,
			4,
		};
		assertArrayEquals(expected, layout);
	}

	/** n two-piece melee sets, set i strongest first, ids 100*i+1 (head) and 100*i+2 (body). */
	private static List<GearItem> manySets(int n)
	{
		String[] names = {"Ahrim's", "Dharok's", "Guthan's", "Karil's", "Torag's", "Verac's", "Torva", "Virtus",
			"Justiciar", "Ghostly"};
		List<GearItem> items = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			items.add(gear(100 * i + 1, names[i] + " helm", GearSlot.HEAD, GearStyle.MELEE, 100 - i));
			items.add(gear(100 * i + 2, names[i] + " body", GearSlot.BODY, GearStyle.MELEE, 100 - i));
		}
		return items;
	}

	@Test
	public void bySetWrapsPastEightSetsIntoAnotherBlock()
	{
		List<GearItem> items = manySets(10);
		items.add(gear(900, "Abyssal whip", GearSlot.WEAPON, GearStyle.MELEE, 1));

		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		Map<Integer, Integer> at = positions(layout);
		for (int i = 0; i < 8; i++)
		{
			assertEquals(i, (int) at.get(100 * i + 1));
			assertEquals(8 + i, (int) at.get(100 * i + 2));
		}
		// Sets 9 and 10 start the next block, in rows 3 and 4; then the weapons row
		assertEquals(16, (int) at.get(801));
		assertEquals(24, (int) at.get(802));
		assertEquals(17, (int) at.get(901));
		assertEquals(25, (int) at.get(902));
		assertEquals(32, (int) at.get(900));

		int[] spaced = LayoutBuilder.build(items, Grouping.BY_SET, true);
		Map<Integer, Integer> spacedAt = positions(spaced);
		assertEquals(24, (int) spacedAt.get(801)); // a blank row between the blocks
		assertEquals(48, (int) spacedAt.get(900)); // and before the weapons
		for (int i = 16; i < 24; i++)
		{
			assertEquals(-1, spaced[i]);
		}
	}

	@Test
	public void bySetUnsettedArmourWrapsAfterTheSets()
	{
		List<GearItem> items = manySets(7);
		items.add(gear(1, "Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 1));
		items.add(gear(2, "Verac's helm", GearSlot.HEAD, GearStyle.MELEE, 0));
		items.add(gear(3, "Dragon boots", GearSlot.FEET, GearStyle.MELEE, 0));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		Map<Integer, Integer> at = positions(layout);
		assertEquals(7, (int) at.get(1)); // the last column of the first block's head row
		assertEquals(23, (int) at.get(3)); // its feet row
		assertEquals(24, (int) at.get(2)); // the next block, which has only a head row
		assertEquals(25, layout.length);
	}

	@Test
	public void bySetAddingAnItemDoesNotMoveOtherSets()
	{
		List<GearItem> base = new ArrayList<>(threeSets);
		base.add(gear(65, "Barrows gloves", GearSlot.HANDS, GearStyle.MELEE, 24));
		int[] before = LayoutBuilder.build(base, Grouping.BY_SET, false);

		List<GearItem[]> additions = Arrays.asList(
			new GearItem[]{gear(61, "Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 600)}, // unsetted, strong
			new GearItem[]{gear(66, "Dragon boots", GearSlot.FEET, GearStyle.MELEE, 600)},
			new GearItem[]{gear(67, "Abyssal whip", GearSlot.WEAPON, GearStyle.MELEE, 600)},
			new GearItem[]{gear(68, "Amulet of fury", GearSlot.NECK, GearStyle.MELEE, 600)},
			new GearItem[]{gear(14, "Bandos chestplate (or)", GearSlot.BODY, GearStyle.MELEE, 1)},
			// A new set after the others (weakest magic set) leaves their columns alone
			new GearItem[]{gear(81, "Mystic hat", GearSlot.HEAD, GearStyle.MAGIC, 1),
				gear(82, "Mystic robe top", GearSlot.BODY, GearStyle.MAGIC, 1)}
		);
		for (GearItem[] added : additions)
		{
			List<GearItem> items = new ArrayList<>(base);
			items.addAll(Arrays.asList(added));
			int[] after = LayoutBuilder.build(items, Grouping.BY_SET, false);
			for (GearItem set : threeSets)
			{
				assertEquals(added[0].getName() + " moved " + set.getName(),
					column(before, set.getItemId()), column(after, set.getItemId()));
			}
		}
	}

	@Test
	public void bySetOtherSlotsGetRowsBelowLikeBySlot()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(91, "Abyssal whip", GearSlot.WEAPON, GearStyle.MELEE, 80));
		items.add(gear(92, "Toxic blowpipe", GearSlot.WEAPON, GearStyle.RANGED, 90));
		items.add(gear(93, "Dragon defender", GearSlot.SHIELD, GearStyle.MELEE, 10));
		items.add(gear(94, "Fire cape", GearSlot.CAPE, GearStyle.MELEE, 10));
		items.add(gear(95, "Shark", GearSlot.NONE, GearStyle.SUPPLIES, 0));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		int[] expected = {
			-1, 21, 31, -1, -1, -1, -1, -1,
			11, 22, 32, -1, -1, -1, -1, -1,
			12, 23, 33, -1, -1, -1, -1, -1,
			13, -1, -1, -1, -1, -1, -1, -1,
			91, 92, -1, -1, -1, -1, -1, -1, // weapons: melee, then ranged
			93, -1, -1, -1, -1, -1, -1, -1, // shield
			94, -1, -1, -1, -1, -1, -1, -1, // cape
			95, // supplies last
		};
		assertArrayEquals(expected, layout);
	}

	@Test
	public void bySetWithNoArmourIsBySlot()
	{
		List<GearItem> noArmour = Arrays.asList(
			item(2, GearSlot.WEAPON, GearStyle.MELEE, 100),
			item(4, GearSlot.WEAPON, GearStyle.MELEE, 150),
			item(6, GearSlot.NONE, GearStyle.SUPPLIES, 0));
		assertArrayEquals(LayoutBuilder.build(noArmour, Grouping.BY_SLOT, true),
			LayoutBuilder.build(noArmour, Grouping.BY_SET, true));
		assertEquals(0, LayoutBuilder.build(new ArrayList<>(), Grouping.BY_SET, true).length);
	}

	/**
	 * The player's own combat tab (issue #31): Bandos, Dharok's, Karil's, black d'hide, Ahrim's and Mystic each line
	 * up in one column; the odd helms and bodies go to the right. Styles are what the classifier gives; powers are
	 * rough but in the right order.
	 */
	@Test
	public void bySetRealTab()
	{
		List<GearItem> items = new ArrayList<>();
		int id = 1;
		Object[][] tab = {
			// melee
			{"Dual macuahuitl", GearSlot.WEAPON, GearStyle.MELEE, 250},
			{"Abyssal whip", GearSlot.WEAPON, GearStyle.MELEE, 230},
			{"Dragon longsword", GearSlot.WEAPON, GearStyle.MELEE, 200},
			{"Dragon battleaxe", GearSlot.WEAPON, GearStyle.MELEE, 190},
			{"Dragon dagger(p)", GearSlot.WEAPON, GearStyle.MELEE, 120},
			{"Dragon defender", GearSlot.SHIELD, GearStyle.MELEE, 40},
			{"Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 6},
			{"Dharok's helm", GearSlot.HEAD, GearStyle.MELEE, 0},
			{"Verac's helm", GearSlot.HEAD, GearStyle.MELEE, 0},
			{"Bandos chestplate", GearSlot.BODY, GearStyle.MELEE, 8},
			{"Fighter torso", GearSlot.BODY, GearStyle.MELEE, 8},
			{"Dharok's platebody", GearSlot.BODY, GearStyle.MELEE, 0},
			{"Proselyte hauberk", GearSlot.BODY, GearStyle.MELEE, 0},
			{"Bandos tassets", GearSlot.LEGS, GearStyle.MELEE, 4},
			{"Dharok's platelegs", GearSlot.LEGS, GearStyle.MELEE, 0},
			{"Barrows gloves", GearSlot.HANDS, GearStyle.MELEE, 36},
			{"Dragon boots", GearSlot.FEET, GearStyle.MELEE, 8},
			{"Fire cape", GearSlot.CAPE, GearStyle.MELEE, 30},
			{"Amulet of fury", GearSlot.NECK, GearStyle.MELEE, 36},
			{"Berserker ring", GearSlot.RING, GearStyle.MELEE, 8},
			// ranged
			{"Magic shortbow (i)", GearSlot.WEAPON, GearStyle.RANGED, 75},
			{"Toxic blowpipe", GearSlot.WEAPON, GearStyle.RANGED, 100},
			{"Armadyl crossbow", GearSlot.WEAPON, GearStyle.RANGED, 140},
			{"Eclipse atlatl", GearSlot.WEAPON, GearStyle.RANGED, 90},
			{"Karil's coif", GearSlot.HEAD, GearStyle.RANGED, 7},
			{"Karil's leathertop", GearSlot.BODY, GearStyle.RANGED, 15},
			{"Black d'hide body", GearSlot.BODY, GearStyle.RANGED, 30},
			{"Black d'hide chaps", GearSlot.LEGS, GearStyle.RANGED, 17},
			{"Karil's leatherskirt", GearSlot.LEGS, GearStyle.RANGED, 12},
			{"Ava's assembler", GearSlot.CAPE, GearStyle.RANGED, 10},
			// magic
			{"Kodai wand", GearSlot.WEAPON, GearStyle.MAGIC, 60},
			{"Iban's staff (u)", GearSlot.WEAPON, GearStyle.MAGIC, 40},
			{"Ahrim's hood", GearSlot.HEAD, GearStyle.MAGIC, 12},
			{"Ahrim's robetop", GearSlot.BODY, GearStyle.MAGIC, 60},
			{"Ahrim's robeskirt", GearSlot.LEGS, GearStyle.MAGIC, 44},
			{"Mystic hat", GearSlot.HEAD, GearStyle.MAGIC, 8},
			{"Mystic robe top", GearSlot.BODY, GearStyle.MAGIC, 40},
			{"Mystic robe bottom", GearSlot.LEGS, GearStyle.MAGIC, 30},
			{"Occult necklace", GearSlot.NECK, GearStyle.MAGIC, 120},
		};
		Map<String, Integer> ids = new HashMap<>();
		for (Object[] row : tab)
		{
			ids.put((String) row[0], id);
			items.add(gear(id++, (String) row[0], (GearSlot) row[1], (GearStyle) row[2], (int) row[3]));
		}

		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, true);
		assertEquals(items.size(), positions(layout).size());

		// Each set reads straight down one column, in this order: melee, ranged, magic, strongest first
		String[][] sets = {
			{"Bandos chestplate", "Bandos tassets"},
			{"Dharok's helm", "Dharok's platebody", "Dharok's platelegs"},
			{"Black d'hide body", "Black d'hide chaps"},
			{"Karil's coif", "Karil's leathertop", "Karil's leatherskirt"},
			{"Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt"},
			{"Mystic hat", "Mystic robe top", "Mystic robe bottom"},
		};
		for (int col = 0; col < sets.length; col++)
		{
			int[] setIds = Arrays.stream(sets[col]).mapToInt(ids::get).toArray();
			assertLinedUp(layout, setIds);
			assertEquals(sets[col][0], col, column(layout, setIds[0]));
		}

		// The rest of the armour to the right of the sets, in its own row
		int helmRow = positions(layout).get(ids.get("Karil's coif")) / 8;
		int bodyRow = positions(layout).get(ids.get("Karil's leathertop")) / 8;
		assertEquals(helmRow * 8 + 6, (int) positions(layout).get(ids.get("Helm of neitiznot")));
		assertEquals(helmRow * 8 + 7, (int) positions(layout).get(ids.get("Verac's helm")));
		assertEquals(bodyRow * 8 + 6, (int) positions(layout).get(ids.get("Fighter torso")));
		assertEquals(bodyRow * 8 + 7, (int) positions(layout).get(ids.get("Proselyte hauberk")));
		assertNotEquals(column(layout, ids.get("Barrows gloves")), column(layout, ids.get("Bandos chestplate")) + 1);

		// Weapons come after the armour block and a blank row
		int lastArmour = positions(layout).get(ids.get("Dragon boots"));
		int firstWeapon = positions(layout).get(ids.get("Dual macuahuitl"));
		assertEquals((lastArmour / 8 + 2) * 8, firstWeapon);

		System.out.println(render(layout, items));
	}

	/** The layout as a text grid, for looking at in the test output. */
	private static String render(int[] layout, List<GearItem> items)
	{
		Map<Integer, String> names = new HashMap<>();
		items.forEach(i -> names.put(i.getItemId(), i.getName()));
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < layout.length; i++)
		{
			String name = layout[i] == -1 ? "." : names.get(layout[i]);
			out.append(String.format("%-20.20s", name));
			if (i % 8 == 7 || i == layout.length - 1)
			{
				out.append('\n');
			}
		}
		return out.toString();
	}
}
