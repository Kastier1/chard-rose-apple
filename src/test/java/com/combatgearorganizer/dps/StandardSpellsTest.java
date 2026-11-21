package com.combatgearorganizer.dps;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StandardSpellsTest
{
	private static StandardSpells.Spell find(List<StandardSpells.Spell> spells, String name)
	{
		return spells.stream().filter(s -> s.getName().equals(name)).findFirst().orElse(null);
	}

	@Test
	public void spellsUnlockByLevelAndShareTheTiersMaxHit()
	{
		List<StandardSpells.Spell> at95 = StandardSpells.castable(95);
		assertEquals(24, find(at95, "Fire Surge").getMaxHit());
		// Wind Surge hits as hard as the highest surge unlocked
		assertEquals(24, find(at95, "Wind Surge").getMaxHit());
		assertEquals("air", find(at95, "Wind Surge").getElement());

		List<StandardSpells.Spell> at90 = StandardSpells.castable(90);
		assertEquals(null, find(at90, "Fire Surge"));
		assertEquals(23, find(at90, "Earth Surge").getMaxHit());
		assertEquals(23, find(at90, "Water Surge").getMaxHit());
		assertEquals(20, find(at90, "Fire Wave").getMaxHit());

		List<StandardSpells.Spell> at81 = StandardSpells.castable(81);
		assertEquals(21, find(at81, "Wind Surge").getMaxHit());
		assertEquals(null, find(at81, "Water Surge"));

		assertEquals(1, StandardSpells.castable(1).size());
		assertEquals(2, find(StandardSpells.castable(1), "Wind Strike").getMaxHit());
		assertTrue(StandardSpells.castable(0).isEmpty());
		assertEquals(20, StandardSpells.castable(99).size());
	}

	@Test
	public void magicDartFromTheMaximumMagicHitTable()
	{
		// Base at 99 Magic: 19, and 29 from a Slayer's staff (e) on task
		assertEquals(19, StandardSpells.magicDart(99, false));
		assertEquals(29, StandardSpells.magicDart(99, true));
		// Boosted levels count
		assertEquals(20, StandardSpells.magicDart(103, false));
		// Needs 50 Magic
		assertEquals(0, StandardSpells.magicDart(49, false));
		assertEquals(15, StandardSpells.magicDart(50, false));
	}
}
