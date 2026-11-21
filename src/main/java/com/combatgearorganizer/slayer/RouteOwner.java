package com.combatgearorganizer.slayer;

import java.util.Objects;

/**
 * Which task the route is for, so it ends with that task and not with what the panel happens to show. A route started
 * from a {@code ::slayertask} preview's place card ends when the preview changes or stops; any other route belongs to
 * the real task and ends only when that changes or ends, so starting or stopping a preview leaves the player's real
 * route alone. Pure logic; the caller ends the route when a method returns true.
 */
final class RouteOwner
{
	private SlayerTaskState lastReal = SlayerTaskState.NONE;
	private boolean forPreview;

	/** A route was started; {@code fromPreview} when the panel was showing a preview. */
	void started(boolean fromPreview)
	{
		forPreview = fromPreview;
	}

	/** The route ended (arrived, cancelled, replaced or ended by a method here). */
	void ended()
	{
		forPreview = false;
	}

	/** Forget the real task, as at start-up. */
	void reset()
	{
		lastReal = SlayerTaskState.NONE;
		forPreview = false;
	}

	/** Call on every refresh with the real task. Whether a route for the real task must end. */
	boolean realTask(SlayerTaskState real)
	{
		boolean ended = lastReal.hasTask() && !real.hasTask();
		boolean changed = lastReal.hasTask() && real.hasTask() && isOtherTask(real, lastReal);
		lastReal = real;
		return (ended || changed) && !forPreview;
	}

	/** Call when the task the panel shows changed or ended. Whether a route for a preview must end. */
	boolean shownTaskChanged()
	{
		return forPreview;
	}

	static boolean isOtherTask(SlayerTaskState a, SlayerTaskState b)
	{
		return a.getTaskId() != b.getTaskId() || !Objects.equals(a.getName(), b.getName());
	}
}
