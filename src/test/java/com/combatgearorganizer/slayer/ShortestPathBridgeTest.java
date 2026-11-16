package com.combatgearorganizer.slayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import org.junit.Before;
import org.junit.Test;

public class ShortestPathBridgeTest
{
	private static final int[] WILDY_CAVE = {3343, 10163, 0};
	private static final int[] CATACOMBS = {1675, 10091, 0};

	/** Stands in for Shortest Path: records what it receives and answers getTarget like the real plugin. */
	public static class FakeShortestPath
	{
		final List<PluginMessage> received = new ArrayList<>();
		/** What it is currently showing; null means nothing. */
		List<Object> shownTargets;
		boolean answers = true;
		private EventBus bus;

		@Subscribe
		public void onPluginMessage(PluginMessage m)
		{
			if (!"shortestpath".equals(m.getNamespace()))
			{
				return;
			}
			if ("currentTarget".equals(m.getName()))
			{
				return;
			}
			received.add(m);
			switch (m.getName())
			{
				case "path":
					if (m.getData().containsKey("target"))
					{
						shownTargets = new ArrayList<>((List<?>) m.getData().get("target"));
					}
					break;
				case "clear":
					shownTargets = null;
					break;
				case "getTarget":
					if (answers)
					{
						Map<String, Object> reply = new HashMap<>();
						reply.put("id", m.getData().get("id"));
						reply.put("set", shownTargets != null);
						if (shownTargets != null)
						{
							reply.put("target", shownTargets);
						}
						bus.post(new PluginMessage("shortestpath", "currentTarget", reply));
					}
					break;
				default:
			}
		}

		long count(String name)
		{
			return received.stream().filter(m -> m.getName().equals(name)).count();
		}

		PluginMessage last(String name)
		{
			PluginMessage found = null;
			for (PluginMessage m : received)
			{
				if (m.getName().equals(name))
				{
					found = m;
				}
			}
			return found;
		}
	}

	private EventBus bus;
	private FakeShortestPath sp;
	private ShortestPathBridge bridge;

	@Before
	public void setUp()
	{
		bus = new EventBus();
		sp = new FakeShortestPath();
		sp.bus = bus;
		bus.register(sp);
		bridge = new ShortestPathBridge(bus, null, () -> true);
		// The plugin forwards PluginMessages to the bridge; do the same here
		bus.register(new Object()
		{
			@Subscribe
			public void onPluginMessage(PluginMessage m)
			{
				bridge.onPluginMessage(m);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static Boolean avoidWilderness(PluginMessage m)
	{
		Map<String, Object> config = (Map<String, Object>) m.getData().get("config");
		return config == null ? null : (Boolean) config.get("avoidWilderness");
	}

	private void ticks(int n, WorldPoint at)
	{
		for (int i = 0; i < n; i++)
		{
			bridge.onGameTick(at);
		}
	}

	@Test
	public void normalRouteSendsNoOverride()
	{
		bridge.path(Collections.singletonList(CATACOMBS), false);
		assertNull(avoidWilderness(sp.last("path")));
		assertFalse(bridge.isOverrideActive());
	}

	@Test
	public void wildernessRouteLiftsAvoidanceUntilArrival()
	{
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		assertEquals(Boolean.FALSE, avoidWilderness(sp.last("path")));
		assertTrue(bridge.isOverrideActive());

		// Still walking: override stays
		bridge.onGameTick(new WorldPoint(3100, 3500, 0));
		assertTrue(bridge.isOverrideActive());

		// Arrived: the player's own setting is put back with a config-only message, the path is left alone
		bridge.onGameTick(new WorldPoint(3345, 10160, 0));
		assertFalse(bridge.isOverrideActive());
		PluginMessage restore = sp.last("path");
		assertEquals(Boolean.TRUE, avoidWilderness(restore));
		assertFalse(restore.getData().containsKey("target"));
		assertEquals(0, sp.count("clear"));
	}

	@Test
	public void noOverrideWhenPlayerAlreadyAllowsWilderness()
	{
		ShortestPathBridge lenient = new ShortestPathBridge(bus, null, () -> false);
		lenient.path(Collections.singletonList(WILDY_CAVE), true);
		assertNull(avoidWilderness(sp.last("path")));
		assertFalse(lenient.isOverrideActive());
	}

	@Test
	public void someoneElsesPathDropsOurOverrideButKeepsTheirPath()
	{
		bridge.path(Collections.singletonList(WILDY_CAVE), true);

		// The player picks their own destination on the world map
		WorldPoint bank = new WorldPoint(3185, 3436, 0);
		sp.shownTargets = new ArrayList<>(Collections.singletonList(bank));

		ticks(ShortestPathBridge.CHECK_INTERVAL_TICKS, new WorldPoint(3100, 3500, 0));
		assertFalse(bridge.isOverrideActive());
		assertFalse(bridge.isPathShown());
		assertEquals(Boolean.TRUE, avoidWilderness(sp.last("path")));
		assertEquals(0, sp.count("clear"));
		assertEquals(Collections.singletonList(bank), sp.shownTargets);
	}

	@Test
	public void taskChangeClearsOurPath()
	{
		bridge.path(Collections.singletonList(CATACOMBS), false);
		bridge.clearIfOurs();
		assertEquals(1, sp.count("clear"));
		assertNull(sp.shownTargets);
		assertFalse(bridge.isPathShown());
	}

	@Test
	public void taskChangeLeavesSomeoneElsesPath()
	{
		bridge.path(Collections.singletonList(CATACOMBS), false);
		WorldPoint quest = new WorldPoint(3222, 3218, 0);
		sp.shownTargets = new ArrayList<>(Collections.singletonList(quest));

		bridge.clearIfOurs();
		assertEquals(0, sp.count("clear"));
		assertEquals(Collections.singletonList(quest), sp.shownTargets);
		assertFalse(bridge.isPathShown());
	}

	@Test
	public void noReplyNeverClearsButRestoresWildernessSetting()
	{
		sp.answers = false;
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		bridge.clearIfOurs();
		ticks(ShortestPathBridge.REPLY_TIMEOUT_TICKS + 1, new WorldPoint(3100, 3500, 0));

		assertEquals(0, sp.count("clear"));
		assertFalse(bridge.isOverrideActive());
		assertEquals(Boolean.TRUE, avoidWilderness(sp.last("path")));
	}

	@Test
	public void normalRouteAfterWildernessRouteRestoresSetting()
	{
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		bridge.path(Collections.singletonList(CATACOMBS), false);
		assertEquals(Boolean.TRUE, avoidWilderness(sp.last("path")));
		assertFalse(bridge.isOverrideActive());
	}

	@Test
	public void releaseOnShutdownRestoresSettingWithoutClearing()
	{
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		bridge.release();
		assertEquals(0, sp.count("clear"));
		assertEquals(Boolean.TRUE, avoidWilderness(sp.last("path")));
		assertFalse(bridge.isOverrideActive());
	}

	@Test
	public void userClearAlwaysClears()
	{
		bridge.path(Collections.singletonList(CATACOMBS), false);
		bridge.clear();
		assertEquals(1, sp.count("clear"));
	}

	@Test
	public void targetPointsReadsPointsAndTargetMaps()
	{
		WorldPoint a = new WorldPoint(1, 2, 0);
		WorldPoint b = new WorldPoint(3, 4, 1);
		Map<String, Object> withReqs = new HashMap<>();
		withReqs.put("point", b);
		List<Object> list = new ArrayList<>();
		list.add(a);
		list.add(withReqs);
		assertEquals(2, ShortestPathBridge.targetPoints(list).size());
		assertTrue(ShortestPathBridge.targetPoints(null).isEmpty());
	}

	@Test
	public void routeIsActiveUntilArrival()
	{
		assertFalse(bridge.isRouteActive());
		bridge.path(Collections.singletonList(CATACOMBS), false);
		assertTrue(bridge.isRouteActive());
		ticks(2, new WorldPoint(3100, 3500, 0));
		assertTrue(bridge.isRouteActive());
		// Close, but on another plane
		bridge.onGameTick(new WorldPoint(1675, 10091, 1));
		assertTrue(bridge.isRouteActive());

		bridge.onGameTick(new WorldPoint(1677, 10093, 0));
		assertFalse(bridge.isRouteActive());
		// The path is still ours, so a task change still clears it
		assertTrue(bridge.isPathShown());
		bridge.clearIfOurs();
		assertEquals(1, sp.count("clear"));
	}

	@Test
	public void routeEndsWhenReplacedOrCleared()
	{
		bridge.path(Collections.singletonList(CATACOMBS), false);
		sp.shownTargets = new ArrayList<>(Collections.singletonList(new WorldPoint(3185, 3436, 0)));
		ticks(ShortestPathBridge.CHECK_INTERVAL_TICKS, new WorldPoint(3100, 3500, 0));
		assertFalse(bridge.isRouteActive());

		bridge.path(Collections.singletonList(CATACOMBS), false);
		assertTrue(bridge.isRouteActive());
		bridge.clear();
		assertFalse(bridge.isRouteActive());

		bridge.path(Collections.singletonList(CATACOMBS), false);
		bridge.release();
		assertFalse(bridge.isRouteActive());
	}

	@Test
	public void nearNeedsThePlaneAndSixTiles()
	{
		List<WorldPoint> targets = Collections.singletonList(new WorldPoint(100, 100, 0));
		assertTrue(ShortestPathBridge.near(targets, new WorldPoint(106, 100, 0)));
		assertFalse(ShortestPathBridge.near(targets, new WorldPoint(107, 100, 0)));
		assertFalse(ShortestPathBridge.near(targets, new WorldPoint(100, 100, 1)));
		assertFalse(ShortestPathBridge.near(targets, null));
	}
}
