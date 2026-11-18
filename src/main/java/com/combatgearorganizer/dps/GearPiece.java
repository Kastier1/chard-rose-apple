package com.combatgearorganizer.dps;

import com.combatgearorganizer.Bonuses;
import com.combatgearorganizer.GearSlot;
import java.util.Locale;
import lombok.Value;

/** One item the player owns that can be worn, with what the DPS maths needs about it. */
@Value
public class GearPiece
{
	int itemId;
	String name;
	GearSlot slot;
	boolean twoHanded;
	Bonuses bonuses;

	/** The name in lower case, for the name rules. */
	public String key()
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}
}
