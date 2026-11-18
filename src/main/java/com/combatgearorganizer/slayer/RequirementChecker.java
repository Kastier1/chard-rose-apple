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

	/**
	 * Whether the player's level meets a place's need. A boostable need (the wiki says a boost works there, such as a
	 * wild pie for Cerberus) is met by the current, boosted level as well as the real one.
	 */
	static boolean meets(int real, int boosted, int needed, boolean boostable)
	{
		return real >= needed || (boostable && boosted >= needed);
	}

	/** "91 Slayer", or "91 Slayer (boostable)" when a boost is enough. */
	static String skillText(int needed, String skill, boolean boostable)
	{
		return needed + " " + skill + (boostable ? " (boostable)" : "");
	}
}
