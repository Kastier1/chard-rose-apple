package com.combatgearorganizer.dps;

import lombok.Value;

/** The player's real (unboosted) combat levels. */
@Value
public class Levels
{
	public static final Levels MAXED = new Levels(99, 99, 99, 99);

	int attack;
	int strength;
	int ranged;
	int magic;

	/** Real levels, with 99 for any the client couldn't read (0 or less). */
	public static Levels of(int attack, int strength, int ranged, int magic)
	{
		return new Levels(orMax(attack), orMax(strength), orMax(ranged), orMax(magic));
	}

	private static int orMax(int level)
	{
		return level > 0 ? level : 99;
	}
}
