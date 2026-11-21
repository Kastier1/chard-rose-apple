package com.combatgearorganizer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Grouping
{
	BY_STYLE("Combat style", "By style", "A section each for melee, ranged, magic and prayer gear, in slot order."),
	BY_SLOT("Equipment slot", "By slot", "A section for each slot, with melee, ranged and magic gear in that order."),
	BY_SET("Gear set", "By set", "A row for each armour slot and a column for each set, with its weapon, so a set lines up.");

	/** The name in the plugin settings. */
	private final String displayName;
	/** The short name in the side panel. */
	private final String shortName;
	/** One line for the side panel. */
	private final String description;

	@Override
	public String toString()
	{
		return displayName;
	}
}
