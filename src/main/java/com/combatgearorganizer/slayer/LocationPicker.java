package com.combatgearorganizer.slayer;

import com.combatgearorganizer.slayer.SlayerLocationData.LocationEntry;
import com.combatgearorganizer.slayer.SlayerLocationData.TaskEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Decides which locations to show for a task, given the master and the player's settings.
 */
public final class LocationPicker
{
	private LocationPicker()
	{
	}

	/** Same as {@link #pick(TaskEntry, int, int, boolean, RequirementChecker)} with every requirement met. */
	public static List<LocationOption> pick(TaskEntry task, int masterId, int konarAreaId, boolean includeWilderness)
	{
		return pick(task, masterId, konarAreaId, includeWilderness, RequirementChecker.NONE);
	}

	/**
	 * @param task              the task's data
	 * @param masterId          SLAYER_MASTER varbit value
	 * @param konarAreaId       SLAYER_AREA varp value; above 0 when Konar assigned a specific place
	 * @param includeWilderness whether to show places and spawns inside the Wilderness for normal masters
	 * @param checker           works out which quests and skills the player is missing for each place
	 */
	public static List<LocationOption> pick(TaskEntry task, int masterId, int konarAreaId, boolean includeWilderness,
		RequirementChecker checker)
	{
		// Krystilia's kills only count in the Wilderness, so never show places outside it for her tasks
		if (masterId == SlayerMasters.KRYSTILIA)
		{
			if (!task.getWildernessLocations().isEmpty())
			{
				return task.getWildernessLocations().stream()
					.map(l -> toOption(l, false, OnlyWilderness.HER_LIST, checker))
					.collect(Collectors.toList());
			}
			// No list from her for this task: fall back to the Wilderness spawns of the normal places
			return task.getLocations().stream()
				.filter(l -> !l.getWildernessPoints().isEmpty())
				.map(l -> toOption(l, false, OnlyWilderness.YES, checker))
				.collect(Collectors.toList());
		}

		// Konar names the place: show only that place, Wilderness or not
		if (konarAreaId > 0)
		{
			List<LocationOption> konar = task.getLocations().stream()
				.filter(l -> l.getAreaId() != null && l.getAreaId() == konarAreaId)
				.map(l -> toOption(l, false, OnlyWilderness.BOTH, checker))
				.collect(Collectors.toList());
			if (!konar.isEmpty())
			{
				return konar;
			}
		}

		List<LocationOption> options = new ArrayList<>();
		for (LocationEntry l : task.getLocations())
		{
			boolean wildernessOnly = l.getPoints().isEmpty() && !l.getWildernessPoints().isEmpty();
			if (wildernessOnly && !includeWilderness)
			{
				continue;
			}
			boolean recommended = l.getKey().equals(task.getRecommended());
			options.add(toOption(l, recommended, includeWilderness ? OnlyWilderness.BOTH : OnlyWilderness.NO, checker));
		}

		// Places the player can get into first, the recommended one at the top of those; otherwise keep the data's
		// order (stable sort). A recommended place behind a quest the player hasn't done goes below the open ones.
		// Boss lairs go last of all: they're for going after that boss.
		options.sort(Comparator.comparing(LocationOption::isBossLair)
			.thenComparing(o -> !o.isAccessible())
			.thenComparing(o -> !o.isRecommended()));
		return options;
	}

	private enum OnlyWilderness
	{
		/** Only points outside the Wilderness. */
		NO,
		/** Points both inside and outside. */
		BOTH,
		/** Only points inside the Wilderness. */
		YES,
		/** A place from Krystilia's own list: every point counts as Wilderness. */
		HER_LIST,
	}

	private static LocationOption toOption(LocationEntry l, boolean recommended, OnlyWilderness which, RequirementChecker checker)
	{
		List<int[]> targets = new ArrayList<>();
		if (which != OnlyWilderness.YES)
		{
			targets.addAll(l.getPoints());
		}
		if (which != OnlyWilderness.NO)
		{
			targets.addAll(l.getWildernessPoints());
		}
		boolean wildernessOnly = which == OnlyWilderness.YES || which == OnlyWilderness.HER_LIST
			|| (l.getPoints().isEmpty() && !l.getWildernessPoints().isEmpty());
		List<String> missing = l.getRequirements() == null
			? Collections.emptyList()
			: checker.missing(l.getRequirements());
		return new LocationOption(
			l.getKey(),
			l.getLabel(),
			recommended,
			wildernessOnly,
			!l.getWildernessPoints().isEmpty(),
			l.getCombat(),
			l.getCannon(),
			l.getNote(),
			targets,
			missing,
			l.isFromWiki(),
			l.isBossLair());
	}

	/**
	 * The places "Go to nearest" chooses between: the routable ones the player can get into, from the slayer location
	 * guide. Places made from one monster's wiki spawns (a quest area, a side room) are for going after that
	 * monster, so they only count when no guide place can be reached. Boss lairs never count: fighting a boss is the
	 * player's choice, made with the lair's own Go button.
	 *
	 * When every routable place needs something the player lacks (or the client hasn't told us yet), it picks from
	 * those instead, like each card's Go: a missing requirement never greys Go out ({@link #nearestIsGated} says so).
	 */
	public static List<LocationOption> nearestTargets(List<LocationOption> shown)
	{
		List<LocationOption> routable = shown.stream()
			.filter(l -> l.isRoutable() && !l.isBossLair())
			.collect(Collectors.toList());
		List<LocationOption> open = routable.stream().filter(LocationOption::isAccessible).collect(Collectors.toList());
		List<LocationOption> pool = open.isEmpty() ? routable : open;
		List<LocationOption> guide = pool.stream().filter(l -> !l.isFromWiki()).collect(Collectors.toList());
		return guide.isEmpty() ? pool : guide;
	}

	/** Whether Go to nearest only has places the player may not be able to get into yet (see {@link #nearestTargets}). */
	public static boolean nearestIsGated(List<LocationOption> targets)
	{
		return !targets.isEmpty() && targets.stream().noneMatch(LocationOption::isAccessible);
	}
}
