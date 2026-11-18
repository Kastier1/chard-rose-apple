package com.combatgearorganizer.dps;

/**
 * Which ammo a ranged weapon fires and which of the player's ammo fits it, by item name. Tiers are from the wiki's
 * "Bow" and "Crossbow (weapon)" tables ("Up to rune arrows"...), and "Ranged" for the light / standard / heavy
 * defence each kind of ammo rolls against.
 */
final class RangedAmmo
{
	private RangedAmmo()
	{
	}

	/** The ranged defence a shot rolls against ("Ranged", Project Rebalance ammo types). */
	enum Defence
	{
		LIGHT,
		STANDARD,
		HEAVY,
	}

	enum Kind
	{
		/** Bows. */
		ARROWS(Defence.STANDARD),
		/** Crossbows. */
		BOLTS(Defence.HEAVY),
		/** Karil's crossbow. */
		BOLT_RACKS(Defence.HEAVY),
		/** Ballistae. */
		JAVELINS(Defence.HEAVY),
		/** Toxic blowpipe: darts loaded in it. */
		BLOWPIPE(Defence.LIGHT),
		/** Eclipse atlatl: atlatl darts; hits with melee strength. */
		ATLATL(Defence.STANDARD),
		/** Bows that make their own arrows (crystal bow, Bow of faerdhinen, Craw's and Webweaver bows). */
		NO_AMMO(Defence.STANDARD),
		/** Darts, knives, thrownaxes and the like: the weapon is the ammo. */
		THROWN(Defence.LIGHT),
		/** Chinchompas. */
		CHINCHOMPA(Defence.HEAVY);

		final Defence defence;

		Kind(Defence defence)
		{
			this.defence = defence;
		}

		boolean needsAmmo()
		{
			return this == ARROWS || this == BOLTS || this == BOLT_RACKS || this == JAVELINS || this == ATLATL;
		}
	}

	private static final String[] OWN_ARROWS = {"crystal bow", "bow of faerdhinen", "craw's bow", "webweaver bow"};

	static Kind kind(String weaponName)
	{
		String n = WeaponRules.lower(weaponName);
		if (n.contains("blowpipe"))
		{
			return Kind.BLOWPIPE;
		}
		if (n.contains("ballista"))
		{
			return Kind.JAVELINS;
		}
		if (n.startsWith("karil's crossbow"))
		{
			return Kind.BOLT_RACKS;
		}
		if (n.contains("crossbow") || n.contains("c'bow"))
		{
			return Kind.BOLTS;
		}
		if (n.startsWith("eclipse atlatl"))
		{
			return Kind.ATLATL;
		}
		if (n.contains("chinchompa"))
		{
			return Kind.CHINCHOMPA;
		}
		if (WeaponRules.startsWithAny(n, OWN_ARROWS))
		{
			return Kind.NO_AMMO;
		}
		if (n.contains("bow"))
		{
			return Kind.ARROWS;
		}
		return Kind.THROWN;
	}

	// Tiers: bronze 2, blurite 3, iron 4, steel 6, mithril 8, adamant 10, rune 12, amethyst 14, dragon 16
	private static final Object[][] BOW_TIERS = {
		{new String[]{"twisted bow", "dark bow", "3rd age bow", "scorching bow", "venator bow"}, 16},
		{new String[]{"magic shortbow", "magic longbow", "magic comp bow", "seercull", "bone shortbow"}, 14},
		{new String[]{"yew "}, 12},
		{new String[]{"maple "}, 10},
		{new String[]{"willow "}, 8},
		{new String[]{"oak ", "signed oak"}, 6},
	};
	private static final Object[][] CROSSBOW_TIERS = {
		{new String[]{"dragon crossbow", "dragon hunter crossbow", "armadyl crossbow", "zaryte crossbow"}, 16},
		{new String[]{"rune crossbow"}, 12},
		{new String[]{"adamant crossbow"}, 10},
		{new String[]{"mithril crossbow"}, 8},
		{new String[]{"steel crossbow"}, 6},
		{new String[]{"iron crossbow"}, 4},
		{new String[]{"blurite crossbow"}, 3},
	};
	/** Arrows by name, first match wins; anything else (ogre, ice, brutal arrows) needs a special bow. */
	private static final Object[][] ARROW_TIERS = {
		{"dragon arrow", 16}, {"amethyst arrow", 14}, {"broad arrow", 14}, {"rune arrow", 12}, {"adamant arrow", 10},
		{"mithril arrow", 8}, {"steel arrow", 6}, {"iron arrow", 4}, {"bronze arrow", 2},
	};
	/** Bolts by name, first match wins ("dragonstone dragon bolts (e)" are dragon bolts). */
	private static final Object[][] BOLT_TIERS = {
		{"broad bolts", 12}, {"dragon bolts", 16}, {"dragonstone bolts", 12}, {"onyx bolts", 12}, {"runite bolts", 12},
		{"ruby bolts", 10}, {"diamond bolts", 10}, {"adamant bolts", 10}, {"sapphire bolts", 8}, {"emerald bolts", 8},
		{"mithril bolts", 8}, {"topaz bolts", 6}, {"steel bolts", 6}, {"pearl bolts", 4}, {"silver bolts", 4},
		{"iron bolts", 4}, {"jade bolts", 3}, {"blurite bolts", 3}, {"opal bolts", 2}, {"bronze bolts", 2},
	};

	/** Whether this ammo (an item in the ammo slot) can be fired from this weapon. */
	static boolean fits(Kind kind, String weaponName, String ammoName)
	{
		String a = WeaponRules.lower(ammoName);
		switch (kind)
		{
			case ARROWS:
				return tier(ARROW_TIERS, a) > 0 && tier(ARROW_TIERS, a) <= weaponTier(BOW_TIERS, weaponName, 4);
			case BOLTS:
				return tier(BOLT_TIERS, a) > 0 && tier(BOLT_TIERS, a) <= weaponTier(CROSSBOW_TIERS, weaponName, 2);
			case BOLT_RACKS:
				return a.startsWith("bolt rack");
			case JAVELINS:
				return a.contains("javelin");
			case ATLATL:
				return a.startsWith("atlatl dart");
			default:
				return false;
		}
	}

	/** Darts that can be loaded into a blowpipe (weapon-slot items). */
	static boolean isDart(String name)
	{
		String n = WeaponRules.lower(name);
		return n.endsWith(" dart") || n.contains(" dart(") || n.contains(" dart (");
	}

	/** Broad arrows and bolts: the only ammo that harms turoth and kurask ("Leaf-bladed weapons"). */
	static boolean isBroad(String ammoName)
	{
		return WeaponRules.lower(ammoName).contains("broad");
	}

	/** Silver bolts harm tier 2 vampyres ("Silver weaponry"). */
	static boolean isSilver(String ammoName)
	{
		return WeaponRules.lower(ammoName).startsWith("silver bolts");
	}

	private static int weaponTier(Object[][] table, String weaponName, int otherwise)
	{
		String n = WeaponRules.lower(weaponName);
		for (Object[] row : table)
		{
			if (WeaponRules.startsWithAny(n, (String[]) row[0]))
			{
				return (Integer) row[1];
			}
		}
		return otherwise;
	}

	private static int tier(Object[][] table, String ammo)
	{
		for (Object[] row : table)
		{
			if (ammo.contains((String) row[0]))
			{
				return (Integer) row[1];
			}
		}
		return 0;
	}
}
