package com.combatgearorganizer;

/**
 * Decides which combat style an item belongs to, and how strong it is for that style.
 */
public final class GearClassifier
{
	private GearClassifier()
	{
	}

	public static GearStyle classify(Bonuses b)
	{
		int melee = meleeScore(b);
		int ranged = rangedScore(b);
		int magic = magicScore(b);
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

		// No offensive bonuses at all: fall back to what the item gives up / provides
		if (b.getPrayer() > 0)
		{
			return GearStyle.PRAYER;
		}
		if (b.getMagic() < 0 && b.getRange() < 0)
		{
			// Classic tank armour (e.g. rune platebody): penalises both magic and ranged
			return GearStyle.MELEE;
		}
		if (b.getMagic() < 0)
		{
			return GearStyle.RANGED;
		}
		return GearStyle.UTILITY;
	}

	public static int power(GearStyle style, Bonuses b)
	{
		switch (style)
		{
			case MELEE:
				return meleeScore(b) + defenceScore(b) / 4;
			case RANGED:
				return rangedScore(b) + defenceScore(b) / 4;
			case MAGIC:
				return magicScore(b) + defenceScore(b) / 4;
			case PRAYER:
				return b.getPrayer() * 10 + defenceScore(b) / 4;
			default:
				return defenceScore(b) + b.getPrayer();
		}
	}

	private static int meleeScore(Bonuses b)
	{
		int attack = Math.max(b.getStab(), Math.max(b.getSlash(), b.getCrush()));
		return attack + b.getStrength();
	}

	private static int rangedScore(Bonuses b)
	{
		return b.getRange() + b.getRangedStrength();
	}

	private static int magicScore(Bonuses b)
	{
		return b.getMagic() + Math.round(b.getMagicDamage());
	}

	private static int defenceScore(Bonuses b)
	{
		return b.getDefStab() + b.getDefSlash() + b.getDefCrush() + b.getDefMagic() + b.getDefRange();
	}
}
