package com.combatgearorganizer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Equipment slots in the order they are laid out inside a section.
 * The order roughly follows how you'd gear up: weapon/shield, armour top to bottom, then jewellery and ammo.
 */
@Getter
@RequiredArgsConstructor
public enum GearSlot
{
	WEAPON(3),
	SHIELD(5),
	HEAD(0),
	BODY(4),
	LEGS(7),
	HANDS(9),
	FEET(10),
	CAPE(1),
	NECK(2),
	RING(12),
	AMMO(13),
	NONE(-1);

	/** The {@code net.runelite.api.EquipmentInventorySlot} index used by the item stats data. */
	private final int equipmentIndex;

	public static GearSlot fromEquipmentIndex(int index)
	{
		for (GearSlot slot : values())
		{
			if (slot.equipmentIndex == index)
			{
				return slot;
			}
		}
		return NONE;
	}
}
