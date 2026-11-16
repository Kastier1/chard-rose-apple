package com.combatgearorganizer;

import com.combatgearorganizer.slayer.SlayerHelper;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
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
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
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
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
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

	private boolean slayerStarted;

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
		updateSlayerPanel();
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkeyListener);
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
	public void onCommandExecuted(CommandExecuted event)
	{
		final String command = event.getCommand().toLowerCase();
		final String[] args = event.getArguments();
		final String tag = args.length > 0 ? String.join(" ", args) : config.tagName();

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

		final Set<Integer> itemIds = collectItemIds(tag);
		if (itemIds.isEmpty())
		{
			sendMessage("Bank tag '" + tag + "' has no items. Tag some gear first, or wear it and type ::" + ADD_WORN_COMMAND);
			return;
		}

		final List<GearItem> items = itemIds.stream()
			.map(this::toGearItem)
			.collect(Collectors.toList());

		final int[] arrangement = LayoutBuilder.build(items, config.grouping(), config.blankRow());
		layoutManager.saveLayout(new Layout(tag, arrangement));

		// Redraw the tab if it's what the player is looking at
		final String activeTag = bankTagsService.getActiveTag();
		if (activeTag != null && tag.equals(Text.standardize(activeTag)))
		{
			bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		}

		sendMessage("Organized " + items.size() + " items in '" + tag + "': " + summarize(items));
	}

	private Set<Integer> collectItemIds(String tag)
	{
		final Set<Integer> ids = new LinkedHashSet<>();

		// Negative ids are "variation" tags (any version of the item); the absolute value is the base item
		for (int id : tagManager.getItemsForTag(tag))
		{
			ids.add(itemManager.canonicalize(Math.abs(id)));
		}

		// Keep anything already placed in the existing layout as well
		final Layout existing = layoutManager.loadLayout(tag);
		if (existing != null)
		{
			Arrays.stream(existing.getLayout())
				.filter(id -> id > 0)
				.map(itemManager::canonicalize)
				.forEach(ids::add);
		}

		return ids;
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

		final GearStyle style = GearClassifier.classify(bonuses);
		return new GearItem(itemId, name, GearSlot.fromEquipmentIndex(e.getSlot()), style, GearClassifier.power(style, bonuses));
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
