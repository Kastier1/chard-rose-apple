package com.combatgearorganizer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(CombatGearOrganizerConfig.GROUP)
public interface CombatGearOrganizerConfig extends Config
{
	String GROUP = "combatgearorganizer";

	@ConfigSection(
		name = "Slayer",
		description = "Slayer task panel and directions",
		position = 10
	)
	String slayerSection = "slayer";

	@ConfigSection(
		name = "Item values",
		description = "How the plugin values items, such as drops in the slayer panel",
		position = 20
	)
	String valuesSection = "values";

	@ConfigItem(
		keyName = "tagName",
		name = "Bank tag",
		description = "The bank tag tab that holds your combat gear",
		position = 0
	)
	default String tagName()
	{
		return "combat";
	}

	@ConfigItem(
		keyName = "grouping",
		name = "Group by",
		description = "Combat style: melee / ranged / magic / prayer sections, each ordered by slot.<br>"
			+ "Equipment slot: one section per slot, each ordered by combat style.",
		position = 1
	)
	default Grouping grouping()
	{
		return Grouping.BY_STYLE;
	}

	@ConfigItem(
		keyName = "blankRow",
		name = "Blank row between sections",
		description = "Leave an empty row between each section to make them easier to tell apart",
		position = 2
	)
	default boolean blankRow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "organizeOnAdd",
		name = "Re-organize after ::gearadd",
		description = "Immediately re-organize the tab after tagging your worn gear with ::gearadd",
		position = 3
	)
	default boolean organizeOnAdd()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hotkey",
		name = "Organize hotkey",
		description = "Press this to organize the combat gear tab (works best with the tab open)",
		position = 4
	)
	default Keybind hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "showSlayerPanel",
		name = "Slayer task panel",
		description = "Show a side panel with your current slayer task and where to do it",
		section = slayerSection,
		position = 11
	)
	default boolean showSlayerPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWildernessLocations",
		name = "Show Wilderness locations",
		description = "List places and spawns inside the Wilderness for tasks from masters other than Krystilia.<br>"
			+ "Krystilia's tasks always show her Wilderness places, and Konar's always show her assigned place.",
		section = slayerSection,
		position = 12
	)
	default boolean showWildernessLocations()
	{
		return false;
	}

	@ConfigItem(
		keyName = "taskSummaryChat",
		name = "Task summary in chat",
		description = "When you finish a task, also put the summary in your chat box,<br>"
			+ "e.g. \"Task done: 140 Abyssal demons in 24 min, 2 superiors, 1.2M loot\".",
		section = slayerSection,
		position = 13
	)
	default boolean taskSummaryChat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "compactPanel",
		name = "Compact panel",
		description = "Show only the task, its progress, the route and the places with their Go buttons.<br>"
			+ "Hides the monster details and drops.",
		section = slayerSection,
		position = 14
	)
	default boolean compactPanel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "itemValue",
		name = "Item value",
		description = "Grand Exchange: what the item sells for. High alchemy: the coins you get for casting High Level Alchemy on it.<br>"
			+ "Used for every value the plugin shows, including the loot value of your current task.",
		section = valuesSection,
		position = 21
	)
	default ItemValueMode itemValue()
	{
		return ItemValueMode.GRAND_EXCHANGE;
	}
}
