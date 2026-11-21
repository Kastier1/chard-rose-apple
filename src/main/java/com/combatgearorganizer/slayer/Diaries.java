package com.combatgearorganizer.slayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * The Achievement Diary tiers a place can need ({@link SlayerLocationData.Requirements#getDiaries()}), each with the
 * game varbit that is 1 once that tier is complete. The names match the generator's DIARIES table in
 * tools/build_slayer_locations.py.
 */
public final class Diaries
{
	/** Diary tier name ("WILDERNESS_MEDIUM") to its *_DIARY_<TIER>_COMPLETE varbit. */
	static final Map<String, Integer> VARBITS;

	static
	{
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("WILDERNESS_EASY", VarbitID.WILDERNESS_DIARY_EASY_COMPLETE);
		m.put("WILDERNESS_MEDIUM", VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE);
		m.put("WILDERNESS_HARD", VarbitID.WILDERNESS_DIARY_HARD_COMPLETE);
		m.put("WILDERNESS_ELITE", VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);
		VARBITS = Collections.unmodifiableMap(m);
	}

	private Diaries()
	{
	}

	/** "WILDERNESS_MEDIUM" becomes "Medium Wilderness Diary", as a place card's "Needs" line says it. */
	static String text(String name)
	{
		int cut = name.lastIndexOf('_');
		if (cut <= 0)
		{
			return name;
		}
		return capitalize(name.substring(cut + 1)) + " " + capitalize(name.substring(0, cut).replace('_', ' '))
			+ " Diary";
	}

	private static String capitalize(String words)
	{
		StringBuilder sb = new StringBuilder();
		for (String w : words.toLowerCase(java.util.Locale.ROOT).split(" "))
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}
}
