package com.combatgearorganizer.dps;

/**
 * The OSRS combat formulas, written from the OSRS Wiki's pages (CC BY-NC-SA 3.0):
 * <ul>
 *   <li>"Maximum melee hit" and "Damage per second/Melee": effective levels, max hit, attack and defence rolls,
 *   hit chance and average damage;</li>
 *   <li>"Maximum ranged hit" and "Damage per second/Ranged";</li>
 *   <li>"Maximum magic hit" and "Damage per second/Magic";</li>
 *   <li>"Temporary skill boost" for potion boosts, "Attack styles" for stance bonuses.</li>
 * </ul>
 * Everything here is integer maths floored where the wiki floors, so max hits match the game's.
 */
public final class CombatMath
{
	/** One game tick, in seconds. */
	public static final double TICK_SECONDS = 0.6;

	private CombatMath()
	{
	}

	/** Super combat potion: 5 + 15% of the level ("Temporary skill boost/Strength"). */
	public static int superCombat(int level)
	{
		return level + 5 + level * 15 / 100;
	}

	/** Ranging potion: 4 + 10% of the level ("Temporary skill boost/Ranged"). */
	public static int rangingPotion(int level)
	{
		return level + 4 + level / 10;
	}

	/** Magic potion: +4 ("Temporary skill boost/Magic"). */
	public static int magicPotion(int level)
	{
		return level + 4;
	}

	/**
	 * Effective melee or ranged level: floor(floor(boosted level x prayer) + stance + 8). The prayer multiplier is
	 * num / den (Piety's Attack is 6/5, its Strength 123/100). No void.
	 */
	public static int effectiveLevel(int boosted, int prayerNum, int prayerDen, int stance)
	{
		return boosted * prayerNum / prayerDen + stance + 8;
	}

	/**
	 * Effective magic level for accuracy ("Damage per second/Magic", step two): floor(boosted x prayer) + stance + 9,
	 * where stance is +2 on accurate with a powered staff.
	 */
	public static int effectiveMagicLevel(int boosted, int prayerNum, int prayerDen, int stance)
	{
		return boosted * prayerNum / prayerDen + stance + 9;
	}

	/** Base max hit from effective strength and strength bonus: floor(0.5 + eff x (bonus + 64) / 640). */
	public static int baseMaxHit(int effectiveStrength, int strengthBonus)
	{
		return Math.max(0, (effectiveStrength * (strengthBonus + 64) + 320) / 640);
	}

	/** Attack roll before gear multipliers: effective level x (attack bonus + 64). */
	public static long attackRoll(int effectiveLevel, int attackBonus)
	{
		return Math.max(0L, (long) effectiveLevel * (attackBonus + 64));
	}

	/** A monster's defence roll: (defence level + 9) x (defence bonus + 64). For magic, its Magic level is used. */
	public static long defenceRoll(int level, int defenceBonus)
	{
		return Math.max(0L, (long) (level + 9) * (defenceBonus + 64));
	}

	/** floor(value x num / den), for the game's chained, floored multipliers. */
	public static long times(long value, int num, int den)
	{
		return value * num / den;
	}

	/** floor(value x num / den) for max hits. */
	public static int times(int value, int num, int den)
	{
		return (int) ((long) value * num / den);
	}

	/** Chance that an attack roll beats a defence roll ("Damage per second/Melee", step seven). */
	public static double hitChance(long attack, long defence)
	{
		if (attack > defence)
		{
			return 1 - (defence + 2.0) / (2.0 * (attack + 1));
		}
		return attack / (2.0 * (defence + 1));
	}

	/**
	 * Osmumten's fang on a stab style outside the Tombs of Amascut rolls accuracy twice against one defence roll
	 * (wiki "Osmumten's fang", passive effects).
	 */
	public static double fangHitChance(long a, long d)
	{
		if (a >= d)
		{
			return 1 - (d + 2.0) * (2.0 * d + 3) / (6.0 * (a + 1) * (a + 1));
		}
		return a * (4.0 * a + 5) / (6.0 * (a + 1) * (d + 1));
	}

	/**
	 * Average damage of one hitsplat: hit chance x (max / 2 + 1 / (max + 1)). A successful hit that rolls 0 becomes
	 * 1, hence the small extra ("Damage per second/Melee", step eight).
	 */
	public static double averageHit(double hitChance, int maxHit)
	{
		if (maxHit <= 0)
		{
			return 0;
		}
		return hitChance * (maxHit / 2.0 + 1.0 / (maxHit + 1));
	}

	/** Damage per second from the average damage per attack and the attack speed in ticks. */
	public static double dps(double averagePerAttack, int speedTicks)
	{
		return averagePerAttack / (Math.max(1, speedTicks) * TICK_SECONDS);
	}

	/**
	 * Twisted bow accuracy multiplier in percent (wiki "Twisted bow"): 140 + (30M/10 - 10)/100 - (3M/10 - 100)^2/100,
	 * capped at 140, where M is the target's Magic level or magic attack bonus, whichever is higher, capped at 250
	 * outside the Chambers of Xeric. Worked in real numbers, as the wiki writes it: that is what makes the cap land at
	 * 244 Magic, as the page says (rounding each term down would reach it a few levels earlier).
	 */
	public static double twistedBowAccuracy(int magic)
	{
		double m = Math.min(250, Math.max(0, magic));
		double pct = 140 + (10 * 3 * m / 10 - 10) / 100 - Math.pow(3 * m / 10 - 100, 2) / 100;
		return Math.max(0, Math.min(140, pct));
	}

	/** Twisted bow damage multiplier in percent: 250 + (30M/10 - 14)/100 - (3M/10 - 140)^2/100, capped at 250. */
	public static double twistedBowDamage(int magic)
	{
		double m = Math.min(250, Math.max(0, magic));
		double pct = 250 + (10 * 3 * m / 10 - 14) / 100 - Math.pow(3 * m / 10 - 140, 2) / 100;
		return Math.max(0, Math.min(250, pct));
	}
}
