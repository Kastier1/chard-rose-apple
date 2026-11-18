package com.combatgearorganizer;

import net.runelite.client.game.ItemEquipmentStats;

/** Turns RuneLite's item stats into {@link Bonuses}, which the pure classes use. */
public final class EquipmentBonuses
{
	private EquipmentBonuses()
	{
	}

	public static Bonuses of(ItemEquipmentStats e)
	{
		return Bonuses.builder()
			.stab(e.getAstab())
			.slash(e.getAslash())
			.crush(e.getAcrush())
			.magic(e.getAmagic())
			.range(e.getArange())
			.defStab(e.getDstab())
			.defSlash(e.getDslash())
			.defCrush(e.getDcrush())
			.defMagic(e.getDmagic())
			.defRange(e.getDrange())
			.strength(e.getStr())
			.rangedStrength(e.getRstr())
			.magicDamage(e.getMdmg())
			.prayer(e.getPrayer())
			.attackSpeed(e.getAspeed())
			.build();
	}
}
