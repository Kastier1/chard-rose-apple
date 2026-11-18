package com.combatgearorganizer.dps;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeaponRulesTest
{
	@Test
	public void poweredStaffMaxHitsAt99MatchTheWikiTable()
	{
		// "Maximum magic hit", magic-scaling sources, base at Magic 99
		assertEquals(34, WeaponRules.poweredStaffBase("Tumeken's shadow", 99));
		assertEquals(33, WeaponRules.poweredStaffBase("Sanguinesti staff", 99));
		assertEquals(33, WeaponRules.poweredStaffBase("Holy sanguinesti staff", 99));
		assertEquals(31, WeaponRules.poweredStaffBase("Trident of the swamp", 99));
		assertEquals(31, WeaponRules.poweredStaffBase("Trident of the swamp (e)", 99));
		assertEquals(28, WeaponRules.poweredStaffBase("Trident of the seas", 99));
		assertEquals(28, WeaponRules.poweredStaffBase("Uncharged trident", 99));
		assertEquals(27, WeaponRules.poweredStaffBase("Accursed sceptre", 99));
		assertEquals(27, WeaponRules.poweredStaffBase("Eye of ayak", 99));
		assertEquals(25, WeaponRules.poweredStaffBase("Thammaron's sceptre", 99));
		assertEquals(24, WeaponRules.poweredStaffBase("Warped sceptre", 99));
		// Boosted Magic raises it
		assertEquals(35, WeaponRules.poweredStaffBase("Tumeken's shadow", 103));
		// Autocast versions and normal staves cast spellbook spells
		assertEquals(0, WeaponRules.poweredStaffBase("Accursed sceptre (a)", 99));
		assertEquals(0, WeaponRules.poweredStaffBase("Kodai wand", 99));
	}

	@Test
	public void banesFromTheirWikiPages()
	{
		assertEquals(70, WeaponRules.demonbane("Arclight").damage);
		assertEquals(70, WeaponRules.demonbane("Emberlight").accuracy);
		assertEquals(60, WeaponRules.demonbane("Darklight").damage);
		assertEquals(60, WeaponRules.demonbane("Darklight").accuracy);
		assertEquals(0, WeaponRules.demonbane("Silverlight").accuracy);
		assertTrue(WeaponRules.demonbane("Scorching bow").addsToSlayerHelm);
		assertNull(WeaponRules.demonbane("Abyssal whip"));

		assertEquals(20, WeaponRules.dragonbane("Dragon hunter lance").damage);
		assertEquals(30, WeaponRules.dragonbane("Dragon hunter crossbow (t)").accuracy);
		assertEquals(25, WeaponRules.dragonbane("Dragon hunter crossbow").damage);
		assertTrue(WeaponRules.dragonbane("Dragon hunter crossbow").addsToSlayerHelm);
		assertEquals(75, WeaponRules.dragonbane("Dragon hunter wand").accuracy);
		assertEquals(40, WeaponRules.dragonbane("Dragon hunter wand").damage);
		assertNull(WeaponRules.dragonbane("Dragon crossbow"));

		assertNotNull(WeaponRules.vampyrebane("Blisterwood flail"));
		assertEquals(20, WeaponRules.vampyrebane("Ivandis flail").damage);
		assertNull(WeaponRules.vampyrebane("Rod of ivandis"));
		assertTrue(WeaponRules.isSilver("Rod of ivandis (10)"));
		assertTrue(WeaponRules.isSilver("Darklight"));
		assertFalse(WeaponRules.isSilver("Abyssal whip"));
	}

	@Test
	public void namedEffects()
	{
		assertTrue(WeaponRules.isKeris("Keris partisan of breaching"));
		assertTrue(WeaponRules.isLeafBladed("Leaf-bladed spear"));
		assertTrue(WeaponRules.isLeafBladedBattleaxe("Leaf-bladed battleaxe"));
		assertFalse(WeaponRules.isLeafBladedBattleaxe("Leaf-bladed sword"));
		assertTrue(WeaponRules.isScythe("Holy scythe of vitur"));
		assertTrue(WeaponRules.castsMagicDart("Toxic staff of the dead"));
		assertTrue(WeaponRules.castsMagicDart("Slayer's staff (e)"));
		assertTrue(WeaponRules.isEnchantedSlayerStaff("Slayer's staff (e)"));
		assertFalse(WeaponRules.isEnchantedSlayerStaff("Slayer's staff"));
		assertFalse(WeaponRules.castsMagicDart("Kodai wand"));
	}

	@Test
	public void slayerHelmetsAndSalves()
	{
		assertTrue(WeaponRules.isSlayerHelm("Slayer helmet (i)"));
		assertTrue(WeaponRules.isSlayerHelm("Twisted slayer helmet (i)"));
		assertTrue(WeaponRules.isSlayerHelm("Black mask (10)"));
		assertFalse(WeaponRules.isSlayerHelm("Neitiznot faceguard"));
		assertTrue(WeaponRules.isImbued("Slayer helmet (i)"));
		assertTrue(WeaponRules.isImbued("Black mask (5) (i)"));
		assertFalse(WeaponRules.isImbued("Slayer helmet"));
		assertTrue(WeaponRules.isImbued("Salve amulet(i)"));
		assertTrue(WeaponRules.isImbued("Salve amulet(ei)"));
		assertFalse(WeaponRules.isImbued("Salve amulet (e)"));
		assertTrue(WeaponRules.isEnchantedSalve("Salve amulet (e)"));
		assertTrue(WeaponRules.isEnchantedSalve("Salve amulet(ei)"));
		assertFalse(WeaponRules.isEnchantedSalve("Salve amulet(i)"));
		assertTrue(WeaponRules.isSalve("Salve amulet"));
	}
}
