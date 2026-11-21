package com.combatgearorganizer.slayer;

import com.combatgearorganizer.slayer.SlayerLocationData.TaskEntry;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Review (round 3): the re-check that keeps a "Needs ..." line read before the server sent the quest and diary varps
 * from sticking had no test; only SlayerHelper.sameRequirements did. These drive the schedule SlayerHelper asks each
 * refresh, with a checker whose quests read as not done until some tick, as SlayerHelper's refresh does it: a full
 * redraw when {@link RequirementRecheck#needsRedraw} says so, else nothing changes on the cards.
 */
public class RequirementRecheckTest
{
	private static TaskEntry skeletons;

	@BeforeClass
	public static void load() throws Exception
	{
		try (Reader r = new InputStreamReader(SlayerHelper.class.getResourceAsStream("slayer_locations.json"),
			StandardCharsets.UTF_8))
		{
			SlayerLocationData data = new Gson().fromJson(r, SlayerLocationData.class);
			skeletons = data.findTask(-1, "Skeletons");
		}
		assertNotNull(skeletons);
	}

	/** A player whose quests read as not done until {@code knownFrom}, then as done. */
	private static class LateQuests implements RequirementChecker
	{
		final int knownFrom;
		int tick;

		LateQuests(int knownFrom)
		{
			this.knownFrom = knownFrom;
		}

		@Override
		public List<String> missing(SlayerLocationData.Requirements r)
		{
			if (tick >= knownFrom)
			{
				return Collections.emptyList();
			}
			List<String> out = new ArrayList<>(r.getQuestsFinished());
			r.getQuestsStarted().forEach(q -> out.add(q + " (started)"));
			return out;
		}
	}

	/**
	 * Runs SlayerHelper's loop for {@code ticks} refreshes after a login (if {@code login}) on the same task, nothing
	 * else changing; returns the tick of each full redraw.
	 */
	private static List<Integer> redraws(RequirementRecheck recheck, LateQuests quests, boolean login, int ticks,
		List<List<LocationOption>> drawnOut)
	{
		// Drawn on the login tick, with the quests not known yet
		List<LocationOption> drawn = LocationPicker.pick(skeletons, 5, 0, true, quests);
		if (login)
		{
			recheck.onLogin();
		}
		List<Integer> out = new ArrayList<>();
		for (int t = 1; t <= ticks; t++)
		{
			quests.tick = t;
			if (recheck.needsRedraw(false, true, () -> LocationPicker.pick(skeletons, 5, 0, true, quests), drawn))
			{
				drawn = LocationPicker.pick(skeletons, 5, 0, true, quests);
				out.add(t);
			}
		}
		drawnOut.add(drawn);
		return out;
	}

	private static boolean anyGated(List<LocationOption> places)
	{
		return places.stream().anyMatch(l -> !l.isAccessible());
	}

	@Test
	public void questsKnownSoonAfterLoginAreRedrawnOnThatTick()
	{
		LateQuests quests = new LateQuests(4);
		assertTrue("the test needs a gated place", anyGated(LocationPicker.pick(skeletons, 5, 0, true, quests)));
		List<List<LocationOption>> drawn = new ArrayList<>();
		assertEquals(Collections.singletonList(4), redraws(new RequirementRecheck(), quests, true, 30, drawn));
		assertFalse("a stale \"Needs ...\" line stuck", anyGated(drawn.get(0)));
	}

	@Test
	public void questsKnownLaterAreRedrawnAtTheNextPeriodicCheck()
	{
		// The 10 login ticks pass with the quests still unknown; the periodic check then picks them up
		LateQuests quests = new LateQuests(RequirementRecheck.AFTER_LOGIN + 3);
		List<List<LocationOption>> drawn = new ArrayList<>();
		List<Integer> at = redraws(new RequirementRecheck(), quests, true, 60, drawn);
		assertEquals(1, at.size());
		// Within one period after the login ticks, on a tick the schedule checks
		assertTrue(at.toString(), at.get(0) > RequirementRecheck.AFTER_LOGIN + 2
			&& at.get(0) <= RequirementRecheck.AFTER_LOGIN + RequirementRecheck.EVERY);
		assertEquals(0, (at.get(0) - RequirementRecheck.AFTER_LOGIN) % RequirementRecheck.EVERY);
		assertFalse(anyGated(drawn.get(0)));
	}

	@Test
	public void theScheduleChecksEachLoginTickThenEveryPeriod()
	{
		RequirementRecheck recheck = new RequirementRecheck();
		recheck.onLogin();
		List<Integer> due = new ArrayList<>();
		for (int t = 1; t <= 70; t++)
		{
			if (recheck.due())
			{
				due.add(t);
			}
		}
		assertEquals(java.util.Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 35, 60), due);

		// A loading screen's LOGGED_IN starts the login ticks again
		recheck.onLogin();
		assertTrue(recheck.due());
	}

	@Test
	public void noRedrawWhenNothingChangedOrTheRefreshRedrawsAnyway()
	{
		LateQuests known = new LateQuests(0);
		List<List<LocationOption>> drawn = new ArrayList<>();
		assertTrue(redraws(new RequirementRecheck(), known, true, 60, drawn).isEmpty());

		// A forced refresh or another task redraws for its own reason; the check doesn't, and still counts the tick
		RequirementRecheck recheck = new RequirementRecheck();
		recheck.onLogin();
		LateQuests quests = new LateQuests(1);
		quests.tick = 0;
		List<LocationOption> stale = LocationPicker.pick(skeletons, 5, 0, true, quests);
		quests.tick = 1;
		int[] picked = {0};
		assertFalse(recheck.needsRedraw(true, true, () ->
		{
			picked[0]++;
			return LocationPicker.pick(skeletons, 5, 0, true, quests);
		}, stale));
		assertFalse(recheck.needsRedraw(false, false, () ->
		{
			picked[0]++;
			return LocationPicker.pick(skeletons, 5, 0, true, quests);
		}, stale));
		assertEquals("places picked when the check can't redraw", 0, picked[0]);
		assertTrue(recheck.needsRedraw(false, true, () -> LocationPicker.pick(skeletons, 5, 0, true, quests), stale));
	}
}
