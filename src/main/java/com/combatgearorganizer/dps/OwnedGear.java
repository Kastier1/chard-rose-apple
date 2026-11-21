package com.combatgearorganizer.dps;

import java.util.Collections;
import java.util.List;
import lombok.Value;

/** Everything the player owns that can be worn (bank, inventory and worn), and their levels. */
@Value
public class OwnedGear
{
	public static final OwnedGear NONE = new OwnedGear(false, Collections.emptyList(), Levels.MAXED);

	/** Whether the bank has been seen on this account, so {@link #pieces} includes it. */
	boolean bankSeen;
	List<GearPiece> pieces;
	Levels levels;
}
