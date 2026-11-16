package com.combatgearorganizer.slayer;

import com.combatgearorganizer.slayer.SlayerLocationData.Variant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Choosing and describing a task's monster variants.
 */
public final class Variants
{
	private Variants()
	{
	}

	/** The variants to list and pick between: everything except superiors and bosses. */
	public static List<Variant> regular(List<Variant> variants)
	{
		return variants.stream().filter(Variant::isRegular).collect(Collectors.toList());
	}

	/**
	 * Which variant to show first: the one at the top place in the list (the recommended or nearest open place),
	 * else the first that spawns at any listed place, else the lowest level.
	 *
	 * @return null when there are no regular variants
	 */
	public static Variant pickDefault(List<Variant> variants, List<LocationOption> shownPlaces)
	{
		List<Variant> regular = regular(variants);
		for (LocationOption place : shownPlaces)
		{
			for (Variant v : regular)
			{
				if (v.getPlaces().contains(place.getKey()))
				{
					return v;
				}
			}
		}
		return regular.isEmpty() ? null : regular.get(0);
	}

	/**
	 * The regular variants found at one of the listed places, to put in the table. When none can be matched to
	 * a place, every regular variant, so the table is never empty.
	 */
	public static List<Variant> listed(List<Variant> variants, List<LocationOption> shownPlaces)
	{
		List<Variant> placed = regular(variants).stream()
			.filter(v -> shownPlaces.stream().anyMatch(p -> v.getPlaces().contains(p.getKey())))
			.collect(Collectors.toList());
		return placed.isEmpty() ? regular(variants) : placed;
	}

	/** Regular variants left out of the table: they count for the task but live somewhere not listed. */
	public static List<Variant> unlisted(List<Variant> variants, List<LocationOption> shownPlaces)
	{
		List<Variant> listed = listed(variants, shownPlaces);
		return regular(variants).stream().filter(v -> !listed.contains(v)).collect(Collectors.toList());
	}

	/** The superiors that can spawn in place of this monster. */
	public static List<Variant> superiorsOf(List<Variant> variants, Variant base)
	{
		return variants.stream()
			.filter(v -> v.isSuperior() && v.getSuperiorOf() != null && v.getSuperiorOf().equalsIgnoreCase(base.getName()))
			.collect(Collectors.toList());
	}

	/**
	 * The drops worth showing next to a superior or boss: a superior's own extras (what its normal monster doesn't
	 * drop), or a boss's drops. Falls back to all of a superior's drops when they're all shared.
	 */
	public static List<SlayerLocationData.Drop> notableDrops(List<Variant> variants, Variant v)
	{
		if (!v.isSuperior() || v.getSuperiorOf() == null)
		{
			return v.getDrops();
		}
		Variant base = variants.stream().filter(b -> b.getName().equalsIgnoreCase(v.getSuperiorOf())).findFirst().orElse(null);
		if (base == null)
		{
			return v.getDrops();
		}
		List<String> baseItems = base.getDrops().stream().map(d -> d.getItem().toLowerCase(Locale.ROOT)).collect(Collectors.toList());
		List<SlayerLocationData.Drop> own = v.getDrops().stream()
			.filter(d -> !baseItems.contains(d.getItem().toLowerCase(Locale.ROOT)))
			.collect(Collectors.toList());
		return own.isEmpty() ? v.getDrops() : own;
	}

	public static Variant byName(List<Variant> variants, String name)
	{
		if (name == null)
		{
			return null;
		}
		return variants.stream().filter(v -> v.getName().equals(name)).findFirst().orElse(null);
	}

	/** Names of the variants that spawn at a place, for its card. */
	public static List<String> at(List<Variant> variants, String placeKey)
	{
		return regular(variants).stream()
			.filter(v -> v.getPlaces().contains(placeKey))
			.map(Variant::getName)
			.collect(Collectors.toList());
	}

	/** "97" or "13-142". */
	public static String range(int[] r)
	{
		if (r == null || r.length == 0)
		{
			return "?";
		}
		return r[0] == r[r.length - 1] ? Integer.toString(r[0]) : r[0] + "-" + r[r.length - 1];
	}

	/** Slayer XP per kill: "123", "157.5" or "17-110". */
	public static String xpEach(double[] xp)
	{
		if (xp == null || xp.length == 0)
		{
			return "?";
		}
		double lo = xp[0];
		double hi = xp[xp.length - 1];
		return lo == hi ? number(lo) : number(lo) + "-" + number(hi);
	}

	/** Expected slayer XP for the kills left: "15,129" or "2,091-13,530". */
	public static String xpTotal(double[] xp, int kills)
	{
		if (xp == null || xp.length == 0)
		{
			return "?";
		}
		long lo = Math.round(xp[0] * kills);
		long hi = Math.round(xp[xp.length - 1] * kills);
		return lo == hi ? String.format(Locale.ROOT, "%,d", lo) : String.format(Locale.ROOT, "%,d-%,d", lo, hi);
	}

	/** Short total for the table: "15.1k", "980". */
	public static String xpTotalShort(double[] xp, int kills)
	{
		if (xp == null || xp.length == 0)
		{
			return "?";
		}
		// The table has no room for a range, so use the middle
		double mid = (xp[0] + xp[xp.length - 1]) / 2 * kills;
		String s = mid >= 10_000 ? String.format(Locale.ROOT, "%.0fk", mid / 1000)
			: mid >= 1_000 ? String.format(Locale.ROOT, "%.1fk", mid / 1000)
			: String.format(Locale.ROOT, "%.0f", mid);
		return xp[0] == xp[xp.length - 1] ? s : "~" + s;
	}

	private static String number(double d)
	{
		return d == Math.rint(d) ? String.format(Locale.ROOT, "%,d", (long) d) : String.format(Locale.ROOT, "%,.1f", d);
	}
}
