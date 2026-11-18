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

	/** The By set layout's armour rows, top to bottom. Other slots get rows of their own below, as in By slot. */
	static final List<GearSlot> SET_ROWS = Collections.unmodifiableList(Arrays.asList(
		GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.HANDS, GearSlot.FEET));
	private static final Set<GearSlot> ARMOUR_SLOTS = Collections.unmodifiableSet(EnumSet.copyOf(SET_ROWS));

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
			final List<GearItem> armour = new ArrayList<>();
			final List<GearItem> rest = new ArrayList<>();
			for (GearItem item : items)
			{
				(isArmourSlot(item.getSlot()) ? armour : rest).add(item);
			}
			sections.addAll(setBlocks(armour));
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

	/** Whether the item goes in the By set armour grid (supplies are always slot NONE). */
	public static boolean isArmourSlot(GearSlot slot)
	{
		return ARMOUR_SLOTS.contains(slot);
	}

	/**
	 * The By set armour block: a row per armour slot and a column per set, so each set reads straight down one column.
	 * Sets come melee, ranged, magic (then prayer, utility), strongest first; armour in no set (or the only piece of
	 * its set in the tab) fills the columns to the right in its own row. Columns past the bank's 8 wrap into another
	 * block below. Each block is a list of full rows, leaving out slots with nothing in that block.
	 */
	private static List<List<Integer>> setBlocks(List<GearItem> armour)
	{
		final Map<String, List<GearItem>> bySet = new HashMap<>();
		final List<GearItem> unsetted = new ArrayList<>();
		for (GearItem item : armour)
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
			if (slots < 2)
			{
				// One piece (or only one slot's worth) of a set lines nothing up
				unsetted.addAll(pieces);
			}
			else
			{
				final GearSet set = new GearSet(e.getKey(), pieces);
				sets.add(set);
				unsetted.addAll(set.extras);
			}
		}
		sets.sort(Comparator.comparing((GearSet s) -> s.style)
			.thenComparing(Comparator.comparingInt((GearSet s) -> s.strength).reversed())
			.thenComparing(s -> s.name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(s -> s.name));

		final List<GearItem[]> columns = new ArrayList<>();
		for (GearSet set : sets)
		{
			columns.add(set.column);
		}
		// Armour in no set: column j holds each row's j-th item
		columns.addAll(columns(unsetted, STYLE_THEN_POWER));

		final List<List<GearItem[]>> blocks = new ArrayList<>();
		for (int start = 0; start < columns.size(); start += BANK_COLUMNS)
		{
			blocks.add(columns.subList(start, Math.min(start + BANK_COLUMNS, columns.size())));
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
		/** The column's most common style; a tie goes to the style listed first (melee, ranged, magic...). */
		final GearStyle style;
		/** Sum of the column's power. */
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
				if (piece != null)
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
