package com.combatgearorganizer.slayer;

import com.combatgearorganizer.ItemValueMode;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaskProgressTest
{
	private static final long MIN = 60_000L;
	private static final int ABYSSAL_DEMONS = 42;

	@Test
	public void rateAndTimeLeftFollowEachKill()
	{
		TaskProgressTracker t = loggedIn(0);
		t.onCount(ABYSSAL_DEMONS, "Abyssal demons", 140, 140, 0);
		assertEquals(-1, t.getProgress().killsPerHour(), 0);
		assertEquals(-1, t.getProgress().millisLeft(140));

		// One kill a minute: 60 an hour
		for (int i = 1; i <= 10; i++)
		{
			assertEquals(TaskProgressTracker.Change.UPDATED, t.onCount(ABYSSAL_DEMONS, "Abyssal demons", 140 - i, 140, i * MIN));
		}
		TaskProgress p = t.getProgress();
		assertEquals(10, p.getKills());
		assertEquals(60, p.killsPerHour(), 0.001);
		assertEquals(130 * MIN, p.millisLeft(130));
		assertEquals("60 kills/hr · about 2 h 10 min left", p.view(130, ItemValueMode.GRAND_EXCHANGE).rateText());

		// Faster kills raise the rate at once
		t.onCount(ABYSSAL_DEMONS, "Abyssal demons", 129, 140, 10 * MIN + 30_000);
		assertEquals(11 * 3_600_000.0 / (10.5 * MIN), p.killsPerHour(), 0.001);
	}

	@Test
	public void longGapsDontCount()
	{
		TaskProgressTracker t = loggedIn(0);
		t.onCount(1, "Bears", 20, 20, 0);
		t.onCount(1, "Bears", 19, 20, MIN);
		// Twenty minutes at the bank: this kill counts, its time doesn't
		t.onCount(1, "Bears", 18, 20, 21 * MIN);
		t.onCount(1, "Bears", 17, 20, 22 * MIN);
		TaskProgress p = t.getProgress();
		assertEquals(3, p.getKills());
		assertEquals(2, p.getTimedKills());
		assertEquals(2 * MIN, p.getActiveMillis());
		assertEquals(60, p.killsPerHour(), 0.001);
	}

	@Test
	public void timeOnlyCountsWhileLoggedIn()
	{
		TaskProgressTracker t = loggedIn(0);
		t.onCount(1, "Bears", 20, 20, 0);
		t.onCount(1, "Bears", 19, 20, MIN);
		t.onLogout();
		t.onLogin(3 * MIN);
		t.onCount(1, "Bears", 19, 20, 3 * MIN);
		t.onCount(1, "Bears", 18, 20, 4 * MIN);
		// The two logged-out minutes are left out
		assertEquals(2 * MIN, t.getProgress().getActiveMillis());
		// A loading screen's LOGGED_IN doesn't restart the clock
		t.onLogin(4 * MIN + 30_000);
		t.onCount(1, "Bears", 17, 20, 5 * MIN);
		assertEquals(3 * MIN, t.getProgress().getActiveMillis());
	}

	@Test
	public void relogKeepsProgress()
	{
		Gson gson = new Gson();
		TaskProgressTracker t = loggedIn(0);
		t.onCount(ABYSSAL_DEMONS, "Abyssal demons", 140, 140, 0);
		t.onCount(ABYSSAL_DEMONS, "Abyssal demons", 139, 140, MIN);
		t.addXp(150);
		t.addSuperior();
		t.addLoot(5000, 3000);
		String saved = gson.toJson(t.getProgress());

		TaskProgressTracker after = new TaskProgressTracker();
		after.load(gson.fromJson(saved, TaskProgress.class), true, 10 * MIN);
		// Kills made on another device while away count, but not towards the rate
		assertEquals(TaskProgressTracker.Change.UPDATED, after.onCount(ABYSSAL_DEMONS, "Abyssal demons", 137, 140, 10 * MIN));
		after.onCount(ABYSSAL_DEMONS, "Abyssal demons", 136, 140, 11 * MIN);
		TaskProgress p = after.getProgress();
		assertEquals(4, p.getKills());
		assertEquals(2, p.getTimedKills());
		assertEquals(2 * MIN, p.getActiveMillis());
		assertEquals(150, p.getXp());
		assertEquals(1, p.getSuperiors());
		assertEquals(5000, p.loot(ItemValueMode.GRAND_EXCHANGE));
		assertEquals(3000, p.loot(ItemValueMode.HIGH_ALCHEMY));
		assertFalse(p.isMidTask());
	}

	@Test
	public void zeroDuringLoginKeepsSavedProgress()
	{
		TaskProgressTracker first = loggedIn(0);
		first.onCount(1, "Bears", 5, 20, 0);
		TaskProgressTracker t = new TaskProgressTracker();
		t.load(first.getProgress(), true, MIN);
		assertEquals(TaskProgressTracker.Change.NONE, t.onCount(0, null, 0, 0, MIN));
		assertEquals(TaskProgressTracker.Change.NONE, t.onCount(1, "Bears", 5, 20, MIN + 600));
		assertNotNull(t.getProgress());
		assertFalse(t.getProgress().isDone());
	}

	@Test
	public void newTaskResets()
	{
		TaskProgressTracker t = loggedIn(0);
		t.onCount(1, "Bears", 20, 20, 0);
		t.onCount(1, "Bears", 19, 20, MIN);
		t.addXp(100);
		// Another task
		assertEquals(TaskProgressTracker.Change.STARTED, t.onCount(2, "Bats", 30, 30, 2 * MIN));
		assertEquals(0, t.getProgress().getKills());
		assertEquals(0, t.getProgress().getXp());
		t.onCount(2, "Bats", 29, 30, 3 * MIN);
		// Same task again, count goes up
		assertEquals(TaskProgressTracker.Change.STARTED, t.onCount(2, "Bats", 40, 40, 4 * MIN));
		assertEquals(0, t.getProgress().getKills());
		// Same id, other name: a new boss task
		t.onCount(98, "Vorkath", 10, 10, 5 * MIN);
		assertEquals(TaskProgressTracker.Change.STARTED, t.onCount(98, "Zulrah", 10, 10, 6 * MIN));
	}

	@Test
	public void unknownNameDoesntReset()
	{
		TaskProgressTracker t = loggedIn(0);
		t.onCount(98, null, 10, 10, 0);
		assertEquals(TaskProgressTracker.Change.UPDATED, t.onCount(98, "Vorkath", 10, 10, MIN));
		assertEquals("Vorkath", t.getProgress().getTaskName());
		assertEquals(TaskProgressTracker.Change.NONE, t.onCount(98, null, 10, 10, 2 * MIN));
	}

	@Test
	public void midTaskStartIsMarked()
	{
		TaskProgressTracker t = loggedIn(0);
		t.onCount(1, "Bears", 12, 20, 0);
		assertTrue(t.getProgress().isMidTask());
		assertEquals(12, t.getProgress().view(12, ItemValueMode.GRAND_EXCHANGE).getTrackedFrom());
	}

	@Test
	public void finishingGivesTheSummaryAfterTheLastLoot()
	{
		TaskProgressTracker t = loggedIn(0);
		t.onCount(ABYSSAL_DEMONS, "Abyssal demons", 140, 140, 0);
		for (int i = 1; i < 140; i++)
		{
			t.onCount(ABYSSAL_DEMONS, "Abyssal demons", 140 - i, 140, i * 10_000L);
		}
		t.addSuperior();
		t.addSuperior();
		t.addLoot(1_000_000, 400_000);
		assertEquals(TaskProgressTracker.Change.FINISHED, t.onCount(0, null, 0, 0, 1_440_000));
		TaskProgress p = t.getProgress();
		assertTrue(p.isDone());
		assertEquals(140, p.getKills());

		// The last kill's loot lands after the death animation
		boolean due = false;
		for (int i = 0; i < 3; i++)
		{
			due |= t.onTick();
		}
		assertFalse(due);
		assertTrue(t.addLoot(200_000, 100_000));
		for (int i = 0; i < TaskProgressTracker.LOOT_WINDOW_TICKS; i++)
		{
			due |= t.onTick();
		}
		assertTrue(due);
		assertFalse(t.onTick());
		assertFalse("no loot once the window is over", t.addLoot(1, 1));

		assertEquals("Task done: 140 Abyssal demons in 24 min, 2 superiors, 1.2M loot", p.summary(ItemValueMode.GRAND_EXCHANGE));
		assertEquals("Task done: 140 Abyssal demons in 24 min, 2 superiors, 500K loot", p.summary(ItemValueMode.HIGH_ALCHEMY));
		assertNotNull(p.view(0, ItemValueMode.GRAND_EXCHANGE).getSummary());

		// Shown until the next task, which starts fresh
		assertEquals(TaskProgressTracker.Change.NONE, t.onCount(0, null, 0, 0, 1_500_000));
		assertEquals(TaskProgressTracker.Change.STARTED, t.onCount(ABYSSAL_DEMONS, "Abyssal demons", 150, 150, 1_600_000));
		assertFalse(t.getProgress().isDone());
		assertNull(t.getProgress().view(150, ItemValueMode.GRAND_EXCHANGE).getSummary());
	}

	@Test
	public void bigDropToZeroIsACancelNotAFinish()
	{
		TaskProgressTracker t = loggedIn(0);
		t.onCount(1, "Bears", 80, 80, 0);
		assertEquals(TaskProgressTracker.Change.CLEARED, t.onCount(0, null, 0, 0, MIN));
		assertNull(t.getProgress());
	}

	@Test
	public void nothingCountsWithoutATask()
	{
		TaskProgressTracker t = loggedIn(0);
		assertFalse(t.addXp(100));
		assertFalse(t.addSuperior());
		assertFalse(t.addLoot(100, 100));
		t.onCount(1, "Bears", 20, 20, 0);
		assertFalse(t.addXp(0));
		assertTrue(t.addXp(25));
	}

	@Test
	public void summaryLeavesOutWhatDidntHappen()
	{
		TaskProgress p = TaskProgress.start(1, "Bears", 1, 1, 0);
		p.kill(1, 30_000);
		p.finish(30_000);
		assertEquals("Task done: 1 Bears in 1 min", p.summary(ItemValueMode.GRAND_EXCHANGE));
		p.addSuperior();
		assertEquals("Task done: 1 Bears in 1 min, 1 superior", p.summary(ItemValueMode.GRAND_EXCHANGE));

		TaskProgress unnamed = TaskProgress.start(98, null, 2, 2, 0);
		unnamed.kill(2, 10 * MIN);
		assertEquals("Task done: 2 kills", unnamed.summary(ItemValueMode.GRAND_EXCHANGE));
	}

	@Test
	public void durations()
	{
		assertEquals("<1 min", TaskProgress.duration(20_000));
		assertEquals("24 min", TaskProgress.duration(24 * MIN));
		assertEquals("1 h", TaskProgress.duration(60 * MIN));
		assertEquals("1 h 5 min", TaskProgress.duration(65 * MIN));
	}

	@Test
	public void taskMonstersByName()
	{
		java.util.List<String> variants = Arrays.asList("Abyssal demon", "Greater abyssal demon", "Abyssal Sire");
		assertTrue(TaskMonsters.matches("Abyssal demon", "Abyssal demons", variants));
		assertTrue(TaskMonsters.matches("Greater abyssal demon", "Abyssal demons", variants));
		assertTrue(TaskMonsters.matches("abyssal demon", "Abyssal demons", Collections.emptyList()));
		assertFalse(TaskMonsters.matches("Black demon", "Abyssal demons", variants));
		assertFalse(TaskMonsters.matches("Cow", "Bats", Collections.emptyList()));
		assertTrue(TaskMonsters.matches("Giant bat", "Bats", Collections.emptyList()));
		assertFalse(TaskMonsters.matches("Batman", "Bats", Collections.emptyList()));
		assertFalse(TaskMonsters.matches(null, "Bats", variants));
	}

	private static TaskProgressTracker loggedIn(long now)
	{
		TaskProgressTracker t = new TaskProgressTracker();
		t.load(null, true, now);
		return t;
	}
}
