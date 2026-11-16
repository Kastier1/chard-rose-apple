package com.combatgearorganizer.slayer;

import com.combatgearorganizer.slayer.SlayerLocationData.LocationEntry;
import com.combatgearorganizer.slayer.SlayerLocationData.TaskEntry;
import java.util.ArrayList;
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

	/**
	 * @param task              the task's data
	 * @param masterId          SLAYER_MASTER varbit value
	 * @param konarAreaId       SLAYER_AREA varp value; above 0 when Konar assigned a specific place
	 * @param includeWilderness whether to show places and spawns inside the Wilderness for normal masters
	 */
	public static List<LocationOption> pick(TaskEntry task, int masterId, int konarAreaId, boolean includeWilderness)
	{
		// Krystilia only assigns Wilderness tasks, so show her list on its own
		if (masterId == SlayerMasters.KRYSTILIA && !task.getWildernessLocations().isEmpty())
		{
			return task.getWildernessLocations().stream()
				.map(l -> toOption(l, false, true))
				.collect(Collectors.toList());
		}

		// Konar names the place: show only that place, Wilderness or not
		if (konarAreaId > 0)
		{
			List<LocationOption> konar = task.getLocations().stream()
				.filter(l -> l.getAreaId() != null && l.getAreaId() == konarAreaId)
				.map(l -> toOption(l, false, true))
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
			LocationOption option = toOption(l, l.getKey().equals(task.getRecommended()), includeWilderness);
			if (option.isRecommended())
			{
				options.add(0, option);
			}
			else
			{
				options.add(option);
			}
		}
		return options;
	}

	private static LocationOption toOption(LocationEntry l, boolean recommended, boolean includeWilderness)
	{
		List<int[]> targets = new ArrayList<>(l.getPoints());
		if (includeWilderness)
		{
			targets.addAll(l.getWildernessPoints());
		}
		boolean wildernessOnly = l.getPoints().isEmpty() && !l.getWildernessPoints().isEmpty();
		return new LocationOption(
			l.getKey(),
			l.getLabel(),
			recommended,
			wildernessOnly,
			!l.getWildernessPoints().isEmpty(),
			l.getCombat(),
			l.getCannon(),
			l.getNote(),
			targets);
	}
}
