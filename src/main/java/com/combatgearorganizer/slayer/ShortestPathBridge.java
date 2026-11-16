package com.combatgearorganizer.slayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Talks to the Shortest Path plugin (plugin hub) through RuneLite's PluginMessage event.
 * See https://github.com/Skretzo/shortest-path/blob/master/docs/Cross-plugin-communication.md
 */
@Singleton
public class ShortestPathBridge
{
	private static final String NAMESPACE = "shortestpath";
	private static final String PLUGIN_CLASS = "shortestpath.ShortestPathPlugin";

	private final EventBus eventBus;
	private final PluginManager pluginManager;
	private boolean pathShown;

	@Inject
	ShortestPathBridge(EventBus eventBus, PluginManager pluginManager)
	{
		this.eventBus = eventBus;
		this.pluginManager = pluginManager;
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

	/**
	 * Shows a path to whichever target is cheapest to reach.
	 *
	 * @param allowWilderness when true, lets this route go through the Wilderness even if the player's
	 *                        Shortest Path settings avoid it. Reset by {@link #clear()}.
	 */
	public void path(Collection<int[]> targets, boolean allowWilderness)
	{
		List<WorldPoint> points = targets.stream()
			.map(p -> new WorldPoint(p[0], p[1], p[2]))
			.collect(Collectors.toList());

		Map<String, Object> data = new HashMap<>();
		data.put("target", points);
		if (allowWilderness)
		{
			Map<String, Object> config = new HashMap<>();
			config.put("avoidWilderness", false);
			data.put("config", config);
		}
		else if (pathShown)
		{
			// Drop any earlier Wilderness override before showing a normal route
			eventBus.post(new PluginMessage(NAMESPACE, "clear"));
		}
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));
		pathShown = true;
	}

	public void clear()
	{
		if (pathShown)
		{
			eventBus.post(new PluginMessage(NAMESPACE, "clear"));
			pathShown = false;
		}
	}
}
