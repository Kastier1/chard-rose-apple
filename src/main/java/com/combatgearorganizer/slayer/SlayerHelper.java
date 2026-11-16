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
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.events.PluginMessage;
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
	/** SLAYER_TARGET value for a boss task; the boss itself is in another varbit. */
	private static final int BOSS_TASK_ID = 98;

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

	@Inject
	private SlayerIcons icons;

	@Inject
	private CollectionLog collectionLog;

	@Inject
	private ChatMessageManager chatMessageManager;

	private SlayerLocationData data;
	private volatile SlayerPanel panel;
	private NavigationButton navButton;
	private BufferedImage markerImage;
	private SlayerTaskState lastState = SlayerTaskState.NONE;
	private SlayerLocationData.TaskEntry lastTask;
	private boolean lastShortestPathAvailable;
	private int lastClogVersion = -1;

	public void start()
	{
		data = loadData();
		List<String> dropNames = new ArrayList<>();
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				v.getDrops().forEach(d -> dropNames.add(d.getItem()));
			}
		}
		collectionLog.watch(dropNames);
		lastClogVersion = -1;
		BufferedImage icon = ImageUtil.loadImageResource(SlayerHelper.class, "slayer_icon.png");
		markerImage = ImageUtil.resizeImage(icon, 24, 21);
		panel = new SlayerPanel(icons, this::go, this::goNearest, this::clearPath);
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
		clientThread.invokeLater(() ->
		{
			// No time to ask Shortest Path whose path it is, so leave the path but never our Wilderness override
			shortestPath.release();
			removeMapMarkers();
			collectionLog.flush();
		});
		panel = null;
		navButton = null;
	}

	/** Call every game tick on the client thread. Cheap when nothing changed. */
	public void onGameTick()
	{
		collectionLog.onGameTick();
		Player player = client.getLocalPlayer();
		shortestPath.onGameTick(player != null ? player.getWorldLocation() : null);
		refresh(false);
	}

	/** Call for every PluginMessage, on the thread it was posted on. */
	public void onPluginMessage(PluginMessage event)
	{
		shortestPath.onPluginMessage(event);
	}

	public void onScriptPreFired(ScriptPreFired event)
	{
		collectionLog.onScriptPreFired(event);
	}

	public void onChatMessage(ChatMessage event)
	{
		collectionLog.onChatMessage(event);
	}

	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.MENU || event.getGroupId() == InterfaceID.MENU_NEW)
		{
			// The Adventure Log, possibly someone else's in their house
			collectionLog.onAdventureLogOpened();
		}
	}

	public void onGameStateChanged(GameStateChanged event)
	{
		collectionLog.onGameStateChanged(event.getGameState());
	}

	/** Call when the plugin's config changes, or when a plugin (such as Shortest Path) is turned on or off. */
	public void onConfigChanged()
	{
		clientThread.invokeLater(() -> refresh(true));
	}

	private void refresh(boolean force)
	{
		// stop() can null the field from the Swing thread at any time, so read it once
		final SlayerPanel p = panel;
		if (p == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		SlayerTaskState state = readTask();
		boolean spAvailable = shortestPath.isAvailable();
		collectionLog.ensureLoaded();
		int clogVersion = collectionLog.getVersion();
		if (!force && state.equals(lastState) && spAvailable == lastShortestPathAvailable && clogVersion == lastClogVersion)
		{
			return;
		}
		lastShortestPathAvailable = spAvailable;
		lastClogVersion = clogVersion;
		CollectionLog.Snapshot clog = collectionLog.snapshot();

		boolean taskEnded = lastState.hasTask() && !state.hasTask();
		boolean taskChanged = state.getTaskId() != lastState.getTaskId() || !Objects.equals(state.getName(), lastState.getName());
		lastState = state;
		if (taskEnded || (taskChanged && lastTask != null))
		{
			// Only clears the path if it is still the one we set
			shortestPath.clearIfOurs();
			removeMapMarkers();
		}

		if (!state.hasTask())
		{
			lastTask = null;
			SwingUtilities.invokeLater(p::showNoTask);
			return;
		}

		SlayerLocationData.TaskEntry task = data.findTask(state.getTaskId(), state.getName());
		lastTask = task;
		List<LocationOption> locations = task == null
			? Collections.emptyList()
			: LocationPicker.pick(task, state.getMasterId(), state.getKonarAreaId(), config.showWildernessLocations(),
				this::missingRequirements);
		SlayerLocationData.TaskInfo info = task != null ? task.getInfo() : null;
		List<SlayerLocationData.Variant> variants = task != null ? task.getVariants() : Collections.emptyList();
		SwingUtilities.invokeLater(() -> p.showTask(state, info, variants, locations, task != null, spAvailable, clog));
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

		// Prefer our data's name: the core Slayer plugin updates its name a tick after the varps change.
		// Boss tasks all share one id, so for those only the Slayer plugin knows which boss it is.
		String name = null;
		if (taskId != BOSS_TASK_ID)
		{
			SlayerLocationData.TaskEntry entry = data.findTask(taskId, null);
			name = entry != null ? entry.getName() : null;
		}
		if (name == null || name.isEmpty())
		{
			name = slayerPluginService.getTask();
		}
		if (name == null || name.isEmpty())
		{
			// Still a task, just one we can't name (e.g. the core Slayer plugin is off)
			name = taskId == BOSS_TASK_ID ? "Boss task" : "Task #" + taskId;
		}

		// The Slayer plugin's amount includes Mortimer's modifier, which can be negative
		int pluginInitial = slayerPluginService.getInitialAmount();
		int initial = pluginInitial > 0 ? pluginInitial : client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
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
		// Only lift the player's "avoid Wilderness" setting when every place is in the Wilderness. With a mix,
		// lifting it would let the route head into the Wilderness, or through it, for the other places too.
		boolean allWilderness = !locations.isEmpty();
		for (LocationOption l : locations)
		{
			targets.addAll(l.getTargets());
			allWilderness &= l.isWilderness();
		}
		if (targets.isEmpty())
		{
			return;
		}

		final boolean allowWilderness = allWilderness;
		clientThread.invokeLater(() ->
		{
			if (shortestPath.isAvailable())
			{
				shortestPath.path(targets, allowWilderness);
				if (allowWilderness)
				{
					chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.GAMEMESSAGE)
						.runeLiteFormattedMessage(new ChatMessageBuilder()
							.append(ChatColorType.HIGHLIGHT)
							.append("[Slayer] This route goes into the Wilderness, where other players can attack you. ")
							.append(ChatColorType.NORMAL)
							.append("Shortest Path may route through the Wilderness until you arrive.")
							.build())
						.build());
				}
			}
			else
			{
				markOnWorldMap(targets);
			}
		});
	}

	/** Must run on the client thread: reads quest states and skill levels. */
	private List<String> missingRequirements(SlayerLocationData.Requirements r)
	{
		List<String> missing = new ArrayList<>();
		for (String q : r.getQuestsFinished())
		{
			Quest quest = quest(q);
			if (quest != null && quest.getState(client) != QuestState.FINISHED)
			{
				missing.add(quest.getName());
			}
		}
		for (String q : r.getQuestsStarted())
		{
			Quest quest = quest(q);
			if (quest != null && quest.getState(client) == QuestState.NOT_STARTED)
			{
				missing.add(quest.getName() + " (started)");
			}
		}
		for (Map.Entry<String, Integer> e : r.getSkills().entrySet())
		{
			Skill skill = skill(e.getKey());
			if (skill != null && client.getRealSkillLevel(skill) < e.getValue())
			{
				missing.add(e.getValue() + " " + skill.getName());
			}
		}
		return missing;
	}

	private static Quest quest(String name)
	{
		try
		{
			return Quest.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			log.debug("Unknown quest {} in slayer data", name);
			return null;
		}
	}

	private static Skill skill(String name)
	{
		try
		{
			return Skill.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			log.debug("Unknown skill {} in slayer data", name);
			return null;
		}
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

	/** The player pressed Clear path. */
	private void clearPath()
	{
		clientThread.invokeLater(() ->
		{
			shortestPath.clear();
			removeMapMarkers();
		});
	}

	private void removeMapMarkers()
	{
		worldMapPointManager.removeIf(p -> MAP_MARKER_NAME.equals(p.getName()));
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
