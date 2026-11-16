package com.combatgearorganizer.slayer;

import java.util.List;
import lombok.Value;

/**
 * One place the player can do their current task, ready to show in the panel.
 */
@Value
public class LocationOption
{
	String key;
	String label;
	boolean recommended;
	/** True when every target is in the Wilderness. */
	boolean wilderness;
	/** True when some targets are in the Wilderness. */
	boolean touchesWilderness;
	String combat;
	String cannon;
	String note;
	/** Points to path to, as [x, y, plane]. Empty when the data has no map point for this place yet. */
	List<int[]> targets;

	public boolean isRoutable()
	{
		return !targets.isEmpty();
	}
}
