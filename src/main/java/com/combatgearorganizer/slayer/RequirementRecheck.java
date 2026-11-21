package com.combatgearorganizer.slayer;

import java.util.List;
import java.util.function.Supplier;

/**
 * When the slayer panel checks its places' "Needs ..." lines again. Quest states and diary varbits can still read as
 * not done on the first ticks after logging in, before the server has sent them, so the places are checked on each of
 * the {@link #AFTER_LOGIN} ticks after a login, and every {@link #EVERY} ticks after that (a quest finished or a level
 * bought mid-task), and the panel is redrawn when one changed: a stale reading never sticks. It never greys Go out
 * either way: see SlayerPanel.goEnabled. Client thread; {@link SlayerHelper} asks once per refresh.
 */
final class RequirementRecheck
{
	static final int AFTER_LOGIN = 10;
	static final int EVERY = 25;

	private int loginTicks;
	private int tick;

	/** Call on every LOGGED_IN (also after a loading screen): the next {@link #AFTER_LOGIN} refreshes check. */
	void onLogin()
	{
		loginTicks = AFTER_LOGIN;
	}

	/** Whether this refresh checks the requirements. Counts the refresh, so call it exactly once per refresh. */
	boolean due()
	{
		boolean due;
		if (loginTicks > 0)
		{
			loginTicks--;
			due = true;
		}
		else
		{
			due = ++tick % EVERY == 0;
		}
		return due;
	}

	/**
	 * Whether this refresh must redraw the whole panel because a place's requirements read differently now than when
	 * it was drawn. Counts the refresh (see {@link #due()}) whatever else holds. {@code force}: the refresh redraws
	 * anyway; {@code sameTask}: the panel shows this task already ({@code drawn} are its places); {@code now} picks the
	 * places with the requirements as they read now, only called when the check is due.
	 */
	boolean needsRedraw(boolean force, boolean sameTask, Supplier<List<LocationOption>> now, List<LocationOption> drawn)
	{
		return due() && !force && sameTask && !SlayerHelper.sameRequirements(now.get(), drawn);
	}
}
