package com.combatgearorganizer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The broad category an item belongs to in the organised layout.
 */
@Getter
@RequiredArgsConstructor
public enum GearStyle
{
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic"),
	PRAYER("Prayer"),
	UTILITY("Utility"),
	SUPPLIES("Supplies");

	private final String displayName;
}
