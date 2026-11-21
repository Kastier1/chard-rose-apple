package com.combatgearorganizer.slayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Talks to the Shortest Path plugin (plugin hub) through RuneLite's PluginMessage event.
 * See https://github.com/Skretzo/shortest-path/blob/master/docs/Cross-plugin-communication.md
 *
 * <p>Two rules keep this from leaking into the player's own use of Shortest Path:
 * <ul>
 * <li>The "avoid Wilderness" override is only for our own route, and is dropped when the player arrives, when someone
 * else's path replaces ours, or when our path is cleared. Shortest Path keeps a {@code config} override until the next
 * {@code clear}, so dropping it means {@code clear} and, where a path should stay, sending that path again without
 * {@code config}. Re-sending the player's own value would itself be an override, hiding later changes to their setting
 * for the rest of the session.</li>
 * <li>Automatic clears (task changed or ended) only clear the path if it is still ours. We ask Shortest Path what it is
 * showing ({@code getTarget}) and act on its {@code currentTarget} reply.</li>
 * </ul>
 * Every method must be called on the client thread.
 */
@Singleton
public class ShortestPathBridge
{
	static final String NAMESPACE = "shortestpath";
	static final String MESSAGE_ID = "combatgearorganizer";
	private static final String PLUGIN_CLASS = "shortestpath.ShortestPathPlugin";
	private static final String AVOID_WILDERNESS = "avoidWilderness";

	/** How close (in tiles) counts as having arrived at one of our targets. */
	static final int ARRIVAL_DISTANCE = 6;
	/** While we have a path shown, check every this many ticks whether it is still ours. */
	static final int CHECK_INTERVAL_TICKS = 5;
	/** How long to wait for a currentTarget reply before giving up on an automatic clear. */
	static final int REPLY_TIMEOUT_TICKS = 3;

	private final EventBus eventBus;
	private final PluginManager pluginManager;
	private final BooleanSupplier playerAvoidsWilderness;

	private Set<WorldPoint> ourTargets = new HashSet<>();
	private boolean overrideActive;
	/** True from Go until the player arrives, the route is cleared, or another path replaces ours. */
	private boolean routeActive;
	private boolean pendingAutoClear;
	/** Arrived with our override in force: drop it once Shortest Path says whose path is shown. */
	private boolean pendingDropOverride;
	/**
	 * Numbers our getTarget requests, so a currentTarget reply answering an older request (Shortest Path answers on a
	 * later client tick) is ignored after a new Go, clear or release.
	 */
	private int requestSequence;
	/** Shortest Path didn't answer a getTarget (an older version): stop the periodic checks, which can't be answered. */
	private boolean noReplies;
	private int ticksSinceRequest = -1;
	private int ticksSinceCheck;

	@Inject
	ShortestPathBridge(EventBus eventBus, PluginManager pluginManager, ConfigManager configManager)
	{
		this(eventBus, pluginManager, () ->
		{
			// Shortest Path's own setting; it avoids the Wilderness unless the player turned that off
			String value = configManager.getConfiguration(NAMESPACE, AVOID_WILDERNESS);
			return value == null || Boolean.parseBoolean(value);
		});
	}

	ShortestPathBridge(EventBus eventBus, PluginManager pluginManager, BooleanSupplier playerAvoidsWilderness)
	{
		this.eventBus = eventBus;
		this.pluginManager = pluginManager;
		this.playerAvoidsWilderness = playerAvoidsWilderness;
	}

	/** Whether Shortest Path is installed and turned on. */
	public boolean isAvailable()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin.getClass().getName().equals(PLUGIN_CLASS))
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}

	/** Whether we currently have a route shown that we set. */
	public boolean isPathShown()
	{
		return !ourTargets.isEmpty();
	}

	/**
	 * Whether the route we set is still under way: true after {@link #path}, false once the player arrives within
	 * {@link #ARRIVAL_DISTANCE} tiles of a target, the path is cleared, or Shortest Path shows another path.
	 * The panel's route line follows this.
	 */
	public boolean isRouteActive()
	{
		return routeActive;
	}

	/** Whether our Wilderness override is in force. */
	boolean isOverrideActive()
	{
		return overrideActive;
	}

	/**
	 * Shows a path to whichever target is cheapest to reach.
	 *
	 * @param allowWilderness when true, lets this route go through the Wilderness even if the player's
	 *                        Shortest Path settings avoid it, until they arrive or the route is replaced
	 */
	public void path(Collection<int[]> targets, boolean allowWilderness)
	{
		Set<WorldPoint> points = targets.stream()
			.map(p -> new WorldPoint(p[0], p[1], p[2]))
			.collect(Collectors.toCollection(HashSet::new));
		if (points.isEmpty())
		{
			return;
		}

		Map<String, Object> data = new HashMap<>();
		data.put("target", points.stream().collect(Collectors.toList()));
		boolean override = allowWilderness && playerAvoidsWilderness.getAsBoolean();
		if (override)
		{
			data.put("config", wildernessConfig(false));
		}
		else if (overrideActive)
		{
			// A path without config keeps the earlier overrides, so drop our Wilderness override first
			postClear();
		}
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));

		ourTargets = points;
		routeActive = true;
		overrideActive = override;
		pendingAutoClear = false;
		pendingDropOverride = false;
		requestSequence++;
		ticksSinceRequest = -1;
		ticksSinceCheck = 0;
	}

	/** The player asked to clear the path: clear it, whoever set it. */
	public void clear()
	{
		if (isPathShown() || overrideActive)
		{
			postClear();
		}
		reset();
	}

	/**
	 * The task changed or ended: clear the path only if Shortest Path is still showing ours.
	 * The answer arrives later through {@link #onPluginMessage(PluginMessage)}.
	 */
	public void clearIfOurs()
	{
		if (!isPathShown() && !overrideActive)
		{
			return;
		}
		pendingAutoClear = true;
		requestTarget();
	}

	/**
	 * The plugin is shutting down, so there is no time to wait for a reply. Leave whatever path is shown, unless our
	 * Wilderness override is still in force: then clear, which is the only way to drop it, so it can't outlive us.
	 */
	public void release()
	{
		if (overrideActive)
		{
			postClear();
		}
		reset();
	}

	/** Call every game tick with the player's location. */
	public void onGameTick(WorldPoint player)
	{
		if (!isPathShown() && !overrideActive)
		{
			return;
		}

		if ((overrideActive || routeActive) && player != null && near(ourTargets, player))
		{
			// Arrived. The path stays ours, so a task change can still clear it
			routeActive = false;
			if (overrideActive && !pendingDropOverride)
			{
				// Drop the override once Shortest Path confirms the path is still ours (or someone else's)
				pendingDropOverride = true;
				if (ticksSinceRequest < 0)
				{
					requestTarget();
				}
			}
		}

		if (ticksSinceRequest >= 0)
		{
			ticksSinceRequest++;
			if (ticksSinceRequest > REPLY_TIMEOUT_TICKS)
			{
				// Shortest Path didn't answer (an older version without getTarget), so it can't say whose path is shown.
				// Never show a path again on a guess: it could cover the player's own or another plugin's. Only when
				// the player arrived or the task ended, and our Wilderness override is in force, clear: that's the only
				// way to drop it, and the route's job is done.
				ticksSinceRequest = -1;
				noReplies = true;
				if (overrideActive && (pendingDropOverride || pendingAutoClear))
				{
					postClear();
					reset();
				}
				else if (pendingAutoClear)
				{
					reset();
				}
			}
			return;
		}

		if (!noReplies && ++ticksSinceCheck >= CHECK_INTERVAL_TICKS)
		{
			ticksSinceCheck = 0;
			requestTarget();
		}
	}

	/** Call for every PluginMessage. Only Shortest Path's replies to us are handled. */
	public void onPluginMessage(PluginMessage message)
	{
		if (!NAMESPACE.equals(message.getNamespace()) || !"currentTarget".equals(message.getName()))
		{
			return;
		}
		Map<String, Object> data = message.getData();
		if (!requestId().equals(data.get("id")) || ticksSinceRequest < 0)
		{
			// Not ours, or an answer to an older request: what it says may be from before a new Go or clear
			return;
		}
		ticksSinceRequest = -1;
		noReplies = false;

		boolean shown = Boolean.TRUE.equals(data.get("set"));
		boolean ours = shown && ourTargets.equals(targetPoints(data.get("target")));
		if (ours)
		{
			if (pendingAutoClear)
			{
				postClear();
				reset();
			}
			else if (pendingDropOverride)
			{
				// Arrived: show the same path again without our override
				postClear();
				postPath(data.get("target"));
				overrideActive = false;
				pendingDropOverride = false;
			}
			return;
		}

		// Someone else's path (or none) replaced ours. Their path message replaced our override only if it had a config
		// of its own; if our override is all that's left, drop it and show their path again as it was.
		if (overrideActive && wildernessConfig(false).equals(data.get("config")))
		{
			postClear();
			if (shown)
			{
				postPath(data.get("target"));
			}
		}
		reset();
	}

	private void postClear()
	{
		eventBus.post(new PluginMessage(NAMESPACE, "clear", new HashMap<>()));
	}

	/** A path to these targets (as {@code currentTarget} lists them, or WorldPoints) with no setting overrides. */
	private void postPath(Object targets)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("target", targets);
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));
	}

	private String requestId()
	{
		return MESSAGE_ID + ":" + requestSequence;
	}

	private void requestTarget()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("id", requestId());
		// Outstanding before posting: a listener that answers at once (as on the client thread) must find it so
		ticksSinceRequest = 0;
		eventBus.post(new PluginMessage(NAMESPACE, "getTarget", data));
	}

	/** Whether the player is within {@link #ARRIVAL_DISTANCE} tiles of one of the targets, on the same plane. */
	static boolean near(Collection<WorldPoint> targets, WorldPoint player)
	{
		if (player == null)
		{
			return false;
		}
		for (WorldPoint t : targets)
		{
			if (t.getPlane() == player.getPlane() && t.distanceTo2D(player) <= ARRIVAL_DISTANCE)
			{
				return true;
			}
		}
		return false;
	}

	private void reset()
	{
		ourTargets = new HashSet<>();
		overrideActive = false;
		routeActive = false;
		pendingAutoClear = false;
		pendingDropOverride = false;
		requestSequence++;
		ticksSinceRequest = -1;
		ticksSinceCheck = 0;
	}

	private static Map<String, Object> wildernessConfig(boolean avoid)
	{
		Map<String, Object> config = new HashMap<>();
		config.put(AVOID_WILDERNESS, avoid);
		return config;
	}

	/** currentTarget's target list holds WorldPoints, or target maps with a "point" for targets with requirements. */
	static Set<WorldPoint> targetPoints(Object target)
	{
		Set<WorldPoint> points = new HashSet<>();
		if (!(target instanceof Collection))
		{
			return points;
		}
		for (Object t : (Collection<?>) target)
		{
			if (t instanceof WorldPoint)
			{
				points.add((WorldPoint) t);
			}
			else if (t instanceof Map && ((Map<?, ?>) t).get("point") instanceof WorldPoint)
			{
				points.add((WorldPoint) ((Map<?, ?>) t).get("point"));
			}
		}
		return points;
	}
}
