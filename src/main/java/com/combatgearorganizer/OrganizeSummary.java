package com.combatgearorganizer;

import java.util.ArrayList;
import java.util.List;

/**
 * One line about an organized tab: "48 items: 14 melee, 12 ranged, 10 magic, 12 other". Pure logic.
 */
public final class OrganizeSummary
{
	private OrganizeSummary()
	{
	}

	public static String of(List<GearItem> items)
	{
		int melee = 0;
		int ranged = 0;
		int magic = 0;
		int other = 0;
		for (GearItem item : items)
		{
			switch (item.getStyle())
			{
				case MELEE:
					melee++;
					break;
				case RANGED:
					ranged++;
					break;
				case MAGIC:
					magic++;
					break;
				default:
					other++;
			}
		}

		final List<String> parts = new ArrayList<>();
		add(parts, melee, "melee");
		add(parts, ranged, "ranged");
		add(parts, magic, "magic");
		add(parts, other, "other");
		final String count = items.size() + (items.size() == 1 ? " item" : " items");
		return parts.isEmpty() ? count : count + ": " + String.join(", ", parts);
	}

	private static void add(List<String> parts, int count, String name)
	{
		if (count > 0)
		{
			parts.add(count + " " + name);
		}
	}
}
