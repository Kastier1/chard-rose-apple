package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/** Deep review: starting a ::slayertask preview cleared the player's real Shortest Path route. */
public class RouteOwnerTest
{
	private static final SlayerTaskState GARGOYLES = new SlayerTaskState(46, "Gargoyles", 120, 150, 5, 0, null);
	private static final SlayerTaskState NECHRYAEL = new SlayerTaskState(52, "Nechryael", 160, 180, 5, 0, null);

	private RouteOwner owner;

	@Before
	public void setUp()
	{
		owner = new RouteOwner();
		assertFalse(owner.realTask(GARGOYLES)); // the first look at the task ends nothing
	}

	@Test
	public void aPreviewLeavesTheRealTasksRoute()
	{
		owner.started(false); // Go on a real task's card
		// Starting, switching and stopping a preview change what the panel shows, not the route's task
		assertFalse(owner.shownTaskChanged());
		assertFalse(owner.realTask(GARGOYLES));
		assertFalse(owner.shownTaskChanged());
		assertFalse(owner.realTask(GARGOYLES));
	}

	@Test
	public void theRealTaskEndingEndsItsRoute()
	{
		owner.started(false);
		assertFalse(owner.realTask(new SlayerTaskState(46, "Gargoyles", 3, 150, 5, 0, null))); // a kill
		assertTrue(owner.realTask(SlayerTaskState.NONE));

		owner.started(false);
		assertFalse(owner.realTask(NECHRYAEL)); // NONE to a task is a new task, not a change of route's task
		assertTrue(owner.realTask(GARGOYLES)); // assigned another task
	}

	@Test
	public void aPreviewsRouteEndsWithThePreviewNotTheRealTask()
	{
		owner.started(true); // Go on a preview's card
		assertFalse(owner.realTask(NECHRYAEL)); // the real task changing isn't this route's business
		assertTrue(owner.shownTaskChanged()); // the preview stopped or changed
		owner.ended();
		assertFalse(owner.shownTaskChanged());
	}

	@Test
	public void anEndedRouteBelongsToNoOne()
	{
		owner.started(true);
		owner.ended();
		owner.started(false);
		assertFalse(owner.shownTaskChanged());
		assertTrue(owner.realTask(NECHRYAEL));
	}
}
