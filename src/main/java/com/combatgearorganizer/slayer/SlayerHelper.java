package com.combatgearorganizer.slayer;

import com.combatgearorganizer.CombatGearOrganizerConfig;
import com.combatgearorganizer.ItemValueMode;
import com.combatgearorganizer.ItemValues;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.slayer.SlayerPluginService;
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
	/** Item definitions to read per tick while matching drop names to items. The game has about 30,000. */
	private static final int ITEM_SCAN_BATCH = 2500;
	/**
	 * Task progress is saved per account in its own config group, so saving after every kill doesn't reach the
	 * plugin's settings-changed handling.
	 */
	static final String PROGRESS_GROUP = "combatgearorganizerprogress";
	static final String PROGRESS_KEY = "task";
	/** What the panel remembers (open drawers, picked monsters, last places), in the same group. */
	static final String PANEL_KEY = "panel";
	/** The game message for a superior spawning, as in the core Slayer plugin. */
	static final String SUPERIOR_MESSAGE = "A superior foe has appeared...";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

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

	@Inject
	private ItemManager itemManager;

	@Inject
	private ItemValues itemValues;

	@Inject
	private ConfigManager configManager;

	private SlayerLocationData data;
	private volatile SlayerPanel panel;
	private BufferedImage markerImage;
	private SlayerTaskState lastState = SlayerTaskState.NONE;
	/** A task the player asked to look at instead of their real one; null normally. */
	private volatile SlayerTaskState preview;
	/** The kill count a preview shows. */
	private static final int PREVIEW_AMOUNT = 100;
	/** Carry items last sent to the panel; null to send the next ones whatever they are. */
	private Set<CarryItem> lastCarried;
	private SlayerLocationData.TaskEntry lastTask;
	/** The places last drawn for {@link #lastTask}, so a kill only updates the count while they stay the same. */
	private List<LocationOption> lastLocations = Collections.emptyList();
	private boolean lastShortestPathAvailable;
	private int lastClogVersion = -1;
	private ItemNameResolver itemNames;
	private boolean lastItemNamesDone;
	private final TaskProgressTracker progress = new TaskProgressTracker();
	/** The account (RS profile key) the progress in memory belongs to; null until loaded. */
	private String progressProfile;
	private boolean progressDirty;
	private int progressVersion;
	private int lastProgressVersion = -1;
	private ItemValueMode lastValueMode;
	/** Slayer XP at the last StatChanged; -1 until the first one after login, which only sets the starting point. */
	private int lastSlayerXp = -1;
	/** The route the panel's route line shows. Client thread. */
	private Route route = Route.NONE;
	/** The places marked on the world map, while {@link #route} is a world map route. */
	private List<WorldPoint> mapTargets = Collections.emptyList();
	/** Redraw the whole panel on the next refresh, e.g. after it showed the logged-out text. */
	private boolean needsRedraw;

	public void start()
	{
		data = loadData();
		List<String> dropNames = new ArrayList<>();
		List<String> itemNamesWanted = new ArrayList<>();
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				v.getDrops().forEach(d -> dropNames.add(d.getItem()));
				itemNamesWanted.addAll(v.getAlwaysDrops());
			}
		}
		itemNamesWanted.addAll(dropNames);
		collectionLog.watch(dropNames);
		lastClogVersion = -1;
		itemNames = new ItemNameResolver(itemNamesWanted);
		lastItemNamesDone = false;
		BufferedImage icon = ImageUtil.loadImageResource(SlayerHelper.class, "slayer_icon.png");
		markerImage = ImageUtil.resizeImage(icon, 24, 21);
		panel = new SlayerPanel(icons, this::go, this::goNearest, this::cancelRoute, this::saveMemory);
		lastState = SlayerTaskState.NONE;
		clientThread.invokeLater(() ->
		{
			// On the client thread, so a save queued by stop() just before still goes to the right account
			progressProfile = null;
			route = Route.NONE;
			// Started while logged in: no StatChanged comes until the next XP drop, so take the XP now or that
			// drop would only set the starting point
			lastSlayerXp = client.getGameState() == GameState.LOGGED_IN ? client.getSkillExperience(Skill.SLAYER) : -1;
			lastCarried = null;
			updateCarried();
			refresh(true);
		});
	}

	/** Call when the inventory or worn equipment changes, on the client thread. */
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN)
		{
			updateCarried();
		}
	}

	/** Tells the panel which carry items (bonecrusher, herb sack...) the player has, when that changes. */
	private void updateCarried()
	{
		final SlayerPanel p = panel;
		if (p == null)
		{
			return;
		}
		List<Integer> ids = new ArrayList<>();
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer items = client.getItemContainer(container);
			if (items != null)
			{
				for (Item item : items.getItems())
				{
					ids.add(item.getId());
				}
			}
		}
		Set<CarryItem> now = CarryItem.carried(ids);
		if (!now.equals(lastCarried))
		{
			lastCarried = now;
			SwingUtilities.invokeLater(() -> p.updateCarried(now));
		}
	}

	public void stop()
	{
		clientThread.invokeLater(() ->
		{
			// No time to ask Shortest Path whose path it is, so leave the path but never our Wilderness override
			shortestPath.release();
			removeMapMarkers();
			collectionLog.flush();
			saveProgress();
		});
		panel = null;
		preview = null;
	}

	/** The Slayer section for the side panel; null while stopped. The plugin puts it in its one panel. */
	public SlayerPanel getPanel()
	{
		return panel;
	}

	/** Call every game tick on the client thread. Cheap when nothing changed. */
	public void onGameTick()
	{
		collectionLog.onGameTick();
		scanItemNames();
		Player player = client.getLocalPlayer();
		WorldPoint at = player != null ? player.getWorldLocation() : null;
		shortestPath.onGameTick(at);
		updateRoute(at);
		refresh(false);
		if (progress.onTick() && config.taskSummaryChat() && progress.getProgress() != null)
		{
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT)
					.append("[Slayer] " + progress.getProgress().summary(itemValues.mode()) + ".")
					.build())
				.build());
		}
		saveProgress();
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
		if ((event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
			&& event.getMessage().contains(SUPERIOR_MESSAGE) && progress.addSuperior())
		{
			progressChanged();
		}
	}

	/** Slayer XP gained counts towards the task. */
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.SLAYER)
		{
			return;
		}
		int xp = event.getXp();
		if (lastSlayerXp >= 0 && xp > lastSlayerXp && progress.addXp(xp - lastSlayerXp))
		{
			progressChanged();
		}
		lastSlayerXp = xp;
	}

	/** Loot from a task monster adds to the task's loot value. Client thread. */
	public void onNpcLootReceived(NpcLootReceived event)
	{
		TaskProgress p = progress.getProgress();
		NPC npc = event.getNpc();
		if (!progress.isCounting() || p == null || npc == null || !isTaskMonster(npc, p))
		{
			return;
		}
		long ge = 0;
		long ha = 0;
		for (ItemStack item : event.getItems())
		{
			ge += itemValues.value(ItemValueMode.GRAND_EXCHANGE, item.getId()) * item.getQuantity();
			ha += itemValues.value(ItemValueMode.HIGH_ALCHEMY, item.getId()) * item.getQuantity();
		}
		if (progress.addLoot(ge, ha))
		{
			progressChanged();
		}
	}

	private boolean isTaskMonster(NPC npc, TaskProgress p)
	{
		List<NPC> targets = slayerPluginService.getTargets();
		if (targets != null && targets.contains(npc))
		{
			return true;
		}
		// The Slayer plugin drops an NPC from its targets when it despawns, which can be before its loot shows
		SlayerLocationData.TaskEntry task = data.findTask(p.getTaskId(), p.getTaskName());
		List<String> names = new ArrayList<>();
		if (task != null)
		{
			task.getVariants().forEach(v -> names.add(v.getName()));
		}
		String taskName = p.getTaskName() != null ? p.getTaskName() : task != null ? task.getName() : null;
		return TaskMonsters.matches(npc.getName(), taskName, names);
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
		switch (event.getGameState())
		{
			case LOGGED_IN:
				// Also fires after every loading screen; the clock only restarts if it was stopped
				progress.onLogin(System.currentTimeMillis());
				break;
			case LOGIN_SCREEN:
			case HOPPING:
			case CONNECTION_LOST:
				saveProgress();
				progress.onLogout();
				lastSlayerXp = -1;
				if (event.getGameState() == GameState.LOGIN_SCREEN)
				{
					// The next login may be another account; load its own progress then
					progressProfile = null;
					needsRedraw = true;
					final SlayerPanel p = panel;
					if (p != null)
					{
						SwingUtilities.invokeLater(p::showLoggedOut);
					}
				}
				break;
			default:
				break;
		}
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

		SlayerTaskState real = readTask();
		// Progress always follows the real task, even while the panel shows a preview
		updateProgress(real);
		final SlayerTaskState shownPreview = preview;
		SlayerTaskState state = shownPreview != null ? shownPreview : real;
		ItemValueMode valueMode = itemValues.mode();
		boolean spAvailable = shortestPath.isAvailable();
		if (!spAvailable && route.getKind() == Route.Kind.SHORTEST_PATH)
		{
			// Shortest Path was turned off mid-route
			setRoute(Route.NONE);
		}
		collectionLog.ensureLoaded();
		int clogVersion = collectionLog.getVersion();
		boolean namesDone = itemNames.isDone();
		force |= needsRedraw;
		needsRedraw = false;
		if (!force && state.equals(lastState) && spAvailable == lastShortestPathAvailable && clogVersion == lastClogVersion
			&& namesDone == lastItemNamesDone && progressVersion == lastProgressVersion && valueMode == lastValueMode)
		{
			return;
		}
		// A kill (or new XP, a superior, loot) on the task already shown only changes the count and progress lines
		boolean countOnly = !force && state.hasTask() && state.sameTaskAs(lastState) && lastTask != null
			&& spAvailable == lastShortestPathAvailable && clogVersion == lastClogVersion && namesDone == lastItemNamesDone
			&& valueMode == lastValueMode;
		if (countOnly)
		{
			// A level gained or a quest finished since the last draw changes what the place cards say
			countOnly = sameRequirements(pickLocations(lastTask, state), lastLocations);
		}
		lastProgressVersion = progressVersion;
		lastValueMode = valueMode;
		TaskProgress tp = shownPreview == null ? progress.getProgress() : null;
		if (countOnly)
		{
			lastState = state;
			TaskProgress.View view = tp != null && !tp.isDone() ? tp.view(state.getRemaining(), valueMode) : null;
			SwingUtilities.invokeLater(() -> p.updateCount(state, view));
			return;
		}
		lastShortestPathAvailable = spAvailable;
		lastClogVersion = clogVersion;
		lastItemNamesDone = namesDone;
		CollectionLog.Snapshot clog = collectionLog.snapshot();

		boolean taskEnded = lastState.hasTask() && !state.hasTask();
		boolean taskChanged = state.getTaskId() != lastState.getTaskId() || !Objects.equals(state.getName(), lastState.getName());
		lastState = state;
		if (taskEnded || (taskChanged && lastTask != null))
		{
			// Only clears the path if it is still the one we set
			shortestPath.clearIfOurs();
			removeMapMarkers();
			setRoute(Route.NONE);
		}

		if (!state.hasTask())
		{
			lastTask = null;
			String summary = tp != null && tp.isDone() ? tp.summary(valueMode) : null;
			SwingUtilities.invokeLater(() -> p.showNoTask(summary));
			return;
		}

		SlayerLocationData.TaskEntry task = data.findTask(state.getTaskId(), state.getName());
		lastTask = task;
		List<LocationOption> locations = task == null ? Collections.<LocationOption>emptyList() : pickLocations(task, state);
		lastLocations = locations;
		SlayerLocationData.TaskInfo info = task != null ? task.getInfo() : null;
		List<SlayerLocationData.Variant> variants = task != null ? task.getVariants() : Collections.emptyList();
		DropItems items = dropItems(variants);
		TaskProgress.View view = tp != null && !tp.isDone() ? tp.view(state.getRemaining(), valueMode) : null;
		boolean compact = config.compactPanel();
		SwingUtilities.invokeLater(() ->
		{
			p.setCompact(compact);
			p.setPreview(shownPreview != null, this::stopPreview);
			p.showTask(state, info, variants, locations, task != null, spAvailable, clog, items, view);
		});
	}

	/**
	 * Shows a task in the panel as if the player had it, to look it up or try the panel out. Changes nothing in the
	 * game: the real task, its progress and the route memory are left alone. Client thread.
	 *
	 * @param query  the task's name, or the start of it ("abyssal" finds Abyssal demons)
	 * @param master a slayer master's name, or null for Duradel
	 * @return what to tell the player
	 */
	public String previewTask(String query, String master)
	{
		if (data == null)
		{
			return "The slayer panel isn't running.";
		}
		SlayerLocationData.TaskEntry task = findByName(data, query);
		if (task == null)
		{
			return "No slayer task or monster called \"" + query + "\". Try the task's name as a master gives it, e.g. \"abyssal demons\".";
		}
		int masterId = master != null ? SlayerMasters.idForName(master) : 0;
		if (master != null && masterId == 0)
		{
			return "\"" + master + "\" isn't a slayer master.";
		}
		if (masterId == 0)
		{
			// Krystilia-only tasks only make sense from her
			masterId = task.getLocations().isEmpty() && !task.getWildernessLocations().isEmpty()
				? SlayerMasters.KRYSTILIA : 5;
		}
		preview = new SlayerTaskState(task.getTaskId(), task.getName(), PREVIEW_AMOUNT, PREVIEW_AMOUNT, masterId, 0, null);
		needsRedraw = true;
		refresh(true);
		return "Previewing " + task.getName() + " from " + SlayerMasters.name(masterId)
			+ " in the slayer panel. Type ::slayertask off, or click Stop, to go back to your task.";
	}

	/** Back to the real task. Safe from any thread. */
	public void stopPreview()
	{
		clientThread.invokeLater(() ->
		{
			if (preview != null)
			{
				preview = null;
				needsRedraw = true;
				refresh(true);
			}
		});
	}

	public boolean isPreviewing()
	{
		return preview != null;
	}

	/**
	 * Exact task name first, then a task name starting with the query, then one containing it, then a task with a
	 * monster of that name. Case doesn't matter.
	 */
	static SlayerLocationData.TaskEntry findByName(SlayerLocationData data, String query)
	{
		String q = query.trim().toLowerCase(java.util.Locale.ROOT);
		if (q.isEmpty())
		{
			return null;
		}
		SlayerLocationData.TaskEntry starts = null;
		SlayerLocationData.TaskEntry contains = null;
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			String n = t.getName().toLowerCase(java.util.Locale.ROOT);
			if (n.equals(q))
			{
				return t;
			}
			if (starts == null && n.startsWith(q))
			{
				starts = t;
			}
			if (contains == null && n.contains(q))
			{
				contains = t;
			}
		}
		if (starts != null || contains != null)
		{
			return starts != null ? starts : contains;
		}
		// A monster's name finds its task ("sulphur nagua" finds Lesser Nagua)
		for (SlayerLocationData.TaskEntry t : data.getTasks())
		{
			for (SlayerLocationData.Variant v : t.getVariants())
			{
				if (v.getName().toLowerCase(java.util.Locale.ROOT).contains(q))
				{
					return t;
				}
			}
		}
		return null;
	}

	/** The task's places for this master, with what the player still needs for each. Client thread. */
	private List<LocationOption> pickLocations(SlayerLocationData.TaskEntry task, SlayerTaskState state)
	{
		return LocationPicker.pick(task, state.getMasterId(), state.getKonarAreaId(), config.showWildernessLocations(),
			this::missingRequirements);
	}

	/** Whether the places are the same and each still needs the same things. */
	static boolean sameRequirements(List<LocationOption> a, List<LocationOption> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			if (!Objects.equals(a.get(i).getKey(), b.get(i).getKey())
				|| !a.get(i).getMissingRequirements().equals(b.get(i).getMissingRequirements()))
			{
				return false;
			}
		}
		return true;
	}

	/** Feeds this tick's count to the progress tracker, loading the account's saved progress first. Client thread. */
	private void updateProgress(SlayerTaskState state)
	{
		if (!loadProgress())
		{
			return;
		}
		String name = state.hasTask() && !isPlaceholderName(state) ? state.getName() : null;
		TaskProgressTracker.Change change = progress.onCount(state.getTaskId(), name, state.hasTask() ? state.getRemaining() : 0,
			state.getInitial(), System.currentTimeMillis());
		if (change != TaskProgressTracker.Change.NONE)
		{
			progressChanged();
		}
	}

	private static boolean isPlaceholderName(SlayerTaskState state)
	{
		return "Boss task".equals(state.getName()) || ("Task #" + state.getTaskId()).equals(state.getName());
	}

	/** Loads the saved progress when the account changes. False while no account is known. Client thread. */
	private boolean loadProgress()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return false;
		}
		if (profile.equals(progressProfile))
		{
			return true;
		}
		TaskProgress saved = null;
		String json = configManager.getRSProfileConfiguration(PROGRESS_GROUP, PROGRESS_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				saved = gson.fromJson(json, TaskProgress.class);
			}
			catch (RuntimeException e)
			{
				log.debug("Couldn't read saved task progress", e);
			}
		}
		progressProfile = profile;
		progressDirty = false;
		progress.load(saved, client.getGameState() == GameState.LOGGED_IN, System.currentTimeMillis());
		progressVersion++;
		loadMemory(profile);
		return true;
	}

	/** Hands the panel this account's open drawers, picked monsters and last places. Client thread. */
	private void loadMemory(String profile)
	{
		PanelMemory memory = PanelMemory.fromJson(gson, configManager.getRSProfileConfiguration(PROGRESS_GROUP, PANEL_KEY));
		memory.setProfile(profile);
		final SlayerPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.setMemory(memory));
		}
	}

	/** The panel's memory changed: save it to the account it belongs to. Called on the Swing thread. */
	private void saveMemory(PanelMemory memory)
	{
		String profile = memory.getProfile();
		if (profile == null)
		{
			// Not loaded for an account yet; the account's own memory replaces it on login
			return;
		}
		String json = memory.toJson(gson);
		clientThread.invokeLater(() ->
		{
			if (profile.equals(configManager.getRSProfileKey()))
			{
				configManager.setRSProfileConfiguration(PROGRESS_GROUP, PANEL_KEY, json);
			}
		});
	}

	/** Ends the route line when the player arrives or our path is gone. Client thread. */
	private void updateRoute(WorldPoint player)
	{
		if (route.getKind() == Route.Kind.SHORTEST_PATH && !shortestPath.isRouteActive())
		{
			// Arrived, or another path replaced ours
			setRoute(Route.NONE);
		}
		else if (route.getKind() == Route.Kind.WORLD_MAP && ShortestPathBridge.near(mapTargets, player))
		{
			removeMapMarkers();
			setRoute(Route.NONE);
		}
	}

	/** Client thread. */
	private void setRoute(Route r)
	{
		if (r.equals(route))
		{
			return;
		}
		route = r;
		if (r.getKind() != Route.Kind.WORLD_MAP)
		{
			mapTargets = Collections.emptyList();
		}
		final SlayerPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.showRoute(r));
		}
	}

	private void progressChanged()
	{
		progressDirty = true;
		progressVersion++;
	}

	/** Saves the progress to the account it belongs to, if it changed. Client thread. */
	private void saveProgress()
	{
		if (!progressDirty || progressProfile == null || !progressProfile.equals(configManager.getRSProfileKey()))
		{
			return;
		}
		progressDirty = false;
		TaskProgress p = progress.getProgress();
		if (p == null)
		{
			configManager.unsetRSProfileConfiguration(PROGRESS_GROUP, PROGRESS_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(PROGRESS_GROUP, PROGRESS_KEY, gson.toJson(p));
		}
	}

	/** Reads the next batch of item definitions to match drop names to items. Client thread only. */
	private void scanItemNames()
	{
		if (itemNames == null || itemNames.isDone() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		itemNames.step(client.getItemCount(), ITEM_SCAN_BATCH, id ->
		{
			try
			{
				ItemComposition c = itemManager.getItemComposition(id);
				return new ItemNameResolver.Candidate(c.getName(), c.getNote() != -1, c.getPlaceholderTemplateId() != -1,
					c.isTradeable());
			}
			catch (RuntimeException e)
			{
				log.debug("Couldn't read item {}", id, e);
				return null;
			}
		});
	}

	/** Item ids and values for the task's drops, with the current "Item value" setting. Client thread only. */
	private DropItems dropItems(List<SlayerLocationData.Variant> variants)
	{
		ItemValueMode mode = itemValues.mode();
		Map<String, DropItems.Item> items = new HashMap<>();
		for (SlayerLocationData.Variant v : variants)
		{
			List<String> names = new ArrayList<>(v.getAlwaysDrops());
			v.getDrops().forEach(d -> names.add(d.getItem()));
			for (String name : names)
			{
				String key = CollectionLog.key(name);
				int id = itemNames.idOf(name);
				if (id < 0 || items.containsKey(key))
				{
					continue;
				}
				boolean tradeable = itemManager.getItemComposition(id).isTradeable();
				items.put(key, new DropItems.Item(id, itemValues.value(mode, id), tradeable));
			}
		}
		return new DropItems(Collections.unmodifiableMap(items), mode);
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
		route(Collections.singletonList(location), location.getLabel());
	}

	private void goNearest(List<LocationOption> locations)
	{
		route(locations, locations.size() == 1 ? locations.get(0).getLabel() : null);
	}

	/** {@code place}: the place's name for the route line, or null for the nearest of several. */
	private void route(List<LocationOption> locations, String place)
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
				// The route line shows one route: drop markers left from before Shortest Path was turned on
				removeMapMarkers();
				shortestPath.path(targets, allowWilderness);
				setRoute(Route.shortestPath(place));
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
				List<WorldPoint> points = new ArrayList<>();
				targets.forEach(t -> points.add(new WorldPoint(t[0], t[1], t[2])));
				setRoute(Route.worldMap(place));
				mapTargets = points;
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

	/** The player pressed Cancel (or Remove) on the route line. */
	private void cancelRoute()
	{
		clientThread.invokeLater(() ->
		{
			if (route.getKind() == Route.Kind.SHORTEST_PATH)
			{
				// Only offered while our route is active, so this clears our own path
				shortestPath.clear();
			}
			removeMapMarkers();
			setRoute(Route.NONE);
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
