package com.combatgearorganizer.slayer;

import lombok.Getter;

/** A carry item's picture in the Carry row: ticked when the player has it with them. */
class CarrySlot extends DropSlot
{
	@Getter
	private final CarryItem item;

	CarrySlot(CarryItem item, boolean have, String tooltip)
	{
		super(have ? CollectionLog.Status.LOGGED : CollectionLog.Status.UNKNOWN, tooltip);
		this.item = item;
	}

	boolean isCarried()
	{
		return getStatus() == CollectionLog.Status.LOGGED;
	}
}
