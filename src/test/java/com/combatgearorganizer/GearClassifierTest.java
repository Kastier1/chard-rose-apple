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

	// Stats below are real, from osrs-dps-calc's equipment.json (used as reference only, not bundled)

	private static GearStyle style(String name, GearSlot slot, Bonuses b)
	{
		return GearClassifier.classify(name, slot, b);
	}

	private static int power(String name, GearSlot slot, Bonuses b)
	{
		return GearClassifier.power(style(name, slot, b), name, slot, b);
	}

	private static final Bonuses STAFF_OF_THE_DEAD = Bonuses.builder().stab(55).slash(70).magic(17).strength(72).magicDamage(15).build();
	private static final Bonuses AHRIMS_STAFF = Bonuses.builder().stab(12).slash(-1).crush(65).magic(15).strength(68).magicDamage(5).build();
	private static final Bonuses PURGING_STAFF = Bonuses.builder().stab(10).slash(-5).crush(83).magic(37).strength(62).magicDamage(10).build();
	private static final Bonuses BANDOS_PLATEBODY = Bonuses.builder().magic(-30).range(-15)
		.defStab(82).defSlash(80).defCrush(72).defMagic(-6).defRange(80).prayer(1).build();
	private static final Bonuses ELITE_VOID_TOP = Bonuses.builder()
		.defStab(45).defSlash(45).defCrush(45).defMagic(45).defRange(45).prayer(3).build();
	private static final Bonuses SLAYER_HELMET_I = Bonuses.builder().magic(3).range(3)
		.defStab(30).defSlash(32).defCrush(27).defMagic(10).defRange(30).build();

	@Test
	public void stavesAreMagicWeapons()
	{
		assertEquals(GearStyle.MAGIC, style("Staff of the dead", GearSlot.WEAPON, STAFF_OF_THE_DEAD));
		assertEquals(GearStyle.MAGIC, style("Ahrim's staff", GearSlot.WEAPON, AHRIMS_STAFF));
		assertEquals(GearStyle.MAGIC, style("Purging staff", GearSlot.WEAPON, PURGING_STAFF));
		// The whip has no magic attack and stays melee
		assertEquals(GearStyle.MELEE, style("Abyssal whip", GearSlot.WEAPON, Bonuses.builder().slash(82).strength(82).build()));
	}

	@Test
	public void godArmourWithPrayerIsStillMelee()
	{
		assertEquals(GearStyle.MELEE, style("Bandos platebody", GearSlot.BODY, BANDOS_PLATEBODY));
	}

	@Test
	public void voidAndSlayerHelmsStayTogetherInUtility()
	{
		assertEquals(GearStyle.UTILITY, style("Elite void top", GearSlot.BODY, ELITE_VOID_TOP));
		assertEquals(GearStyle.UTILITY, style("Void knight top", GearSlot.BODY, ELITE_VOID_TOP));
		assertEquals(GearStyle.UTILITY, style("Slayer helmet (i)", GearSlot.HEAD, SLAYER_HELMET_I));
	}

	@Test
	public void damageBeatsTankinessForShields()
	{
		Bonuses avernic = Bonuses.builder().stab(30).slash(29).crush(28).magic(-5).range(-4)
			.defStab(30).defSlash(29).defCrush(28).defMagic(-5).defRange(-4).strength(8).build();
		Bonuses dragonfire = Bonuses.builder().magic(-10).range(-5)
			.defStab(70).defSlash(75).defCrush(72).defMagic(10).defRange(72).strength(7).build();
		assertTrue(power("Avernic defender", GearSlot.SHIELD, avernic) > power("Dragonfire shield", GearSlot.SHIELD, dragonfire));
	}

	@Test
	public void meleeWeaponOrder()
	{
		Bonuses fang = Bonuses.builder().stab(105).slash(75).strength(103).build();
		Bonuses bulwark = Bonuses.builder().crush(124).strength(38)
			.defStab(106).defSlash(109).defCrush(109).defMagic(-10).defRange(148).build();
		Bonuses scythe = Bonuses.builder().stab(70).slash(125).crush(30).strength(75).build();
		int fangPower = power("Osmumten's fang", GearSlot.WEAPON, fang);
		assertTrue(fangPower > power("Dinh's bulwark", GearSlot.WEAPON, bulwark));
		assertTrue(power("Scythe of vitur", GearSlot.WEAPON, scythe) > fangPower);
	}

	@Test
	public void tortureBeatsFury()
	{
		Bonuses torture = Bonuses.builder().stab(15).slash(15).crush(15).strength(10).prayer(2).build();
		Bonuses fury = Bonuses.builder().stab(10).slash(10).crush(10).magic(10).range(10).strength(8).prayer(5)
			.defStab(15).defSlash(15).defCrush(15).defMagic(15).defRange(15).build();
		assertTrue(power("Amulet of torture", GearSlot.NECK, torture) > power("Amulet of fury", GearSlot.NECK, fury));
	}

	@Test
	public void ammoFiredWeaponsCountTheirAmmo()
	{
		Bonuses tbow = Bonuses.builder().range(70).rangedStrength(20).attackSpeed(6).build();
		Bonuses bowfa = Bonuses.builder().range(128).rangedStrength(106).attackSpeed(5).build();
		Bonuses acb = Bonuses.builder().range(100).prayer(1).attackSpeed(6).build();
		Bonuses msb = Bonuses.builder().range(75).attackSpeed(4).build();
		int tbowPower = power("Twisted bow", GearSlot.WEAPON, tbow);
		int acbPower = power("Armadyl crossbow", GearSlot.WEAPON, acb);
		assertTrue(tbowPower > power("Bow of faerdhinen (c)", GearSlot.WEAPON, bowfa));
		// Without the bolts' strength the crossbow (+100 ranged, no strength) would rank below a shortbow
		assertTrue(acbPower > power("", GearSlot.NONE, msb));
	}

	// Stats below are the wiki's (osrs-dps-calc equipment.json), attack speed in ticks.

	@Test
	public void rangedWeaponsRankPerTick()
	{
		Bonuses bowfa = Bonuses.builder().range(128).rangedStrength(106).attackSpeed(5).build();
		Bonuses heavyBallista = Bonuses.builder().range(125).rangedStrength(15).attackSpeed(7).build();
		Bonuses blowpipe = Bonuses.builder().range(30).rangedStrength(20).attackSpeed(3).build();
		Bonuses rcb = Bonuses.builder().range(90).attackSpeed(6).build();
		assertTrue(power("Bow of faerdhinen (c)", GearSlot.WEAPON, bowfa) > power("Heavy ballista", GearSlot.WEAPON, heavyBallista));
		assertTrue(power("Toxic blowpipe", GearSlot.WEAPON, blowpipe) > power("Rune crossbow", GearSlot.WEAPON, rcb));
	}

	@Test
	public void specWeaponsGoAfterMainWeapons()
	{
		Bonuses fang = Bonuses.builder().stab(105).slash(75).strength(103).attackSpeed(5).build();
		Bonuses maul = Bonuses.builder().crush(135).strength(147).attackSpeed(6).build();
		Bonuses ags = Bonuses.builder().slash(132).crush(80).strength(132).prayer(8).attackSpeed(6).build();
		Bonuses whip = Bonuses.builder().slash(82).strength(82).attackSpeed(4).build();
		int fangPower = power("Osmumten's fang", GearSlot.WEAPON, fang);
		assertTrue(fangPower > power("Elder maul", GearSlot.WEAPON, maul));
		assertTrue(fangPower > power("Armadyl godsword", GearSlot.WEAPON, ags));
		// Spec weapons still sort among themselves
		assertTrue(power("Elder maul", GearSlot.WEAPON, maul) > power("Armadyl godsword", GearSlot.WEAPON, ags));
		// Even a whip comes before them
		assertTrue(power("Abyssal whip", GearSlot.WEAPON, whip) > power("Elder maul", GearSlot.WEAPON, maul));
	}

	@Test
	public void slowWeaponsLoseToFastOnes()
	{
		// Same bonuses, a two-handed speed against a one-handed one
		Bonuses fast = Bonuses.builder().slash(90).strength(86).attackSpeed(4).build();
		Bonuses slow = Bonuses.builder().slash(90).strength(86).attackSpeed(7).build();
		assertTrue(power("", GearSlot.WEAPON, fast) > power("", GearSlot.WEAPON, slow));
	}

	@Test
	public void poweredStavesRankByTheirSpell()
	{
		Bonuses shadow = Bonuses.builder().magic(35).prayer(1).attackSpeed(5).build();
		Bonuses sang = Bonuses.builder().magic(25).range(-4).attackSpeed(4).build();
		Bonuses ahrims = Bonuses.builder().magic(15).magicDamage(5).crush(68).strength(68).attackSpeed(6).build();
		Bonuses masterWand = Bonuses.builder().magic(20).magicDamage(10).attackSpeed(4).build();
		Bonuses staffOfLight = Bonuses.builder().stab(55).slash(70).magic(17).magicDamage(15).strength(72).attackSpeed(4).build();
		int sangPower = power("Sanguinesti staff", GearSlot.WEAPON, sang);
		assertEquals(GearStyle.MAGIC, style("Sanguinesti staff", GearSlot.WEAPON, sang));
		assertTrue(power("Tumeken's shadow", GearSlot.WEAPON, shadow) > sangPower);
		assertTrue(sangPower > power("Ahrim's staff", GearSlot.WEAPON, ahrims));
		assertTrue(sangPower > power("Master wand", GearSlot.WEAPON, masterWand));
		assertTrue(sangPower > power("Staff of light", GearSlot.WEAPON, staffOfLight));
	}

	@Test
	public void shadowBeatsKodai()
	{
		Bonuses shadow = Bonuses.builder().magic(35).defMagic(20).prayer(1).build();
		Bonuses kodai = Bonuses.builder().magic(28).magicDamage(15).defSlash(3).defCrush(3).defMagic(20).build();
		assertTrue(power("Tumeken's shadow", GearSlot.WEAPON, shadow) > power("Kodai wand", GearSlot.WEAPON, kodai));
	}

	@Test
	public void defenceBreaksTies()
	{
		Bonuses plain = Bonuses.builder().slash(10).strength(10).build();
		Bonuses tanky = Bonuses.builder().slash(10).strength(10).defSlash(40).build();
		assertTrue(power("", GearSlot.HEAD, tanky) > power("", GearSlot.HEAD, plain));
	}
}
