package com.combatgearorganizer.slayer;

import com.combatgearorganizer.slayer.SlayerLocationData.Variant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Choosing and describing a task's monster variants.
 */
public final class Variants
{
	private Variants()
	{
	}

	/** The regular monsters: everything except superiors and bosses. A boss is never picked by default. */
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

	/** "Found at" for a monster the data has no place for. */
	public static final String NO_PLACE = "No place in the data yet";
	/** "Found at" for a monster whose places are all left out of the list (Wilderness setting, Konar's area). */
	public static final String NOT_SHOWN = "Not at any of the places below";
	/**
	 * "Found at" for a boss whose lair isn't listed. The bosses that don't count for the master are already left out
	 * ({@link #counting}), so the lair is in the Wilderness and the Wilderness setting is off.
	 */
	public static final String BOSS_IN_WILDERNESS = "In the Wilderness: turn on \"Show Wilderness locations\" in the "
		+ "plugin settings to see its lair";

	/**
	 * The task's monsters, without the bosses that don't count for this master: those the data says count only for
	 * other masters or not for this one (the King Black Dragon for Krystilia, Eldric the Ice King for anyone but
	 * Vannaka), and those whose lair isn't among the places that count for the master (with the Wilderness shown):
	 * outside the Wilderness for Krystilia, outside Konar's assigned area. Used for the panel's table and to decide
	 * whose loot belongs to the task.
	 *
	 * @param masterId    SLAYER_MASTER varbit value; 0 when unknown (no master rules)
	 * @param konarAreaId SLAYER_AREA varp value; above 0 when Konar assigned a specific place
	 */
	public static List<Variant> counting(SlayerLocationData.TaskEntry task, int masterId, int konarAreaId)
	{
		String master = SlayerMasters.name(masterId);
		Set<String> places = LocationPicker.pick(task, masterId, konarAreaId, true).stream()
			.map(LocationOption::getKey)
			.collect(Collectors.toSet());
		return task.getVariants().stream()
			.filter(v -> !v.isBoss() || countsFor(v, master, places))
			.collect(Collectors.toList());
	}

	/** The names of the task's bosses that {@link #counting} leaves out for this master. */
	public static List<String> notCounting(SlayerLocationData.TaskEntry task, int masterId, int konarAreaId)
	{
		List<Variant> counted = counting(task, masterId, konarAreaId);
		return task.getVariants().stream()
			.filter(v -> v.isBoss() && !counted.contains(v))
			.map(Variant::getName)
			.collect(Collectors.toList());
	}

	/** Whether a boss counts for a task from this master (null: unknown), given the keys of the places that count. */
	static boolean countsFor(Variant boss, String master, Set<String> placeKeys)
	{
		if (master != null)
		{
			if (!boss.getMasters().isEmpty() && boss.getMasters().stream().noneMatch(master::equalsIgnoreCase))
			{
				return false;
			}
			if (boss.getNotMasters().stream().anyMatch(master::equalsIgnoreCase))
			{
				return false;
			}
		}
		return boss.getPlaces().stream().anyMatch(placeKeys::contains);
	}

	/**
	 * Every monster for the table: the regular ones at a listed place first, then the rest of them, each part in the
	 * data's order (lowest level first); then the bosses, in the data's order. Every monster and boss that counts is in
	 * the table; its "Found at" row says where it lives. Superiors aren't listed: they show with their monster.
	 */
	public static List<Variant> listed(List<Variant> variants, List<LocationOption> shownPlaces)
	{
		Map<Boolean, List<Variant>> atShown = regular(variants).stream()
			.collect(Collectors.partitioningBy(v -> isAtAny(v, shownPlaces)));
		List<Variant> out = new ArrayList<>(atShown.get(true));
		out.addAll(atShown.get(false));
		out.addAll(bosses(variants));
		return out;
	}

	/** The bosses that count for the task, in the data's order. */
	public static List<Variant> bosses(List<Variant> variants)
	{
		return variants.stream().filter(Variant::isBoss).collect(Collectors.toList());
	}

	/**
	 * The labels of the listed places this variant lives at, in list order; or a single line saying why there are
	 * none ({@link #NO_PLACE}, {@link #BOSS_IN_WILDERNESS} or {@link #NOT_SHOWN}). Plain text, not escaped.
	 */
	public static List<String> foundAt(Variant v, List<LocationOption> shownPlaces)
	{
		if (v.getPlaces().isEmpty())
		{
			return Collections.singletonList(NO_PLACE);
		}
		List<String> labels = shownPlaces.stream()
			.filter(p -> v.getPlaces().contains(p.getKey()))
			.map(LocationOption::getLabel)
			.collect(Collectors.toList());
		if (labels.isEmpty())
		{
			return Collections.singletonList(v.isBoss() ? BOSS_IN_WILDERNESS : NOT_SHOWN);
		}
		return labels;
	}

	/** True when this variant lives at one of the listed places. */
	public static boolean isAtAny(Variant v, List<LocationOption> shownPlaces)
	{
		return shownPlaces.stream().anyMatch(p -> v.getPlaces().contains(p.getKey()));
	}

	/** The superiors that can spawn in place of this monster. */
	public static List<Variant> superiorsOf(List<Variant> variants, Variant base)
	{
		return variants.stream()
			.filter(v -> v.isSuperior() && v.getSuperiorOf() != null && v.getSuperiorOf().equalsIgnoreCase(base.getName()))
			.collect(Collectors.toList());
	}

	/**
	 * The drops worth showing next to a superior: its own extras (what its normal monster doesn't drop). Falls back to
	 * all of its drops when they're all shared, or when it isn't a superior.
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

	/** Names of the monsters that spawn at a place, for its card: the regular ones, then bosses (at their lair). */
	public static List<String> at(List<Variant> variants, String placeKey)
	{
		List<Variant> here = new ArrayList<>(regular(variants));
		here.addAll(bosses(variants));
		return here.stream()
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

	/**
	 * Slayer XP per kill for the monster table, where a range has little room: "123" or "157.5" as {@link #xpEach}, a
	 * range in whole numbers, thousands as "2.1k": "17-182", "600-2.1k". The card's XP row has the exact range.
	 */
	public static String xpEachShort(double[] xp)
	{
		if (xp == null || xp.length == 0)
		{
			return "?";
		}
		double lo = xp[0];
		double hi = xp[xp.length - 1];
		if (lo == hi)
		{
			return number(lo);
		}
		// 49.6-50 would read "50-50"
		return whole(lo).equals(whole(hi)) ? "~" + whole(hi) : whole(lo) + "-" + whole(hi);
	}

	private static String whole(double d)
	{
		return d >= 1_000 ? String.format(Locale.ROOT, "%.1fk", d / 1000) : Long.toString(Math.round(d));
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
