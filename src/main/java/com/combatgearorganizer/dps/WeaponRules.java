package com.combatgearorganizer.dps;

import java.util.Locale;

/**
 * What a weapon does beyond its bonuses, by item name (lower case). Each value is from the item's OSRS Wiki page;
 * the page is named next to it.
 */
final class WeaponRules
{
	private WeaponRules()
	{
	}

	/** An accuracy and damage bonus in percent, e.g. 70 and 70 for Arclight against demons. */
	static final class Bane
	{
		final int accuracy;
		final int damage;
		/** Added to the slayer helmet's bonus instead of multiplying it (dragon hunter crossbow, scorching bow). */
		final boolean addsToSlayerHelm;
		final String label;

		Bane(int accuracy, int damage, boolean addsToSlayerHelm, String label)
		{
			this.accuracy = accuracy;
			this.damage = damage;
			this.addsToSlayerHelm = addsToSlayerHelm;
			this.label = label;
		}
	}

	// "Demonbane weapons", "Arclight", "Darklight", "Emberlight", "Scorching bow", "Demon (attribute)":
	// Arclight and Emberlight +70% accuracy and damage, Darklight +60% accuracy and damage, Silverlight +60% damage,
	// scorching bow +30% accuracy and damage, added to the slayer helmet (i)'s bonus.
	private static final Bane ARCLIGHT = new Bane(70, 70, false, "demonbane");
	private static final Bane DARKLIGHT = new Bane(60, 60, false, "demonbane");
	private static final Bane SILVERLIGHT = new Bane(0, 60, false, "demonbane");
	private static final Bane SCORCHING_BOW = new Bane(30, 30, true, "demonbane");
	// "Dragon hunter lance": +20% accuracy and damage, times the slayer helmet or salve. "Dragon hunter crossbow":
	// +30% accuracy, +25% damage, added to the slayer helmet (i)'s bonus, times a salve amulet's.
	// "Dragon hunter wand": +75% accuracy, +40% damage, times the slayer helmet (i).
	private static final Bane LANCE = new Bane(20, 20, false, "dragonbane");
	private static final Bane DH_CROSSBOW = new Bane(30, 25, true, "dragonbane");
	private static final Bane DH_WAND = new Bane(75, 40, false, "dragonbane");
	// "Vampyre (attribute)": the weapons that harm tier 3 vampyres, with their bonuses against every vampyre
	private static final String[][] VAMPYREBANE = {
		{"blisterwood sickle", "5", "15"},
		{"ivandis flail", "0", "20"},
		{"blisterwood flail", "5", "25"},
		{"hallowed flail", "25", "25"},
		{"sunspear", "25", "50"},
		{"blisterwood stake", "25", "25"},
	};
	/** "Silver weaponry": weapons that harm tier 2 vampyres (and do 10% more to tier 1), but not tier 3. */
	private static final String[] SILVER = {
		"blessed axe", "rod of ivandis", "silver sickle", "emerald sickle", "enchanted emerald sickle", "ruby sickle",
		"enchanted ruby sickle", "diamond sickle", "enchanted diamond sickle", "silverlight", "darklight", "arclight",
		"emberlight", "wolfbane",
	};
	/** "Silver weaponry": silver weapons do 10% more damage to tier 1 vampyres. */
	static final int SILVER_TIER1_DAMAGE = 10;

	/** The demonbane bonus of a melee or ranged weapon, or null. */
	static Bane demonbane(String name)
	{
		String n = lower(name);
		if (n.startsWith("arclight") || n.startsWith("emberlight"))
		{
			return ARCLIGHT;
		}
		if (n.startsWith("darklight"))
		{
			return DARKLIGHT;
		}
		if (n.startsWith("silverlight"))
		{
			return SILVERLIGHT;
		}
		if (n.startsWith("scorching bow"))
		{
			return SCORCHING_BOW;
		}
		return null;
	}

	/** The dragonbane bonus of a weapon, or null. */
	static Bane dragonbane(String name)
	{
		String n = lower(name);
		if (n.startsWith("dragon hunter lance"))
		{
			return LANCE;
		}
		if (n.startsWith("dragon hunter crossbow"))
		{
			return DH_CROSSBOW;
		}
		if (n.startsWith("dragon hunter wand"))
		{
			return DH_WAND;
		}
		return null;
	}

	/** Bonus against vampyres of a weapon that harms tier 3 vampyres, or null. */
	static Bane vampyrebane(String name)
	{
		String n = lower(name);
		for (String[] w : VAMPYREBANE)
		{
			if (n.startsWith(w[0]))
			{
				return new Bane(Integer.parseInt(w[1]), Integer.parseInt(w[2]), false, "vampyrebane");
			}
		}
		return null;
	}

	static boolean isSilver(String name)
	{
		return startsWithAny(lower(name), SILVER);
	}

	/** "Keris": +33% damage against kalphites and scabarites, and 1 in 51 hits deal triple. */
	static boolean isKeris(String name)
	{
		return lower(name).startsWith("keris");
	}

	/** "Leaf-bladed weapons": the only melee weapons that harm turoth and kurask. */
	static boolean isLeafBladed(String name)
	{
		return lower(name).startsWith("leaf-bladed");
	}

	/** "Leaf-bladed battleaxe": +17.5% damage against turoth and kurask. */
	static boolean isLeafBladedBattleaxe(String name)
	{
		return lower(name).startsWith("leaf-bladed battleaxe");
	}

	/** "Osmumten's fang": rolls accuracy twice on stab, and hits between 15% and 85% of its max hit. */
	static boolean isFang(String name)
	{
		return lower(name).startsWith("osmumten's fang");
	}

	/** "Scythe of Vitur": hits a 2x2 monster twice and a 3x3 or bigger one three times, each hit half the last. */
	static boolean isScythe(String name)
	{
		String n = lower(name);
		return n.startsWith("scythe of vitur") || n.startsWith("holy scythe of vitur")
			|| n.startsWith("sanguine scythe of vitur");
	}

	/** "Dual macuahuitl": splits its max hit over two hits; the second only lands if the first did. */
	static boolean isDualMacuahuitl(String name)
	{
		return lower(name).startsWith("dual macuahuitl");
	}

	/** "Colossal blade": +2 max hit per tile of the monster's size, up to 5 tiles. */
	static boolean isColossalBlade(String name)
	{
		return lower(name).startsWith("colossal blade");
	}

	/** "Eclipse atlatl": its max hit comes from Strength and melee strength bonus, its accuracy from Ranged. */
	static boolean isAtlatl(String name)
	{
		return lower(name).startsWith("eclipse atlatl");
	}

	static boolean isTwistedBow(String name)
	{
		return lower(name).startsWith("twisted bow");
	}

	static boolean isTumekensShadow(String name)
	{
		return lower(name).startsWith("tumeken's shadow");
	}

	/** "Harmonised nightmare staff": standard spells take 4 ticks instead of 5. */
	static boolean isHarmonisedStaff(String name)
	{
		return lower(name).startsWith("harmonised nightmare staff");
	}

	/** "Magic Dart": the staves that can cast it. */
	static boolean castsMagicDart(String name)
	{
		return startsWithAny(lower(name), new String[]{"slayer's staff", "staff of the dead", "toxic staff of the dead",
			"staff of light", "staff of balance"});
	}

	/** "Magic Dart": cast from a Slayer's staff (e) on a task monster it hits harder. */
	static boolean isEnchantedSlayerStaff(String name)
	{
		return lower(name).startsWith("slayer's staff (e)");
	}

	/**
	 * The base max hit of a powered staff's built-in spell at this (visible) Magic level, from the wiki's "Maximum
	 * magic hit" table of magic-scaling sources; 0 when it isn't a powered staff. Autocast versions ("(a)") cast
	 * spellbook spells instead.
	 */
	static int poweredStaffBase(String name, int magic)
	{
		String n = lower(name);
		if (n.endsWith("(a)") || n.endsWith("(au)"))
		{
			return 0;
		}
		if (n.startsWith("tumeken's shadow"))
		{
			return magic / 3 + 1;
		}
		if (n.startsWith("sanguinesti staff") || n.startsWith("holy sanguinesti staff"))
		{
			return magic / 3;
		}
		if (n.startsWith("trident of the swamp") || n.startsWith("uncharged toxic trident"))
		{
			return magic / 3 - 2;
		}
		if (n.startsWith("trident of the seas") || n.startsWith("uncharged trident"))
		{
			return magic / 3 - 5;
		}
		if (n.startsWith("accursed sceptre") || n.startsWith("eye of ayak"))
		{
			return magic / 3 - 6;
		}
		if (n.startsWith("thammaron's sceptre"))
		{
			return magic / 3 - 8;
		}
		if (n.startsWith("warped sceptre"))
		{
			return (8 * magic + 96) / 37;
		}
		return 0;
	}

	/**
	 * The melee attack types the weapon's category has a style for ("Weapons/Categories" and "Combat Options"), for the
	 * categories where a bonus shows in a type the weapon can't attack with: scythes and 2h swords have no stab, axes no
	 * stab, flails and hammers only crush, whips only slash, halberds no crush, pickaxes no slash. Null when any type with a bonus will do.
	 */
	static java.util.Set<String> meleeTypes(String name)
	{
		String n = lower(name);
		if (n.contains("whip") || n.contains("abyssal tentacle"))
		{
			return set("slash");
		}
		if (n.contains("flail") || n.contains("hammer") || n.contains("maul") || n.contains("bludgeon"))
		{
			return set("crush");
		}
		if (n.contains("halberd"))
		{
			return set("stab", "slash");
		}
		if (n.contains("pickaxe"))
		{
			return set("stab", "crush");
		}
		if (n.contains("scythe") || n.contains("godsword") || n.contains("2h sword") || n.startsWith("colossal blade")
			|| n.startsWith("saradomin sword") || n.contains("axe"))
		{
			return set("slash", "crush");
		}
		return null;
	}

	private static java.util.Set<String> set(String... types)
	{
		return new java.util.HashSet<>(java.util.Arrays.asList(types));
	}

	static boolean isSlayerHelm(String name)
	{
		String n = lower(name);
		return n.contains("slayer helmet") || n.startsWith("black mask");
	}

	static boolean isSalve(String name)
	{
		return lower(name).startsWith("salve amulet");
	}

	/** "(i)" and "(ei)" versions also work for ranged and magic. */
	static boolean isImbued(String name)
	{
		String n = lower(name);
		return n.contains("(i)") || n.contains("(ei)");
	}

	/** Salve amulet (e) and (ei) give 20% instead of 16.67%. */
	static boolean isEnchantedSalve(String name)
	{
		String n = lower(name);
		return n.contains("(e)") || n.contains("(ei)");
	}

	static String lower(String name)
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
	}

	static boolean startsWithAny(String n, String[] prefixes)
	{
		for (String p : prefixes)
		{
			if (n.startsWith(p))
			{
				return true;
			}
		}
		return false;
	}
}
