package com.combatgearorganizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GearClassifierTest
{
	@Test
	public void meleeWeapon()
	{
		// Abyssal whip
		Bonuses whip = Bonuses.builder().slash(82).strength(82).build();
		assertEquals(GearStyle.MELEE, GearClassifier.classify(whip));
	}

	@Test
	public void rangedArmour()
	{
		// Black d'hide body
		Bonuses body = Bonuses.builder().magic(-15).range(30).defStab(55).defSlash(47).defCrush(60).defMagic(50).defRange(55).build();
		assertEquals(GearStyle.RANGED, GearClassifier.classify(body));
	}

	@Test
	public void magicNecklace()
	{
		// Occult necklace
		Bonuses occult = Bonuses.builder().magic(12).magicDamage(10).build();
		assertEquals(GearStyle.MAGIC, GearClassifier.classify(occult));
	}

	@Test
	public void tankArmourIsMelee()
	{
		// Rune platebody: no offensive bonuses, but penalises magic and ranged
		Bonuses rune = Bonuses.builder().magic(-30).range(-10).defStab(82).defSlash(80).defCrush(72).defMagic(-6).defRange(80).build();
		assertEquals(GearStyle.MELEE, GearClassifier.classify(rune));
	}

	@Test
	public void prayerGear()
	{
		// Holy book style item with only prayer bonus
		Bonuses book = Bonuses.builder().prayer(5).build();
		assertEquals(GearStyle.PRAYER, GearClassifier.classify(book));
	}

	@Test
	public void noBonusesIsUtility()
	{
		assertEquals(GearStyle.UTILITY, GearClassifier.classify(Bonuses.builder().build()));
	}

	@Test
	public void hybridJewelleryPrefersMelee()
	{
		// Amulet of fury
		Bonuses fury = Bonuses.builder().stab(10).slash(10).crush(10).magic(10).range(10).strength(8).prayer(5).build();
		assertEquals(GearStyle.MELEE, GearClassifier.classify(fury));
	}

	@Test
	public void strongerItemsHaveMorePower()
	{
		Bonuses whip = Bonuses.builder().slash(82).strength(82).build();
		Bonuses scim = Bonuses.builder().slash(67).strength(66).build();
		assertTrue(GearClassifier.power(GearStyle.MELEE, whip) > GearClassifier.power(GearStyle.MELEE, scim));
	}
}
