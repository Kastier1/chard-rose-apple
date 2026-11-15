package com.combatgearorganizer;

import lombok.Value;

/**
 * An item from the bank tag together with everything needed to place it.
 */
@Value
public class GearItem
{
	int itemId;
	String name;
	GearSlot slot;
	GearStyle style;
	/** Higher is better; used to put your best-in-slot items first. */
	int power;
}
