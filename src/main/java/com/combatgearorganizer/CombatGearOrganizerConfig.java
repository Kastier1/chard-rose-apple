package com.combatgearorganizer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(CombatGearOrganizerConfig.GROUP)
public interface CombatGearOrganizerConfig extends Config
{
	String GROUP = "combatgearorganizer";

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
		return true;
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
}
