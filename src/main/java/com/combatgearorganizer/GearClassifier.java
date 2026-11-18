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
	/** Melee strength from typical worn gear (Eclipse moon set and the like), for the Eclipse atlatl. */
	private static final int ATLATL_GEAR_STRENGTH = 60;
	/** Dragon darts, for the blowpipe, which carries its darts' strength on top of its own. */
	private static final int DART_STRENGTH = 35;

	/** Weapons are compared per tick at this speed, the speed of most one-handed weapons. */
	private static final int BASE_SPEED = 4;
	/** Every spell from an autocast staff or wand takes 5 ticks, whatever the weapon's own speed. */
	private static final int AUTOCAST_SPEED = 5;
	/** Max hit of a good autocast spell (Fire Surge at 95 Magic), for staves and wands. */
	private static final int AUTOCAST_MAX_HIT = 24;
	/** Max hit counts this much more than magic accuracy; it is what the bonuses can't show for magic weapons. */
	private static final int MAX_HIT_WEIGHT = 6;

	/**
	 * Powered staves cast their own spell, so their power is that spell's max hit at 99 Magic, not their bonuses.
	 * Name prefix to max hit.
	 */
	private static final String[][] POWERED_STAVES = {
		{"tumeken's shadow", "40"},
		{"holy sanguinesti staff", "32"},
		{"sanguinesti staff", "32"},
		{"trident of the swamp", "31"},
		{"trident of the seas", "28"},
		{"eye of ayak", "27"},
		{"accursed sceptre", "27"},
		{"thammaron's sceptre", "25"},
		{"warped sceptre", "24"},
	};

	/**
	 * Weapons brought for their special attack. They go after the main weapons in their row, whatever their bonuses
	 * (an Elder maul's bonuses would put it above Osmumten's fang).
	 */
	private static final String[] SPEC_WEAPONS = {
		"armadyl godsword", "bandos godsword", "saradomin godsword", "zamorak godsword", "ancient godsword",
		"elder maul", "granite maul", "dragon warhammer", "dragon claws", "burning claws", "voidwaker",
		"dragon dagger", "abyssal dagger", "crystal halberd", "dragon halberd", "heavy ballista", "light ballista",
		"dark bow", "dragon thrownaxe", "dorgeshuun crossbow", "saradomin sword", "eldritch nightmare staff",
		"volatile nightmare staff",
	};
	/** Puts spec weapons below every main weapon. */
	private static final int SPEC_WEAPON_PENALTY = 5000;

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
		if (slot == GearSlot.AMMO && b.getPrayer() <= 0 && defenceScore(b) == 0)
		{
			// Ammo with no bonuses of its own, such as atlatl darts, whose damage comes from the weapon
			return GearStyle.RANGED;
		}
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
		boolean weapon = slot == GearSlot.WEAPON;
		int offence;
		switch (style)
		{
			case MELEE:
				offence = b.getStrength() * 2 + bestMeleeAttack(b);
				if (weapon)
				{
					offence = perTick(offence, speed(b));
				}
				break;
			case RANGED:
				offence = (b.getRangedStrength() + ammoStrength(name, slot, b) + meleeStrengthUsed(name, b)) * 2 + b.getRange();
				if (weapon)
				{
					// Ranged weapons are used on Rapid, one tick faster
					offence = perTick(offence, Math.max(1, speed(b) - 1));
				}
				break;
			case MAGIC:
				offence = Math.round(b.getMagicDamage() * 3) + b.getMagic();
				if (weapon)
				{
					int poweredMaxHit = poweredStaffMaxHit(name);
					if (poweredMaxHit > 0)
					{
						offence = perTick(offence + poweredMaxHit * MAX_HIT_WEIGHT, speed(b));
					}
					else
					{
						offence = perTick(offence + AUTOCAST_MAX_HIT * MAX_HIT_WEIGHT, AUTOCAST_SPEED);
					}
				}
				break;
			case PRAYER:
				offence = b.getPrayer();
				break;
			default:
				// Utility items have no offence to speak of; defence and prayer are all there is
				return defenceScore(b) + b.getPrayer();
		}

		if (weapon && isEffectWeapon(name))
		{
			offence += EFFECT_WEAPON_BONUS;
		}
		if (weapon && startsWithAny(name, SPEC_WEAPONS))
		{
			offence -= SPEC_WEAPON_PENALTY;
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
		if (n.contains("blowpipe"))
		{
			return DART_STRENGTH;
		}
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

	/**
	 * The Eclipse atlatl hits with the player's melee strength bonus instead of ranged strength: its own, plus what
	 * their other gear adds, the way an ammo-fired weapon gets its arrows' strength.
	 */
	private static int meleeStrengthUsed(String name, Bonuses b)
	{
		return lower(name).startsWith("eclipse atlatl") ? Math.max(0, b.getStrength()) + ATLATL_GEAR_STRENGTH : 0;
	}

	/** Damage per {@link #BASE_SPEED} ticks, so fast and slow weapons compare fairly. */
	private static int perTick(int offence, int speed)
	{
		return offence * BASE_SPEED / speed;
	}

	private static int speed(Bonuses b)
	{
		return b.getAttackSpeed() > 0 ? b.getAttackSpeed() : BASE_SPEED;
	}

	private static int poweredStaffMaxHit(String name)
	{
		String n = lower(name);
		for (String[] staff : POWERED_STAVES)
		{
			if (n.startsWith(staff[0]))
			{
				return Integer.parseInt(staff[1]);
			}
		}
		return 0;
	}

	private static boolean startsWithAny(String name, String[] prefixes)
	{
		String n = lower(name);
		for (String p : prefixes)
		{
			if (n.startsWith(p))
			{
				return true;
			}
		}
		return false;
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
