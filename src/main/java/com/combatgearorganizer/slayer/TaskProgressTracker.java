package com.combatgearorganizer.slayer;

import lombok.Getter;

/**
 * Decides what the game state means for the task's progress: a kill, a new task, a finished task, a relog. Holds one
 * {@link TaskProgress}. No Swing or game types, so it can be unit-tested; the caller passes the time.
 */
public class TaskProgressTracker
{
	/** The most kills one tick can plausibly take off the count (cannon plus barrage). More looks like a cancel. */
	static final int MAX_KILLS_PER_TICK = 15;
	/** Ticks after the last kill to still take that kill's loot, which drops after the death animation. */
	static final int LOOT_WINDOW_TICKS = 10;

	public enum Change
	{
		/** Nothing to save or redraw. */
		NONE,
		/** The numbers changed. */
		UPDATED,
		/** A new task began; old progress is gone. */
		STARTED,
		/** The task was finished; the summary is ready. */
		FINISHED,
		/** The task went away without being finished (e.g. cancelled). */
		CLEARED,
	}

	@Getter
	private TaskProgress progress;
	/**
	 * False from login until the count first reads a task. Until then a 0 is not taken as the task ending: the count
	 * can read 0 while logging in, and a task finished on another client can't be summed up here.
	 */
	private boolean taskSeen;
	private boolean loggedIn;
	private int lootTicksLeft;
	private boolean summaryDue;

	/** Replace the progress with a saved one, e.g. after a relog or switching accounts. */
	public void load(TaskProgress saved, boolean loggedIn, long now)
	{
		progress = saved;
		taskSeen = false;
		lootTicksLeft = 0;
		summaryDue = false;
		this.loggedIn = loggedIn;
		if (progress != null && loggedIn)
		{
			progress.resume(now);
		}
	}

	public void onLogin(long now)
	{
		loggedIn = true;
		if (progress != null)
		{
			progress.resume(now);
		}
	}

	public void onLogout()
	{
		loggedIn = false;
		taskSeen = false;
		if (progress != null)
		{
			progress.pause();
		}
	}

	/**
	 * The task's count this tick. {@code remaining} is 0 with no task. {@code taskName} is null when it isn't known
	 * for sure (a placeholder name).
	 */
	public Change onCount(int taskId, String taskName, int remaining, int initial, long now)
	{
		if (remaining <= 0)
		{
			if (progress == null || progress.isDone() || !taskSeen)
			{
				return Change.NONE;
			}
			if (progress.getLastRemaining() <= MAX_KILLS_PER_TICK)
			{
				progress.finish(now);
				lootTicksLeft = LOOT_WINDOW_TICKS;
				summaryDue = false;
				return Change.FINISHED;
			}
			progress = null;
			return Change.CLEARED;
		}
		boolean firstRead = !taskSeen;
		taskSeen = true;

		if (progress == null || progress.isDone() || !progress.isSameTask(taskId, taskName)
			|| remaining > progress.getLastRemaining())
		{
			progress = TaskProgress.start(taskId, taskName, remaining, initial, now);
			if (!loggedIn)
			{
				progress.pause();
			}
			lootTicksLeft = 0;
			summaryDue = false;
			return Change.STARTED;
		}

		Change change = Change.NONE;
		if (progress.getTaskName() == null && taskName != null)
		{
			progress.setTaskName(taskName);
			change = Change.UPDATED;
		}
		if (remaining < progress.getLastRemaining())
		{
			// Kills found on the first read after login were made elsewhere, with no time to go with them
			progress.kill(progress.getLastRemaining() - remaining, now, !firstRead);
			change = Change.UPDATED;
		}
		return change;
	}

	/** Call every game tick. True on the tick the task-done chat message should go out, once the last loot is in. */
	public boolean onTick()
	{
		if (lootTicksLeft > 0 && --lootTicksLeft == 0 && progress != null && progress.isDone())
		{
			summaryDue = true;
		}
		boolean due = summaryDue;
		summaryDue = false;
		return due;
	}

	/** Whether loot, XP and superiors count now: while on task, or just after its last kill. */
	public boolean isCounting()
	{
		return progress != null && (!progress.isDone() || lootTicksLeft > 0);
	}

	public boolean addXp(long gained)
	{
		if (!isCounting() || gained <= 0)
		{
			return false;
		}
		progress.addXp(gained);
		return true;
	}

	public boolean addSuperior()
	{
		if (!isCounting())
		{
			return false;
		}
		progress.addSuperior();
		return true;
	}

	public boolean addLoot(long geValue, long haValue)
	{
		if (!isCounting() || (geValue <= 0 && haValue <= 0))
		{
			return false;
		}
		progress.addLoot(geValue, haValue);
		return true;
	}
}
