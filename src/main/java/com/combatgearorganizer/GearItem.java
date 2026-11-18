package com.combatgearorganizer;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * An item from the bank tag together with everything needed to place it.
 */
@Value
@AllArgsConstructor
public class GearItem
{
	int itemId;
	String name;
	GearSlot slot;
	GearStyle style;
	/** Higher is better; used to put your best-in-slot items first. */
	int power;
	/** The armour set the item belongs to ({@link GearSets}), or null. Used by the By set layout. */
	String set;

	/** An item whose set comes from its own name only. */
	public GearItem(int itemId, String name, GearSlot slot, GearStyle style, int power)
	{
		this(itemId, name, slot, style, power, GearSets.setOf(name));
	}
}
