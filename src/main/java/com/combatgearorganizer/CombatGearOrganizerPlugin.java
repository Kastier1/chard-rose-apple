package com.combatgearorganizer;

import com.combatgearorganizer.slayer.SlayerHelper;
import com.combatgearorganizer.slayer.SlayerPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Combat Gear Organizer",
	description = "Lays out your combat gear bank tag, and shows where to do your slayer task",
	tags = {"bank", "tag", "layout", "gear", "combat", "organize", "sort", "slayer", "task", "shortest path"}
)
@PluginDependency(BankTagsPlugin.class)
@PluginDependency(SlayerPlugin.class)
public class CombatGearOrganizerPlugin extends Plugin
{
	static final String ORGANIZE_COMMAND = "gearsort";
	static final String ADD_WORN_COMMAND = "gearadd";
	static final String UNDO_ARGUMENT = "undo";
	/** Shows as "Auto layout: Combat gear" on a laid-out tag tab's menu, with a preview and Keep / Undo. */
	static final String AUTO_LAYOUT_NAME = "Combat gear";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private TagManager tagManager;

	@Inject
	private LayoutManager layoutManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private CombatGearOrganizerConfig config;

	@Inject
	private SlayerHelper slayerHelper;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private BankSearch bankSearch;

	private boolean slayerStarted;

	/** The one side panel: Slayer and Combat tab sections. Swing thread. */
	private OrganizerPanel organizerPanel;
	private CombatTabPanel combatTabPanel;
	private NavigationButton navButton;
	/** Read the Combat tab state again on the next tick, e.g. after a Bank Tags setting changed. */
	private volatile boolean combatTabDirty = true;
	/** What the Combat tab section last showed. Client thread. */
	private CombatTabStatus shownStatus;
	private Grouping shownGrouping;
	private boolean lastBankOpen;

	/** Layout of each tag before this plugin last overwrote it, for ::gearsort undo. Cleared on shutdown. */
	private final Map<String, int[]> undoLayouts = new HashMap<>();

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.hotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invokeLater(() -> organize(config.tagName()));
		}
	};

	@Provides
	CombatGearOrganizerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CombatGearOrganizerConfig.class);
	}

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(hotkeyListener);
		layoutManager.registerAutoLayout(this, AUTO_LAYOUT_NAME,
			previous -> new Layout(previous.getTag(), buildArrangement(previous.getTag(), previous)));

		combatTabPanel = new CombatTabPanel(this::chooseTag, this::chooseGrouping,
			() -> clientThread.invokeLater(this::organizeFromPanel),
			() -> clientThread.invokeLater(() -> undo(config.tagName())),
			() -> clientThread.invokeLater(() -> addWornGear(config.tagName())));
		organizerPanel = new OrganizerPanel(combatTabPanel);
		final BufferedImage icon = ImageUtil.loadImageResource(SlayerHelper.class, "slayer_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Combat Gear Organizer")
			.icon(ImageUtil.resizeImage(icon, 18, 16))
			.priority(7)
			.panel(organizerPanel)
			.build();
		clientToolbar.addNavigation(navButton);

		updateSlayerPanel();
		shownStatus = null;
		shownGrouping = null;
		combatTabDirty = true;
		clientThread.invokeLater(this::refreshCombatTab);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkeyListener);
		layoutManager.unregisterAutoLayout(AUTO_LAYOUT_NAME);
		undoLayouts.clear();
		if (slayerStarted)
		{
			slayerHelper.stop();
			slayerStarted = false;
		}
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		organizerPanel = null;
		combatTabPanel = null;
	}

	private void updateSlayerPanel()
	{
		if (config.showSlayerPanel() && !slayerStarted)
		{
			slayerHelper.start();
			slayerStarted = true;
		}
		else if (!config.showSlayerPanel() && slayerStarted)
		{
			slayerHelper.stop();
			slayerStarted = false;
		}
		else
		{
			return;
		}
		final OrganizerPanel panel = organizerPanel;
		final SlayerPanel slayer = slayerHelper.getPanel();
		if (panel != null)
		{
			onSwing(() -> panel.setSlayerPanel(slayer));
		}
	}

	private static void onSwing(Runnable r)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			r.run();
		}
		else
		{
			SwingUtilities.invokeLater(r);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		final boolean bankOpen = isBankOpen();
		if (combatTabDirty || bankOpen != lastBankOpen)
		{
			refreshCombatTab();
		}
		if (slayerStarted)
		{
			slayerHelper.onGameTick();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (slayerStarted)
		{
			slayerHelper.onScriptPreFired(event);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (slayerStarted)
		{
			slayerHelper.onChatMessage(event);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (slayerStarted)
		{
			slayerHelper.onWidgetLoaded(event);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		refreshCombatTab();
		if (slayerStarted)
		{
			slayerHelper.onGameStateChanged(event);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (slayerStarted)
		{
			slayerHelper.onStatChanged(event);
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (slayerStarted)
		{
			slayerHelper.onNpcLootReceived(event);
		}
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (slayerStarted)
		{
			slayerHelper.onPluginMessage(event);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BankTagsPlugin.CONFIG_GROUP.equals(event.getGroup()))
		{
			// Tag tabs, tagged items and layouts all live here; tagging can change many keys at once
			markCombatTabDirty();
			return;
		}
		if (!CombatGearOrganizerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		// Bank tag or layout changed in the settings or the panel: show it in the panel
		clientThread.invokeLater(this::refreshCombatTab);
		updateSlayerPanel();
		if (slayerStarted)
		{
			slayerHelper.onConfigChanged();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		// Bank Tags turned on or off: update the checklist
		markCombatTabDirty();
		// e.g. Shortest Path turned on or off: update the panel's directions text
		if (slayerStarted && event.getPlugin() != this)
		{
			slayerHelper.onConfigChanged();
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		final String command = event.getCommand().toLowerCase();
		final String[] args = event.getArguments();

		if (ORGANIZE_COMMAND.equals(command) && args.length > 0 && UNDO_ARGUMENT.equalsIgnoreCase(args[0]))
		{
			final String tag = args.length > 1 ? joinFrom(args, 1) : config.tagName();
			clientThread.invokeLater(() -> undo(tag));
			return;
		}

		final String tag = args.length > 0 ? joinFrom(args, 0) : config.tagName();
		if (ORGANIZE_COMMAND.equals(command))
		{
			clientThread.invokeLater(() -> organize(tag));
		}
		else if (ADD_WORN_COMMAND.equals(command))
		{
			clientThread.invokeLater(() -> addWornGear(tag));
		}
	}

	/**
	 * Tags everything you're currently wearing with the combat gear tag.
	 */
	private void addWornGear(String rawTag)
	{
		final String tag = Text.standardize(rawTag);
		if (tag.isEmpty())
		{
			sendMessage("No bank tag configured.");
			return;
		}

		final ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			sendMessage("Couldn't read your worn equipment.");
			return;
		}

		int added = 0;
		for (Item item : worn.getItems())
		{
			if (item.getId() <= 0)
			{
				continue;
			}
			// variation=true so e.g. any charge state / degradation level of the item matches
			tagManager.addTag(item.getId(), tag, true);
			added++;
		}

		final String tagged = "Tagged " + added + " worn item" + (added == 1 ? "" : "s") + " with '" + tag + "'.";
		sendMessage(tagged);
		panelNote(tagged, null);
		markCombatTabDirty();

		if (config.organizeOnAdd())
		{
			organize(tag);
		}
	}

	/**
	 * Rebuilds the layout of the given bank tag tab. Must be called on the client thread.
	 */
	private void organize(String rawTag)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final String tag = Text.standardize(rawTag);
		if (tag.isEmpty())
		{
			sendMessage("No bank tag configured.");
			return;
		}

		if (!isBankTagsActive())
		{
			sendMessage("Turn on the Bank Tags plugin first; the layout is drawn by its tag tabs.");
			return;
		}

		final Layout existing = layoutManager.loadLayout(tag);
		final List<GearItem> items = gearItems(tag, existing);
		if (items.isEmpty())
		{
			sendMessage("Bank tag '" + tag + "' has no items. Tag some gear first, or wear it and type ::" + ADD_WORN_COMMAND);
			return;
		}

		final int[] arrangement = LayoutBuilder.build(items, config.grouping(), config.blankRow());
		undoLayouts.put(tag, existing != null ? existing.getLayout().clone() : null);
		layoutManager.saveLayout(new Layout(tag, arrangement));
		redrawIfOpen(tag);
		panelNote("Organized '" + tag + "'. Click Undo to put it back.", OrganizeSummary.of(items));
		markCombatTabDirty();

		sendMessage("Organized " + items.size() + " items in '" + tag + "': " + summarize(items)
			+ ". Type ::" + ORGANIZE_COMMAND + " " + UNDO_ARGUMENT + " to put it back.");
	}

	/**
	 * Organize now in the side panel: the same auto layout as the tab menu's "Auto layout: Combat gear", shown as a
	 * preview with Keep and Undo in the chat box. Client thread.
	 */
	private void organizeFromPanel()
	{
		final CombatTabStatus status = readCombatTabStatus();
		final String blocker = SetupChecklist.organizeBlocker(status);
		if (blocker != null)
		{
			panelNote(blocker, null);
			refreshCombatTab();
			return;
		}

		final String tag = status.getTag();
		if (!(bankTagsService instanceof BankTagsPlugin))
		{
			// No way to show the preview: organize with the panel's Undo button and ::gearsort undo
			organize(tag);
			return;
		}

		final BankTagsPlugin bankTags = (BankTagsPlugin) bankTagsService;
		final Layout saved = layoutManager.loadLayout(tag);
		final List<GearItem> items = gearItems(tag, saved);
		if (items.isEmpty())
		{
			panelNote("Put some gear in the tab first.", null);
			return;
		}

		final Layout preview = new Layout(tag, LayoutBuilder.build(items, config.grouping(), config.blankRow()));
		final String summary = OrganizeSummary.of(items);
		// What the menu entry does: show the new layout without saving it, then ask
		bankTags.openTag(tag, preview);
		// Picking an option closes the menu too, and the close can run before or after the option (the close is
		// queued on the client thread; a click runs the option at once, a key press queues it after the close)
		final boolean[] answered = {false};
		chatboxPanelManager.openTextMenuInput("Tab laid out using the '" + AUTO_LAYOUT_NAME + "' layout.")
			.option("1. Keep", () -> clientThread.invoke(() ->
			{
				answered[0] = true;
				layoutManager.saveLayout(preview);
				// The plugin's own undo is for an older layout now
				undoLayouts.remove(tag);
				panelNote("Kept the new layout of '" + tag + "'.", summary);
				markCombatTabDirty();
			}))
			.option("2. Undo", () -> clientThread.invoke(() ->
			{
				answered[0] = true;
				bankTags.openTag(tag, saved);
				panelNote("Put back the old layout of '" + tag + "'.", null);
			}))
			.onClose(() ->
			{
				bankSearch.layoutBank();
				// Runs after a queued option, so only a menu closed without an answer (e.g. Escape) gets this note
				clientThread.invokeLater(() ->
				{
					if (!answered[0])
					{
						panelNote("Not kept. Open the tab again to see your old layout.", null);
					}
				});
			})
			.build();
		panelNote("Pick Keep or Undo in the chat box.", summary);
	}

	/** The player picked or typed a tag in the panel. Swing thread. */
	private void chooseTag(String raw)
	{
		final String tag = Text.standardize(raw);
		if (!tag.isEmpty() && !tag.equals(Text.standardize(config.tagName())))
		{
			configManager.setConfiguration(CombatGearOrganizerConfig.GROUP, CombatGearOrganizerConfig.TAG_NAME_KEY, tag);
		}
	}

	/** The player picked By style or By slot in the panel. Swing thread. */
	private void chooseGrouping(Grouping grouping)
	{
		if (grouping != config.grouping())
		{
			configManager.setConfiguration(CombatGearOrganizerConfig.GROUP, CombatGearOrganizerConfig.GROUPING_KEY, grouping);
		}
	}

	private void markCombatTabDirty()
	{
		combatTabDirty = true;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			// No game ticks while logged out
			clientThread.invokeLater(this::refreshCombatTab);
		}
	}

	/** Reads the Combat tab state and hands it to the panel if it changed. Client thread. */
	private void refreshCombatTab()
	{
		combatTabDirty = false;
		final CombatTabPanel panel = combatTabPanel;
		if (panel == null)
		{
			return;
		}
		final CombatTabStatus status = readCombatTabStatus();
		final Grouping grouping = config.grouping();
		lastBankOpen = status.isBankOpen();
		if (status.equals(shownStatus) && grouping == shownGrouping)
		{
			return;
		}
		shownStatus = status;
		shownGrouping = grouping;
		SwingUtilities.invokeLater(() -> panel.update(status, grouping));
	}

	/** Client thread. */
	private CombatTabStatus readCombatTabStatus()
	{
		final boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		final String tag = Text.standardize(Objects.toString(config.tagName(), ""));
		final List<String> tabs = new ArrayList<>();
		for (String t : Text.fromCSV(Objects.toString(
			configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG), "")))
		{
			final String name = Text.standardize(t);
			if (!name.isEmpty() && !tabs.contains(name))
			{
				tabs.add(name);
			}
		}
		final int tagged = tag.isEmpty() ? 0 : tagManager.getItemsForTag(tag).size();
		final boolean layout = !tag.isEmpty() && layoutManager.loadLayout(tag) != null;
		return new CombatTabStatus(loggedIn, isBankTagsActive(), loggedIn && isBankOpen(), tag, tabs, tagged, layout,
			undoLayouts.containsKey(tag));
	}

	/** Client thread. */
	private boolean isBankOpen()
	{
		final Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return items != null && !items.isHidden();
	}

	/** A line in the Combat tab section about what just happened. Any thread. */
	private void panelNote(String text, String summary)
	{
		final CombatTabPanel panel = combatTabPanel;
		if (panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.showNote(text, summary));
		}
	}

	/** @PluginDependency loads Bank Tags but doesn't turn it on, and without it no layout is drawn. */
	private boolean isBankTagsActive()
	{
		// Look the instance up: injecting BankTagsPlugin here would make Guice build a second, inactive one
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof BankTagsPlugin)
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}

	/** Puts back the layout from before the last ::gearsort of this tag. Must be called on the client thread. */
	private void undo(String rawTag)
	{
		final String tag = Text.standardize(rawTag);
		if (!undoLayouts.containsKey(tag))
		{
			sendMessage("Nothing to undo for '" + tag + "' since the plugin started.");
			panelNote("Nothing to undo for '" + tag + "'.", null);
			return;
		}
		final int[] previous = undoLayouts.remove(tag);
		if (previous == null)
		{
			layoutManager.removeLayout(tag);
		}
		else
		{
			layoutManager.saveLayout(new Layout(tag, previous));
		}
		redrawIfOpen(tag);
		sendMessage("Put back the previous layout of '" + tag + "'.");
		panelNote("Put back the previous layout of '" + tag + "'.", null);
		markCombatTabDirty();
	}

	/** Builds the layout for the auto layout menu entry, which shows a preview with Keep / Undo. */
	private int[] buildArrangement(String tag, Layout previous)
	{
		final List<GearItem> items = gearItems(Text.standardize(tag), previous);
		return items.isEmpty()
			? previous.getLayout()
			: LayoutBuilder.build(items, config.grouping(), config.blankRow());
	}

	private void redrawIfOpen(String tag)
	{
		// Redraw the tab if it's what the player is looking at
		final String activeTag = bankTagsService.getActiveTag();
		if (activeTag != null && tag.equals(Text.standardize(activeTag)))
		{
			bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		}
	}

	/** The tag's items, each as the concrete version the player has. Must be called on the client thread. */
	private List<GearItem> gearItems(String tag, Layout existing)
	{
		final TagItemResolver.Items lookup = new TagItemResolver.Items()
		{
			@Override
			public int canonicalize(int itemId)
			{
				return itemManager.canonicalize(itemId);
			}

			@Override
			public int variationBase(int itemId)
			{
				return ItemVariationMapping.map(itemId);
			}
		};

		final List<Integer> ids = TagItemResolver.resolve(
			tagManager.getItemsForTag(tag),
			existing != null ? existing.getLayout() : null,
			ownedItemIds(),
			lookup,
			this::bestVariation);

		return ids.stream()
			.map(this::toGearItem)
			.collect(Collectors.toList());
	}

	/** Everything in the bank, worn and in the inventory. The bank is remembered after it's closed. */
	private Set<Integer> ownedItemIds()
	{
		final Set<Integer> ids = new LinkedHashSet<>();
		for (int inventoryId : new int[]{InventoryID.WORN, InventoryID.BANK, InventoryID.INV})
		{
			final ItemContainer container = client.getItemContainer(inventoryId);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (item.getId() > 0)
				{
					ids.add(item.getId());
				}
			}
		}
		return ids;
	}

	/** For a tagged variation group the player has none of: the version with the best stats. */
	private int bestVariation(int baseId)
	{
		int best = baseId;
		int bestPower = Integer.MIN_VALUE;
		for (int id : ItemVariationMapping.getVariations(baseId))
		{
			final GearItem item = toGearItem(itemManager.canonicalize(id));
			if (item.getStyle() != GearStyle.SUPPLIES && item.getPower() > bestPower)
			{
				best = item.getItemId();
				bestPower = item.getPower();
			}
		}
		return best;
	}

	private GearItem toGearItem(int itemId)
	{
		final ItemComposition comp = itemManager.getItemComposition(itemId);
		final String name = comp.getName();
		final ItemStats stats = itemManager.getItemStats(itemId);

		if (stats == null || !stats.isEquipable() || stats.getEquipment() == null)
		{
			return new GearItem(itemId, name, GearSlot.NONE, GearStyle.SUPPLIES, 0);
		}

		final ItemEquipmentStats e = stats.getEquipment();
		final Bonuses bonuses = Bonuses.builder()
			.stab(e.getAstab())
			.slash(e.getAslash())
			.crush(e.getAcrush())
			.magic(e.getAmagic())
			.range(e.getArange())
			.defStab(e.getDstab())
			.defSlash(e.getDslash())
			.defCrush(e.getDcrush())
			.defMagic(e.getDmagic())
			.defRange(e.getDrange())
			.strength(e.getStr())
			.rangedStrength(e.getRstr())
			.magicDamage(e.getMdmg())
			.prayer(e.getPrayer())
			.attackSpeed(e.getAspeed())
			.build();

		final GearSlot slot = GearSlot.fromEquipmentIndex(e.getSlot());
		final GearStyle style = GearClassifier.classify(name, slot, bonuses);
		return new GearItem(itemId, name, slot, style, GearClassifier.power(style, name, slot, bonuses));
	}

	private static String joinFrom(String[] args, int from)
	{
		return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
	}

	private static String summarize(List<GearItem> items)
	{
		final Map<GearStyle, Integer> counts = new EnumMap<>(GearStyle.class);
		items.forEach(i -> counts.merge(i.getStyle(), 1, Integer::sum));

		final List<String> parts = new ArrayList<>();
		counts.forEach((style, count) -> parts.add(count + " " + style.getDisplayName().toLowerCase()));
		return String.join(", ", parts);
	}

	private void sendMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder().append("[Gear Organizer] ").append(message).build())
			.build());
	}
}
