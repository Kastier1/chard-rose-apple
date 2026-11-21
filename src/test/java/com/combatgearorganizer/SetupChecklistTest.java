package com.combatgearorganizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SetupChecklistTest
{
	private static CombatTabStatus status(boolean bankTags, List<String> tabs, int tagged, boolean layout, boolean bankOpen)
	{
		return new CombatTabStatus(true, bankTags, bankOpen, "combat", tabs, tagged, layout, false);
	}

	private static List<SetupChecklist.Step> steps(CombatTabStatus s)
	{
		return SetupChecklist.steps(s);
	}

	@Test
	public void newPlayerHasNothingTicked()
	{
		List<SetupChecklist.Step> steps = steps(status(false, Collections.emptyList(), 0, false, false));
		assertEquals(4, steps.size());
		assertEquals(SetupChecklist.BANK_TAGS, steps.get(0).getTitle());
		assertEquals(SetupChecklist.TAB_EXISTS, steps.get(1).getTitle());
		assertEquals(SetupChecklist.HAS_GEAR, steps.get(2).getTitle());
		assertEquals(SetupChecklist.LAYOUT, steps.get(3).getTitle());
		for (SetupChecklist.Step step : steps)
		{
			assertFalse(step.getTitle(), step.isDone());
			assertNotNull(step.getTitle(), step.getHelp());
			assertTrue(step.getHelp(), step.getHelp().endsWith("."));
		}
		assertFalse(SetupChecklist.isComplete(status(false, Collections.emptyList(), 0, false, false)));
	}

	@Test
	public void helpSaysWhatToClick()
	{
		List<SetupChecklist.Step> steps = steps(status(false, Collections.emptyList(), 0, false, false));
		assertTrue(steps.get(0).getHelp().contains("Bank Tags"));
		assertTrue(steps.get(1).getHelp().contains("New tag tab"));
		assertTrue(steps.get(1).getHelp().contains("\"combat\""));
		assertTrue(steps.get(2).getHelp().contains("Add worn gear"));
		assertTrue(steps.get(3).getHelp().contains("Organize now"));
		assertTrue(steps.get(3).getHelp().contains("Enable layout"));
	}

	@Test
	public void eachStepTicksOnItsOwn()
	{
		assertTrue(steps(status(true, Collections.emptyList(), 0, false, false)).get(0).isDone());

		List<SetupChecklist.Step> tabOnly = steps(status(false, Arrays.asList("weapons", "combat"), 0, false, false));
		assertTrue(tabOnly.get(1).isDone());
		assertNull(tabOnly.get(1).getHelp());
		assertFalse(tabOnly.get(2).isDone());

		// Another tab with that name in it doesn't count
		assertFalse(steps(status(false, Collections.singletonList("combat gear"), 0, false, false)).get(1).isDone());

		assertTrue(steps(status(false, Collections.emptyList(), 3, false, false)).get(2).isDone());
		assertTrue(steps(status(false, Collections.emptyList(), 0, true, false)).get(3).isDone());
	}

	@Test
	public void completeWhenEveryStepIsDone()
	{
		CombatTabStatus done = status(true, Collections.singletonList("combat"), 48, true, false);
		assertTrue(SetupChecklist.isComplete(done));
		for (SetupChecklist.Step step : steps(done))
		{
			assertTrue(step.isDone());
			assertNull(step.getHelp());
		}
	}

	@Test
	public void noTagNameStillSaysWhatToName()
	{
		CombatTabStatus s = new CombatTabStatus(true, true, true, "", Collections.singletonList(""), 0, false, false);
		assertFalse(s.hasTagTab());
		assertTrue(steps(s).get(1).getHelp().contains("\"combat\""));
		assertEquals("Pick a tag first.", SetupChecklist.organizeBlocker(s));
		assertEquals("Pick a tag first.", SetupChecklist.addWornBlocker(s));
	}

	@Test
	public void organizeNeedsTheBankOpen()
	{
		CombatTabStatus ready = status(true, Collections.singletonList("combat"), 10, false, true);
		assertNull(SetupChecklist.organizeBlocker(ready));
		assertEquals("Open your bank first.", SetupChecklist.organizeBlocker(status(true, Collections.singletonList("combat"), 10, true, false)));
		assertEquals("Turn on the Bank Tags plugin first.", SetupChecklist.organizeBlocker(status(false, Collections.singletonList("combat"), 10, true, true)));
		assertEquals("Put some gear in the tab first.", SetupChecklist.organizeBlocker(status(true, Collections.singletonList("combat"), 0, false, true)));
		assertEquals("Log in first.", SetupChecklist.organizeBlocker(CombatTabStatus.LOGGED_OUT));
	}

	@Test
	public void addWornGearWorksWithTheBankClosed()
	{
		assertNull(SetupChecklist.addWornBlocker(status(false, Collections.emptyList(), 0, false, false)));
		assertEquals("Log in first.", SetupChecklist.addWornBlocker(CombatTabStatus.LOGGED_OUT));
	}
}
