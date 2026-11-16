package com.combatgearorganizer.slayer;

import com.combatgearorganizer.CombatGearOrganizerConfig;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;

/**
 * Watches the current slayer task and drives the slayer side panel.
 * Lives inside the main plugin so the gear features can use the same task later.
 */
@Slf4j
@Singleton
public class SlayerHelper
{
	private static final String DATA_FILE = "slayer_locations.json";
	private static final String MAP_MARKER_NAME = "Slayer location";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private SlayerPluginService slayerPluginService;

	@Inject
	private ShortestPathBridge shortestPath;

	@Inject
	private CombatGearOrganizerConfig config;

	@Inject
	private Gson gson;

	private SlayerLocationData data;
	private SlayerPanel panel;
	private NavigationButton navButton;
	private BufferedImage markerImage;
	private SlayerTaskState lastState = SlayerTaskState.NONE;
	private SlayerLocationData.TaskEntry lastTask;

	public void start()
	{
		data = loadData();
		BufferedImage icon = ImageUtil.loadImageResource(SlayerHelper.class, "slayer_icon.png");
		markerImage = ImageUtil.resizeImage(icon, 24, 21);
		panel = new SlayerPanel(this::go, this::goNearest, this::clearPath);
		navButton = NavigationButton.builder()
			.tooltip("Slayer task")
			.icon(ImageUtil.resizeImage(icon, 18, 16))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		lastState = SlayerTaskState.NONE;
		clientThread.invokeLater(() -> refresh(true));
	}

	public void stop()
	{
		clientToolbar.removeNavigation(navButton);
		clearPath();
		panel = null;
		navButton = null;
	}

	/** Call every game tick on the client thread. Cheap when nothing changed. */
	public void onGameTick()
	{
		refresh(false);
	}

	/** Call when the plugin's config changes. */
	public void onConfigChanged()
	{
		clientThread.invokeLater(() -> refresh(true));
	}

	private void refresh(boolean force)
	{
		if (panel == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		SlayerTaskState state = readTask();
		if (!force && state.equals(lastState))
		{
			return;
		}

		boolean taskEnded = lastState.hasTask() && !state.hasTask();
		boolean taskChanged = state.getTaskId() != lastState.getTaskId() || !Objects.equals(state.getName(), lastState.getName());
		lastState = state;
		if (taskEnded || (taskChanged && lastTask != null))
		{
			clearPath();
		}

		if (!state.hasTask())
		{
			lastTask = null;
			SwingUtilities.invokeLater(panel::showNoTask);
			return;
		}

		SlayerLocationData.TaskEntry task = data.findTask(state.getTaskId(), state.getName());
		lastTask = task;
		List<LocationOption> locations = task == null
			? Collections.emptyList()
			: LocationPicker.pick(task, state.getMasterId(), state.getKonarAreaId(), config.showWildernessLocations());
		boolean spAvailable = shortestPath.isAvailable();
		SlayerPanel p = panel;
		SwingUtilities.invokeLater(() -> p.showTask(state, locations, task != null, spAvailable));
	}

	private SlayerTaskState readTask()
	{
		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (remaining <= 0)
		{
			return SlayerTaskState.NONE;
		}

		int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		int masterId = client.getVarbitValue(VarbitID.SLAYER_MASTER);

		// The core Slayer plugin resolves names for every task, including boss tasks; fall back to our data
		String name = slayerPluginService.getTask();
		if (name == null || name.isEmpty())
		{
			SlayerLocationData.TaskEntry entry = data.findTask(taskId, null);
			name = entry != null ? entry.getName() : null;
		}
		if (name == null)
		{
			return SlayerTaskState.NONE;
		}

		int initial = Math.max(slayerPluginService.getInitialAmount(), client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL));
		String konarArea = areaId > 0 ? slayerPluginService.getTaskLocation() : null;
		return new SlayerTaskState(taskId, name, remaining, initial, masterId, areaId, konarArea);
	}

	private void go(LocationOption location)
	{
		route(Collections.singletonList(location));
	}

	private void goNearest(List<LocationOption> locations)
	{
		route(locations);
	}

	private void route(List<LocationOption> locations)
	{
		List<int[]> targets = new ArrayList<>();
		boolean wilderness = false;
		for (LocationOption l : locations)
		{
			targets.addAll(l.getTargets());
			// Only lift the player's "avoid Wilderness" setting when the place can't be reached any other way
			wilderness |= l.isWilderness();
		}
		if (targets.isEmpty())
		{
			return;
		}

		final boolean allowWilderness = wilderness;
		clientThread.invokeLater(() ->
		{
			if (shortestPath.isAvailable())
			{
				shortestPath.path(targets, allowWilderness);
			}
			else
			{
				markOnWorldMap(targets);
			}
		});
	}

	private void markOnWorldMap(List<int[]> targets)
	{
		worldMapPointManager.removeIf(p -> MAP_MARKER_NAME.equals(p.getName()));
		for (int[] t : targets)
		{
			WorldMapPoint point = WorldMapPoint.builder()
				.worldPoint(new WorldPoint(t[0], t[1], t[2]))
				.image(markerImage)
				.name(MAP_MARKER_NAME)
				.tooltip(MAP_MARKER_NAME)
				.snapToEdge(true)
				.jumpOnClick(true)
				.build();
			worldMapPointManager.add(point);
		}
	}

	private void clearPath()
	{
		clientThread.invokeLater(() ->
		{
			shortestPath.clear();
			worldMapPointManager.removeIf(p -> MAP_MARKER_NAME.equals(p.getName()));
		});
	}

	private SlayerLocationData loadData()
	{
		try (InputStream in = SlayerHelper.class.getResourceAsStream(DATA_FILE))
		{
			if (in == null)
			{
				log.warn("Missing {}", DATA_FILE);
				return new SlayerLocationData();
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return gson.fromJson(reader, SlayerLocationData.class);
			}
		}
		catch (IOException e)
		{
			log.warn("Couldn't read {}", DATA_FILE, e);
			return new SlayerLocationData();
		}
	}
}
