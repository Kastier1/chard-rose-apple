package com.combatgearorganizer.dps;

import com.combatgearorganizer.GearStyle;
import java.util.List;
import lombok.Value;

/** One owned weapon's expected damage against a monster, and why. */
@Value
public class WeaponScore
{
	GearStyle style;
	int itemId;
	String name;
	double dps;
	/** Max hit of one attack (the first hit for multi-hit weapons). */
	int maxHit;
	double hitChance;
	/** Ticks per attack, after the attack style. */
	int speed;
	/** How it attacks: "slash", "crush", "Rune arrow", "Fire Surge", "built-in spell"... */
	String using;
	/** Target-specific bonuses that apply: "demonbane", "dragonbane", "salve (ei)", "water weakness"... */
	List<String> effects;
	/** The slayer helmet, black mask or salve amulet worn with it, or null. */
	String slayerItem;
}
