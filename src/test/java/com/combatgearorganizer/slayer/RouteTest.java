package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RouteTest
{
	@Test
	public void routeLineText()
	{
		assertFalse(Route.NONE.isShown());
		assertNull(Route.NONE.text());
		assertEquals("On route to Catacombs of Kourend", Route.shortestPath("Catacombs of Kourend").text());
		assertEquals("On route to the nearest place", Route.shortestPath(null).text());
		assertEquals("Cancel", Route.shortestPath(null).buttonText());
		assertEquals("Marked on your world map", Route.worldMap("Catacombs of Kourend").text());
		assertEquals("Remove", Route.worldMap(null).buttonText());
		assertTrue(Route.worldMap(null).isShown());
	}

	@Test
	public void sameTaskIgnoresTheCount()
	{
		SlayerTaskState a = new SlayerTaskState(42, "Abyssal demons", 50, 120, 5, 0, null);
		assertTrue(a.sameTaskAs(new SlayerTaskState(42, "Abyssal demons", 49, 120, 5, 0, null)));
		assertFalse(a.sameTaskAs(new SlayerTaskState(42, "Abyssal demons", 49, 130, 5, 0, null)));
		assertFalse(a.sameTaskAs(new SlayerTaskState(43, "Abyssal demons", 49, 120, 5, 0, null)));
		assertFalse(a.sameTaskAs(new SlayerTaskState(42, "Task #42", 49, 120, 5, 0, null)));
		assertFalse(a.sameTaskAs(new SlayerTaskState(42, "Abyssal demons", 49, 120, 8, 3, "Catacombs")));
		assertFalse(a.sameTaskAs(SlayerTaskState.NONE));
		assertFalse(a.sameTaskAs(null));
	}
}
