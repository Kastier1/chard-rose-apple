package com.combatgearorganizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a list of classified items into a bank tag layout (an array of item ids, -1 for empty slots).
 */
public final class LayoutBuilder
{
	public static final int BANK_COLUMNS = 8;

	private LayoutBuilder()
	{
	}

	public static int[] build(List<GearItem> items, Grouping grouping, boolean blankRowBetweenSections)
	{
		final List<List<GearItem>> sections = grouping == Grouping.BY_SLOT
			? sectionsBySlot(items)
			: sectionsByStyle(items);

		final List<Integer> layout = new ArrayList<>();
		for (List<GearItem> section : sections)
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

			for (GearItem item : section)
			{
				layout.add(item.getItemId());
			}
		}

		return layout.stream().mapToInt(Integer::intValue).toArray();
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

		Comparator<GearItem> order = Comparator.comparing(GearItem::getStyle)
			.thenComparing(Comparator.comparingInt(GearItem::getPower).reversed())
			.thenComparing(GearItem::getName, String.CASE_INSENSITIVE_ORDER)
			.thenComparingInt(GearItem::getItemId);

		List<List<GearItem>> sections = new ArrayList<>();
		for (List<GearItem> section : bySlot.values())
		{
			section.sort(order);
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
