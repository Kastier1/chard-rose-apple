package com.combatgearorganizer.slayer;

import com.combatgearorganizer.ItemValueMode;
import com.combatgearorganizer.ItemValues;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

/**
 * How the current slayer task is going: kills, time spent, slayer XP, superiors and loot. Saved per account as JSON,
 * so every field that isn't transient is kept across a relog. No Swing or game types, so it can be unit-tested.
 */
@Getter
@NoArgsConstructor
public class TaskProgress
{
	/** A gap this long or longer without a kill doesn't count towards the kill rate. */
	public static final long IDLE_GAP_MILLIS = 5 * 60 * 1000L;

	private int taskId;
	/** Null until the task's real name is known (e.g. a boss task before the Slayer plugin names it). */
	@Setter(AccessLevel.PACKAGE)
	private String taskName;
	private int initial;
	/** Kills left when tracking began: less than {@link #initial} when the plugin started mid-task. */
	private int startRemaining;
	private int lastRemaining;
	/** Every kill seen. */
	private int kills;
	/** Kills that came within {@link #IDLE_GAP_MILLIS} of the one before, so their time counts. */
	private int timedKills;
	/** Logged-in time between kills, leaving out long gaps. */
	private long activeMillis;
	private long xp;
	private int superiors;
	private long lootGe;
	private long lootHa;
	/** The task was finished; kept to show the summary until the next task. */
	private boolean done;

	/** When the time to the next kill starts counting; null while logged out. Not saved. */
	private transient Long anchor;

	public static TaskProgress start(int taskId, String taskName, int remaining, int initial, long now)
	{
		TaskProgress p = new TaskProgress();
		p.taskId = taskId;
		p.taskName = taskName;
		p.initial = Math.max(initial, remaining);
		p.startRemaining = remaining;
		p.lastRemaining = remaining;
		p.anchor = now;
		return p;
	}

	/** Whether this is the same task. A null name on either side (not known yet) only compares the id. */
	public boolean isSameTask(int taskId, String taskName)
	{
		if (taskId != this.taskId)
		{
			return false;
		}
		return taskName == null || this.taskName == null || taskName.equalsIgnoreCase(this.taskName);
	}

	/** True when tracking began after the task's first kill. */
	public boolean isMidTask()
	{
		return startRemaining < initial;
	}

	/** Count {@code count} kills made at {@code now}. */
	void kill(int count, long now)
	{
		kill(count, now, true);
	}

	/**
	 * Count {@code count} kills. Untimed kills were made while this client wasn't watching (e.g. on another device),
	 * so they add to the kills but not to the rate.
	 */
	void kill(int count, long now, boolean timed)
	{
		if (count <= 0)
		{
			return;
		}
		kills += count;
		if (timed && anchor != null)
		{
			long gap = now - anchor;
			if (gap >= 0 && gap < IDLE_GAP_MILLIS)
			{
				activeMillis += gap;
				timedKills += count;
			}
		}
		// Even after a long break, the next kill's time starts from this one
		anchor = now;
		lastRemaining = Math.max(0, lastRemaining - count);
	}

	void finish(long now)
	{
		if (lastRemaining > 0)
		{
			kill(lastRemaining, now);
		}
		done = true;
		anchor = null;
	}

	/** Logged out: stop the clock. */
	void pause()
	{
		anchor = null;
	}

	/** Logged in: time counts again from now. Does nothing if the clock is already running. */
	void resume(long now)
	{
		if (anchor == null && !done)
		{
			anchor = now;
		}
	}

	void addXp(long gained)
	{
		if (gained > 0)
		{
			xp += gained;
		}
	}

	void addSuperior()
	{
		superiors++;
	}

	void addLoot(long geValue, long haValue)
	{
		lootGe += Math.max(0, geValue);
		lootHa += Math.max(0, haValue);
	}

	/** Loot value with the given "Item value" setting. */
	public long loot(ItemValueMode mode)
	{
		return mode == ItemValueMode.HIGH_ALCHEMY ? lootHa : lootGe;
	}

	/** Kills per hour, or -1 until there is a timed kill. */
	public double killsPerHour()
	{
		if (timedKills <= 0 || activeMillis <= 0)
		{
			return -1;
		}
		return timedKills * 3_600_000.0 / activeMillis;
	}

	/** Estimated time for the kills left at the current rate, or -1 when there's no rate yet. */
	public long millisLeft(int remaining)
	{
		double rate = killsPerHour();
		if (rate <= 0)
		{
			return -1;
		}
		return Math.round(remaining / rate * 3_600_000.0);
	}

	/** "Task done: 140 Abyssal demons in 24 min, 2 superiors, 1.2M loot" */
	public String summary(ItemValueMode mode)
	{
		StringBuilder s = new StringBuilder("Task done: ").append(kills);
		if (taskName != null)
		{
			s.append(' ').append(taskName);
		}
		else
		{
			s.append(kills == 1 ? " kill" : " kills");
		}
		if (activeMillis > 0)
		{
			s.append(" in ").append(duration(activeMillis));
		}
		List<String> extras = new ArrayList<>();
		if (superiors > 0)
		{
			extras.add(superiors + (superiors == 1 ? " superior" : " superiors"));
		}
		long loot = loot(mode);
		if (loot > 0)
		{
			extras.add(ItemValues.format(loot) + " loot");
		}
		for (String e : extras)
		{
			s.append(", ").append(e);
		}
		return s.toString();
	}

	/** What the panel shows, frozen for the Swing thread. */
	public View view(int remaining, ItemValueMode mode)
	{
		return new View((int) Math.round(killsPerHour()), millisLeft(remaining), xp, superiors, loot(mode), mode,
			isMidTask() ? startRemaining : -1, done ? summary(mode) : null);
	}

	/** "<1 min", "24 min", "1 h 5 min" */
	public static String duration(long millis)
	{
		long minutes = Math.round(millis / 60_000.0);
		if (minutes < 1)
		{
			return "<1 min";
		}
		if (minutes < 60)
		{
			return minutes + " min";
		}
		long hours = minutes / 60;
		long rest = minutes % 60;
		return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
	}

	/** One frozen copy of the progress for the panel. */
	@Value
	public static class View
	{
		/** -1 until there's a rate. */
		int killsPerHour;
		/** -1 until there's a rate. */
		long millisLeft;
		long xp;
		int superiors;
		long loot;
		ItemValueMode mode;
		/** Kills left when tracking began, or -1 when it began with the task. */
		int trackedFrom;
		/** The end-of-task summary, or null while the task is on. */
		String summary;

		public String rateText()
		{
			if (killsPerHour < 0)
			{
				return null;
			}
			String text = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(killsPerHour) + " kills/hr";
			return millisLeft >= 0 ? text + " · about " + duration(millisLeft) + " left" : text;
		}
	}
}
