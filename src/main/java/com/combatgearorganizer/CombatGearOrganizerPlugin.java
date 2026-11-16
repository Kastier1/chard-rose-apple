package com.combatgearorganizer;

import com.combatgearorganizer.slayer.SlayerHelper;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.util.HotkeyListener;
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

	private boolean slayerStarted;

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
		updateSlayerPanel();
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
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
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
		if (!CombatGearOrganizerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		updateSlayerPanel();
		if (slayerStarted)
		{
			slayerHelper.onConfigChanged();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
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

		sendMessage("Tagged " + added + " worn item" + (added == 1 ? "" : "s") + " with '" + tag + "'.");

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

		sendMessage("Organized " + items.size() + " items in '" + tag + "': " + summarize(items)
			+ ". Type ::" + ORGANIZE_COMMAND + " " + UNDO_ARGUMENT + " to put it back.");
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
