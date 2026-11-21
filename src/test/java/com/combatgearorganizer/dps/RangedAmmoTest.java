package com.combatgearorganizer.dps;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RangedAmmoTest
{
	@Test
	public void weaponKinds()
	{
		assertEquals(RangedAmmo.Kind.BOLTS, RangedAmmo.kind("Dragon hunter crossbow"));
		assertEquals(RangedAmmo.Kind.BOLTS, RangedAmmo.kind("Armadyl crossbow"));
		assertEquals(RangedAmmo.Kind.BOLT_RACKS, RangedAmmo.kind("Karil's crossbow"));
		assertEquals(RangedAmmo.Kind.JAVELINS, RangedAmmo.kind("Heavy ballista"));
		assertEquals(RangedAmmo.Kind.BLOWPIPE, RangedAmmo.kind("Toxic blowpipe"));
		assertEquals(RangedAmmo.Kind.ARROWS, RangedAmmo.kind("Twisted bow"));
		assertEquals(RangedAmmo.Kind.ARROWS, RangedAmmo.kind("Magic shortbow (i)"));
		assertEquals(RangedAmmo.Kind.NO_AMMO, RangedAmmo.kind("Bow of faerdhinen (c)"));
		assertEquals(RangedAmmo.Kind.NO_AMMO, RangedAmmo.kind("Crystal bow"));
		assertEquals(RangedAmmo.Kind.ATLATL, RangedAmmo.kind("Eclipse atlatl"));
		assertEquals(RangedAmmo.Kind.CHINCHOMPA, RangedAmmo.kind("Black chinchompa"));
		assertEquals(RangedAmmo.Kind.THROWN, RangedAmmo.kind("Dragon knife"));
		// Light, standard, heavy ("Ranged")
		assertEquals(RangedAmmo.Defence.HEAVY, RangedAmmo.Kind.BOLTS.defence);
		assertEquals(RangedAmmo.Defence.STANDARD, RangedAmmo.Kind.ARROWS.defence);
		assertEquals(RangedAmmo.Defence.LIGHT, RangedAmmo.Kind.BLOWPIPE.defence);
	}

	@Test
	public void ammoTiersFromTheBowAndCrossbowTables()
	{
		RangedAmmo.Kind arrows = RangedAmmo.Kind.ARROWS;
		assertTrue(RangedAmmo.fits(arrows, "Twisted bow", "Dragon arrow"));
		assertTrue(RangedAmmo.fits(arrows, "Magic shortbow (i)", "Amethyst arrow"));
		assertFalse(RangedAmmo.fits(arrows, "Magic shortbow (i)", "Dragon arrow"));
		assertTrue(RangedAmmo.fits(arrows, "Yew longbow", "Rune arrow"));
		assertFalse(RangedAmmo.fits(arrows, "Yew longbow", "Amethyst arrow"));
		assertFalse(RangedAmmo.fits(arrows, "Maple shortbow", "Rune arrow"));
		assertTrue(RangedAmmo.fits(arrows, "Shortbow", "Iron arrow"));
		assertFalse(RangedAmmo.fits(arrows, "Shortbow", "Steel arrow"));
		// "Broad arrows": magic bows or stronger
		assertTrue(RangedAmmo.fits(arrows, "Magic longbow", "Broad arrows"));
		assertFalse(RangedAmmo.fits(arrows, "Yew longbow", "Broad arrows"));
		// Bows don't fire bolts
		assertFalse(RangedAmmo.fits(arrows, "Twisted bow", "Dragon bolts"));
		assertFalse(RangedAmmo.fits(arrows, "Twisted bow", "Ogre arrow"));

		RangedAmmo.Kind bolts = RangedAmmo.Kind.BOLTS;
		assertTrue(RangedAmmo.fits(bolts, "Armadyl crossbow", "Ruby dragon bolts (e)"));
		assertTrue(RangedAmmo.fits(bolts, "Rune crossbow", "Runite bolts"));
		assertTrue(RangedAmmo.fits(bolts, "Rune crossbow", "Dragonstone bolts (e)"));
		assertFalse(RangedAmmo.fits(bolts, "Rune crossbow", "Dragon bolts"));
		assertFalse(RangedAmmo.fits(bolts, "Rune crossbow", "Dragonstone dragon bolts (e)"));
		// "Broad bolts" and "Amethyst broad bolts": rune crossbow and up
		assertTrue(RangedAmmo.fits(bolts, "Rune crossbow", "Amethyst broad bolts"));
		assertFalse(RangedAmmo.fits(bolts, "Adamant crossbow", "Broad bolts"));
		// "Silver bolts": iron crossbow or better
		assertTrue(RangedAmmo.fits(bolts, "Iron crossbow", "Silver bolts"));
		assertFalse(RangedAmmo.fits(bolts, "Bronze crossbow", "Silver bolts"));

		assertTrue(RangedAmmo.fits(RangedAmmo.Kind.BOLT_RACKS, "Karil's crossbow", "Bolt rack"));
		assertFalse(RangedAmmo.fits(RangedAmmo.Kind.BOLT_RACKS, "Karil's crossbow", "Dragon bolts"));
		assertTrue(RangedAmmo.fits(RangedAmmo.Kind.JAVELINS, "Heavy ballista", "Dragon javelin"));
		assertTrue(RangedAmmo.fits(RangedAmmo.Kind.ATLATL, "Eclipse atlatl", "Atlatl dart"));
		assertFalse(RangedAmmo.fits(RangedAmmo.Kind.NO_AMMO, "Crystal bow", "Dragon arrow"));
	}

	@Test
	public void dartsBroadAndSilver()
	{
		assertTrue(RangedAmmo.isDart("Dragon dart"));
		assertTrue(RangedAmmo.isDart("Amethyst dart(p++)"));
		assertFalse(RangedAmmo.isDart("Dragon dart tip"));
		assertTrue(RangedAmmo.isBroad("Amethyst broad bolts"));
		assertTrue(RangedAmmo.isSilver("Silver bolts (p)"));
		assertFalse(RangedAmmo.isSilver("Dragon bolts"));
	}
}
