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

	/**
	 * Stands in for Shortest Path: records what it receives, keeps setting overrides the way the real plugin does (a
	 * path message with a non-empty config replaces them, and they last until the next clear), and answers getTarget.
	 */
	public static class FakeShortestPath
	{
		final List<PluginMessage> received = new ArrayList<>();
		/** What it is currently showing; null means nothing. */
		List<Object> shownTargets;
		/** The setting overrides in force. */
		Map<String, Object> overrides = new HashMap<>();
		boolean answers = true;
		/** Like the real plugin: the reply is read at once but posted on a later client tick, by {@link #flush}. */
		boolean deferReplies;
		final List<PluginMessage> deferred = new ArrayList<>();
		private EventBus bus;

		void flush()
		{
			List<PluginMessage> replies = new ArrayList<>(deferred);
			deferred.clear();
			replies.forEach(bus::post);
		}

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
					Object config = m.getData().get("config");
					if (config instanceof Map && !((Map<?, ?>) config).isEmpty())
					{
						overrides = new HashMap<>();
						((Map<?, ?>) config).forEach((k, v) -> overrides.put((String) k, v));
					}
					break;
				case "clear":
					shownTargets = null;
					overrides = new HashMap<>();
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
						if (!overrides.isEmpty())
						{
							reply.put("config", new HashMap<>(overrides));
						}
						PluginMessage answer = new PluginMessage("shortestpath", "currentTarget", reply);
						if (deferReplies)
						{
							deferred.add(answer);
						}
						else
						{
							bus.post(answer);
						}
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
		assertEquals(Boolean.FALSE, sp.overrides.get("avoidWilderness"));
		assertTrue(bridge.isOverrideActive());

		// Still walking: override stays
		bridge.onGameTick(new WorldPoint(3100, 3500, 0));
		assertTrue(bridge.isOverrideActive());

		// Arrived: Shortest Path confirms the path is ours, so it's cleared and shown again with no override at all.
		// Re-sending the player's own value would be an override too, hiding later changes to their setting.
		bridge.onGameTick(new WorldPoint(3345, 10160, 0));
		assertFalse(bridge.isOverrideActive());
		assertTrue(sp.overrides.isEmpty());
		assertEquals(1, sp.count("clear"));
		PluginMessage again = sp.last("path");
		assertNull(again.getData().get("config"));
		assertEquals(Collections.singletonList(new WorldPoint(3343, 10163, 0)), sp.shownTargets);
		// Still our path: a task change still clears it
		assertTrue(bridge.isPathShown());
		bridge.clearIfOurs();
		assertEquals(2, sp.count("clear"));
		assertNull(sp.shownTargets);
	}

	@Test
	public void noOverrideWhenPlayerAlreadyAllowsWilderness()
	{
		ShortestPathBridge lenient = new ShortestPathBridge(bus, null, () -> false);
		lenient.path(Collections.singletonList(WILDY_CAVE), true);
		assertNull(avoidWilderness(sp.last("path")));
		assertFalse(lenient.isOverrideActive());
		assertTrue(sp.overrides.isEmpty());
	}

	@Test
	public void someoneElsesPathDropsOurOverrideButKeepsTheirPath()
	{
		bridge.path(Collections.singletonList(WILDY_CAVE), true);

		// The player picks their own destination on the world map: Shortest Path keeps our override for it
		WorldPoint bank = new WorldPoint(3185, 3436, 0);
		sp.shownTargets = new ArrayList<>(Collections.singletonList(bank));

		ticks(ShortestPathBridge.CHECK_INTERVAL_TICKS, new WorldPoint(3100, 3500, 0));
		assertFalse(bridge.isOverrideActive());
		assertFalse(bridge.isPathShown());
		// Our override is gone, and their path is shown again as it was
		assertTrue(sp.overrides.isEmpty());
		assertEquals(Collections.singletonList(bank), sp.shownTargets);
		assertNull(sp.last("path").getData().get("config"));
	}

	@Test
	public void anotherPluginsOwnOverridesAreLeftAlone()
	{
		bridge.path(Collections.singletonList(WILDY_CAVE), true);

		// Another plugin shows its own path with its own overrides, which replaced ours
		WorldPoint clue = new WorldPoint(3240, 3225, 0);
		sp.shownTargets = new ArrayList<>(Collections.singletonList(clue));
		sp.overrides = new HashMap<>();
		sp.overrides.put("useFairyRings", false);
		long clears = sp.count("clear");

		ticks(ShortestPathBridge.CHECK_INTERVAL_TICKS, new WorldPoint(3100, 3500, 0));
		assertEquals(clears, sp.count("clear"));
		assertEquals(Boolean.FALSE, sp.overrides.get("useFairyRings"));
		assertEquals(Collections.singletonList(clue), sp.shownTargets);
		assertFalse(bridge.isOverrideActive());
		assertFalse(bridge.isPathShown());
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
	public void noReplyNeverClearsAPathWithoutOurOverride()
	{
		sp.answers = false;
		bridge.path(Collections.singletonList(CATACOMBS), false);
		bridge.clearIfOurs();
		ticks(ShortestPathBridge.REPLY_TIMEOUT_TICKS + 1, new WorldPoint(3100, 3500, 0));
		assertEquals(0, sp.count("clear"));
		assertFalse(bridge.isPathShown());
	}

	@Test
	public void noReplyStillDropsOurOverride()
	{
		// An older Shortest Path that doesn't answer getTarget: a clear is the only way to drop the override
		sp.answers = false;
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		bridge.clearIfOurs();
		ticks(ShortestPathBridge.REPLY_TIMEOUT_TICKS + 1, new WorldPoint(3100, 3500, 0));
		assertEquals(1, sp.count("clear"));
		assertTrue(sp.overrides.isEmpty());
		assertFalse(bridge.isOverrideActive());

		// Arriving without a reply: cleared to drop the override, and not shown again on a guess that it's still ours
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		bridge.onGameTick(new WorldPoint(3345, 10160, 0));
		ticks(ShortestPathBridge.REPLY_TIMEOUT_TICKS + 1, new WorldPoint(3345, 10160, 0));
		assertTrue(sp.overrides.isEmpty());
		assertNull(sp.shownTargets);
		assertFalse(bridge.isPathShown());
	}

	@Test
	public void noReplyToARoutineCheckNeverTouchesThePath()
	{
		// Review: an older Shortest Path timing out on the routine check wiped the player's own path and redrew ours
		sp.answers = false;
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		WorldPoint theirs = new WorldPoint(3185, 3436, 0);
		sp.shownTargets = new ArrayList<>(Collections.singletonList(theirs));
		ticks(ShortestPathBridge.CHECK_INTERVAL_TICKS + ShortestPathBridge.REPLY_TIMEOUT_TICKS + 1,
			new WorldPoint(3100, 3500, 0));
		assertEquals(0, sp.count("clear"));
		assertEquals(Collections.singletonList(theirs), sp.shownTargets);
		// It stops asking: this version can't answer
		long asked = sp.count("getTarget");
		ticks(ShortestPathBridge.CHECK_INTERVAL_TICKS * 3, new WorldPoint(3100, 3500, 0));
		assertEquals(asked, sp.count("getTarget"));
	}

	@Test
	public void aReplyFromBeforeANewGoIsIgnored()
	{
		// Review: Shortest Path answers getTarget on a later client tick; a Go in between must not be undone by it
		sp.deferReplies = true;
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		bridge.onGameTick(new WorldPoint(3345, 10160, 0)); // arrived: asks whose path is shown
		WorldPoint other = new WorldPoint(3000, 3700, 0);
		bridge.path(Collections.singletonList(new int[]{3000, 3700, 0}), true); // a new Go before the answer
		long clears = sp.count("clear");
		sp.flush();
		assertEquals(clears, sp.count("clear"));
		assertEquals(Collections.singletonList(other), sp.shownTargets);
		assertEquals(Boolean.FALSE, sp.overrides.get("avoidWilderness"));
		assertTrue(bridge.isOverrideActive());
		assertTrue(bridge.isPathShown());
	}

	@Test
	public void normalRouteAfterWildernessRouteDropsTheOverride()
	{
		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		bridge.path(Collections.singletonList(CATACOMBS), false);
		assertNull(avoidWilderness(sp.last("path")));
		assertTrue(sp.overrides.isEmpty());
		assertEquals(Collections.singletonList(new WorldPoint(1675, 10091, 0)), sp.shownTargets);
		assertFalse(bridge.isOverrideActive());
	}

	@Test
	public void releaseOnShutdownClearsOnlyToDropOurOverride()
	{
		bridge.path(Collections.singletonList(CATACOMBS), false);
		bridge.release();
		assertEquals(0, sp.count("clear"));
		assertEquals(Collections.singletonList(new WorldPoint(1675, 10091, 0)), sp.shownTargets);

		bridge.path(Collections.singletonList(WILDY_CAVE), true);
		bridge.release();
		assertEquals(1, sp.count("clear"));
		assertTrue(sp.overrides.isEmpty());
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
