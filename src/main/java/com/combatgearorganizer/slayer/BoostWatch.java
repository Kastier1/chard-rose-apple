package com.combatgearorganizer.slayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Notices when the player's boosted level changes in a skill some place lets them boost to get in (a wild pie for
 * Cerberus' 91 Slayer), so the place cards can be checked again: drinking or eating a boost, or the boost wearing off,
 * changes nothing else the panel watches.
 */
final class BoostWatch
{
	private final Set<String> skills;
	private Map<String, Integer> last = Collections.emptyMap();

	BoostWatch(Set<String> skills)
	{
		this.skills = Collections.unmodifiableSet(new TreeSet<>(skills));
	}

	/** Every skill that some place in the data lists as boostable. */
	static Set<String> boostableSkills(SlayerLocationData data)
	{
		Set<String> out = new TreeSet<>();
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.LocationEntry l : t.getLocations())
			{
				add(out, l);
			}
			for (SlayerLocationData.LocationEntry l : t.getWildernessLocations())
			{
				add(out, l);
			}
		}
		return out;
	}

	private static void add(Set<String> out, SlayerLocationData.LocationEntry l)
	{
		if (l.getRequirements() != null)
		{
			out.addAll(l.getRequirements().getBoostable());
		}
	}

	Set<String> getSkills()
	{
		return skills;
	}

	/**
	 * @param boosted the current boosted level of each watched skill
	 * @return whether any of them differs from the last call's (the first call only remembers them)
	 */
	boolean changed(Map<String, Integer> boosted)
	{
		Map<String, Integer> now = new HashMap<>();
		for (String s : skills)
		{
			now.put(s, boosted.get(s));
		}
		boolean changed = !last.isEmpty() && !now.equals(last);
		last = now;
		return changed;
	}

	/**
	 * After a boost change, whether the shown places must be drawn again: the same places now need different things
	 * (one opened or closed).
	 */
	static boolean placesChanged(List<LocationOption> now, List<LocationOption> shown)
	{
		return !SlayerHelper.sameRequirements(now, shown);
	}
}
