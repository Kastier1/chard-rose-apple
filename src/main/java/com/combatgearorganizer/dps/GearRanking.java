package com.combatgearorganizer.dps;

import com.combatgearorganizer.GearStyle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** The player's weapons for one monster, best first, per style. */
@Value
public class GearRanking
{
	/** Weapons that can hurt the monster, highest DPS first. Every combat style has a list, maybe empty. */
	Map<GearStyle, List<WeaponScore>> byStyle;
	/** Per style, why none of the player's weapons of that style can hurt it; absent when some can. */
	Map<GearStyle, String> cannotHarm;

	public List<WeaponScore> best(GearStyle style)
	{
		return byStyle.getOrDefault(style, Collections.emptyList());
	}

	public String cannotHarm(GearStyle style)
	{
		return cannotHarm.get(style);
	}
}
