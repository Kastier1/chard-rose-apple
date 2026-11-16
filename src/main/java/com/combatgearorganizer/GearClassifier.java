package com.combatgearorganizer;

import java.util.Locale;

/**
 * Decides which combat style an item belongs to, and how strong it is for that style.
 */
public final class GearClassifier
{
	/** A weapon with at least this much magic attack is a magic weapon, whatever its melee bonuses (staves). */
	private static final int MAGIC_WEAPON_ATTACK = 10;

	/**
	 * Ranged weapons that fire ammo get their strength from the ammo. A weapon with this much ranged strength or
	 * less is treated as ammo-fired and given the strength of typical good ammo for its type.
	 */
	private static final int AMMO_FIRED_MAX_STRENGTH = 20;
	private static final int ARROW_STRENGTH = 60;
	private static final int BOLT_STRENGTH = 100;
	private static final int JAVELIN_STRENGTH = 150;

	/**
	 * Score bonus for weapons whose power comes from an effect the bonuses don't show (Twisted bow's scaling,
	 * Tumeken's shadow multiplying magic gear, Scythe hitting three times). Keeps them first in their row.
	 */
	private static final String[] EFFECT_WEAPONS = {"twisted bow", "tumeken's shadow", "scythe of vitur"};
	private static final int EFFECT_WEAPON_BONUS = 400;

	/** Defence only breaks ties between items with the same offensive score. */
	private static final int OFFENCE_WEIGHT = 1000;
	private static final int MAX_DEFENCE_TIEBREAK = OFFENCE_WEIGHT - 1;

	private GearClassifier()
	{
	}

	public static GearStyle classify(Bonuses b)
	{
		return classify("", GearSlot.NONE, b);
	}

	public static GearStyle classify(String name, GearSlot slot, Bonuses b)
	{
		if (isSlayerUtility(name))
		{
			return GearStyle.UTILITY;
		}

		// Staves have big crush and strength bonuses, but a staff is a magic weapon
		if (slot == GearSlot.WEAPON && b.getMagic() >= MAGIC_WEAPON_ATTACK && b.getMagic() > b.getRange())
		{
			return GearStyle.MAGIC;
		}

		int melee = meleeOffence(b);
		int ranged = rangedOffence(b);
		int magic = magicOffence(b);
		int best = Math.max(melee, Math.max(ranged, magic));

		if (best > 0)
		{
			// Ties favour melee, then ranged, then magic (e.g. hybrid jewellery like an amulet of fury)
			if (melee == best)
			{
				return GearStyle.MELEE;
			}
			return ranged == best ? GearStyle.RANGED : GearStyle.MAGIC;
		}

		// No offensive bonuses at all: fall back to what the item gives up
		if (b.getMagic() < 0 && b.getRange() < 0)
		{
			// Tank armour (rune platebody, Bandos, Justiciar, Verac's): penalises both magic and ranged.
			// Checked before prayer, because a lot of god and Barrows armour also has a prayer bonus.
			return GearStyle.MELEE;
		}
		if (b.getMagic() < 0)
		{
			return GearStyle.RANGED;
		}
		if (b.getPrayer() > 0)
		{
			return GearStyle.PRAYER;
		}
		return GearStyle.UTILITY;
	}

	public static int power(GearStyle style, Bonuses b)
	{
		return power(style, "", GearSlot.NONE, b);
	}

	/**
	 * Higher is better. Offence decides; defence only breaks ties.
	 */
	public static int power(GearStyle style, String name, GearSlot slot, Bonuses b)
	{
		int defence = Math.max(0, Math.min(defenceScore(b), MAX_DEFENCE_TIEBREAK));
		int offence;
		switch (style)
		{
			case MELEE:
				offence = b.getStrength() * 2 + bestMeleeAttack(b);
				break;
			case RANGED:
				offence = (b.getRangedStrength() + ammoStrength(name, slot, b)) * 2 + b.getRange();
				break;
			case MAGIC:
				offence = Math.round(b.getMagicDamage() * 3) + b.getMagic();
				break;
			case PRAYER:
				offence = b.getPrayer();
				break;
			default:
				// Utility items have no offence to speak of; defence and prayer are all there is
				return defenceScore(b) + b.getPrayer();
		}

		if (slot == GearSlot.WEAPON && isEffectWeapon(name))
		{
			offence += EFFECT_WEAPON_BONUS;
		}
		return offence * OFFENCE_WEIGHT + defence;
	}

	/** Strength an ammo-fired ranged weapon gets from typical good ammo; 0 for anything else. */
	private static int ammoStrength(String name, GearSlot slot, Bonuses b)
	{
		if (slot != GearSlot.WEAPON || b.getRangedStrength() > AMMO_FIRED_MAX_STRENGTH)
		{
			return 0;
		}
		String n = lower(name);
		if (n.contains("ballista"))
		{
			return JAVELIN_STRENGTH;
		}
		if (n.contains("crossbow"))
		{
			return BOLT_STRENGTH;
		}
		if (n.contains("bow") && !n.contains("blowpipe"))
		{
			return ARROW_STRENGTH;
		}
		return 0;
	}

	/**
	 * Items you wear on task whatever your style, or that only work as a set: void, slayer helmets and black
	 * masks, salve amulets. Kept together in the utility section instead of being scattered by their bonuses.
	 */
	private static boolean isSlayerUtility(String name)
	{
		String n = lower(name);
		return n.startsWith("void ") || n.startsWith("elite void")
			|| n.contains("slayer helmet") || n.startsWith("black mask")
			|| n.startsWith("salve amulet");
	}

	private static boolean isEffectWeapon(String name)
	{
		String n = lower(name);
		for (String weapon : EFFECT_WEAPONS)
		{
			if (n.startsWith(weapon))
			{
				return true;
			}
		}
		return false;
	}

	private static String lower(String name)
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}

	private static int bestMeleeAttack(Bonuses b)
	{
		return Math.max(b.getStab(), Math.max(b.getSlash(), b.getCrush()));
	}

	// The *Offence scores only decide the style, so they weigh accuracy and damage equally.
	private static int meleeOffence(Bonuses b)
	{
		return bestMeleeAttack(b) + b.getStrength();
	}

	private static int rangedOffence(Bonuses b)
	{
		return b.getRange() + b.getRangedStrength();
	}

	private static int magicOffence(Bonuses b)
	{
		return b.getMagic() + Math.round(b.getMagicDamage());
	}

	private static int defenceScore(Bonuses b)
	{
		return b.getDefStab() + b.getDefSlash() + b.getDefCrush() + b.getDefMagic() + b.getDefRange();
	}
}
