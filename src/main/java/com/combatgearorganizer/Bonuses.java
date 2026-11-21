package com.combatgearorganizer;

import lombok.Builder;
import lombok.Value;

/**
 * The subset of equipment bonuses the classifier cares about.
 * Kept independent of RuneLite types so the classification logic is easy to unit test.
 */
@Value
@Builder
public class Bonuses
{
	int stab;
	int slash;
	int crush;
	int magic;
	int range;
	int defStab;
	int defSlash;
	int defCrush;
	int defMagic;
	int defRange;
	int strength;
	int rangedStrength;
	float magicDamage;
	int prayer;
	/** Attack speed in game ticks, as RuneLite's item stats give it; 0 when unknown. */
	int attackSpeed;
}
