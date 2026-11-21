package com.combatgearorganizer.slayer;

import lombok.Value;

/**
 * What the game says about the current task. A null name means no task.
 */
@Value
public class SlayerTaskState
{
	public static final SlayerTaskState NONE = new SlayerTaskState(0, null, 0, 0, 0, 0, null);

	int taskId;
	String name;
	int remaining;
	int initial;
	int masterId;
	int konarAreaId;
	/** Konar's assigned place as the game names it, if any. */
	String konarArea;

	public boolean hasTask()
	{
		return name != null && remaining > 0;
	}

	/** The same task, maybe with a different number left: what a kill changes. */
	public boolean sameTaskAs(SlayerTaskState other)
	{
		return other != null && taskId == other.taskId && java.util.Objects.equals(name, other.name)
			&& initial == other.initial && masterId == other.masterId && konarAreaId == other.konarAreaId
			&& java.util.Objects.equals(konarArea, other.konarArea);
	}
}
