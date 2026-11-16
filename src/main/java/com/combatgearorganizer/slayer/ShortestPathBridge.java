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
 * <li>The "avoid Wilderness" override is only for our own route. It is put back to the player's own setting when they
 * arrive, when someone else's path replaces ours, or when our path is cleared.</li>
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
			// Replace our earlier Wilderness override with the player's own setting
			data.put("config", wildernessConfig(playerAvoidsWilderness.getAsBoolean()));
		}
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));

		ourTargets = points;
		routeActive = true;
		overrideActive = override;
		pendingAutoClear = false;
		ticksSinceRequest = -1;
		ticksSinceCheck = 0;
	}

	/** The player asked to clear the path: clear it, whoever set it. */
	public void clear()
	{
		if (isPathShown() || overrideActive)
		{
			eventBus.post(new PluginMessage(NAMESPACE, "clear", new HashMap<>()));
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
	 * The plugin is shutting down, so there is no time to wait for a reply. Leave whatever path is shown, but never leave
	 * our Wilderness override behind.
	 */
	public void release()
	{
		restoreWildernessSetting();
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
			restoreWildernessSetting();
			routeActive = false;
		}

		if (ticksSinceRequest >= 0)
		{
			ticksSinceRequest++;
			if (ticksSinceRequest > REPLY_TIMEOUT_TICKS)
			{
				// Shortest Path didn't answer (an older version without getTarget). Don't clear a path that might not be
				// ours, but do give the player their Wilderness setting back.
				restoreWildernessSetting();
				if (pendingAutoClear)
				{
					reset();
				}
				ticksSinceRequest = -1;
			}
			return;
		}

		if (++ticksSinceCheck >= CHECK_INTERVAL_TICKS)
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
		if (!MESSAGE_ID.equals(data.get("id")))
		{
			return;
		}
		ticksSinceRequest = -1;

		boolean ours = Boolean.TRUE.equals(data.get("set")) && ourTargets.equals(targetPoints(data.get("target")));
		if (ours)
		{
			if (pendingAutoClear)
			{
				eventBus.post(new PluginMessage(NAMESPACE, "clear", new HashMap<>()));
				reset();
			}
			return;
		}

		// Someone else's path (or none) replaced ours: drop our override without touching their path
		restoreWildernessSetting();
		reset();
	}

	private void requestTarget()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("id", MESSAGE_ID);
		eventBus.post(new PluginMessage(NAMESPACE, "getTarget", data));
		ticksSinceRequest = 0;
	}

	private void restoreWildernessSetting()
	{
		if (!overrideActive)
		{
			return;
		}
		// A config-only path message applies the overrides and changes nothing else
		Map<String, Object> data = new HashMap<>();
		data.put("config", wildernessConfig(playerAvoidsWilderness.getAsBoolean()));
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));
		overrideActive = false;
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
