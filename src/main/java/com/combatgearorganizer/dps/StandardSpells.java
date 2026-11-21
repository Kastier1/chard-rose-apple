package com.combatgearorganizer.dps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * The standard spellbook's elemental combat spells (strike to surge), with the levels and max hits from their OSRS
 * Wiki pages. Within a tier, air, water and earth spells hit as hard as the highest one unlocked ("Maximum magic
 * hit", base max damage note), so Wind Surge at 95 Magic hits 24 like Fire Surge.
 */
final class StandardSpells
{
	private StandardSpells()
	{
	}

	@Value
	static class Spell
	{
		String name;
		/** "air", "water", "earth" or "fire". */
		String element;
		int maxHit;
	}

	private static final String[] TIERS = {"Strike", "Bolt", "Blast", "Wave", "Surge"};
	private static final String[] SPELL_ELEMENTS = {"Wind", "Water", "Earth", "Fire"};
	private static final String[] ELEMENTS = {"air", "water", "earth", "fire"};
	/** [tier][element] Magic level needed. */
	private static final int[][] LEVELS = {
		{1, 5, 9, 13},
		{17, 23, 29, 35},
		{41, 47, 53, 59},
		{62, 65, 70, 75},
		{81, 85, 90, 95},
	};
	/** [tier][element] max hit of that spell. */
	private static final int[][] MAX_HITS = {
		{2, 4, 6, 8},
		{9, 10, 11, 12},
		{13, 14, 15, 16},
		{17, 18, 19, 20},
		{21, 22, 23, 24},
	};

	/** Every elemental spell this Magic level can cast, with the max hit it has at that level. */
	static List<Spell> castable(int magic)
	{
		List<Spell> out = new ArrayList<>();
		for (int t = 0; t < TIERS.length; t++)
		{
			int best = 0;
			for (int e = 0; e < ELEMENTS.length; e++)
			{
				if (magic >= LEVELS[t][e])
				{
					best = Math.max(best, MAX_HITS[t][e]);
				}
			}
			for (int e = 0; e < ELEMENTS.length; e++)
			{
				if (magic >= LEVELS[t][e])
				{
					out.add(new Spell(SPELL_ELEMENTS[e] + " " + TIERS[t], ELEMENTS[e], best));
				}
			}
		}
		return Collections.unmodifiableList(out);
	}

	/**
	 * Magic Dart ("Magic Dart", "Maximum magic hit"): floor(Magic / 10) + 10, or floor(Magic / 6) + 13 from a
	 * Slayer's staff (e) on a slayer task monster. Needs 50 Magic.
	 */
	static int magicDart(int magic, boolean enchantedOnTask)
	{
		if (magic < 50)
		{
			return 0;
		}
		return enchantedOnTask ? magic / 6 + 13 : magic / 10 + 10;
	}
}
