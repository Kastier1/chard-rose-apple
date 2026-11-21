package com.combatgearorganizer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
	public void bySetUnsettedArmourThatDoesNotFitBesideTheSetsGetsABlockOfItsOwn()
	{
		// Seven sets and two columns of odd armour: wrapping one column under the sets would put one odd helm at the
		// end of the head row and the other in a row far below, so all of it starts the next block
		List<GearItem> items = manySets(7);
		items.add(gear(901, "Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 1));
		items.add(gear(902, "Verac's helm", GearSlot.HEAD, GearStyle.MELEE, 0));
		items.add(gear(903, "Dragon boots", GearSlot.FEET, GearStyle.MELEE, 0));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		Map<Integer, Integer> at = positions(layout);
		assertEquals(-1, layout[7]); // the sets' block keeps its last column free
		assertEquals(16, (int) at.get(901)); // the odd helms side by side in the next block's head row
		assertEquals(17, (int) at.get(902));
		assertEquals(24, (int) at.get(903)); // its feet row, under the first helm
		assertEquals(25, layout.length);
	}

	@Test
	public void bySetUnsettedArmourThatFitsGoesBesideTheSets()
	{
		List<GearItem> items = manySets(6);
		items.add(gear(901, "Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 1));
		items.add(gear(902, "Verac's helm", GearSlot.HEAD, GearStyle.MELEE, 0));
		items.add(gear(903, "Dragon boots", GearSlot.FEET, GearStyle.MELEE, 0));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		Map<Integer, Integer> at = positions(layout);
		assertEquals(6, (int) at.get(901));
		assertEquals(7, (int) at.get(902));
		assertEquals(22, (int) at.get(903)); // the feet row is the block's third
		assertEquals(23, layout.length);
	}

	@Test
	public void bySetUnsettedArmourWiderThanTheBankWrapsInItsOwnBlocks()
	{
		List<GearItem> items = manySets(2);
		for (int i = 0; i < 9; i++)
		{
			items.add(gear(501 + i, "Odd helm " + i, GearSlot.HEAD, GearStyle.MELEE, 20 - i));
		}
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		Map<Integer, Integer> at = positions(layout);
		// The sets' block has two rows; the odd helms fill the next block's head row and wrap into a third
		assertEquals(-1, layout[2]);
		assertEquals(16, (int) at.get(501));
		assertEquals(23, (int) at.get(508));
		assertEquals(24, (int) at.get(509));
		assertEquals(25, layout.length);
	}

	/** Each Barrows brother's weapon goes in his column under his armour; other weapons stay in the weapons rows. */
	@Test
	public void bySetSetWeaponsGoUnderTheirSet()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(24, "Karil's crossbow", GearSlot.WEAPON, GearStyle.RANGED, 50));
		items.add(gear(34, "Ahrim's staff", GearSlot.WEAPON, GearStyle.MAGIC, 40));
		items.add(gear(91, "Abyssal whip", GearSlot.WEAPON, GearStyle.MELEE, 80));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		int[] expected = {
			-1, 21, 31, -1, -1, -1, -1, -1,
			11, 22, 32, -1, -1, -1, -1, -1,
			12, 23, 33, -1, -1, -1, -1, -1,
			13, -1, -1, -1, -1, -1, -1, -1,
			-1, 24, 34, -1, -1, -1, -1, -1, // the sets' weapons row
			91, // weapons in no set
		};
		assertArrayEquals(expected, layout);
	}

	/** Tested in game: the Blood moon and Eclipse moon weapons aren't named after their set, but go under it too. */
	@Test
	public void bySetMoonWeaponsGoUnderTheirSets()
	{
		List<GearItem> items = Arrays.asList(
			gear(1, "Blood moon helm", GearSlot.HEAD, GearStyle.MELEE, 30),
			gear(2, "Blood moon chestplate", GearSlot.BODY, GearStyle.MELEE, 30),
			gear(3, "Blood moon tassets", GearSlot.LEGS, GearStyle.MELEE, 30),
			gear(4, "Dual macuahuitl", GearSlot.WEAPON, GearStyle.MELEE, 80),
			gear(11, "Eclipse moon helm", GearSlot.HEAD, GearStyle.RANGED, 30),
			gear(12, "Eclipse moon chestplate", GearSlot.BODY, GearStyle.RANGED, 30),
			gear(13, "Eclipse moon tassets", GearSlot.LEGS, GearStyle.RANGED, 30),
			gear(14, "Eclipse atlatl", GearSlot.WEAPON, GearStyle.RANGED, 80),
			gear(21, "Blue moon helm", GearSlot.HEAD, GearStyle.MAGIC, 30),
			gear(22, "Blue moon chestplate", GearSlot.BODY, GearStyle.MAGIC, 30),
			gear(23, "Blue moon tassets", GearSlot.LEGS, GearStyle.MAGIC, 30),
			gear(24, "Blue moon spear", GearSlot.WEAPON, GearStyle.MAGIC, 80)
		);
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		assertLinedUp(layout, 1, 2, 3, 4);
		assertLinedUp(layout, 11, 12, 13, 14);
		assertLinedUp(layout, 21, 22, 23, 24);
		assertEquals(27, layout.length); // three armour rows, then the weapons row ending at the Blue moon spear
	}

	@Test
	public void bySetSetWeaponWithoutItsArmourStaysWithTheWeapons()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(41, "Dharok's greataxe", GearSlot.WEAPON, GearStyle.MELEE, 90));
		items.add(gear(51, "Verac's flail", GearSlot.WEAPON, GearStyle.MELEE, 60));
		items.add(gear(52, "Verac's helm", GearSlot.HEAD, GearStyle.MELEE, 10));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		Map<Integer, Integer> at = positions(layout);
		// A lone Verac's helm is odd armour even with Verac's flail: one armour slot doesn't make a set
		assertEquals(3, column(layout, 52));
		assertTrue(at.get(52) < 8);
		// No Dharok's armour: the greataxe is in the weapons rows, not alone in a column of the grid, and so is the
		// flail; strongest first
		assertEquals(32, (int) at.get(41));
		assertEquals(33, (int) at.get(51));
	}

	@Test
	public void bySetWeaponsAndShieldsOfASetWithNoArmourStayWithTheWeapons()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(41, "Black d'hide shield", GearSlot.SHIELD, GearStyle.RANGED, 5));
		items.add(gear(42, "Black d'hide sword", GearSlot.WEAPON, GearStyle.MELEE, 5)); // made up: weapon and shield
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		int[] expected = {
			-1, 21, 31, -1, -1, -1, -1, -1,
			11, 22, 32, -1, -1, -1, -1, -1,
			12, 23, 33, -1, -1, -1, -1, -1,
			13, -1, -1, -1, -1, -1, -1, -1,
			42, -1, -1, -1, -1, -1, -1, -1,
			41,
		};
		assertArrayEquals(expected, layout);
	}

	@Test
	public void bySetSecondWeaponOfASetGoesWithTheWeapons()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(24, "Karil's crossbow", GearSlot.WEAPON, GearStyle.RANGED, 50));
		items.add(gear(25, "Karil's crossbow 0", GearSlot.WEAPON, GearStyle.RANGED, 1)); // degraded
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		assertLinedUp(layout, 21, 22, 23, 24);
		assertEquals(33, (int) positions(layout).get(24)); // the grid's weapons row, in Karil's column
		assertEquals(40, (int) positions(layout).get(25)); // the row after the grid, with the other weapons
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
			// The set's own weapon goes under it, however strong, without moving it or the others
			new GearItem[]{gear(24, "Karil's crossbow", GearSlot.WEAPON, GearStyle.RANGED, 6000)},
			// Nor does a lone piece's set weapon make that piece a set
			new GearItem[]{gear(51, "Verac's flail", GearSlot.WEAPON, GearStyle.MELEE, 6000),
				gear(52, "Verac's helm", GearSlot.HEAD, GearStyle.MELEE, 6000)},
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

	@Test
	public void bySetDoesNotDependOnTheTabOrder()
	{
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(61, "Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 6));
		items.add(gear(62, "Verac's helm", GearSlot.HEAD, GearStyle.MELEE, 6));
		items.add(gear(91, "Abyssal whip", GearSlot.WEAPON, GearStyle.MELEE, 80));
		int[] expected = LayoutBuilder.build(items, Grouping.BY_SET, true);
		for (long seed = 0; seed < 20; seed++)
		{
			List<GearItem> shuffled = new ArrayList<>(items);
			Collections.shuffle(shuffled, new Random(seed));
			assertArrayEquals(expected, LayoutBuilder.build(shuffled, Grouping.BY_SET, true));
		}
	}

	@Test
	public void bySetStyleTieGoesToTheStyleListedFirst()
	{
		// One melee and one magic piece: a melee set, so before the ranged set however weak it is
		List<GearItem> items = Arrays.asList(
			gear(1, "Infinity hat", GearSlot.HEAD, GearStyle.MAGIC, 1),
			gear(2, "Infinity top", GearSlot.BODY, GearStyle.MELEE, 1),
			gear(21, "Karil's coif", GearSlot.HEAD, GearStyle.RANGED, 50),
			gear(22, "Karil's leathertop", GearSlot.BODY, GearStyle.RANGED, 50)
		);
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		assertLinedUp(layout, 1, 2);
		assertEquals(0, column(layout, 1));
		assertEquals(1, column(layout, 21));
	}

	@Test
	public void bySetUtilitySetsComeAfterMagic()
	{
		// The classifier puts void in utility, so a void set goes after the magic sets
		List<GearItem> items = new ArrayList<>(threeSets);
		items.add(gear(1, "Void ranger helm", GearSlot.HEAD, GearStyle.UTILITY, 900));
		items.add(gear(2, "Elite void top", GearSlot.BODY, GearStyle.UTILITY, 900));
		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, false);
		assertLinedUp(layout, 1, 2);
		assertEquals(3, column(layout, 1));
		assertEquals(2, column(layout, 31));
	}

	@Test
	public void bySetExactlyEightSetsFillOneBlock()
	{
		int[] layout = LayoutBuilder.build(manySets(8), Grouping.BY_SET, true);
		// Two full rows, no second block and nothing after it
		assertEquals(16, layout.length);
		assertTrue(Arrays.stream(layout).noneMatch(i -> i == -1));

		// One odd helm more starts the next block, after a blank row, with only a head row
		List<GearItem> items = manySets(8);
		items.add(gear(901, "Helm of neitiznot", GearSlot.HEAD, GearStyle.MELEE, 1));
		int[] wrapped = LayoutBuilder.build(items, Grouping.BY_SET, true);
		assertEquals(25, wrapped.length);
		assertEquals(901, wrapped[24]);
		for (int i = 16; i < 24; i++)
		{
			assertEquals(-1, wrapped[i]);
		}
	}

	/**
	 * The player's own combat tab (issue #31), with each item's bonuses from the OSRS Wiki run through the real
	 * classifier: Bandos, Dharok's, Karil's, black d'hide, Ahrim's and Mystic each line up in one column, and the odd
	 * armour goes to the right in its row.
	 */
	@Test
	public void bySetRealTab()
	{
		// name, slot, stab, slash, crush, magic, range attack; stab, slash, crush, magic, range defence;
		// strength, ranged strength, magic damage %, prayer, attack speed
		Object[][] tab = {
			// melee
			{"Dual macuahuitl", GearSlot.WEAPON, 115, -4, 121, -4, 0, 0, 0, 0, 0, 0, 81, 0, 0, 0, 4},
			{"Abyssal whip", GearSlot.WEAPON, 0, 82, 0, 0, 0, 0, 0, 0, 0, 0, 82, 0, 0, 0, 4},
			{"Dragon longsword", GearSlot.WEAPON, 58, 69, -2, 0, 0, 0, 3, 2, 0, 0, 71, 0, 0, 0, 5},
			{"Dragon battleaxe", GearSlot.WEAPON, -2, 70, 65, 0, 0, 0, 0, 0, 0, -1, 85, 0, 0, 0, 6},
			{"Dragon dagger(p)", GearSlot.WEAPON, 40, 25, -4, 1, 0, 0, 0, 0, 1, 0, 40, 0, 0, 0, 4},
			{"Dragon defender", GearSlot.SHIELD, 25, 24, 23, -3, -2, 25, 24, 23, -3, -2, 6, 0, 0, 0, 0},
			{"Helm of neitiznot", GearSlot.HEAD, 0, 0, 0, 0, 0, 31, 29, 34, 3, 30, 3, 0, 0, 3, 0},
			{"Dharok's helm", GearSlot.HEAD, 0, 0, 0, -3, -1, 45, 48, 44, -1, 51, 0, 0, 0, 0, 0},
			{"Verac's helm", GearSlot.HEAD, 0, 0, 0, -6, -2, 55, 58, 54, 0, 56, 0, 0, 0, 3, 0},
			{"Bandos chestplate", GearSlot.BODY, 0, 0, 0, -15, -10, 98, 93, 105, -6, 133, 4, 0, 0, 1, 0},
			{"Fighter torso", GearSlot.BODY, 0, 0, 0, -40, 0, 62, 85, 62, -10, 67, 4, 0, 0, 0, 0},
			{"Dharok's platebody", GearSlot.BODY, 0, 0, 0, -30, -10, 122, 120, 107, -6, 132, 0, 0, 0, 0, 0},
			{"Proselyte hauberk", GearSlot.BODY, 0, 0, 0, -30, -15, 65, 63, 55, -6, 63, 0, 0, 0, 8, 0},
			{"Bandos tassets", GearSlot.LEGS, 0, 0, 0, -21, -7, 71, 63, 66, -4, 93, 2, 0, 0, 1, 0},
			{"Dharok's platelegs", GearSlot.LEGS, 0, 0, 0, -21, -11, 85, 82, 83, -4, 92, 0, 0, 0, 0, 0},
			{"Barrows gloves", GearSlot.HANDS, 12, 12, 12, 6, 12, 12, 12, 12, 6, 12, 12, 0, 0, 0, 0},
			{"Dragon boots", GearSlot.FEET, 0, 0, 0, -3, -1, 16, 17, 18, 0, 0, 4, 0, 0, 0, 0},
			{"Fire cape", GearSlot.CAPE, 1, 1, 1, 1, 1, 11, 11, 11, 11, 11, 4, 0, 0, 2, 0},
			{"Amulet of fury", GearSlot.NECK, 10, 10, 10, 10, 10, 15, 15, 15, 15, 15, 8, 0, 0, 5, 0},
			{"Berserker ring", GearSlot.RING, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 4, 0, 0, 0, 0},
			// ranged
			{"Magic shortbow (i)", GearSlot.WEAPON, 0, 0, 0, 0, 75, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4},
			{"Toxic blowpipe", GearSlot.WEAPON, 0, 0, 0, 0, 30, 0, 0, 0, 0, 0, 0, 20, 0, 0, 3},
			{"Armadyl crossbow", GearSlot.WEAPON, 0, 0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0, 1, 6},
			{"Eclipse atlatl", GearSlot.WEAPON, 0, 0, 0, 0, 87, 0, 0, 0, 0, 0, 40, 0, 0, 0, 4},
			{"Karil's coif", GearSlot.HEAD, 0, 0, 0, -1, 7, 6, 9, 12, 6, 10, 0, 0, 0, 0, 0},
			{"Karil's leathertop", GearSlot.BODY, 0, 0, 0, -15, 30, 47, 42, 50, 65, 57, 0, 0, 0, 0, 0},
			{"Black d'hide body", GearSlot.BODY, 0, 0, 0, -15, 30, 30, 38, 45, 45, 50, 0, 0, 0, 0, 0},
			{"Black d'hide chaps", GearSlot.LEGS, 0, 0, 0, -10, 17, 18, 20, 26, 23, 26, 0, 0, 0, 0, 0},
			{"Karil's leatherskirt", GearSlot.LEGS, 0, 0, 0, -10, 17, 26, 20, 28, 35, 33, 0, 0, 0, 0, 0},
			{"Ava's assembler", GearSlot.CAPE, 0, 0, 0, 0, 8, 1, 1, 1, 8, 2, 0, 2, 0, 0, 0},
			// magic
			{"Kodai wand", GearSlot.WEAPON, 0, 0, 0, 28, 0, 0, 3, 3, 20, 0, 0, 0, 15, 0, 4},
			{"Iban's staff (u)", GearSlot.WEAPON, 10, -1, 40, 10, 0, 2, 3, 1, 10, 0, 50, 0, 0, 0, 5},
			{"Ahrim's hood", GearSlot.HEAD, 0, 0, 0, 6, -2, 15, 13, 16, 6, 0, 0, 0, 1, 0, 0},
			{"Ahrim's robetop", GearSlot.BODY, 0, 0, 0, 30, -10, 52, 37, 63, 30, 0, 0, 0, 1, 0, 0},
			{"Ahrim's robeskirt", GearSlot.LEGS, 0, 0, 0, 22, -7, 33, 30, 36, 22, 0, 0, 0, 1, 0, 0},
			{"Mystic hat", GearSlot.HEAD, 0, 0, 0, 4, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0},
			{"Mystic robe top", GearSlot.BODY, 0, 0, 0, 20, 0, 0, 0, 0, 20, 0, 0, 0, 0, 0, 0},
			{"Mystic robe bottom", GearSlot.LEGS, 0, 0, 0, 15, 0, 0, 0, 0, 15, 0, 0, 0, 0, 0, 0},
			{"Occult necklace", GearSlot.NECK, 0, 0, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, 5, 2, 0},
		};
		List<GearItem> items = new ArrayList<>();
		Map<String, Integer> ids = new HashMap<>();
		int id = 1;
		for (Object[] row : tab)
		{
			String name = (String) row[0];
			GearSlot slot = (GearSlot) row[1];
			Bonuses b = Bonuses.builder()
				.stab((int) row[2]).slash((int) row[3]).crush((int) row[4]).magic((int) row[5]).range((int) row[6])
				.defStab((int) row[7]).defSlash((int) row[8]).defCrush((int) row[9]).defMagic((int) row[10])
				.defRange((int) row[11]).strength((int) row[12]).rangedStrength((int) row[13])
				.magicDamage((int) row[14]).prayer((int) row[15]).attackSpeed((int) row[16])
				.build();
			GearStyle style = GearClassifier.classify(name, slot, b);
			ids.put(name, id);
			items.add(gear(id++, name, slot, style, GearClassifier.power(style, name, slot, b)));
		}

		int[] layout = LayoutBuilder.build(items, Grouping.BY_SET, true);
		String grid = render(layout, items);
		assertEquals(items.size(), positions(layout).size());

		// The armour block: each set straight down one column, melee, ranged, magic, strongest first (Karil's coif
		// puts Karil's ahead of black d'hide); then the odd pieces in their rows
		String[] armour = {
			".|Dharok's helm|Karil's coif|.|Ahrim's hood|Mystic hat|Helm of neitiznot|Verac's helm",
			"Bandos chestplate|Dharok's platebody|Karil's leathertop|Black d'hide body|Ahrim's robetop|Mystic robe top"
				+ "|Fighter torso|Proselyte hauberk",
			"Bandos tassets|Dharok's platelegs|Karil's leatherskirt|Black d'hide chaps|Ahrim's robeskirt"
				+ "|Mystic robe bottom|.|.",
			".|.|.|.|.|.|Barrows gloves|.",
			".|.|.|.|.|.|Dragon boots|.",
		};
		String[] rows = grid.split("\n");
		for (int r = 0; r < armour.length; r++)
		{
			assertEquals(grid, armour[r], rows[r]);
		}
		// A blank row, then the weapons (melee, ranged, magic, strongest first) and the other slots as in By slot
		assertEquals(grid, ".|.|.|.|.|.|.|.", rows[5]);
		assertEquals(grid, (armour.length + 1) * 8, (int) positions(layout).get(ids.get("Dual macuahuitl")));
		assertEquals(grid, 0, column(layout, ids.get("Dragon defender")));
		assertEquals(grid, 0, column(layout, ids.get("Fire cape")));
		assertEquals(grid, ids.get("Berserker ring").intValue(), layout[layout.length - 1]);
	}

	/** The layout as a text grid of names ("." for a gap), a row per bank row, columns split by "|". */
	private static String render(int[] layout, List<GearItem> items)
	{
		Map<Integer, String> names = new HashMap<>();
		items.forEach(i -> names.put(i.getItemId(), i.getName()));
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < layout.length; i++)
		{
			out.append(layout[i] == -1 ? "." : names.get(layout[i]));
			out.append(i % 8 == 7 || i == layout.length - 1 ? '\n' : '|');
		}
		return out.toString();
	}
}
