package com.combatgearorganizer.slayer;

import com.combatgearorganizer.slayer.SlayerLocationData.Requirements;
import java.util.Collections;
import java.util.List;

/**
 * Says which of a place's requirements the player is missing. Kept as an interface so the picker can be tested
 * without a game client.
 */
public interface RequirementChecker
{
	/** Treats every requirement as met. */
	RequirementChecker NONE = r -> Collections.emptyList();

	/**
	 * @return readable descriptions of what's missing, e.g. "Priest in Peril" or "62 Sailing"; empty when the
	 * player can get in
	 */
	List<String> missing(Requirements requirements);
}
