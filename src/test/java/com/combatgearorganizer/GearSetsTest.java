package com.combatgearorganizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class GearSetsTest
{
	@Test
	public void barrowsPiecesIncludingDegradedVersions()
	{
		assertEquals("Dharok's", GearSets.setOf("Dharok's helm"));
		assertEquals("Dharok's", GearSets.setOf("Dharok's platebody 100"));
		assertEquals("Dharok's", GearSets.setOf("Dharok's platelegs 0"));
		assertEquals("Karil's", GearSets.setOf("Karil's leatherskirt"));
		assertEquals("Ahrim's", GearSets.setOf("Ahrim's robetop"));
		assertEquals("Verac's", GearSets.setOf("Verac's brassard"));
		assertEquals("Guthan's", GearSets.setOf("Guthan's chainskirt"));
		assertEquals("Torag's", GearSets.setOf("Torag's platelegs"));
	}

	@Test
	public void bossArmourAndOrnaments()
	{
		assertEquals("Bandos", GearSets.setOf("Bandos chestplate"));
		assertEquals("Bandos", GearSets.setOf("Bandos tassets (or)"));
		assertEquals("Bandos", GearSets.setOf("Bandos boots"));
		assertEquals("Armadyl", GearSets.setOf("Armadyl chainskirt"));
		assertEquals("Torva", GearSets.setOf("Torva full helm (damaged)"));
		assertEquals("Masori", GearSets.setOf("Masori body (f)"));
		assertEquals("Ancestral", GearSets.setOf("Twisted ancestral hat"));
		assertEquals("Inquisitor's", GearSets.setOf("Inquisitor's great helm"));
		assertEquals("Crystal", GearSets.setOf("Crystal helm (perfected)"));
		assertEquals("Void knight", GearSets.setOf("Elite void top"));
		assertEquals("Void knight", GearSets.setOf("Void ranger helm (or)"));
		assertEquals("Eclipse moon", GearSets.setOf("Eclipse moon helm"));
		assertEquals("Blue moon", GearSets.setOf("Blue moon tassets"));
		assertEquals("Sunfire fanatic", GearSets.setOf("Sunfire fanatic cuirass"));
		assertEquals("Dagon'hai", GearSets.setOf("Dagon'hai robe top"));
		assertEquals("Mystic", GearSets.setOf("Mystic robe top (dark)"));
		assertEquals("Proselyte", GearSets.setOf("Proselyte hauberk"));
		assertEquals("Graceful", GearSets.setOf("Graceful hood"));
	}

	@Test
	public void dhideAndGodArmourAreTheirOwnSets()
	{
		assertEquals("Black d'hide", GearSets.setOf("Black d'hide body"));
		assertEquals("Black d'hide", GearSets.setOf("Black d'hide chaps (t)"));
		assertEquals("Black d'hide", GearSets.setOf("Black dragonhide vambraces"));
		assertEquals("Red d'hide", GearSets.setOf("Red d'hide chaps"));
		// Blessed d'hide, not the Bandos or Armadyl sets
		assertEquals("Bandos d'hide", GearSets.setOf("Bandos d'hide body"));
		assertEquals("Bandos d'hide", GearSets.setOf("Bandos d'hide boots"));
		assertEquals("Armadyl d'hide", GearSets.setOf("Armadyl coif"));
		assertEquals("Saradomin d'hide", GearSets.setOf("Saradomin bracers"));
		// God rune armour
		assertEquals("Zamorak armour", GearSets.setOf("Zamorak platebody"));
		assertEquals("Armadyl armour", GearSets.setOf("Armadyl full helm"));
		assertEquals("Rune armour", GearSets.setOf("Rune full helm (t)"));
		assertEquals("Dragon armour", GearSets.setOf("Dragon platelegs"));
	}

	@Test
	public void notSetPieces()
	{
		assertNull(GearSets.setOf("Black mask"));
		assertNull(GearSets.setOf("Dragon boots"));
		assertNull(GearSets.setOf("Helm of neitiznot"));
		assertNull(GearSets.setOf("Bandos cloak"));
		assertNull(GearSets.setOf("Oathplate slayer helmet"));
		assertNull(GearSets.setOf("Barrows gloves"));
		assertNull(GearSets.setOf(null));
		// Whole words only
		assertNull(GearSets.setOf("Torvald's hat"));
	}

	@Test
	public void namesAreNormalized()
	{
		// Curly apostrophes, odd case and spacing, and "dragonhide" spelled out
		assertEquals("Karil's", GearSets.setOf("KARIL\u2019S  coif"));
		assertEquals("Green d'hide", GearSets.setOf("Green dragonhide body"));
		assertEquals("Dagon'hai", GearSets.setOf("Dagonhai hat"));
		// Ornament and trimmed versions in brackets straight after the name
		assertEquals("Rune armour", GearSets.setOf("Rune platebody(g)"));
		assertNull(GearSets.setOf(""));
	}

	@Test
	public void longestPrefixWins()
	{
		// "Armadyl" alone is no set; the god d'hide and rune armour and the boss armour are three different sets
		assertEquals("Armadyl", GearSets.setOf("Armadyl chestplate"));
		assertEquals("Armadyl d'hide", GearSets.setOf("Armadyl d'hide body"));
		assertEquals("Armadyl armour", GearSets.setOf("Armadyl platebody"));
		assertNull(GearSets.setOf("Armadyl godsword"));
		assertEquals("Ancestral", GearSets.setOf("Twisted ancestral robe top"));
		assertEquals("Void knight", GearSets.setOf("Void knight gloves"));
	}

	@Test
	public void fallsBackToTheVariationBaseName()
	{
		assertEquals("Void knight", GearSets.setOf("Some renamed top", () -> "Void knight top"));
		assertNull(GearSets.setOf("Abyssal whip", () -> null));
		// The base name is only looked up when the item's own name matches no set
		assertEquals("Karil's", GearSets.setOf("Karil's coif", () ->
		{
			throw new AssertionError("looked up");
		}));
	}

	@Test
	public void gearItemTakesItsSetFromItsName()
	{
		assertEquals("Karil's", new GearItem(1, "Karil's coif", GearSlot.HEAD, GearStyle.RANGED, 1).getSet());
		assertNull(new GearItem(1, "Abyssal whip", GearSlot.WEAPON, GearStyle.MELEE, 1).getSet());
	}
}
