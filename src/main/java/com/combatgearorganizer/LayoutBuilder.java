package com.combatgearorganizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a list of classified items into a bank tag layout (an array of item ids, -1 for empty slots).
 */
public final class LayoutBuilder
{
	public static final int BANK_COLUMNS = 8;

	/**
	 * The By set layout's rows, top to bottom: the armour, then a set's own weapon and shield (Dharok's greataxe under
	 * Dharok's armour). Other slots, and weapons in no set, get rows of their own below, as in By slot.
	 */
	static final List<GearSlot> SET_ROWS = Collections.unmodifiableList(Arrays.asList(
		GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.HANDS, GearSlot.FEET, GearSlot.WEAPON, GearSlot.SHIELD));
	private static final Set<GearSlot> ARMOUR_SLOTS = Collections.unmodifiableSet(EnumSet.of(
		GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.HANDS, GearSlot.FEET));
	/** Slots whose items can be in a set's column; only armour in no set goes in the grid too. */
	private static final Set<GearSlot> SET_SLOTS = Collections.unmodifiableSet(EnumSet.copyOf(SET_ROWS));

	/** Melee, ranged, magic..., then strongest first: the order within a By slot section or a By set row. */
	private static final Comparator<GearItem> STYLE_THEN_POWER = Comparator.comparing(GearItem::getStyle)
		.thenComparing(Comparator.comparingInt(GearItem::getPower).reversed())
		.thenComparing(GearItem::getName, String.CASE_INSENSITIVE_ORDER)
		.thenComparingInt(GearItem::getItemId);

	/** Strongest first, whatever the style: picks a set's piece for each slot. */
	private static final Comparator<GearItem> POWER = Comparator.comparingInt(GearItem::getPower).reversed()
		.thenComparing(STYLE_THEN_POWER);

	private LayoutBuilder()
	{
	}

	public static int[] build(List<GearItem> items, Grouping grouping, boolean blankRowBetweenSections)
	{
		final List<List<Integer>> sections = new ArrayList<>();
		if (grouping == Grouping.BY_SET)
		{
			final List<GearItem> grid = new ArrayList<>();
			final List<GearItem> rest = new ArrayList<>();
			for (GearItem item : items)
			{
				// A weapon or shield goes in the grid only as part of a set; setBlocks hands it back otherwise
				final boolean inGrid = isArmourSlot(item.getSlot())
					|| (isSetSlot(item.getSlot()) && item.getSet() != null);
				(inGrid ? grid : rest).add(item);
			}
			sections.addAll(setBlocks(grid, rest));
			sectionsBySlot(rest).forEach(s -> sections.add(ids(s)));
		}
		else
		{
			final List<List<GearItem>> bySection = grouping == Grouping.BY_SLOT
				? sectionsBySlot(items)
				: sectionsByStyle(items);
			bySection.forEach(s -> sections.add(ids(s)));
		}

		final List<Integer> layout = new ArrayList<>();
		for (List<Integer> section : sections)
		{
			if (section.isEmpty())
			{
				continue;
			}

			if (!layout.isEmpty())
			{
				padToRowEnd(layout);
				if (blankRowBetweenSections)
				{
					for (int i = 0; i < BANK_COLUMNS; i++)
					{
						layout.add(-1);
					}
				}
			}

			layout.addAll(section);
		}

		return layout.stream().mapToInt(Integer::intValue).toArray();
	}

	private static List<Integer> ids(List<GearItem> section)
	{
		final List<Integer> ids = new ArrayList<>(section.size());
		section.forEach(i -> ids.add(i.getItemId()));
		return ids;
	}

	/** Whether the item goes in the By set grid whether or not it's in a set (supplies are always slot NONE). */
	public static boolean isArmourSlot(GearSlot slot)
	{
		return ARMOUR_SLOTS.contains(slot);
	}

	/** Whether an item in this slot can be in a set's By set column: armour, or the set's own weapon or shield. */
	public static boolean isSetSlot(GearSlot slot)
	{
		return SET_SLOTS.contains(slot);
	}

	/**
	 * The By set block: a row per {@link #SET_ROWS} slot and a column per set, so each set reads straight down one
	 * column, its weapon under its armour. Sets come melee, ranged, magic (then prayer, utility), strongest first, and
	 * wrap into another block below past the bank's 8 columns. Armour in no set (or the only piece of its set in the
	 * tab) goes in columns of its own, each row's pieces left to right: beside the last sets if it all fits there,
	 * otherwise in blocks of its own, so its pieces for a slot stay in one row. Each block is a list of full rows,
	 * leaving out slots with nothing in that block. Weapons and shields that end up in no set's column go to
	 * {@code rest}.
	 */
	private static List<List<Integer>> setBlocks(List<GearItem> grid, List<GearItem> rest)
	{
		final Map<String, List<GearItem>> bySet = new HashMap<>();
		final List<GearItem> unsetted = new ArrayList<>();
		for (GearItem item : grid)
		{
			if (item.getSet() == null)
			{
				unsetted.add(item);
			}
			else
			{
				bySet.computeIfAbsent(item.getSet(), s -> new ArrayList<>()).add(item);
			}
		}

		final List<GearSet> sets = new ArrayList<>();
		for (Map.Entry<String, List<GearItem>> e : bySet.entrySet())
		{
			final List<GearItem> pieces = e.getValue();
			final long slots = pieces.stream().map(GearItem::getSlot).distinct().count();
			if (slots < 2 || pieces.stream().noneMatch(i -> isArmourSlot(i.getSlot())))
			{
				// One piece (or only one slot's worth) of a set lines nothing up, nor does a weapon and shield alone
				pieces.forEach(i -> (isArmourSlot(i.getSlot()) ? unsetted : rest).add(i));
			}
			else
			{
				final GearSet set = new GearSet(e.getKey(), pieces);
				sets.add(set);
				set.extras.forEach(i -> (isArmourSlot(i.getSlot()) ? unsetted : rest).add(i));
			}
		}
		if (sets.isEmpty() && unsetted.isEmpty())
		{
			return Collections.emptyList();
		}
		sets.sort(Comparator.comparing((GearSet s) -> s.style)
			.thenComparing(Comparator.comparingInt((GearSet s) -> s.strength).reversed())
			.thenComparing(s -> s.name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(s -> s.name));

		final List<GearItem[]> setColumns = new ArrayList<>();
		for (GearSet set : sets)
		{
			setColumns.add(set.column);
		}
		// Armour in no set: column j holds each row's j-th item
		final List<GearItem[]> unsettedColumns = columns(unsetted, STYLE_THEN_POWER);

		final List<List<GearItem[]>> blocks = new ArrayList<>(inBlocks(setColumns));
		final List<GearItem[]> last = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
		if (last != null && last.size() + unsettedColumns.size() <= BANK_COLUMNS)
		{
			// It all fits beside the last sets
			final List<GearItem[]> joined = new ArrayList<>(last);
			joined.addAll(unsettedColumns);
			blocks.set(blocks.size() - 1, joined);
		}
		else
		{
			// Wrapping some of it under the sets would split a slot's odd pieces between two rows far apart
			blocks.addAll(inBlocks(unsettedColumns));
		}

		final List<List<Integer>> sections = new ArrayList<>();
		for (List<GearItem[]> blockColumns : blocks)
		{
			final List<Integer> rows = new ArrayList<>();
			for (int row = 0; row < SET_ROWS.size(); row++)
			{
				final int r = row;
				if (blockColumns.stream().allMatch(c -> c[r] == null))
				{
					continue;
				}
				for (int col = 0; col < BANK_COLUMNS; col++)
				{
					final GearItem item = col < blockColumns.size() ? blockColumns.get(col)[row] : null;
					rows.add(item != null ? item.getItemId() : -1);
				}
			}
			// The next section starts on a new row anyway, and the layout ends with its last item
			while (rows.get(rows.size() - 1) == -1)
			{
				rows.remove(rows.size() - 1);
			}
			sections.add(rows);
		}
		return sections;
	}

	/** The columns in blocks of at most the bank's width. */
	private static List<List<GearItem[]>> inBlocks(List<GearItem[]> columns)
	{
		final List<List<GearItem[]>> blocks = new ArrayList<>();
		for (int start = 0; start < columns.size(); start += BANK_COLUMNS)
		{
			blocks.add(columns.subList(start, Math.min(start + BANK_COLUMNS, columns.size())));
		}
		return blocks;
	}

	/** The items as columns of {@link #SET_ROWS}: each row's items in this order, left to right. */
	private static List<GearItem[]> columns(List<GearItem> items, Comparator<GearItem> order)
	{
		final List<GearItem[]> columns = new ArrayList<>();
		for (int row = 0; row < SET_ROWS.size(); row++)
		{
			final GearSlot slot = SET_ROWS.get(row);
			final List<GearItem> inRow = new ArrayList<>();
			items.stream().filter(i -> i.getSlot() == slot).forEach(inRow::add);
			inRow.sort(order);
			for (int col = 0; col < inRow.size(); col++)
			{
				if (col == columns.size())
				{
					columns.add(new GearItem[SET_ROWS.size()]);
				}
				columns.get(col)[row] = inRow.get(col);
			}
		}
		return columns;
	}

	/** A set with pieces for at least two slots in the tab. */
	private static final class GearSet
	{
		final String name;
		/** Its column of {@link #SET_ROWS}: its strongest piece for each slot. */
		final GearItem[] column;
		/**
		 * More pieces for a slot it already has (a second void helm). They go with the unsetted armour, so they can't
		 * widen the set and push the sets after it along.
		 */
		final List<GearItem> extras = new ArrayList<>();
		/**
		 * The column's armour's most common style; a tie goes to the style listed first (melee, ranged, magic...). Its
		 * weapon and shield don't count, so getting the set's weapon doesn't move the set.
		 */
		final GearStyle style;
		/** Sum of the column's armour's power. */
		final int strength;

		GearSet(String name, List<GearItem> pieces)
		{
			this.name = name;
			final List<GearItem[]> columns = columns(pieces, POWER);
			this.column = columns.get(0);
			for (GearItem[] extra : columns.subList(1, columns.size()))
			{
				Arrays.stream(extra).filter(Objects::nonNull).forEach(extras::add);
			}

			final Map<GearStyle, Integer> counts = new EnumMap<>(GearStyle.class);
			int sum = 0;
			for (GearItem piece : column)
			{
				if (piece != null && isArmourSlot(piece.getSlot()))
				{
					counts.merge(piece.getStyle(), 1, Integer::sum);
					sum += piece.getPower();
				}
			}
			GearStyle majority = null;
			for (Map.Entry<GearStyle, Integer> e : counts.entrySet())
			{
				if (majority == null || e.getValue() > counts.get(majority))
				{
					majority = e.getKey();
				}
			}
			this.style = majority;
			this.strength = sum;
		}
	}

	private static List<List<GearItem>> sectionsByStyle(List<GearItem> items)
	{
		Map<GearStyle, List<GearItem>> byStyle = new EnumMap<>(GearStyle.class);
		Arrays.stream(GearStyle.values()).forEach(s -> byStyle.put(s, new ArrayList<>()));
		items.forEach(i -> byStyle.get(i.getStyle()).add(i));

		Comparator<GearItem> order = Comparator.comparing(GearItem::getSlot)
			.thenComparing(Comparator.comparingInt(GearItem::getPower).reversed())
			.thenComparing(GearItem::getName, String.CASE_INSENSITIVE_ORDER)
			.thenComparingInt(GearItem::getItemId);

		List<List<GearItem>> sections = new ArrayList<>();
		for (List<GearItem> section : byStyle.values())
		{
			section.sort(order);
			sections.add(section);
		}
		return sections;
	}

	private static List<List<GearItem>> sectionsBySlot(List<GearItem> items)
	{
		// Non-equipables (supplies) always get their own section at the end
		Map<GearSlot, List<GearItem>> bySlot = new EnumMap<>(GearSlot.class);
		List<GearItem> supplies = new ArrayList<>();
		for (GearItem item : items)
		{
			if (item.getStyle() == GearStyle.SUPPLIES)
			{
				supplies.add(item);
			}
			else
			{
				bySlot.computeIfAbsent(item.getSlot(), s -> new ArrayList<>()).add(item);
			}
		}

		List<List<GearItem>> sections = new ArrayList<>();
		for (List<GearItem> section : bySlot.values())
		{
			section.sort(STYLE_THEN_POWER);
			sections.add(section);
		}
		supplies.sort(Comparator.comparing(GearItem::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(GearItem::getItemId));
		sections.add(supplies);
		return sections;
	}

	private static void padToRowEnd(List<Integer> layout)
	{
		while (layout.size() % BANK_COLUMNS != 0)
		{
			layout.add(-1);
		}
	}
}
