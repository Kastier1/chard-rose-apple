package com.combatgearorganizer.dps;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CombatMathTest
{
	@Test
	public void potionBoostsFromTheWiki()
	{
		// "Temporary skill boost": super combat 5 + 15%, ranging potion 4 + 10%, magic potion 4
		assertEquals(118, CombatMath.superCombat(99));
		assertEquals(112, CombatMath.rangingPotion(99));
		assertEquals(103, CombatMath.magicPotion(99));
		// Low levels round the percentage down
		assertEquals(1 + 5, CombatMath.superCombat(1));
	}

	@Test
	public void freeToPlayMaxHitFromMaximumMeleeHit()
	{
		// "Maximum melee hit": the free-to-play max hit is 31 with a rune 2h sword (+70), amulet of strength (+10),
		// decorative boots (gold) (+1), a strength potion (10% + 3) and Ultimate Strength (x1.15) on aggressive
		int boosted = 99 + 99 * 10 / 100 + 3;
		int effective = CombatMath.effectiveLevel(boosted, 115, 100, 3);
		assertEquals(138, effective);
		assertEquals(31, CombatMath.baseMaxHit(effective, 70 + 10 + 1));
	}

	@Test
	public void maxedMeleeEffectiveStrength()
	{
		// 99 Strength, super combat (118), Piety (x1.23 -> 145), aggressive (+3), +8
		assertEquals(156, CombatMath.effectiveLevel(CombatMath.superCombat(99), 123, 100, 3));
		// With no strength bonus: floor(0.5 + 156 x 64 / 640) = floor(16.1)
		assertEquals(16, CombatMath.baseMaxHit(156, 0));
		// A whip alone (+82): floor(0.5 + 156 x 146 / 640) = floor(36.09)
		assertEquals(36, CombatMath.baseMaxHit(156, 82));
	}

	@Test
	public void rollsAndHitChance()
	{
		assertEquals(149L * 64, CombatMath.attackRoll(149, 0));
		// Greater demon: Defence 81, slash bonus 0
		assertEquals(90L * 64, CombatMath.defenceRoll(81, 0));
		// "Damage per second/Melee" step seven, both branches
		assertEquals(1 - (100 + 2.0) / (2 * 201), CombatMath.hitChance(200, 100), 1e-12);
		assertEquals(100 / (2.0 * 201), CombatMath.hitChance(100, 200), 1e-12);
		assertEquals(0, CombatMath.hitChance(0, 100), 1e-12);
		// Equal rolls take the second branch: A / (2(D + 1))
		assertEquals(100 / 202.0, CombatMath.hitChance(100, 100), 1e-12);
	}

	@Test
	public void fangRollsAccuracyTwice()
	{
		// The fang's chance is always higher than a single roll's, and at most 1
		long[][] rolls = {{1000, 5000}, {5000, 5000}, {20000, 5000}, {100, 0}};
		for (long[] r : rolls)
		{
			double one = CombatMath.hitChance(r[0], r[1]);
			double fang = CombatMath.fangHitChance(r[0], r[1]);
			assertTrue(r[0] + " vs " + r[1], fang > one && fang <= 1);
		}
		// Against no defence: 1 - 2 x 3 / (6 (a + 1)^2)
		assertEquals(1 - 1.0 / (101 * 101), CombatMath.fangHitChance(100, 0), 1e-12);
	}

	@Test
	public void averageHitCountsZeroAsOne()
	{
		// hit chance x (max / 2 + 1 / (max + 1))
		assertEquals(0.5 * (10 + 1.0 / 21), CombatMath.averageHit(0.5, 20), 1e-12);
		assertEquals(0, CombatMath.averageHit(1, 0), 1e-12);
		// 4 ticks = 2.4 seconds
		assertEquals(10 / 2.4, CombatMath.dps(10, 4), 1e-12);
	}

	@Test
	public void twistedBowScalingFromItsWikiPage()
	{
		// "The accuracy multiplier caps at 140% (reached at 244 magic level or accuracy)"
		assertEquals(140, CombatMath.twistedBowAccuracy(244), 1e-9);
		assertTrue(CombatMath.twistedBowAccuracy(243) < 140);
		// "the damage multiplier caps at 250% (never reached due to the scaling cap)": Magic counts up to 250
		assertTrue(CombatMath.twistedBowDamage(250) < 250);
		assertEquals(CombatMath.twistedBowDamage(250), CombatMath.twistedBowDamage(400), 1e-9);
		// 250 + (750 - 14) / 100 - (75 - 140)^2 / 100
		assertEquals(250 + 7.36 - 42.25, CombatMath.twistedBowDamage(250), 1e-9);
		// Weak against low-magic monsters: 250 + (3 - 14) / 100 - (0.3 - 140)^2 / 100
		assertEquals(250 - 0.11 - 195.1609, CombatMath.twistedBowDamage(1), 1e-9);
		assertEquals(140 - 0.07 - 99.4009, CombatMath.twistedBowAccuracy(1), 1e-9);
	}

	@Test
	public void magicEffectiveLevel()
	{
		// 99 Magic + magic potion (103) x Augury 1.25 = 128, +9; powered staff on accurate +2
		assertEquals(137, CombatMath.effectiveMagicLevel(103, 125, 100, 0));
		assertEquals(139, CombatMath.effectiveMagicLevel(103, 125, 100, 2));
	}
}
